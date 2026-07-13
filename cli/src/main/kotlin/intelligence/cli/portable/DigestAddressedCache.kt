package intelligence.cli.portable

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object DigestCacheRoot {
    fun resolve(
        xdgCacheHome: String?,
        userHome: String?,
    ): DigestCacheRootResolution {
        val base =
            if (xdgCacheHome != null) {
                xdgCacheHome
            } else {
                userHome?.let { home -> "$home/.cache" }
                    ?: return DigestCacheRootResolution.Rejected(DigestCacheRootRejection.MissingUserHome)
            }
        if (base.isBlank()) {
            return DigestCacheRootResolution.Rejected(DigestCacheRootRejection.EmptyPath)
        }
        val path =
            try {
                Path.of(base)
            } catch (_: InvalidPathException) {
                return DigestCacheRootResolution.Rejected(DigestCacheRootRejection.InvalidPath)
            }
        if (!path.isAbsolute) {
            return DigestCacheRootResolution.Rejected(DigestCacheRootRejection.RelativePath(path))
        }
        return DigestCacheRootResolution.Resolved(
            path.normalize().resolve("intelligence").resolve("sha256"),
        )
    }
}

internal sealed interface DigestCacheRootResolution {
    data class Resolved(val path: Path) : DigestCacheRootResolution

    data class Rejected(val reason: DigestCacheRootRejection) : DigestCacheRootResolution
}

internal sealed interface DigestCacheRootRejection {
    data object MissingUserHome : DigestCacheRootRejection

    data object EmptyPath : DigestCacheRootRejection

    data object InvalidPath : DigestCacheRootRejection

    data class RelativePath(val path: Path) : DigestCacheRootRejection
}

internal class CacheBlobExpectation private constructor(
    val byteSize: Int,
    val sha256: Sha256Digest,
) {
    companion object {
        fun fromVerified(bytes: ByteArray): CacheBlobExpectation {
            require(bytes.isNotEmpty() && bytes.size <= MAX_RELEASE_ARTIFACT_BYTES) {
                "Verified cache content must satisfy the release artifact size boundary"
            }
            return CacheBlobExpectation(bytes.size, Sha256Digest.compute(bytes))
        }

        fun from(asset: LockedAsset): CacheBlobExpectation =
            CacheBlobExpectation(asset.byteSize, asset.sha256)

        fun from(asset: SnapshotAssetEvidence): CacheBlobExpectation =
            CacheBlobExpectation(asset.byteSize, asset.sha256)

        fun from(asset: GitHubReleaseAsset): CacheBlobExpectation =
            CacheBlobExpectation(asset.byteSize, asset.sha256)
    }
}

internal class CachedBlob internal constructor(
    val expectation: CacheBlobExpectation,
    val path: Path,
    private val content: ByteArray,
) {
    fun bytes(): ByteArray = content.copyOf()
}

internal class DigestAddressedCache private constructor(
    root: Path,
) {
    val root: Path = root.toAbsolutePath().normalize()

    fun pathFor(digest: Sha256Digest): Path {
        val rendered = digest.render()
        return root.resolve(rendered.substring(0, CACHE_SHARD_CHARACTERS))
            .resolve(rendered.substring(CACHE_SHARD_CHARACTERS))
    }

    fun read(expectation: CacheBlobExpectation): DigestCacheRead {
        when (val rootState = existingDirectory(root, DigestCacheDirectoryKind.ROOT)) {
            ExistingDirectory.Missing -> return DigestCacheRead.Miss
            ExistingDirectory.Ready -> Unit
            is ExistingDirectory.Rejected -> return DigestCacheRead.Rejected(rootState.reason)
        }
        val target = pathFor(expectation.sha256)
        when (val shardState = existingDirectory(target.parent, DigestCacheDirectoryKind.SHARD)) {
            ExistingDirectory.Missing -> return DigestCacheRead.Miss
            ExistingDirectory.Ready -> Unit
            is ExistingDirectory.Rejected -> return DigestCacheRead.Rejected(shardState.reason)
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return DigestCacheRead.Miss
        }
        return when (val verified = readVerified(target, expectation)) {
            is VerifiedBlobRead.Read ->
                DigestCacheRead.Hit(CachedBlob(expectation, target, verified.bytes))
            is VerifiedBlobRead.Rejected -> DigestCacheRead.Rejected(verified.reason)
        }
    }

    fun insert(
        expectation: CacheBlobExpectation,
        bytes: ByteArray,
    ): DigestCacheInsertion {
        verifyBytes(bytes, expectation)?.let { reason ->
            return DigestCacheInsertion.Rejected(reason)
        }
        when (val existing = read(expectation)) {
            is DigestCacheRead.Hit -> return DigestCacheInsertion.AlreadyPresent(existing.blob)
            DigestCacheRead.Miss -> Unit
            is DigestCacheRead.Rejected -> return DigestCacheInsertion.Rejected(existing.reason)
        }

        ensureDirectory(root, DigestCacheDirectoryKind.ROOT)?.let { reason ->
            return DigestCacheInsertion.Rejected(reason)
        }
        val target = pathFor(expectation.sha256)
        val shard = target.parent
        ensureDirectory(shard, DigestCacheDirectoryKind.SHARD)?.let { reason ->
            return DigestCacheInsertion.Rejected(reason)
        }
        when (val existing = read(expectation)) {
            is DigestCacheRead.Hit -> return DigestCacheInsertion.AlreadyPresent(existing.blob)
            DigestCacheRead.Miss -> Unit
            is DigestCacheRead.Rejected -> return DigestCacheInsertion.Rejected(existing.reason)
        }

        val staging =
            try {
                Files.createTempFile(shard, CACHE_STAGING_PREFIX, CACHE_STAGING_SUFFIX)
            } catch (_: IOException) {
                return DigestCacheInsertion.Rejected(DigestCacheRejection.StagingCreationFailed(shard))
            } catch (_: SecurityException) {
                return DigestCacheInsertion.Rejected(DigestCacheRejection.StagingCreationFailed(shard))
            }
        try {
            FileChannel.open(
                staging,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
        } catch (_: IOException) {
            return rejectAfterStagingCleanup(
                staging,
                DigestCacheRejection.StagingWriteFailed(staging),
            )
        } catch (_: SecurityException) {
            return rejectAfterStagingCleanup(
                staging,
                DigestCacheRejection.StagingWriteFailed(staging),
            )
        }

        when (val verified = readVerified(staging, expectation)) {
            is VerifiedBlobRead.Read -> Unit
            is VerifiedBlobRead.Rejected -> return rejectAfterStagingCleanup(staging, verified.reason)
        }

        try {
            Files.createLink(target, staging)
        } catch (_: FileAlreadyExistsException) {
            deleteQuietly(staging)
            return when (val existing = read(expectation)) {
                is DigestCacheRead.Hit -> DigestCacheInsertion.AlreadyPresent(existing.blob)
                DigestCacheRead.Miss ->
                    DigestCacheInsertion.Rejected(DigestCacheRejection.ConcurrentPromotionLost(target))
                is DigestCacheRead.Rejected -> DigestCacheInsertion.Rejected(existing.reason)
            }
        } catch (_: IOException) {
            return rejectAfterStagingCleanup(
                staging,
                DigestCacheRejection.AtomicPromotionFailed(target),
            )
        } catch (_: SecurityException) {
            return rejectAfterStagingCleanup(
                staging,
                DigestCacheRejection.AtomicPromotionFailed(target),
            )
        }
        if (!deleteQuietly(staging)) {
            return DigestCacheInsertion.Rejected(DigestCacheRejection.StagingCleanupFailed(staging))
        }
        return when (val inserted = read(expectation)) {
            is DigestCacheRead.Hit -> DigestCacheInsertion.Stored(inserted.blob)
            DigestCacheRead.Miss ->
                DigestCacheInsertion.Rejected(DigestCacheRejection.AtomicPromotionFailed(target))
            is DigestCacheRead.Rejected -> DigestCacheInsertion.Rejected(inserted.reason)
        }
    }

    companion object {
        fun at(root: Path): DigestAddressedCache = DigestAddressedCache(root)
    }
}

internal sealed interface DigestCacheRead {
    data class Hit(val blob: CachedBlob) : DigestCacheRead

    data object Miss : DigestCacheRead

    data class Rejected(val reason: DigestCacheRejection) : DigestCacheRead
}

internal sealed interface DigestCacheInsertion {
    data class Stored(val blob: CachedBlob) : DigestCacheInsertion

    data class AlreadyPresent(val blob: CachedBlob) : DigestCacheInsertion

    data class Rejected(val reason: DigestCacheRejection) : DigestCacheInsertion
}

internal sealed interface DigestCacheRejection {
    data class UnusableRoot(val path: Path) : DigestCacheRejection

    data class UnusableShard(val path: Path) : DigestCacheRejection

    data class NonRegularBlob(val path: Path) : DigestCacheRejection

    data class SizeMismatch(
        val expectedBytes: Int,
        val actualBytes: Long,
    ) : DigestCacheRejection

    data class DigestMismatch(
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : DigestCacheRejection

    data class IoFailure(val path: Path) : DigestCacheRejection

    data class StagingCreationFailed(val parent: Path) : DigestCacheRejection

    data class StagingWriteFailed(val path: Path) : DigestCacheRejection

    data class AtomicPromotionFailed(val target: Path) : DigestCacheRejection

    data class ConcurrentPromotionLost(val target: Path) : DigestCacheRejection

    data class StagingCleanupFailed(val path: Path) : DigestCacheRejection
}

private enum class DigestCacheDirectoryKind {
    ROOT,
    SHARD,
}

private sealed interface ExistingDirectory {
    data object Missing : ExistingDirectory

    data object Ready : ExistingDirectory

    data class Rejected(val reason: DigestCacheRejection) : ExistingDirectory
}

private sealed interface VerifiedBlobRead {
    data class Read(val bytes: ByteArray) : VerifiedBlobRead

    data class Rejected(val reason: DigestCacheRejection) : VerifiedBlobRead
}

private fun existingDirectory(
    path: Path,
    kind: DigestCacheDirectoryKind,
): ExistingDirectory {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return ExistingDirectory.Missing
    }
    return if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        ExistingDirectory.Ready
    } else {
        ExistingDirectory.Rejected(unusableDirectory(path, kind))
    }
}

private fun ensureDirectory(
    path: Path,
    kind: DigestCacheDirectoryKind,
): DigestCacheRejection? {
    when (val existing = existingDirectory(path, kind)) {
        ExistingDirectory.Ready -> return null
        is ExistingDirectory.Rejected -> return existing.reason
        ExistingDirectory.Missing -> Unit
    }
    try {
        Files.createDirectories(path)
    } catch (_: IOException) {
        return unusableDirectory(path, kind)
    } catch (_: SecurityException) {
        return unusableDirectory(path, kind)
    }
    return when (val created = existingDirectory(path, kind)) {
        ExistingDirectory.Ready -> null
        ExistingDirectory.Missing -> unusableDirectory(path, kind)
        is ExistingDirectory.Rejected -> created.reason
    }
}

private fun unusableDirectory(
    path: Path,
    kind: DigestCacheDirectoryKind,
): DigestCacheRejection =
    when (kind) {
        DigestCacheDirectoryKind.ROOT -> DigestCacheRejection.UnusableRoot(path)
        DigestCacheDirectoryKind.SHARD -> DigestCacheRejection.UnusableShard(path)
    }

private fun readVerified(
    path: Path,
    expectation: CacheBlobExpectation,
): VerifiedBlobRead {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        return VerifiedBlobRead.Rejected(DigestCacheRejection.NonRegularBlob(path))
    }
    val bytes =
        try {
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val size = channel.size()
                if (size != expectation.byteSize.toLong()) {
                    return VerifiedBlobRead.Rejected(
                        DigestCacheRejection.SizeMismatch(expectation.byteSize, size),
                    )
                }
                val content = ByteArray(expectation.byteSize)
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        return VerifiedBlobRead.Rejected(DigestCacheRejection.IoFailure(path))
                    }
                }
                if (channel.size() != expectation.byteSize.toLong()) {
                    return VerifiedBlobRead.Rejected(
                        DigestCacheRejection.SizeMismatch(expectation.byteSize, channel.size()),
                    )
                }
                content
            }
        } catch (_: IOException) {
            return VerifiedBlobRead.Rejected(DigestCacheRejection.IoFailure(path))
        } catch (_: SecurityException) {
            return VerifiedBlobRead.Rejected(DigestCacheRejection.IoFailure(path))
        }
    verifyBytes(bytes, expectation)?.let { reason -> return VerifiedBlobRead.Rejected(reason) }
    return VerifiedBlobRead.Read(bytes)
}

private fun verifyBytes(
    bytes: ByteArray,
    expectation: CacheBlobExpectation,
): DigestCacheRejection? {
    if (bytes.size != expectation.byteSize) {
        return DigestCacheRejection.SizeMismatch(expectation.byteSize, bytes.size.toLong())
    }
    val actual = Sha256Digest.compute(bytes)
    return if (actual == expectation.sha256) {
        null
    } else {
        DigestCacheRejection.DigestMismatch(expectation.sha256, actual)
    }
}

private fun rejectAfterStagingCleanup(
    staging: Path,
    reason: DigestCacheRejection,
): DigestCacheInsertion =
    if (deleteQuietly(staging)) {
        DigestCacheInsertion.Rejected(reason)
    } else {
        DigestCacheInsertion.Rejected(DigestCacheRejection.StagingCleanupFailed(staging))
    }

private fun deleteQuietly(path: Path): Boolean =
    try {
        Files.deleteIfExists(path)
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private const val CACHE_SHARD_CHARACTERS = 2
private const val CACHE_STAGING_PREFIX = ".intelligence-cache-"
private const val CACHE_STAGING_SUFFIX = ".tmp"
