package intelligence.cli.portable

internal enum class ConsumerPersistedFile(
    val targetPath: String,
    val stem: String,
) {
    INTENT(".intelligence/adaptable.marketplace.json", "adaptable.marketplace.json"),
    LOCK(".intelligence/marketplace-lock.json", "marketplace-lock.json"),
}

@JvmInline
internal value class MarketplaceTransactionId private constructor(
    private val digest: Sha256Digest,
) {
    fun render(): String = digest.render()

    companion object {
        fun parse(candidate: String): MarketplaceTransactionIdParsing =
            when (val parsed = Sha256Digest.parse(candidate)) {
                is Sha256DigestParsing.Parsed ->
                    MarketplaceTransactionIdParsing.Parsed(MarketplaceTransactionId(parsed.digest))
                is Sha256DigestParsing.Rejected ->
                    MarketplaceTransactionIdParsing.Rejected(parsed.reason)
            }

        internal fun derive(
            oldIntent: Sha256Digest?,
            oldLock: Sha256Digest?,
            newIntent: Sha256Digest?,
            newLock: Sha256Digest?,
        ): MarketplaceTransactionId {
            val preimage =
                buildString {
                    append("MARKETPLACE_TRANSACTION_V1\n")
                    append(oldIntent?.render() ?: "ABSENT")
                    append('\n')
                    append(oldLock?.render() ?: "ABSENT")
                    append('\n')
                    append(newIntent?.render() ?: "ABSENT")
                    append('\n')
                    append(newLock?.render() ?: "ABSENT")
                    append('\n')
                }.encodeToByteArray()
            return MarketplaceTransactionId(Sha256Digest.compute(preimage))
        }
    }
}

internal sealed interface MarketplaceTransactionIdParsing {
    data class Parsed(val id: MarketplaceTransactionId) : MarketplaceTransactionIdParsing

    data class Rejected(val reason: Sha256DigestRejection) : MarketplaceTransactionIdParsing
}

@JvmInline
internal value class MarketplaceTransactionPath private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): MarketplaceTransactionPathParsing =
            if (candidate.length <= MAX_TRANSACTION_PATH_BYTES &&
                candidate.encodeToByteArray().size <= MAX_TRANSACTION_PATH_BYTES &&
                transactionPathPattern.matches(candidate)
            ) {
                MarketplaceTransactionPathParsing.Parsed(MarketplaceTransactionPath(candidate))
            } else {
                MarketplaceTransactionPathParsing.Rejected
            }

        internal fun staged(
            transactionId: MarketplaceTransactionId,
            file: ConsumerPersistedFile,
        ): MarketplaceTransactionPath = trusted(transactionId, file, "new")

        internal fun backup(
            transactionId: MarketplaceTransactionId,
            file: ConsumerPersistedFile,
        ): MarketplaceTransactionPath = trusted(transactionId, file, "old")

        private fun trusted(
            transactionId: MarketplaceTransactionId,
            file: ConsumerPersistedFile,
            suffix: String,
        ): MarketplaceTransactionPath =
            MarketplaceTransactionPath(
                ".intelligence/.marketplace-transactions/${transactionId.render()}/${file.stem}.$suffix",
            )
    }
}

internal sealed interface MarketplaceTransactionPathParsing {
    data class Parsed(val path: MarketplaceTransactionPath) : MarketplaceTransactionPathParsing

    data object Rejected : MarketplaceTransactionPathParsing
}

internal data class TransactionFileRecord(
    val file: ConsumerPersistedFile,
    val oldSha256: Sha256Digest?,
    val newSha256: Sha256Digest?,
    val stagedPath: MarketplaceTransactionPath,
    val backupPath: MarketplaceTransactionPath,
)

internal data class TransactionFileObservation(
    val targetSha256: Sha256Digest?,
    val stagedSha256: Sha256Digest?,
    val backupSha256: Sha256Digest?,
)

internal class MarketplaceTransactionJournal private constructor(
    val transactionId: MarketplaceTransactionId,
    records: List<TransactionFileRecord>,
    private val document: CanonicalJsonDocument,
) {
    val records: List<TransactionFileRecord> = records.toList()

    fun canonicalBytes(): ByteArray = document.bytes()

    fun sha256(): Sha256Digest = document.sha256()

    fun recovery(
        observations: Map<ConsumerPersistedFile, TransactionFileObservation>,
    ): MarketplaceTransactionRecovery {
        ConsumerPersistedFile.entries.firstOrNull { file -> file !in observations }?.let { missing ->
            return MarketplaceTransactionRecovery.Unrecoverable(
                missing,
                MarketplaceTransactionRecoveryFailure.MissingObservation,
            )
        }
        val recordsByFile = records.associateBy(TransactionFileRecord::file)
        val completion =
            ConsumerPersistedFile.entries.mapNotNull { file ->
                completionAction(recordsByFile.getValue(file), observations.getValue(file))
            }
        if (completion.size == ConsumerPersistedFile.entries.size) {
            return MarketplaceTransactionRecovery.CompleteNew(completion)
        }

        val restoration = mutableListOf<MarketplaceTransactionRecoveryAction>()
        ConsumerPersistedFile.entries.forEach { file ->
            val action = restorationAction(recordsByFile.getValue(file), observations.getValue(file))
                ?: return MarketplaceTransactionRecovery.Unrecoverable(
                    file,
                    MarketplaceTransactionRecoveryFailure.UnknownTarget,
                )
            restoration += action
        }
        return MarketplaceTransactionRecovery.RestoreOld(restoration)
    }

    companion object {
        fun parse(bytes: ByteArray): MarketplaceTransactionJournalParsing =
            MarketplaceTransactionJournalParser.parse(bytes)

        fun materialize(
            oldIntentSha256: Sha256Digest?,
            oldLockSha256: Sha256Digest?,
            newIntentSha256: Sha256Digest?,
            newLockSha256: Sha256Digest?,
        ): MarketplaceTransactionJournalMaterialization {
            if ((newIntentSha256 == null) != (newLockSha256 == null)) {
                return MarketplaceTransactionJournalMaterialization.Rejected(
                    MarketplaceTransactionJournalRejection.IncompleteNewPair,
                )
            }
            val transactionId =
                MarketplaceTransactionId.derive(
                    oldIntentSha256,
                    oldLockSha256,
                    newIntentSha256,
                    newLockSha256,
                )
            val records =
                listOf(
                    TransactionFileRecord(
                        ConsumerPersistedFile.INTENT,
                        oldIntentSha256,
                        newIntentSha256,
                        MarketplaceTransactionPath.staged(transactionId, ConsumerPersistedFile.INTENT),
                        MarketplaceTransactionPath.backup(transactionId, ConsumerPersistedFile.INTENT),
                    ),
                    TransactionFileRecord(
                        ConsumerPersistedFile.LOCK,
                        oldLockSha256,
                        newLockSha256,
                        MarketplaceTransactionPath.staged(transactionId, ConsumerPersistedFile.LOCK),
                        MarketplaceTransactionPath.backup(transactionId, ConsumerPersistedFile.LOCK),
                    ),
                )
            val document =
                when (
                    val created =
                        CanonicalJsonDocument.create(
                            canonicalJsonObject(
                                "files" to CanonicalJsonArray(records.map(TransactionFileRecord::canonicalValue)),
                                "schemaVersion" to
                                    canonicalJsonInteger(MARKETPLACE_TRANSACTION_SCHEMA_VERSION.toLong()),
                                "transactionId" to canonicalJsonString(transactionId.render()),
                                "type" to canonicalJsonString(MARKETPLACE_TRANSACTION_TYPE),
                            ),
                        )
                ) {
                    is CanonicalJsonDocumentCreation.Created -> created.document
                    is CanonicalJsonDocumentCreation.Rejected -> {
                        val reason =
                            when (val rejection = created.reason) {
                                is CanonicalJsonDocumentRejection.SizeExceeded -> rejection
                            }
                        return MarketplaceTransactionJournalMaterialization.Rejected(
                            MarketplaceTransactionJournalRejection.JsonDocumentTooLarge(
                                reason.actualBytes,
                                reason.maximumBytes,
                            ),
                        )
                    }
                }
            return MarketplaceTransactionJournalMaterialization.Materialized(
                MarketplaceTransactionJournal(transactionId, records, document),
            )
        }
    }
}

internal sealed interface MarketplaceTransactionJournalMaterialization {
    data class Materialized(
        val journal: MarketplaceTransactionJournal,
    ) : MarketplaceTransactionJournalMaterialization

    data class Rejected(
        val reason: MarketplaceTransactionJournalRejection,
    ) : MarketplaceTransactionJournalMaterialization
}

internal sealed interface MarketplaceTransactionJournalParsing {
    data class Parsed(val journal: MarketplaceTransactionJournal) : MarketplaceTransactionJournalParsing

    data class Rejected(
        val reason: MarketplaceTransactionJournalRejection,
    ) : MarketplaceTransactionJournalParsing
}

internal sealed interface MarketplaceTransactionRecovery {
    data class CompleteNew(
        val actions: List<MarketplaceTransactionRecoveryAction>,
    ) : MarketplaceTransactionRecovery

    data class RestoreOld(
        val actions: List<MarketplaceTransactionRecoveryAction>,
    ) : MarketplaceTransactionRecovery

    data class Unrecoverable(
        val file: ConsumerPersistedFile,
        val reason: MarketplaceTransactionRecoveryFailure,
    ) : MarketplaceTransactionRecovery
}

internal sealed interface MarketplaceTransactionRecoveryAction {
    data class KeepNew(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
    data class PromoteStaged(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
    data class RemoveTarget(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
    data class KeepOld(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
    data class RestoreBackup(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
    data class RemoveNew(val file: ConsumerPersistedFile) : MarketplaceTransactionRecoveryAction
}

internal enum class MarketplaceTransactionRecoveryFailure {
    MissingObservation,
    UnknownTarget,
}

internal sealed interface MarketplaceTransactionJournalRejection {
    data class JsonDocumentTooLarge(val actualBytes: Long, val maximumBytes: Int) : MarketplaceTransactionJournalRejection
    data object InvalidUtf8 : MarketplaceTransactionJournalRejection
    data object MalformedJson : MarketplaceTransactionJournalRejection
    data object RootMustBeObject : MarketplaceTransactionJournalRejection
    data object NonCanonicalJson : MarketplaceTransactionJournalRejection
    data class MissingField(val path: String, val field: String) : MarketplaceTransactionJournalRejection
    data class UnknownField(val path: String, val field: String) : MarketplaceTransactionJournalRejection
    data class WrongFieldType(val path: String, val expected: String) : MarketplaceTransactionJournalRejection
    data class UnsupportedType(val actual: String) : MarketplaceTransactionJournalRejection
    data class UnsupportedSchemaVersion(val actual: Long) : MarketplaceTransactionJournalRejection
    data class InvalidTransactionId(val reason: Sha256DigestRejection) : MarketplaceTransactionJournalRejection
    data object UnexpectedTransactionId : MarketplaceTransactionJournalRejection
    data object IncompleteNewPair : MarketplaceTransactionJournalRejection
    data object UnexpectedFileSet : MarketplaceTransactionJournalRejection
    data class InvalidTargetPath(val index: Int, val actual: String) : MarketplaceTransactionJournalRejection
    data class InvalidDigest(val path: String, val reason: Sha256DigestRejection) : MarketplaceTransactionJournalRejection
    data class InvalidTransactionPath(val path: String) : MarketplaceTransactionJournalRejection
    data class UnexpectedStagedPath(val file: ConsumerPersistedFile) : MarketplaceTransactionJournalRejection
    data class UnexpectedBackupPath(val file: ConsumerPersistedFile) : MarketplaceTransactionJournalRejection
}

private fun completionAction(
    record: TransactionFileRecord,
    observation: TransactionFileObservation,
): MarketplaceTransactionRecoveryAction? =
    if (record.newSha256 == null) {
        when (observation.targetSha256) {
            null -> MarketplaceTransactionRecoveryAction.KeepNew(record.file)
            record.oldSha256 -> MarketplaceTransactionRecoveryAction.RemoveTarget(record.file)
            else -> null
        }
    } else {
        when {
            observation.targetSha256 == record.newSha256 ->
                MarketplaceTransactionRecoveryAction.KeepNew(record.file)
            targetMatchesOld(record, observation) && observation.stagedSha256 == record.newSha256 ->
                MarketplaceTransactionRecoveryAction.PromoteStaged(record.file)
            else -> null
        }
    }

private fun restorationAction(
    record: TransactionFileRecord,
    observation: TransactionFileObservation,
): MarketplaceTransactionRecoveryAction? =
    if (record.oldSha256 == null) {
        when (observation.targetSha256) {
            null -> MarketplaceTransactionRecoveryAction.KeepOld(record.file)
            record.newSha256 -> MarketplaceTransactionRecoveryAction.RemoveNew(record.file)
            else -> null
        }
    } else {
        when {
            observation.targetSha256 == record.oldSha256 ->
                MarketplaceTransactionRecoveryAction.KeepOld(record.file)
            observation.backupSha256 == record.oldSha256 &&
                (observation.targetSha256 == null || observation.targetSha256 == record.newSha256) ->
                MarketplaceTransactionRecoveryAction.RestoreBackup(record.file)
            else -> null
        }
    }

private fun targetMatchesOld(
    record: TransactionFileRecord,
    observation: TransactionFileObservation,
): Boolean = observation.targetSha256 == record.oldSha256

private fun TransactionFileRecord.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "backupPath" to canonicalJsonString(backupPath.render()),
        "newSha256" to (newSha256?.let { digest -> canonicalJsonString(digest.render()) } ?: CanonicalJsonNull),
        "oldSha256" to (oldSha256?.let { digest -> canonicalJsonString(digest.render()) } ?: CanonicalJsonNull),
        "path" to canonicalJsonString(file.targetPath),
        "stagedPath" to canonicalJsonString(stagedPath.render()),
    )

internal const val MARKETPLACE_TRANSACTION_SCHEMA_VERSION = 1
internal const val MARKETPLACE_TRANSACTION_TYPE = "MARKETPLACE_TRANSACTION"
internal const val MAX_MARKETPLACE_TRANSACTION_JSON_BYTES = 64 * 1024
private const val MAX_TRANSACTION_PATH_BYTES = 320
private val transactionPathPattern =
    Regex("\\.intelligence/\\.marketplace-transactions/[0-9a-f]{64}/[A-Za-z0-9._-]+\\.(?:new|old)")
