package intelligence.cli.portable

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.Comparator

internal object ProviderProjectionDirectory {
    fun materialize(
        output: Path,
        snapshotId: SnapshotId,
        packages: List<PackageArchive>,
        provider: PortableProvider,
    ): ProviderProjectionDirectoryMaterialization {
        if (packages.isEmpty()) {
            return ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.NoPackages,
            )
        }
        val orderedPackages = packages.sortedBy { archive -> archive.packageName.render() }
        val duplicate = orderedPackages.zipWithNext()
            .firstOrNull { (left, right) -> left.packageName == right.packageName }
            ?.first
        if (duplicate != null) {
            return ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.DuplicatePackage(duplicate.packageName),
            )
        }
        val tree =
            ProjectionTree(
                orderedPackages.flatMap { archive ->
                    val projection = ProviderPackageProjection.project(snapshotId, archive, provider)
                    projection.files().map { file ->
                        ProjectionTreeFile(
                            relativePath = "${archive.packageName.render()}/${file.path.render()}",
                            file = file,
                        )
                    }
                },
            )
        val normalizedOutput = output.toAbsolutePath().normalize()
        val parent = normalizedOutput.parent
            ?: return ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.ParentUnavailable(normalizedOutput),
            )
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.ParentUnavailable(parent),
            )
        }
        if (Files.exists(normalizedOutput, LinkOption.NOFOLLOW_LINKS)) {
            return if (tree.verify(normalizedOutput)) {
                ProviderProjectionDirectoryMaterialization.Unchanged(
                    normalizedOutput,
                    provider,
                    orderedPackages.map(PackageArchive::packageName),
                    tree.digest,
                )
            } else {
                ProviderProjectionDirectoryMaterialization.Rejected(
                    ProviderProjectionDirectoryRejection.OutputExists(normalizedOutput),
                )
            }
        }

        val staging =
            try {
                Files.createTempDirectory(parent, ".${normalizedOutput.fileName}.intelligence-projection-")
            } catch (_: IOException) {
                return ProviderProjectionDirectoryMaterialization.Rejected(
                    ProviderProjectionDirectoryRejection.StagingCreationFailed(parent),
                )
            } catch (_: SecurityException) {
                return ProviderProjectionDirectoryMaterialization.Rejected(
                    ProviderProjectionDirectoryRejection.StagingCreationFailed(parent),
                )
            }
        if (!setPermissions(staging, DIRECTORY_PERMISSIONS)) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.StagingWriteFailed(staging),
            )
        }
        tree.files.forEach { treeFile ->
            val target = staging.resolve(treeFile.relativePath)
            if (!createProjectionDirectories(staging, target.parent) ||
                !writeProjectionFile(target, treeFile.file)
            ) {
                return rejectAfterProjectionCleanup(
                    staging,
                    ProviderProjectionDirectoryRejection.StagingWriteFailed(target),
                )
            }
        }
        if (!forceProjectionDirectories(staging)) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.StagingWriteFailed(staging),
            )
        }
        if (!tree.verify(staging)) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.StagingVerificationFailed(staging),
            )
        }
        if (Files.exists(normalizedOutput, LinkOption.NOFOLLOW_LINKS)) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.OutputExists(normalizedOutput),
            )
        }
        try {
            Files.move(staging, normalizedOutput, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.AtomicPromotionFailed(normalizedOutput),
            )
        } catch (_: SecurityException) {
            return rejectAfterProjectionCleanup(
                staging,
                ProviderProjectionDirectoryRejection.AtomicPromotionFailed(normalizedOutput),
            )
        }
        if (!forceDirectory(parent)) {
            return ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.DirectoryFlushFailed(parent),
            )
        }
        return ProviderProjectionDirectoryMaterialization.Written(
            normalizedOutput,
            provider,
            orderedPackages.map(PackageArchive::packageName),
            tree.digest,
        )
    }
}

internal sealed interface ProviderProjectionDirectoryMaterialization {
    data class Written(
        val output: Path,
        val provider: PortableProvider,
        val packages: List<PackageName>,
        val treeDigest: Sha256Digest,
    ) : ProviderProjectionDirectoryMaterialization

    data class Unchanged(
        val output: Path,
        val provider: PortableProvider,
        val packages: List<PackageName>,
        val treeDigest: Sha256Digest,
    ) : ProviderProjectionDirectoryMaterialization

    data class Rejected(
        val reason: ProviderProjectionDirectoryRejection,
    ) : ProviderProjectionDirectoryMaterialization
}

internal sealed interface ProviderProjectionDirectoryRejection {
    data object NoPackages : ProviderProjectionDirectoryRejection

    data class DuplicatePackage(val packageName: PackageName) : ProviderProjectionDirectoryRejection

    data class ParentUnavailable(val parent: Path) : ProviderProjectionDirectoryRejection

    data class OutputExists(val output: Path) : ProviderProjectionDirectoryRejection

    data class StagingCreationFailed(val parent: Path) : ProviderProjectionDirectoryRejection

    data class StagingWriteFailed(val path: Path) : ProviderProjectionDirectoryRejection

    data class StagingVerificationFailed(val path: Path) : ProviderProjectionDirectoryRejection

    data class AtomicPromotionFailed(val output: Path) : ProviderProjectionDirectoryRejection

    data class DirectoryFlushFailed(val directory: Path) : ProviderProjectionDirectoryRejection

    data class StagingCleanupFailed(val staging: Path) : ProviderProjectionDirectoryRejection
}

private data class ProjectionTreeFile(
    val relativePath: String,
    val file: ProjectedFile,
)

private class ProjectionTree(files: List<ProjectionTreeFile>) {
    val files: List<ProjectionTreeFile> = files.sortedBy(ProjectionTreeFile::relativePath)
    val digest: Sha256Digest =
        Sha256Digest.compute(
            buildString {
                this@ProjectionTree.files.forEach { treeFile ->
                    append(treeFile.file.sha256.render())
                    append("  ")
                    append(treeFile.file.mode.renderProjectionMode())
                    append("  ")
                    append(treeFile.relativePath)
                    append('\n')
                }
            }.encodeToByteArray(),
        )

    fun verify(root: Path): Boolean {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false
        val expectedFiles = files.associateBy(ProjectionTreeFile::relativePath)
        val expectedDirectories =
            files.flatMap { treeFile ->
                val segments = treeFile.relativePath.split('/').dropLast(1)
                segments.indices.map { index -> segments.take(index + 1).joinToString("/") }
            }.toSet()
        val entries =
            try {
                Files.walk(root).use { stream ->
                    stream.filter { path -> path != root }
                        .sorted(Comparator.comparing { path: Path -> root.relativize(path).toString() })
                        .toList()
                }
            } catch (_: IOException) {
                return false
            } catch (_: SecurityException) {
                return false
            }
        val actualFiles = mutableSetOf<String>()
        val actualDirectories = mutableSetOf<String>()
        entries.forEach { path ->
            if (Files.isSymbolicLink(path)) return false
            val relative = root.relativize(path).joinToString("/") { segment -> segment.toString() }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                actualDirectories += relative
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                val expected = expectedFiles[relative] ?: return false
                if (!projectionFileMatches(path, expected.file)) return false
                actualFiles += relative
            } else {
                return false
            }
        }
        return actualFiles == expectedFiles.keys && actualDirectories == expectedDirectories
    }
}

private fun createProjectionDirectories(
    root: Path,
    directory: Path,
): Boolean {
    val relative = root.relativize(directory)
    var current = root
    relative.forEach { segment ->
        current = current.resolve(segment)
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(current)
            } catch (_: IOException) {
                return false
            } catch (_: SecurityException) {
                return false
            }
            if (!setPermissions(current, DIRECTORY_PERMISSIONS)) return false
        } else if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
    }
    return true
}

private fun writeProjectionFile(
    path: Path,
    file: ProjectedFile,
): Boolean =
    try {
        FileChannel.open(
            path,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            val buffer = ByteBuffer.wrap(file.bytes())
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        setPermissions(path, file.mode.permissions()) && projectionFileMatches(path, file)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun projectionFileMatches(
    path: Path,
    expected: ProjectedFile,
): Boolean {
    val bytes =
        try {
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                if (channel.size() != expected.byteSize.toLong()) return false
                val content = ByteArray(expected.byteSize)
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) return false
                }
                content
            }
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    if (Sha256Digest.compute(bytes) != expected.sha256) return false
    val permissions =
        try {
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
        } catch (_: IOException) {
            return false
        } catch (_: UnsupportedOperationException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    return permissions == expected.mode.permissions()
}

private fun CanonicalZipEntryMode.permissions(): Set<PosixFilePermission> =
    when (this) {
        CanonicalZipEntryMode.REGULAR -> REGULAR_FILE_PERMISSIONS
        CanonicalZipEntryMode.EXECUTABLE -> EXECUTABLE_FILE_PERMISSIONS
    }

private fun CanonicalZipEntryMode.renderProjectionMode(): String =
    when (this) {
        CanonicalZipEntryMode.REGULAR -> "100644"
        CanonicalZipEntryMode.EXECUTABLE -> "100755"
    }

private fun setPermissions(
    path: Path,
    permissions: Set<PosixFilePermission>,
): Boolean =
    try {
        Files.setPosixFilePermissions(path, permissions)
        true
    } catch (_: IOException) {
        false
    } catch (_: UnsupportedOperationException) {
        false
    } catch (_: SecurityException) {
        false
    }

private fun forceProjectionDirectories(root: Path): Boolean {
    val directories =
        try {
            Files.walk(root).use { stream ->
                stream.filter { path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) }
                    .sorted(Comparator.reverseOrder())
                    .toList()
            }
        } catch (_: IOException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    return directories.all(::forceDirectory)
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

private fun rejectAfterProjectionCleanup(
    staging: Path,
    reason: ProviderProjectionDirectoryRejection,
): ProviderProjectionDirectoryMaterialization.Rejected =
    if (deleteProjectionTree(staging)) {
        ProviderProjectionDirectoryMaterialization.Rejected(reason)
    } else {
        ProviderProjectionDirectoryMaterialization.Rejected(
            ProviderProjectionDirectoryRejection.StagingCleanupFailed(staging),
        )
    }

private fun deleteProjectionTree(root: Path): Boolean =
    try {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private val REGULAR_FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--")
private val EXECUTABLE_FILE_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x")
private val DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x")
