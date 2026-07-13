package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.io.TempDir

class ConsumerStateTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `journal round trips exact derived paths and immutable evidence`() {
        val journal = journal(oldIntent = digest("old-intent"), oldLock = null)
        assertEquals(
            listOf(ConsumerPersistedFile.INTENT, ConsumerPersistedFile.LOCK),
            journal.records.map(TransactionFileRecord::file),
        )
        journal.records.forEach { record ->
            val prefix = ".intelligence/.marketplace-transactions/${journal.transactionId.render()}/"
            assertEquals(prefix + record.file.stem + ".new", record.stagedPath.render())
            assertEquals(prefix + record.file.stem + ".old", record.backupPath.render())
        }
        val reparsed = assertIs<MarketplaceTransactionJournalParsing.Parsed>(
            MarketplaceTransactionJournal.parse(journal.canonicalBytes()),
        ).journal
        assertContentEquals(journal.canonicalBytes(), reparsed.canonicalBytes())
        assertEquals(journal.transactionId, reparsed.transactionId)
    }

    @Test
    fun `recovery completes intact new content across partial rename states`() {
        val oldIntent = digest("old-intent")
        val oldLock = digest("old-lock")
        val journal = journal(oldIntent, oldLock)
        val records = journal.records.associateBy(TransactionFileRecord::file)

        assertEquals(
            MarketplaceTransactionRecovery.CompleteNew(
                listOf(
                    MarketplaceTransactionRecoveryAction.KeepNew(ConsumerPersistedFile.INTENT),
                    MarketplaceTransactionRecoveryAction.KeepNew(ConsumerPersistedFile.LOCK),
                ),
            ),
            journal.recovery(
                observations(
                    intent = observation(target = records.getValue(ConsumerPersistedFile.INTENT).newSha256),
                    lock = observation(target = records.getValue(ConsumerPersistedFile.LOCK).newSha256),
                ),
            ),
        )

        assertEquals(
            MarketplaceTransactionRecovery.CompleteNew(
                listOf(
                    MarketplaceTransactionRecoveryAction.KeepNew(ConsumerPersistedFile.INTENT),
                    MarketplaceTransactionRecoveryAction.PromoteStaged(ConsumerPersistedFile.LOCK),
                ),
            ),
            journal.recovery(
                observations(
                    intent = observation(
                        target = records.getValue(ConsumerPersistedFile.INTENT).newSha256,
                        backup = oldIntent,
                    ),
                    lock = observation(
                        target = oldLock,
                        staged = records.getValue(ConsumerPersistedFile.LOCK).newSha256,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `recovery restores the old pair when staged evidence is incomplete`() {
        val oldIntent = digest("old-intent")
        val journal = journal(oldIntent, oldLock = null)
        val records = journal.records.associateBy(TransactionFileRecord::file)
        assertEquals(
            MarketplaceTransactionRecovery.RestoreOld(
                listOf(
                    MarketplaceTransactionRecoveryAction.RestoreBackup(ConsumerPersistedFile.INTENT),
                    MarketplaceTransactionRecoveryAction.KeepOld(ConsumerPersistedFile.LOCK),
                ),
            ),
            journal.recovery(
                observations(
                    intent = observation(
                        target = records.getValue(ConsumerPersistedFile.INTENT).newSha256,
                        backup = oldIntent,
                    ),
                    lock = observation(
                        target = null,
                        staged = digest("corrupt-stage"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `recovery rejects unknown target bytes instead of guessing`() {
        val journal = journal(digest("old-intent"), digest("old-lock"))
        assertEquals(
            MarketplaceTransactionRecovery.Unrecoverable(
                ConsumerPersistedFile.INTENT,
                MarketplaceTransactionRecoveryFailure.UnknownTarget,
            ),
            journal.recovery(
                observations(
                    intent = observation(target = digest("unknown")),
                    lock = observation(target = digest("old-lock")),
                ),
            ),
        )
    }

    @Test
    fun `consumer state classifies every persisted file combination fail closed`() {
        val intent = localIntent()
        val lock = localLock()
        val staleIntent = localIntent(packageName = "beta-tools")
        val journal = journal(intent.sha256(), lock.sha256())

        assertEquals(ConsumerState.Uninitialized, ConsumerState.classify(null, null, null))
        assertIs<ConsumerState.Unresolved>(ConsumerState.classify(intent.canonicalBytes(), null, null))
        assertIs<ConsumerState.Orphaned>(ConsumerState.classify(null, lock.canonicalBytes(), null))
        assertIs<ConsumerState.Resolved>(
            ConsumerState.classify(intent.canonicalBytes(), lock.canonicalBytes(), null),
        )
        assertIs<ConsumerState.Stale>(
            ConsumerState.classify(staleIntent.canonicalBytes(), lock.canonicalBytes(), null),
        )
        assertIs<ConsumerState.Recovering>(
            ConsumerState.classify(null, null, journal.canonicalBytes()),
        )
        assertEquals(
            ConsumerState.Invalid(ConsumerStateInvalidity.Intent(MarketplaceIntentRejection.RootMustBeObject)),
            ConsumerState.classify("not-json\n".encodeToByteArray(), null, null),
        )
        assertEquals(
            ConsumerState.Invalid(
                ConsumerStateInvalidity.Journal(MarketplaceTransactionJournalRejection.RootMustBeObject),
            ),
            ConsumerState.classify(intent.canonicalBytes(), lock.canonicalBytes(), "bad\n".encodeToByteArray()),
        )
    }

    @Test
    fun `journal parser rejects changed derived paths transaction identity and unknown fields`() {
        val journal = journal(null, null)
        val valid = journal.canonicalBytes().decodeToString()
        assertEquals(
            MarketplaceTransactionJournalRejection.UnexpectedStagedPath(ConsumerPersistedFile.INTENT),
            rejected(valid.replace("adaptable.marketplace.json.new", "other.new")),
        )
        assertEquals(
            MarketplaceTransactionJournalRejection.UnexpectedTransactionId,
            rejected(valid.replace(journal.transactionId.render(), "0".repeat(64))),
        )
        assertEquals(
            MarketplaceTransactionJournalRejection.UnknownField("$", "timestamp"),
            rejected(valid.replace("\"transactionId\"", "\"timestamp\":1,\"transactionId\"")),
        )
    }

    @Test
    fun `repository mutation lease seals concurrent writers and rejects symlinked state roots`() {
        val first = assertIs<ConsumerMutationLeaseAcquisition.Acquired>(
            ConsumerMutationLease.acquire(temporaryDirectory),
        )
        assertEquals(
            ConsumerMutationLeaseAcquisition.Rejected(ConsumerMutationLeaseRejection.ConcurrentMutation),
            ConsumerMutationLease.acquire(temporaryDirectory),
        )
        first.lease.close()
        assertIs<ConsumerMutationLeaseAcquisition.Acquired>(
            ConsumerMutationLease.acquire(temporaryDirectory),
        ).lease.close()

        val linkedRepository = temporaryDirectory.resolve("linked-repository")
        Files.createDirectory(linkedRepository)
        Files.createSymbolicLink(linkedRepository.resolve(".intelligence"), temporaryDirectory.resolve(".intelligence"))
        assertEquals(
            ConsumerMutationLeaseAcquisition.Rejected(ConsumerMutationLeaseRejection.InvalidStateDirectory),
            ConsumerMutationLease.acquire(linkedRepository),
        )
    }

    private fun journal(
        oldIntent: Sha256Digest?,
        oldLock: Sha256Digest?,
    ): MarketplaceTransactionJournal =
        assertIs<MarketplaceTransactionJournalMaterialization.Materialized>(
            MarketplaceTransactionJournal.materialize(
                oldIntentSha256 = oldIntent,
                oldLockSha256 = oldLock,
                newIntentSha256 = digest("new-intent"),
                newLockSha256 = digest("new-lock"),
            ),
        ).journal

    private fun rejected(document: String): MarketplaceTransactionJournalRejection =
        assertIs<MarketplaceTransactionJournalParsing.Rejected>(
            MarketplaceTransactionJournal.parse(document.encodeToByteArray()),
        ).reason

    private fun observations(
        intent: TransactionFileObservation,
        lock: TransactionFileObservation,
    ): Map<ConsumerPersistedFile, TransactionFileObservation> =
        mapOf(ConsumerPersistedFile.INTENT to intent, ConsumerPersistedFile.LOCK to lock)

    private fun observation(
        target: Sha256Digest? = null,
        staged: Sha256Digest? = null,
        backup: Sha256Digest? = null,
    ): TransactionFileObservation = TransactionFileObservation(target, staged, backup)

    private fun localIntent(packageName: String = "alpha-tools"): MarketplaceIntent {
        val selection =
            assertIs<MarketplaceIntentSelectionCreation.Created>(
                MarketplaceIntentSelection.create(
                    marketplaceId("alpha-marketplace"),
                    MarketplaceIntentSource.LocalSnapshot(
                        relativeDirectory("fixtures/alpha"),
                        digest("alpha-index"),
                    ),
                    listOf(packageName(packageName)),
                ),
            ).selection
        return assertIs<MarketplaceIntentMaterialization.Materialized>(
            MarketplaceIntent.materialize(listOf(selection)),
        ).intent
    }

    private fun localLock(): MarketplaceLock {
        val source = MarketplaceLockSource.LocalSnapshot(relativeDirectory("fixtures/alpha"))
        val entry =
            assertIs<MarketplaceLockEntryCreation.Created>(
                MarketplaceLockEntry.create(
                    marketplaceId("alpha-marketplace"),
                    source,
                    localAsset("marketplace.json", "alpha-index"),
                    localAsset("SHA256SUMS", "alpha-checksum"),
                    listOf(
                        LockedPackage(
                            packageName("alpha-tools"),
                            localAsset("package-alpha-tools.zip", "alpha-package"),
                        ),
                    ),
                ),
            ).entry
        return assertIs<MarketplaceLockMaterialization.Materialized>(
            MarketplaceLock.materialize(listOf(entry)),
        ).lock
    }

    private fun localAsset(
        name: String,
        content: String,
    ): LockedAsset.Local = LockedAsset.Local(releaseAsset(name), content.length, digest(content))

    private fun digest(text: String): Sha256Digest = Sha256Digest.compute(text.encodeToByteArray())

    private fun relativeDirectory(raw: String): ConsumerRelativeDirectory =
        assertIs<ConsumerRelativeDirectoryParsing.Parsed>(ConsumerRelativeDirectory.parse(raw)).directory

    private fun releaseAsset(raw: String): ReleaseAssetName =
        assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse(raw)).name

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value
}
