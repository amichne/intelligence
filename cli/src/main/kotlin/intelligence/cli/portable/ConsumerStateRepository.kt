package intelligence.cli.portable

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object ConsumerStateRepository {
    fun read(repository: Path): ConsumerStateReading =
        when (val snapshot = readConsumerSnapshot(repository)) {
            is ConsumerSnapshotReading.Read -> ConsumerStateReading.Read(snapshot.snapshot.state)
            is ConsumerSnapshotReading.Rejected -> ConsumerStateReading.Rejected(snapshot.reason)
        }

    fun planCommit(
        repository: Path,
        intent: MarketplaceIntent,
        lock: MarketplaceLock,
    ): ConsumerCommitPlanning {
        val agreement = lock.agreement(intent)
        if (agreement is MarketplaceLockAgreement.Stale) {
            return ConsumerCommitPlanning.Rejected(
                ConsumerCommitRejection.NewPairMismatch(agreement.reason),
            )
        }
        val snapshot =
            when (val read = readConsumerSnapshot(repository)) {
                is ConsumerSnapshotReading.Read -> read.snapshot
                is ConsumerSnapshotReading.Rejected -> {
                    return ConsumerCommitPlanning.Rejected(
                        ConsumerCommitRejection.ReadRejected(read.reason),
                    )
                }
            }
        when (val state = snapshot.state) {
            is ConsumerState.Recovering -> {
                return ConsumerCommitPlanning.Rejected(
                    ConsumerCommitRejection.RecoveryRequired(state.journal.transactionId),
                )
            }
            is ConsumerState.Invalid -> {
                return ConsumerCommitPlanning.Rejected(
                    ConsumerCommitRejection.InvalidState(state.reason),
                )
            }
            is ConsumerState.Orphaned -> {
                return ConsumerCommitPlanning.Rejected(ConsumerCommitRejection.OrphanedState)
            }
            ConsumerState.Uninitialized,
            is ConsumerState.Unresolved,
            is ConsumerState.Stale,
            is ConsumerState.Resolved,
            -> Unit
        }
        val newIntentBytes = intent.canonicalBytes()
        val newLockBytes = lock.canonicalBytes()
        return ConsumerCommitPlanning.Planned(
            ConsumerCommitPlan(
                repository = snapshot.repository,
                before = snapshot.state,
                intent = intent,
                lock = lock,
                oldIntentBytes = snapshot.intentBytesCopy(),
                oldLockBytes = snapshot.lockBytesCopy(),
                newIntentBytes = newIntentBytes,
                newLockBytes = newLockBytes,
            ),
        )
    }

    fun planRecovery(repository: Path): ConsumerRecoveryPlanning {
        val snapshot =
            when (val read = readConsumerSnapshot(repository)) {
                is ConsumerSnapshotReading.Read -> read.snapshot
                is ConsumerSnapshotReading.Rejected -> {
                    return ConsumerRecoveryPlanning.Rejected(
                        ConsumerRecoveryRejection.ReadRejected(read.reason),
                    )
                }
            }
        val journalBytes = snapshot.journalBytesCopy()
            ?: return ConsumerRecoveryPlanning.NotRequired(snapshot.state)
        val journal =
            when (val parsed = MarketplaceTransactionJournal.parse(journalBytes)) {
                is MarketplaceTransactionJournalParsing.Parsed -> parsed.journal
                is MarketplaceTransactionJournalParsing.Rejected -> {
                    return ConsumerRecoveryPlanning.Rejected(
                        ConsumerRecoveryRejection.InvalidJournal(parsed.reason),
                    )
                }
            }
        val observations = linkedMapOf<ConsumerPersistedFile, TransactionFileObservation>()
        journal.records.forEach { record ->
            val target =
                when (
                    val read =
                        readOptionalOwnedFile(
                            snapshot.repository.resolve(record.file.targetPath),
                            record.file.maximumBytes(),
                        )
                ) {
                    is OptionalOwnedFileReading.Read -> read.bytes?.let(Sha256Digest::compute)
                    is OptionalOwnedFileReading.Rejected -> {
                        return ConsumerRecoveryPlanning.Rejected(
                            ConsumerRecoveryRejection.ObservationRejected(
                                record.file,
                                ConsumerRecoveryObservation.TARGET,
                                read.reason,
                            ),
                        )
                    }
                }
            val staged =
                when (
                    val read =
                        readOptionalOwnedFile(
                            snapshot.repository.resolve(record.stagedPath.render()),
                            record.file.maximumBytes(),
                        )
                ) {
                    is OptionalOwnedFileReading.Read -> read.bytes?.let(Sha256Digest::compute)
                    is OptionalOwnedFileReading.Rejected -> {
                        return ConsumerRecoveryPlanning.Rejected(
                            ConsumerRecoveryRejection.ObservationRejected(
                                record.file,
                                ConsumerRecoveryObservation.STAGED,
                                read.reason,
                            ),
                        )
                    }
                }
            val backup =
                when (
                    val read =
                        readOptionalOwnedFile(
                            snapshot.repository.resolve(record.backupPath.render()),
                            record.file.maximumBytes(),
                        )
                ) {
                    is OptionalOwnedFileReading.Read -> read.bytes?.let(Sha256Digest::compute)
                    is OptionalOwnedFileReading.Rejected -> {
                        return ConsumerRecoveryPlanning.Rejected(
                            ConsumerRecoveryRejection.ObservationRejected(
                                record.file,
                                ConsumerRecoveryObservation.BACKUP,
                                read.reason,
                            ),
                        )
                    }
                }
            observations[record.file] = TransactionFileObservation(target, staged, backup)
        }
        val recovery =
            when (val planned = journal.recovery(observations)) {
                is MarketplaceTransactionRecovery.CompleteNew -> planned
                is MarketplaceTransactionRecovery.RestoreOld -> planned
                is MarketplaceTransactionRecovery.Unrecoverable -> {
                    return ConsumerRecoveryPlanning.Rejected(
                        ConsumerRecoveryRejection.Unrecoverable(planned.file, planned.reason),
                    )
                }
            }
        return ConsumerRecoveryPlanning.Planned(
            ConsumerRecoveryPlan(
                repository = snapshot.repository,
                journal = journal,
                journalBytes = journalBytes,
                observations = observations,
                recovery = recovery,
            ),
        )
    }

    fun planDeletion(repository: Path): ConsumerDeletionPlanning {
        val snapshot =
            when (val read = readConsumerSnapshot(repository)) {
                is ConsumerSnapshotReading.Read -> read.snapshot
                is ConsumerSnapshotReading.Rejected -> {
                    return ConsumerDeletionPlanning.Rejected(
                        ConsumerDeletionRejection.ReadRejected(read.reason),
                    )
                }
            }
        when (val state = snapshot.state) {
            is ConsumerState.Recovering -> {
                return ConsumerDeletionPlanning.Rejected(
                    ConsumerDeletionRejection.RecoveryRequired(state.journal.transactionId),
                )
            }
            is ConsumerState.Invalid -> {
                return ConsumerDeletionPlanning.Rejected(
                    ConsumerDeletionRejection.InvalidState(state.reason),
                )
            }
            is ConsumerState.Orphaned -> {
                return ConsumerDeletionPlanning.Rejected(ConsumerDeletionRejection.OrphanedState)
            }
            ConsumerState.Uninitialized,
            is ConsumerState.Unresolved,
            is ConsumerState.Stale,
            is ConsumerState.Resolved,
            -> Unit
        }
        return ConsumerDeletionPlanning.Planned(
            ConsumerDeletionPlan(
                repository = snapshot.repository,
                before = snapshot.state,
                oldIntentBytes = snapshot.intentBytesCopy(),
                oldLockBytes = snapshot.lockBytesCopy(),
            ),
        )
    }

    fun execute(plan: ConsumerCommitPlan): ConsumerCommitExecution =
        ConsumerStateTransaction.execute(plan)

    fun execute(plan: ConsumerRecoveryPlan): ConsumerRecoveryExecution =
        ConsumerStateTransaction.execute(plan)

    fun execute(plan: ConsumerDeletionPlan): ConsumerDeletionExecution =
        ConsumerStateTransaction.execute(plan)
}

internal class ConsumerCommitPlan internal constructor(
    val repository: Path,
    val before: ConsumerState,
    val intent: MarketplaceIntent,
    val lock: MarketplaceLock,
    oldIntentBytes: ByteArray?,
    oldLockBytes: ByteArray?,
    newIntentBytes: ByteArray,
    newLockBytes: ByteArray,
) {
    private val oldIntentBytes = oldIntentBytes?.copyOf()
    private val oldLockBytes = oldLockBytes?.copyOf()
    private val newIntentBytes = newIntentBytes.copyOf()
    private val newLockBytes = newLockBytes.copyOf()

    val after: ConsumerState.Resolved = ConsumerState.Resolved(intent, lock)

    val changed: Boolean =
        !sameOptionalBytes(this.oldIntentBytes, this.newIntentBytes) ||
            !sameOptionalBytes(this.oldLockBytes, this.newLockBytes)

    internal fun oldBytes(file: ConsumerPersistedFile): ByteArray? =
        when (file) {
            ConsumerPersistedFile.INTENT -> oldIntentBytes?.copyOf()
            ConsumerPersistedFile.LOCK -> oldLockBytes?.copyOf()
        }

    internal fun newBytes(file: ConsumerPersistedFile): ByteArray =
        when (file) {
            ConsumerPersistedFile.INTENT -> newIntentBytes.copyOf()
            ConsumerPersistedFile.LOCK -> newLockBytes.copyOf()
        }

    internal fun samePrecondition(other: ConsumerCommitPlan): Boolean =
        sameOptionalBytes(oldIntentBytes, other.oldIntentBytes) &&
            sameOptionalBytes(oldLockBytes, other.oldLockBytes)
}

internal class ConsumerRecoveryPlan internal constructor(
    val repository: Path,
    val journal: MarketplaceTransactionJournal,
    journalBytes: ByteArray,
    observations: Map<ConsumerPersistedFile, TransactionFileObservation>,
    val recovery: MarketplaceTransactionRecovery,
) {
    private val journalBytes = journalBytes.copyOf()
    internal val observations: Map<ConsumerPersistedFile, TransactionFileObservation> = observations.toMap()

    internal fun samePrecondition(other: ConsumerRecoveryPlan): Boolean =
        journalBytes.contentEquals(other.journalBytes) && observations == other.observations && recovery == other.recovery
}

internal class ConsumerDeletionPlan internal constructor(
    val repository: Path,
    val before: ConsumerState,
    oldIntentBytes: ByteArray?,
    oldLockBytes: ByteArray?,
) {
    private val oldIntentBytes = oldIntentBytes?.copyOf()
    private val oldLockBytes = oldLockBytes?.copyOf()

    val after: ConsumerState = ConsumerState.Uninitialized
    val changed: Boolean = this.oldIntentBytes != null || this.oldLockBytes != null

    internal fun oldBytes(file: ConsumerPersistedFile): ByteArray? =
        when (file) {
            ConsumerPersistedFile.INTENT -> oldIntentBytes?.copyOf()
            ConsumerPersistedFile.LOCK -> oldLockBytes?.copyOf()
        }

    internal fun samePrecondition(other: ConsumerDeletionPlan): Boolean =
        sameOptionalBytes(oldIntentBytes, other.oldIntentBytes) &&
            sameOptionalBytes(oldLockBytes, other.oldLockBytes)
}

internal sealed interface ConsumerStateReading {
    data class Read(val state: ConsumerState) : ConsumerStateReading

    data class Rejected(val reason: ConsumerStateReadRejection) : ConsumerStateReading
}

internal sealed interface ConsumerCommitPlanning {
    data class Planned(val plan: ConsumerCommitPlan) : ConsumerCommitPlanning

    data class Rejected(val reason: ConsumerCommitRejection) : ConsumerCommitPlanning
}

internal sealed interface ConsumerCommitExecution {
    data class Committed(val state: ConsumerState.Resolved) : ConsumerCommitExecution

    data class Unchanged(val state: ConsumerState.Resolved) : ConsumerCommitExecution

    data class Rejected(val reason: ConsumerCommitRejection) : ConsumerCommitExecution
}

internal sealed interface ConsumerRecoveryPlanning {
    data class Planned(val plan: ConsumerRecoveryPlan) : ConsumerRecoveryPlanning

    data class NotRequired(val state: ConsumerState) : ConsumerRecoveryPlanning

    data class Rejected(val reason: ConsumerRecoveryRejection) : ConsumerRecoveryPlanning
}

internal sealed interface ConsumerRecoveryExecution {
    data class Recovered(
        val outcome: ConsumerRecoveryOutcome,
        val actions: List<MarketplaceTransactionRecoveryAction>,
    ) : ConsumerRecoveryExecution

    data class NotRequired(val state: ConsumerState) : ConsumerRecoveryExecution

    data class Rejected(val reason: ConsumerRecoveryRejection) : ConsumerRecoveryExecution
}

internal sealed interface ConsumerDeletionPlanning {
    data class Planned(val plan: ConsumerDeletionPlan) : ConsumerDeletionPlanning

    data class Rejected(val reason: ConsumerDeletionRejection) : ConsumerDeletionPlanning
}

internal sealed interface ConsumerDeletionExecution {
    data object Deleted : ConsumerDeletionExecution

    data object Unchanged : ConsumerDeletionExecution

    data class Rejected(val reason: ConsumerDeletionRejection) : ConsumerDeletionExecution
}

internal enum class ConsumerRecoveryOutcome {
    COMPLETED_NEW,
    RESTORED_OLD,
}

internal sealed interface ConsumerStateReadRejection {
    data class InvalidRepository(val repository: Path) : ConsumerStateReadRejection

    data class InvalidStateDirectory(val path: Path) : ConsumerStateReadRejection

    data class NonRegularFile(val path: Path) : ConsumerStateReadRejection

    data class FileTooLarge(
        val path: Path,
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : ConsumerStateReadRejection

    data class IoFailure(val path: Path) : ConsumerStateReadRejection
}

internal sealed interface ConsumerCommitRejection {
    data class ReadRejected(val reason: ConsumerStateReadRejection) : ConsumerCommitRejection

    data class NewPairMismatch(val reason: MarketplaceLockStaleness) : ConsumerCommitRejection

    data class RecoveryRequired(val transactionId: MarketplaceTransactionId) : ConsumerCommitRejection

    data class InvalidState(val reason: ConsumerStateInvalidity) : ConsumerCommitRejection

    data object OrphanedState : ConsumerCommitRejection

    data class LeaseRejected(val reason: ConsumerMutationLeaseRejection) : ConsumerCommitRejection

    data object PreconditionChanged : ConsumerCommitRejection

    data class JournalRejected(val reason: MarketplaceTransactionJournalRejection) : ConsumerCommitRejection

    data class FileSystemFailure(
        val operation: ConsumerTransactionOperation,
        val path: Path,
    ) : ConsumerCommitRejection
}

internal sealed interface ConsumerRecoveryRejection {
    data class ReadRejected(val reason: ConsumerStateReadRejection) : ConsumerRecoveryRejection

    data class InvalidJournal(val reason: MarketplaceTransactionJournalRejection) : ConsumerRecoveryRejection

    data class ObservationRejected(
        val file: ConsumerPersistedFile,
        val observation: ConsumerRecoveryObservation,
        val reason: ConsumerStateReadRejection,
    ) : ConsumerRecoveryRejection

    data class Unrecoverable(
        val file: ConsumerPersistedFile,
        val reason: MarketplaceTransactionRecoveryFailure,
    ) : ConsumerRecoveryRejection

    data class LeaseRejected(val reason: ConsumerMutationLeaseRejection) : ConsumerRecoveryRejection

    data object PreconditionChanged : ConsumerRecoveryRejection

    data class FileSystemFailure(
        val operation: ConsumerTransactionOperation,
        val path: Path,
    ) : ConsumerRecoveryRejection
}

internal sealed interface ConsumerDeletionRejection {
    data class ReadRejected(val reason: ConsumerStateReadRejection) : ConsumerDeletionRejection

    data class RecoveryRequired(val transactionId: MarketplaceTransactionId) : ConsumerDeletionRejection

    data class InvalidState(val reason: ConsumerStateInvalidity) : ConsumerDeletionRejection

    data object OrphanedState : ConsumerDeletionRejection

    data class LeaseRejected(val reason: ConsumerMutationLeaseRejection) : ConsumerDeletionRejection

    data object PreconditionChanged : ConsumerDeletionRejection

    data class JournalRejected(
        val reason: MarketplaceTransactionJournalRejection,
    ) : ConsumerDeletionRejection

    data class FileSystemFailure(
        val operation: ConsumerTransactionOperation,
        val path: Path,
    ) : ConsumerDeletionRejection
}

internal enum class ConsumerRecoveryObservation {
    TARGET,
    STAGED,
    BACKUP,
}

internal enum class ConsumerTransactionOperation {
    PREPARE_TRANSACTION_ROOT,
    WRITE_JOURNAL,
    CREATE_TRANSACTION_DIRECTORY,
    WRITE_BACKUP,
    WRITE_STAGED,
    PROMOTE_STAGED,
    RESTORE_BACKUP,
    REMOVE_NEW,
    VERIFY_TARGET,
    FLUSH_DIRECTORY,
    CLEAN_TRANSACTION,
    REMOVE_JOURNAL,
}

internal class ConsumerSnapshot internal constructor(
    val repository: Path,
    intentBytes: ByteArray?,
    lockBytes: ByteArray?,
    journalBytes: ByteArray?,
) {
    private val intentBytes = intentBytes?.copyOf()
    private val lockBytes = lockBytes?.copyOf()
    private val journalBytes = journalBytes?.copyOf()

    val state: ConsumerState = ConsumerState.classify(this.intentBytes, this.lockBytes, this.journalBytes)

    fun intentBytesCopy(): ByteArray? = intentBytes?.copyOf()

    fun lockBytesCopy(): ByteArray? = lockBytes?.copyOf()

    fun journalBytesCopy(): ByteArray? = journalBytes?.copyOf()
}

internal sealed interface ConsumerSnapshotReading {
    data class Read(val snapshot: ConsumerSnapshot) : ConsumerSnapshotReading

    data class Rejected(val reason: ConsumerStateReadRejection) : ConsumerSnapshotReading
}

internal fun readConsumerSnapshot(repository: Path): ConsumerSnapshotReading {
    val root = repository.toAbsolutePath().normalize()
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
        return ConsumerSnapshotReading.Rejected(ConsumerStateReadRejection.InvalidRepository(root))
    }
    val stateDirectory = root.resolve(CONSUMER_STATE_DIRECTORY)
    if (!Files.exists(stateDirectory, LinkOption.NOFOLLOW_LINKS)) {
        return ConsumerSnapshotReading.Read(ConsumerSnapshot(root, null, null, null))
    }
    if (!Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS)) {
        return ConsumerSnapshotReading.Rejected(
            ConsumerStateReadRejection.InvalidStateDirectory(stateDirectory),
        )
    }
    val intent =
        when (
            val read =
                readOptionalOwnedFile(
                    root.resolve(ConsumerPersistedFile.INTENT.targetPath),
                    MAX_MARKETPLACE_INTENT_JSON_BYTES,
                )
        ) {
            is OptionalOwnedFileReading.Read -> read.bytes
            is OptionalOwnedFileReading.Rejected -> return ConsumerSnapshotReading.Rejected(read.reason)
        }
    val lock =
        when (
            val read =
                readOptionalOwnedFile(
                    root.resolve(ConsumerPersistedFile.LOCK.targetPath),
                    MAX_MARKETPLACE_LOCK_JSON_BYTES,
                )
        ) {
            is OptionalOwnedFileReading.Read -> read.bytes
            is OptionalOwnedFileReading.Rejected -> return ConsumerSnapshotReading.Rejected(read.reason)
        }
    val journal =
        when (
            val read =
                readOptionalOwnedFile(
                    root.resolve(CONSUMER_TRANSACTION_JOURNAL_PATH),
                    MAX_MARKETPLACE_TRANSACTION_JSON_BYTES,
                )
        ) {
            is OptionalOwnedFileReading.Read -> read.bytes
            is OptionalOwnedFileReading.Rejected -> return ConsumerSnapshotReading.Rejected(read.reason)
        }
    return ConsumerSnapshotReading.Read(ConsumerSnapshot(root, intent, lock, journal))
}

internal sealed interface OptionalOwnedFileReading {
    data class Read(val bytes: ByteArray?) : OptionalOwnedFileReading

    data class Rejected(val reason: ConsumerStateReadRejection) : OptionalOwnedFileReading
}

internal fun readOptionalOwnedFile(
    path: Path,
    maximumBytes: Int,
): OptionalOwnedFileReading {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return OptionalOwnedFileReading.Read(null)
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        return OptionalOwnedFileReading.Rejected(ConsumerStateReadRejection.NonRegularFile(path))
    }
    val bytes =
        try {
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val size = channel.size()
                if (size > maximumBytes.toLong()) {
                    return OptionalOwnedFileReading.Rejected(
                        ConsumerStateReadRejection.FileTooLarge(path, size, maximumBytes),
                    )
                }
                val content = ByteArray(size.toInt())
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        return OptionalOwnedFileReading.Rejected(
                            ConsumerStateReadRejection.IoFailure(path),
                        )
                    }
                }
                if (channel.size() != size) {
                    return OptionalOwnedFileReading.Rejected(ConsumerStateReadRejection.IoFailure(path))
                }
                content
            }
        } catch (_: IOException) {
            return OptionalOwnedFileReading.Rejected(ConsumerStateReadRejection.IoFailure(path))
        } catch (_: SecurityException) {
            return OptionalOwnedFileReading.Rejected(ConsumerStateReadRejection.IoFailure(path))
        }
    return OptionalOwnedFileReading.Read(bytes)
}

internal fun ConsumerPersistedFile.maximumBytes(): Int =
    when (this) {
        ConsumerPersistedFile.INTENT -> MAX_MARKETPLACE_INTENT_JSON_BYTES
        ConsumerPersistedFile.LOCK -> MAX_MARKETPLACE_LOCK_JSON_BYTES
    }

internal const val CONSUMER_STATE_DIRECTORY = ".intelligence"
internal const val CONSUMER_TRANSACTION_JOURNAL_PATH = ".intelligence/.marketplace-transaction.json"
internal const val CONSUMER_TRANSACTION_ROOT_PATH = ".intelligence/.marketplace-transactions"

private fun sameOptionalBytes(
    left: ByteArray?,
    right: ByteArray?,
): Boolean =
    when {
        left == null -> right == null
        right == null -> false
        else -> left.contentEquals(right)
    }
