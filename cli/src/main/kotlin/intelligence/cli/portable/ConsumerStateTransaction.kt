package intelligence.cli.portable

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal object ConsumerStateTransaction {
    fun execute(plan: ConsumerCommitPlan): ConsumerCommitExecution {
        val lease =
            when (val acquired = ConsumerMutationLease.acquire(plan.repository)) {
                is ConsumerMutationLeaseAcquisition.Acquired -> acquired.lease
                is ConsumerMutationLeaseAcquisition.Rejected -> {
                    return ConsumerCommitExecution.Rejected(
                        ConsumerCommitRejection.LeaseRejected(acquired.reason),
                    )
                }
            }
        lease.use {
            val current =
                when (val replanned = ConsumerStateRepository.planCommit(plan.repository, plan.intent, plan.lock)) {
                    is ConsumerCommitPlanning.Planned -> replanned.plan
                    is ConsumerCommitPlanning.Rejected -> {
                        return ConsumerCommitExecution.Rejected(ConsumerCommitRejection.PreconditionChanged)
                    }
                }
            if (!plan.samePrecondition(current)) {
                return ConsumerCommitExecution.Rejected(ConsumerCommitRejection.PreconditionChanged)
            }
            if (!plan.changed) {
                return ConsumerCommitExecution.Unchanged(plan.after)
            }
            return writeCommit(plan)
        }
    }

    fun execute(plan: ConsumerRecoveryPlan): ConsumerRecoveryExecution {
        val lease =
            when (val acquired = ConsumerMutationLease.acquire(plan.repository)) {
                is ConsumerMutationLeaseAcquisition.Acquired -> acquired.lease
                is ConsumerMutationLeaseAcquisition.Rejected -> {
                    return ConsumerRecoveryExecution.Rejected(
                        ConsumerRecoveryRejection.LeaseRejected(acquired.reason),
                    )
                }
            }
        lease.use {
            val current =
                when (val replanned = ConsumerStateRepository.planRecovery(plan.repository)) {
                    is ConsumerRecoveryPlanning.Planned -> replanned.plan
                    is ConsumerRecoveryPlanning.NotRequired,
                    is ConsumerRecoveryPlanning.Rejected,
                    -> {
                        return ConsumerRecoveryExecution.Rejected(
                            ConsumerRecoveryRejection.PreconditionChanged,
                        )
                    }
                }
            if (!plan.samePrecondition(current)) {
                return ConsumerRecoveryExecution.Rejected(ConsumerRecoveryRejection.PreconditionChanged)
            }
            return executeRecovery(plan)
        }
    }

    fun execute(plan: ConsumerDeletionPlan): ConsumerDeletionExecution {
        val lease =
            when (val acquired = ConsumerMutationLease.acquire(plan.repository)) {
                is ConsumerMutationLeaseAcquisition.Acquired -> acquired.lease
                is ConsumerMutationLeaseAcquisition.Rejected -> {
                    return ConsumerDeletionExecution.Rejected(
                        ConsumerDeletionRejection.LeaseRejected(acquired.reason),
                    )
                }
            }
        lease.use {
            val current =
                when (val replanned = ConsumerStateRepository.planDeletion(plan.repository)) {
                    is ConsumerDeletionPlanning.Planned -> replanned.plan
                    is ConsumerDeletionPlanning.Rejected -> {
                        return ConsumerDeletionExecution.Rejected(
                            ConsumerDeletionRejection.PreconditionChanged,
                        )
                    }
                }
            if (!plan.samePrecondition(current)) {
                return ConsumerDeletionExecution.Rejected(ConsumerDeletionRejection.PreconditionChanged)
            }
            if (!plan.changed) {
                return ConsumerDeletionExecution.Unchanged
            }
            return writeDeletion(plan)
        }
    }
}

private fun writeCommit(plan: ConsumerCommitPlan): ConsumerCommitExecution {
    val journal =
        when (
            val materialized =
                MarketplaceTransactionJournal.materialize(
                    oldIntentSha256 = plan.oldBytes(ConsumerPersistedFile.INTENT)?.let(Sha256Digest::compute),
                    oldLockSha256 = plan.oldBytes(ConsumerPersistedFile.LOCK)?.let(Sha256Digest::compute),
                    newIntentSha256 = Sha256Digest.compute(plan.newBytes(ConsumerPersistedFile.INTENT)),
                    newLockSha256 = Sha256Digest.compute(plan.newBytes(ConsumerPersistedFile.LOCK)),
                )
        ) {
            is MarketplaceTransactionJournalMaterialization.Materialized -> materialized.journal
            is MarketplaceTransactionJournalMaterialization.Rejected -> {
                return ConsumerCommitExecution.Rejected(
                    ConsumerCommitRejection.JournalRejected(materialized.reason),
                )
            }
        }
    val transactionRoot = plan.repository.resolve(CONSUMER_TRANSACTION_ROOT_PATH)
    if (!ensureDirectory(transactionRoot)) {
        return commitFileSystemFailure(
            ConsumerTransactionOperation.PREPARE_TRANSACTION_ROOT,
            transactionRoot,
        )
    }
    val journalPath = plan.repository.resolve(CONSUMER_TRANSACTION_JOURNAL_PATH)
    if (!publishForcedFile(journalPath, journal.canonicalBytes())) {
        return commitFileSystemFailure(ConsumerTransactionOperation.WRITE_JOURNAL, journalPath)
    }

    val transactionDirectory = plan.repository.resolve(journal.records.first().stagedPath.render()).parent
    if (!createNewDirectory(transactionDirectory)) {
        return commitFileSystemFailure(
            ConsumerTransactionOperation.CREATE_TRANSACTION_DIRECTORY,
            transactionDirectory,
        )
    }
    journal.records.forEach { record ->
        val oldBytes = plan.oldBytes(record.file)
        if (oldBytes != null) {
            val backup = plan.repository.resolve(record.backupPath.render())
            if (!writeForcedNewFile(backup, oldBytes)) {
                return commitFileSystemFailure(ConsumerTransactionOperation.WRITE_BACKUP, backup)
            }
        }
        val staged = plan.repository.resolve(record.stagedPath.render())
        if (!writeForcedNewFile(staged, plan.newBytes(record.file))) {
            return commitFileSystemFailure(ConsumerTransactionOperation.WRITE_STAGED, staged)
        }
    }
    if (!forceDirectory(transactionDirectory) || !forceDirectory(transactionRoot)) {
        return commitFileSystemFailure(
            ConsumerTransactionOperation.FLUSH_DIRECTORY,
            transactionDirectory,
        )
    }
    journal.records.forEach { record ->
        val staged = plan.repository.resolve(record.stagedPath.render())
        val target = plan.repository.resolve(record.file.targetPath)
        if (!moveAtomically(staged, target)) {
            return commitFileSystemFailure(ConsumerTransactionOperation.PROMOTE_STAGED, target)
        }
    }
    val stateDirectory = plan.repository.resolve(CONSUMER_STATE_DIRECTORY)
    if (!forceDirectory(transactionDirectory) || !forceDirectory(stateDirectory)) {
        return commitFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }
    journal.records.forEach { record ->
        val target = plan.repository.resolve(record.file.targetPath)
        if (!targetMatches(target, record.file.maximumBytes(), record.newSha256)) {
            return commitFileSystemFailure(ConsumerTransactionOperation.VERIFY_TARGET, target)
        }
    }
    if (!cleanupTransactionFiles(plan.repository, journal)) {
        return commitFileSystemFailure(
            ConsumerTransactionOperation.CLEAN_TRANSACTION,
            transactionDirectory,
        )
    }
    if (!deleteRegularFile(journalPath)) {
        return commitFileSystemFailure(ConsumerTransactionOperation.REMOVE_JOURNAL, journalPath)
    }
    if (!forceDirectory(stateDirectory)) {
        return commitFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }
    return ConsumerCommitExecution.Committed(plan.after)
}

private fun executeRecovery(plan: ConsumerRecoveryPlan): ConsumerRecoveryExecution {
    val actions =
        when (val recovery = plan.recovery) {
            is MarketplaceTransactionRecovery.CompleteNew -> recovery.actions
            is MarketplaceTransactionRecovery.RestoreOld -> recovery.actions
            is MarketplaceTransactionRecovery.Unrecoverable -> {
                return ConsumerRecoveryExecution.Rejected(
                    ConsumerRecoveryRejection.Unrecoverable(recovery.file, recovery.reason),
                )
            }
        }
    val records = plan.journal.records.associateBy(TransactionFileRecord::file)
    actions.forEach { action ->
        val record = records.getValue(action.file())
        when (action) {
            is MarketplaceTransactionRecoveryAction.KeepNew,
            is MarketplaceTransactionRecoveryAction.KeepOld,
            -> Unit
            is MarketplaceTransactionRecoveryAction.PromoteStaged -> {
                val staged = plan.repository.resolve(record.stagedPath.render())
                val target = plan.repository.resolve(record.file.targetPath)
                if (!moveAtomically(staged, target)) {
                    return recoveryFileSystemFailure(ConsumerTransactionOperation.PROMOTE_STAGED, target)
                }
            }
            is MarketplaceTransactionRecoveryAction.RemoveTarget -> {
                val target = plan.repository.resolve(record.file.targetPath)
                if (!deleteRegularFile(target)) {
                    return recoveryFileSystemFailure(ConsumerTransactionOperation.REMOVE_NEW, target)
                }
            }
            is MarketplaceTransactionRecoveryAction.RestoreBackup -> {
                val backup = plan.repository.resolve(record.backupPath.render())
                val target = plan.repository.resolve(record.file.targetPath)
                if (!moveAtomically(backup, target)) {
                    return recoveryFileSystemFailure(ConsumerTransactionOperation.RESTORE_BACKUP, target)
                }
            }
            is MarketplaceTransactionRecoveryAction.RemoveNew -> {
                val target = plan.repository.resolve(record.file.targetPath)
                if (!deleteRegularFile(target)) {
                    return recoveryFileSystemFailure(ConsumerTransactionOperation.REMOVE_NEW, target)
                }
            }
        }
    }

    val transactionDirectory = plan.repository.resolve(plan.journal.records.first().stagedPath.render()).parent
    val stateDirectory = plan.repository.resolve(CONSUMER_STATE_DIRECTORY)
    if ((Files.exists(transactionDirectory, LinkOption.NOFOLLOW_LINKS) && !forceDirectory(transactionDirectory)) ||
        !forceDirectory(stateDirectory)
    ) {
        return recoveryFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }

    val outcome =
        when (plan.recovery) {
            is MarketplaceTransactionRecovery.CompleteNew -> ConsumerRecoveryOutcome.COMPLETED_NEW
            is MarketplaceTransactionRecovery.RestoreOld -> ConsumerRecoveryOutcome.RESTORED_OLD
            is MarketplaceTransactionRecovery.Unrecoverable -> {
                return ConsumerRecoveryExecution.Rejected(ConsumerRecoveryRejection.PreconditionChanged)
            }
        }
    plan.journal.records.forEach { record ->
        val target = plan.repository.resolve(record.file.targetPath)
        val expected =
            when (outcome) {
                ConsumerRecoveryOutcome.COMPLETED_NEW -> record.newSha256
                ConsumerRecoveryOutcome.RESTORED_OLD -> record.oldSha256
            }
        if (!targetMatches(target, record.file.maximumBytes(), expected)) {
            return recoveryFileSystemFailure(ConsumerTransactionOperation.VERIFY_TARGET, target)
        }
    }
    if (!cleanupTransactionFiles(plan.repository, plan.journal)) {
        return recoveryFileSystemFailure(
            ConsumerTransactionOperation.CLEAN_TRANSACTION,
            transactionDirectory,
        )
    }
    val journalPath = plan.repository.resolve(CONSUMER_TRANSACTION_JOURNAL_PATH)
    if (!deleteRegularFile(journalPath)) {
        return recoveryFileSystemFailure(ConsumerTransactionOperation.REMOVE_JOURNAL, journalPath)
    }
    if (!forceDirectory(stateDirectory)) {
        return recoveryFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }
    return ConsumerRecoveryExecution.Recovered(outcome, actions)
}

private fun writeDeletion(plan: ConsumerDeletionPlan): ConsumerDeletionExecution {
    val journal =
        when (
            val materialized =
                MarketplaceTransactionJournal.materialize(
                    oldIntentSha256 = plan.oldBytes(ConsumerPersistedFile.INTENT)?.let(Sha256Digest::compute),
                    oldLockSha256 = plan.oldBytes(ConsumerPersistedFile.LOCK)?.let(Sha256Digest::compute),
                    newIntentSha256 = null,
                    newLockSha256 = null,
                )
        ) {
            is MarketplaceTransactionJournalMaterialization.Materialized -> materialized.journal
            is MarketplaceTransactionJournalMaterialization.Rejected -> {
                return ConsumerDeletionExecution.Rejected(
                    ConsumerDeletionRejection.JournalRejected(materialized.reason),
                )
            }
        }
    val transactionRoot = plan.repository.resolve(CONSUMER_TRANSACTION_ROOT_PATH)
    if (!ensureDirectory(transactionRoot)) {
        return deletionFileSystemFailure(
            ConsumerTransactionOperation.PREPARE_TRANSACTION_ROOT,
            transactionRoot,
        )
    }
    val journalPath = plan.repository.resolve(CONSUMER_TRANSACTION_JOURNAL_PATH)
    if (!publishForcedFile(journalPath, journal.canonicalBytes())) {
        return deletionFileSystemFailure(ConsumerTransactionOperation.WRITE_JOURNAL, journalPath)
    }
    val transactionDirectory = plan.repository.resolve(journal.records.first().backupPath.render()).parent
    if (!createNewDirectory(transactionDirectory)) {
        return deletionFileSystemFailure(
            ConsumerTransactionOperation.CREATE_TRANSACTION_DIRECTORY,
            transactionDirectory,
        )
    }
    journal.records.forEach { record ->
        val oldBytes = plan.oldBytes(record.file)
        if (oldBytes != null) {
            val backup = plan.repository.resolve(record.backupPath.render())
            if (!writeForcedNewFile(backup, oldBytes)) {
                return deletionFileSystemFailure(ConsumerTransactionOperation.WRITE_BACKUP, backup)
            }
        }
    }
    if (!forceDirectory(transactionDirectory) || !forceDirectory(transactionRoot)) {
        return deletionFileSystemFailure(
            ConsumerTransactionOperation.FLUSH_DIRECTORY,
            transactionDirectory,
        )
    }
    journal.records.forEach { record ->
        val target = plan.repository.resolve(record.file.targetPath)
        if (!deleteRegularFile(target)) {
            return deletionFileSystemFailure(ConsumerTransactionOperation.REMOVE_NEW, target)
        }
    }
    val stateDirectory = plan.repository.resolve(CONSUMER_STATE_DIRECTORY)
    if (!forceDirectory(stateDirectory)) {
        return deletionFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }
    journal.records.forEach { record ->
        val target = plan.repository.resolve(record.file.targetPath)
        if (!targetMatches(target, record.file.maximumBytes(), null)) {
            return deletionFileSystemFailure(ConsumerTransactionOperation.VERIFY_TARGET, target)
        }
    }
    if (!cleanupTransactionFiles(plan.repository, journal)) {
        return deletionFileSystemFailure(
            ConsumerTransactionOperation.CLEAN_TRANSACTION,
            transactionDirectory,
        )
    }
    if (!deleteRegularFile(journalPath)) {
        return deletionFileSystemFailure(ConsumerTransactionOperation.REMOVE_JOURNAL, journalPath)
    }
    if (!forceDirectory(stateDirectory)) {
        return deletionFileSystemFailure(ConsumerTransactionOperation.FLUSH_DIRECTORY, stateDirectory)
    }
    return ConsumerDeletionExecution.Deleted
}

private fun MarketplaceTransactionRecoveryAction.file(): ConsumerPersistedFile =
    when (this) {
        is MarketplaceTransactionRecoveryAction.KeepNew -> file
        is MarketplaceTransactionRecoveryAction.PromoteStaged -> file
        is MarketplaceTransactionRecoveryAction.RemoveTarget -> file
        is MarketplaceTransactionRecoveryAction.KeepOld -> file
        is MarketplaceTransactionRecoveryAction.RestoreBackup -> file
        is MarketplaceTransactionRecoveryAction.RemoveNew -> file
    }

private fun ensureDirectory(path: Path): Boolean {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
    }
    return try {
        Files.createDirectory(path)
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun createNewDirectory(path: Path): Boolean =
    try {
        Files.createDirectory(path)
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun publishForcedFile(
    target: Path,
    bytes: ByteArray,
): Boolean {
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false
    val staging =
        try {
            Files.createTempFile(target.parent, CONSUMER_JOURNAL_STAGING_PREFIX, CONSUMER_JOURNAL_STAGING_SUFFIX)
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    if (!writeForcedExistingFile(staging, bytes)) {
        deleteRegularFile(staging)
        return false
    }
    return try {
        Files.createLink(target, staging)
        deleteRegularFile(staging) && forceDirectory(target.parent)
    } catch (_: IOException) {
        deleteRegularFile(staging)
        false
    } catch (_: SecurityException) {
        deleteRegularFile(staging)
        false
    }
}

private fun writeForcedNewFile(
    path: Path,
    bytes: ByteArray,
): Boolean =
    try {
        FileChannel.open(
            path,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            writeAndForce(channel, bytes)
        }
        targetBytesEqual(path, bytes)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun writeForcedExistingFile(
    path: Path,
    bytes: ByteArray,
): Boolean =
    try {
        FileChannel.open(
            path,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            writeAndForce(channel, bytes)
        }
        targetBytesEqual(path, bytes)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun writeAndForce(
    channel: FileChannel,
    bytes: ByteArray,
) {
    val buffer = ByteBuffer.wrap(bytes)
    while (buffer.hasRemaining()) {
        channel.write(buffer)
    }
    channel.force(true)
}

private fun moveAtomically(
    source: Path,
    target: Path,
): Boolean =
    try {
        Files.move(
            source,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun targetMatches(
    path: Path,
    maximumBytes: Int,
    expected: Sha256Digest?,
): Boolean =
    when (val read = readOptionalOwnedFile(path, maximumBytes)) {
        is OptionalOwnedFileReading.Read -> {
            val actual = read.bytes?.let(Sha256Digest::compute)
            actual == expected
        }
        is OptionalOwnedFileReading.Rejected -> false
    }

private fun targetBytesEqual(
    path: Path,
    expected: ByteArray,
): Boolean =
    when (val read = readOptionalOwnedFile(path, expected.size)) {
        is OptionalOwnedFileReading.Read -> read.bytes?.contentEquals(expected) == true
        is OptionalOwnedFileReading.Rejected -> false
    }

private fun cleanupTransactionFiles(
    repository: Path,
    journal: MarketplaceTransactionJournal,
): Boolean {
    journal.records.forEach { record ->
        val staged = repository.resolve(record.stagedPath.render())
        if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS) && !deleteRegularFile(staged)) return false
        val backup = repository.resolve(record.backupPath.render())
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS) && !deleteRegularFile(backup)) return false
    }
    val transactionDirectory = repository.resolve(journal.records.first().stagedPath.render()).parent
    if (!Files.exists(transactionDirectory, LinkOption.NOFOLLOW_LINKS)) return true
    if (!Files.isDirectory(transactionDirectory, LinkOption.NOFOLLOW_LINKS)) return false
    if (!forceDirectory(transactionDirectory)) return false
    return try {
        Files.delete(transactionDirectory)
        forceDirectory(transactionDirectory.parent)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun deleteRegularFile(path: Path): Boolean {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
    return try {
        Files.delete(path)
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun forceDirectory(path: Path): Boolean =
    try {
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            channel.force(true)
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun commitFileSystemFailure(
    operation: ConsumerTransactionOperation,
    path: Path,
): ConsumerCommitExecution.Rejected =
    ConsumerCommitExecution.Rejected(ConsumerCommitRejection.FileSystemFailure(operation, path))

private fun recoveryFileSystemFailure(
    operation: ConsumerTransactionOperation,
    path: Path,
): ConsumerRecoveryExecution.Rejected =
    ConsumerRecoveryExecution.Rejected(ConsumerRecoveryRejection.FileSystemFailure(operation, path))

private fun deletionFileSystemFailure(
    operation: ConsumerTransactionOperation,
    path: Path,
): ConsumerDeletionExecution.Rejected =
    ConsumerDeletionExecution.Rejected(ConsumerDeletionRejection.FileSystemFailure(operation, path))

private const val CONSUMER_JOURNAL_STAGING_PREFIX = ".marketplace-journal-"
private const val CONSUMER_JOURNAL_STAGING_SUFFIX = ".tmp"
