package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ConsumerStateRepositoryTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `commit planning is side effect free and execution atomically writes one resolved pair`() {
        val pair = pair("alpha-tools")
        val plan = assertIs<ConsumerCommitPlanning.Planned>(
            ConsumerStateRepository.planCommit(repository, pair.intent, pair.lock),
        ).plan

        assertIs<ConsumerState.Uninitialized>(plan.before)
        assertTrue(plan.changed)
        assertFalse(Files.exists(repository.resolve(".intelligence")))

        assertIs<ConsumerCommitExecution.Committed>(ConsumerStateRepository.execute(plan))
        val read = assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(repository))
        assertIs<ConsumerState.Resolved>(read.state)
        assertContentEquals(pair.intent.canonicalBytes(), Files.readAllBytes(intentPath()))
        assertContentEquals(pair.lock.canonicalBytes(), Files.readAllBytes(lockPath()))
        assertFalse(Files.exists(journalPath()))

        val repeated = assertIs<ConsumerCommitPlanning.Planned>(
            ConsumerStateRepository.planCommit(repository, pair.intent, pair.lock),
        ).plan
        assertFalse(repeated.changed)
        assertIs<ConsumerCommitExecution.Unchanged>(ConsumerStateRepository.execute(repeated))
        assertContentEquals(pair.intent.canonicalBytes(), Files.readAllBytes(intentPath()))
        assertContentEquals(pair.lock.canonicalBytes(), Files.readAllBytes(lockPath()))
    }

    @Test
    fun `execution rejects a changed precondition without overwriting it`() {
        val first = pair("alpha-tools")
        val second = pair("beta-tools")
        val plan = assertIs<ConsumerCommitPlanning.Planned>(
            ConsumerStateRepository.planCommit(repository, first.intent, first.lock),
        ).plan
        Files.createDirectories(intentPath().parent)
        Files.write(intentPath(), second.intent.canonicalBytes())

        assertIs<ConsumerCommitRejection.PreconditionChanged>(
            assertIs<ConsumerCommitExecution.Rejected>(ConsumerStateRepository.execute(plan)).reason,
        )
        assertContentEquals(second.intent.canonicalBytes(), Files.readAllBytes(intentPath()))
        assertFalse(Files.exists(lockPath()))
        assertFalse(Files.exists(journalPath()))
    }

    @Test
    fun `recovery completes a partially promoted new pair`() {
        val old = pair("alpha-tools")
        commit(old)
        val replacement = pair("beta-tools")
        val journal = journal(old, replacement)
        writeInterruptedLayout(journal, old, replacement, promoteIntent = true, stageLock = true)

        val plan = assertIs<ConsumerRecoveryPlanning.Planned>(
            ConsumerStateRepository.planRecovery(repository),
        ).plan
        val completion = assertIs<MarketplaceTransactionRecovery.CompleteNew>(plan.recovery)
        assertEquals(
            listOf(
                MarketplaceTransactionRecoveryAction.KeepNew(ConsumerPersistedFile.INTENT),
                MarketplaceTransactionRecoveryAction.PromoteStaged(ConsumerPersistedFile.LOCK),
            ),
            completion.actions,
        )
        assertContentEquals(old.lock.canonicalBytes(), Files.readAllBytes(lockPath()))

        val recovered = assertIs<ConsumerRecoveryExecution.Recovered>(
            ConsumerStateRepository.execute(plan),
        )
        assertEquals(ConsumerRecoveryOutcome.COMPLETED_NEW, recovered.outcome)
        assertContentEquals(replacement.intent.canonicalBytes(), Files.readAllBytes(intentPath()))
        assertContentEquals(replacement.lock.canonicalBytes(), Files.readAllBytes(lockPath()))
        assertFalse(Files.exists(journalPath()))
        assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(repository)).state,
        )
    }

    @Test
    fun `recovery restores the old pair when completion is impossible`() {
        val old = pair("alpha-tools")
        commit(old)
        val replacement = pair("beta-tools")
        val journal = journal(old, replacement)
        writeInterruptedLayout(journal, old, replacement, promoteIntent = true, stageLock = false)

        val plan = assertIs<ConsumerRecoveryPlanning.Planned>(
            ConsumerStateRepository.planRecovery(repository),
        ).plan
        val restoration = assertIs<MarketplaceTransactionRecovery.RestoreOld>(plan.recovery)
        assertEquals(
            listOf(
                MarketplaceTransactionRecoveryAction.RestoreBackup(ConsumerPersistedFile.INTENT),
                MarketplaceTransactionRecoveryAction.KeepOld(ConsumerPersistedFile.LOCK),
            ),
            restoration.actions,
        )

        val recovered = assertIs<ConsumerRecoveryExecution.Recovered>(
            ConsumerStateRepository.execute(plan),
        )
        assertEquals(ConsumerRecoveryOutcome.RESTORED_OLD, recovered.outcome)
        assertContentEquals(old.intent.canonicalBytes(), Files.readAllBytes(intentPath()))
        assertContentEquals(old.lock.canonicalBytes(), Files.readAllBytes(lockPath()))
        assertFalse(Files.exists(journalPath()))
    }

    @Test
    fun `unknown interrupted target is unrecoverable and unchanged`() {
        val old = pair("alpha-tools")
        commit(old)
        val replacement = pair("beta-tools")
        val journal = journal(old, replacement)
        writeInterruptedLayout(journal, old, replacement, promoteIntent = false, stageLock = false)
        val unknown = "unknown target\n".encodeToByteArray()
        Files.write(intentPath(), unknown)

        val rejected = assertIs<ConsumerRecoveryPlanning.Rejected>(
            ConsumerStateRepository.planRecovery(repository),
        )
        assertEquals(
            ConsumerRecoveryRejection.Unrecoverable(
                ConsumerPersistedFile.INTENT,
                MarketplaceTransactionRecoveryFailure.UnknownTarget,
            ),
            rejected.reason,
        )
        assertContentEquals(unknown, Files.readAllBytes(intentPath()))
        assertTrue(Files.exists(journalPath()))
    }

    @Test
    fun `state readers reject symbolic files without following them`() {
        val pair = pair("alpha-tools")
        val external = repository.resolve("external-intent.json")
        Files.write(external, pair.intent.canonicalBytes())
        Files.createDirectories(intentPath().parent)
        Files.createSymbolicLink(intentPath(), external)

        val rejected = assertIs<ConsumerStateReading.Rejected>(
            ConsumerStateRepository.read(repository),
        )
        assertEquals(ConsumerStateReadRejection.NonRegularFile(intentPath()), rejected.reason)
        assertContentEquals(pair.intent.canonicalBytes(), Files.readAllBytes(external))
        assertTrue(Files.isSymbolicLink(intentPath()))
    }

    @Test
    fun `deleting the final pair is planned without effects and executes as uninitialized state`() {
        val pair = pair("alpha-tools")
        commit(pair)
        val plan = assertIs<ConsumerDeletionPlanning.Planned>(
            ConsumerStateRepository.planDeletion(repository),
        ).plan
        assertIs<ConsumerState.Resolved>(plan.before)
        assertTrue(plan.changed)
        assertTrue(Files.exists(intentPath()))
        assertTrue(Files.exists(lockPath()))

        assertIs<ConsumerDeletionExecution.Deleted>(ConsumerStateRepository.execute(plan))
        assertFalse(Files.exists(intentPath()))
        assertFalse(Files.exists(lockPath()))
        assertFalse(Files.exists(journalPath()))
        assertEquals(
            ConsumerState.Uninitialized,
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(repository)).state,
        )

        val repeated = assertIs<ConsumerDeletionPlanning.Planned>(
            ConsumerStateRepository.planDeletion(repository),
        ).plan
        assertFalse(repeated.changed)
        assertIs<ConsumerDeletionExecution.Unchanged>(ConsumerStateRepository.execute(repeated))
    }

    private fun commit(pair: PairEvidence) {
        val plan = assertIs<ConsumerCommitPlanning.Planned>(
            ConsumerStateRepository.planCommit(repository, pair.intent, pair.lock),
        ).plan
        assertIs<ConsumerCommitExecution.Committed>(ConsumerStateRepository.execute(plan))
    }

    private fun journal(
        old: PairEvidence,
        replacement: PairEvidence,
    ): MarketplaceTransactionJournal =
        assertIs<MarketplaceTransactionJournalMaterialization.Materialized>(
            MarketplaceTransactionJournal.materialize(
                old.intent.sha256(),
                old.lock.sha256(),
                replacement.intent.sha256(),
                replacement.lock.sha256(),
            ),
        ).journal

    private fun writeInterruptedLayout(
        journal: MarketplaceTransactionJournal,
        old: PairEvidence,
        replacement: PairEvidence,
        promoteIntent: Boolean,
        stageLock: Boolean,
    ) {
        Files.write(journalPath(), journal.canonicalBytes())
        journal.records.forEach { record ->
            val staged = repository.resolve(record.stagedPath.render())
            val backup = repository.resolve(record.backupPath.render())
            Files.createDirectories(staged.parent)
            val oldBytes =
                when (record.file) {
                    ConsumerPersistedFile.INTENT -> old.intent.canonicalBytes()
                    ConsumerPersistedFile.LOCK -> old.lock.canonicalBytes()
                }
            Files.write(backup, oldBytes)
            val shouldStage = record.file == ConsumerPersistedFile.INTENT || stageLock
            if (shouldStage) {
                val newBytes =
                    when (record.file) {
                        ConsumerPersistedFile.INTENT -> replacement.intent.canonicalBytes()
                        ConsumerPersistedFile.LOCK -> replacement.lock.canonicalBytes()
                    }
                Files.write(staged, newBytes)
            }
        }
        if (promoteIntent) {
            val intentRecord = journal.records.single { record -> record.file == ConsumerPersistedFile.INTENT }
            Files.move(
                repository.resolve(intentRecord.stagedPath.render()),
                intentPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun pair(packageName: String): PairEvidence {
        val marketplaceId = marketplaceId("example-marketplace")
        val packageValue = packageName(packageName)
        val directory = relativeDirectory("snapshots/current")
        val indexBytes = "index:$packageName".encodeToByteArray()
        val selection = assertIs<MarketplaceIntentSelectionCreation.Created>(
            MarketplaceIntentSelection.create(
                marketplaceId,
                MarketplaceIntentSource.LocalSnapshot(directory, Sha256Digest.compute(indexBytes)),
                listOf(packageValue),
            ),
        ).selection
        val intent = assertIs<MarketplaceIntentMaterialization.Materialized>(
            MarketplaceIntent.materialize(listOf(selection)),
        ).intent
        val entry = assertIs<MarketplaceLockEntryCreation.Created>(
            MarketplaceLockEntry.create(
                marketplaceId = marketplaceId,
                source = MarketplaceLockSource.LocalSnapshot(directory),
                index = localAsset("marketplace.json", indexBytes),
                checksum = localAsset("SHA256SUMS", "checksums:$packageName".encodeToByteArray()),
                packages =
                    listOf(
                        LockedPackage(
                            packageValue,
                            localAsset("package-$packageName.zip", "package:$packageName".encodeToByteArray()),
                        ),
                    ),
            ),
        ).entry
        val lock = assertIs<MarketplaceLockMaterialization.Materialized>(
            MarketplaceLock.materialize(listOf(entry)),
        ).lock
        return PairEvidence(intent, lock)
    }

    private fun localAsset(
        name: String,
        bytes: ByteArray,
    ): LockedAsset.Local =
        LockedAsset.Local(
            releaseAsset(name),
            bytes.size,
            Sha256Digest.compute(bytes),
        )

    private fun intentPath(): Path = repository.resolve(ConsumerPersistedFile.INTENT.targetPath)

    private fun lockPath(): Path = repository.resolve(ConsumerPersistedFile.LOCK.targetPath)

    private fun journalPath(): Path = repository.resolve(".intelligence/.marketplace-transaction.json")

    private fun releaseAsset(raw: String): ReleaseAssetName =
        assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse(raw)).name

    private fun relativeDirectory(raw: String): ConsumerRelativeDirectory =
        assertIs<ConsumerRelativeDirectoryParsing.Parsed>(ConsumerRelativeDirectory.parse(raw)).directory

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value

    private data class PairEvidence(
        val intent: MarketplaceIntent,
        val lock: MarketplaceLock,
    )
}
