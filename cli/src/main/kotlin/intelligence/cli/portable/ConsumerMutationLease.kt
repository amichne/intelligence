package intelligence.cli.portable

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class ConsumerMutationLease private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        fun acquire(repository: Path): ConsumerMutationLeaseAcquisition {
            val root = repository.toAbsolutePath().normalize()
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return ConsumerMutationLeaseAcquisition.Rejected(
                    ConsumerMutationLeaseRejection.InvalidRepository,
                )
            }
            val stateDirectory = root.resolve(INTELLIGENCE_DIRECTORY)
            if (!Files.exists(stateDirectory, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(stateDirectory)
                } catch (_: IOException) {
                    return ConsumerMutationLeaseAcquisition.Rejected(
                        ConsumerMutationLeaseRejection.StateDirectoryUnavailable,
                    )
                } catch (_: SecurityException) {
                    return ConsumerMutationLeaseAcquisition.Rejected(
                        ConsumerMutationLeaseRejection.StateDirectoryUnavailable,
                    )
                }
            }
            if (!Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return ConsumerMutationLeaseAcquisition.Rejected(
                    ConsumerMutationLeaseRejection.InvalidStateDirectory,
                )
            }

            val lockPath = stateDirectory.resolve(MUTATION_LOCK_FILENAME)
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
            ) {
                return ConsumerMutationLeaseAcquisition.Rejected(
                    ConsumerMutationLeaseRejection.InvalidLockFile,
                )
            }
            val channel =
                try {
                    FileChannel.open(
                        lockPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                } catch (_: IOException) {
                    return ConsumerMutationLeaseAcquisition.Rejected(
                        ConsumerMutationLeaseRejection.LockUnavailable,
                    )
                } catch (_: SecurityException) {
                    return ConsumerMutationLeaseAcquisition.Rejected(
                        ConsumerMutationLeaseRejection.LockUnavailable,
                    )
                }
            val fileLock =
                try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } catch (_: IOException) {
                    channel.close()
                    return ConsumerMutationLeaseAcquisition.Rejected(
                        ConsumerMutationLeaseRejection.LockUnavailable,
                    )
                }
            if (fileLock == null) {
                channel.close()
                return ConsumerMutationLeaseAcquisition.Rejected(
                    ConsumerMutationLeaseRejection.ConcurrentMutation,
                )
            }
            return ConsumerMutationLeaseAcquisition.Acquired(ConsumerMutationLease(channel, fileLock))
        }
    }
}

internal sealed interface ConsumerMutationLeaseAcquisition {
    data class Acquired(val lease: ConsumerMutationLease) : ConsumerMutationLeaseAcquisition

    data class Rejected(val reason: ConsumerMutationLeaseRejection) : ConsumerMutationLeaseAcquisition
}

internal enum class ConsumerMutationLeaseRejection {
    InvalidRepository,
    StateDirectoryUnavailable,
    InvalidStateDirectory,
    InvalidLockFile,
    LockUnavailable,
    ConcurrentMutation,
}

private const val INTELLIGENCE_DIRECTORY = ".intelligence"
private const val MUTATION_LOCK_FILENAME = ".marketplace-mutation.lock"
