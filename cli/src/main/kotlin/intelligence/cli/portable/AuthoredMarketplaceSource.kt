package intelligence.cli.portable

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Comparator

internal class AuthoredMarketplace private constructor(
    val marketplaceId: MarketplaceId,
    val defaultPackage: PackageName,
    packages: List<PackageArchive>,
) {
    val packages: List<PackageArchive> = packages.sortedBy { archive -> archive.packageName.render() }

    companion object {
        fun inspect(source: Path): AuthoredMarketplaceInspection {
            val root = source.toAbsolutePath().normalize()
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return AuthoredMarketplaceInspection.Rejected(AuthoredMarketplaceRejection.NotDirectory(root))
            }
            val rootEntries = listDirectory(root)
                ?: return AuthoredMarketplaceInspection.Rejected(AuthoredMarketplaceRejection.ReadFailed(root))
            val expectedRootEntries = listOf(root.resolve(DEFAULT_PACKAGE_FILE), root.resolve(PACKAGES_DIRECTORY))
            if (rootEntries != expectedRootEntries) {
                return AuthoredMarketplaceInspection.Rejected(
                    AuthoredMarketplaceRejection.RootClosureMismatch(
                        expectedRootEntries.map { path -> path.fileName.toString() },
                        rootEntries.map { path -> path.fileName.toString() },
                    ),
                )
            }
            val defaultPackage =
                when (val parsed = readDefaultPackage(root.resolve(DEFAULT_PACKAGE_FILE))) {
                    is DefaultPackageReading.Read -> parsed.packageName
                    is DefaultPackageReading.Rejected ->
                        return AuthoredMarketplaceInspection.Rejected(parsed.reason)
                }
            val packagesRoot = root.resolve(PACKAGES_DIRECTORY)
            if (!Files.isDirectory(packagesRoot, LinkOption.NOFOLLOW_LINKS)) {
                return AuthoredMarketplaceInspection.Rejected(
                    AuthoredMarketplaceRejection.NotDirectory(packagesRoot),
                )
            }
            val packageDirectories = listDirectory(packagesRoot)
                ?: return AuthoredMarketplaceInspection.Rejected(AuthoredMarketplaceRejection.ReadFailed(packagesRoot))
            if (packageDirectories.isEmpty()) {
                return AuthoredMarketplaceInspection.Rejected(AuthoredMarketplaceRejection.NoPackages)
            }
            if (packageDirectories.size > MAX_PACKAGES_PER_SNAPSHOT) {
                return AuthoredMarketplaceInspection.Rejected(
                    AuthoredMarketplaceRejection.TooManyPackages(
                        packageDirectories.size,
                        MAX_PACKAGES_PER_SNAPSHOT,
                    ),
                )
            }
            val archives = mutableListOf<PackageArchive>()
            var marketplaceId: MarketplaceId? = null
            packageDirectories.forEach { packageDirectory ->
                if (!Files.isDirectory(packageDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.NotDirectory(packageDirectory),
                    )
                }
                val directoryName =
                    when (val parsed = PackageName.parse(packageDirectory.fileName.toString())) {
                        is IdentifierParse.Accepted -> parsed.value
                        is IdentifierParse.Rejected -> {
                            return AuthoredMarketplaceInspection.Rejected(
                                AuthoredMarketplaceRejection.InvalidPackageDirectory(
                                    packageDirectory.fileName.toString(),
                                    parsed.reason,
                                ),
                            )
                        }
                    }
                val manifestPath = packageDirectory.resolve(PACKAGE_MANIFEST_FILE)
                val manifestBytes = readBounded(manifestPath, MAX_PACKAGE_JSON_BYTES)
                    ?: return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.InvalidManifestFile(manifestPath),
                    )
                val manifest =
                    when (val parsed = PackageManifest.parse(manifestBytes)) {
                        is PackageManifestParsing.Parsed -> parsed.manifest
                        is PackageManifestParsing.Rejected -> {
                            return AuthoredMarketplaceInspection.Rejected(
                                AuthoredMarketplaceRejection.ManifestRejected(directoryName, parsed.reason),
                            )
                        }
                    }
                if (manifest.name != directoryName) {
                    return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.PackageDirectoryMismatch(directoryName, manifest.name),
                    )
                }
                val expectedMarketplace = marketplaceId
                if (expectedMarketplace == null) {
                    marketplaceId = manifest.marketplaceId
                } else if (expectedMarketplace != manifest.marketplaceId) {
                    return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.MarketplaceMismatch(
                            expectedMarketplace,
                            manifest.marketplaceId,
                        ),
                    )
                }
                val evidence = manifest.skills.flatMap { skill -> listOf(skill.primary) + skill.assets }
                val expectedFiles = (listOf(PACKAGE_MANIFEST_FILE) + evidence.map { file -> file.path.render() }).toSortedSet()
                val expectedDirectories =
                    evidence.flatMap { file ->
                        val segments = file.path.render().split('/')
                        (1 until segments.size).map { count -> segments.take(count).joinToString("/") }
                    }.toSortedSet()
                val actualClosure = readPackageClosure(packageDirectory)
                    ?: return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.ReadFailed(packageDirectory),
                    )
                if (actualClosure.files != expectedFiles || actualClosure.directories != expectedDirectories) {
                    return AuthoredMarketplaceInspection.Rejected(
                        AuthoredMarketplaceRejection.PackageClosureMismatch(
                            directoryName,
                            expectedFiles.toList(),
                            actualClosure.files.toList(),
                        ),
                    )
                }
                val sourceFiles = mutableListOf<PackageSourceFile>()
                evidence.forEach { fileEvidence ->
                    val path = packageDirectory.resolve(fileEvidence.path.render())
                    val bytes = readBounded(path, MAX_PACKAGE_FILE_BYTES)
                        ?: return AuthoredMarketplaceInspection.Rejected(
                            AuthoredMarketplaceRejection.InvalidSourceFile(directoryName, fileEvidence.path),
                        )
                    when (
                        val created =
                            PackageSourceFile.create(
                                fileEvidence.path,
                                bytes,
                                Files.isExecutable(path),
                            )
                    ) {
                        is PackageSourceFileCreation.Created -> sourceFiles += created.file
                        is PackageSourceFileCreation.Rejected -> {
                            return AuthoredMarketplaceInspection.Rejected(
                                AuthoredMarketplaceRejection.SourceFileRejected(
                                    directoryName,
                                    fileEvidence.path,
                                    created.reason,
                                ),
                            )
                        }
                    }
                }
                when (val materialized = PackageArchive.materialize(manifest, sourceFiles)) {
                    is PackageArchiveMaterialization.Materialized -> archives += materialized.archive
                    is PackageArchiveMaterialization.Rejected -> {
                        return AuthoredMarketplaceInspection.Rejected(
                            AuthoredMarketplaceRejection.PackageRejected(directoryName, materialized.reason),
                        )
                    }
                }
            }
            if (archives.none { archive -> archive.packageName == defaultPackage }) {
                return AuthoredMarketplaceInspection.Rejected(
                    AuthoredMarketplaceRejection.DefaultPackageMissing(defaultPackage),
                )
            }
            return AuthoredMarketplaceInspection.Inspected(
                root,
                AuthoredMarketplace(checkNotNull(marketplaceId), defaultPackage, archives),
            )
        }
    }
}

internal sealed interface AuthoredMarketplaceInspection {
    data class Inspected(
        val source: Path,
        val marketplace: AuthoredMarketplace,
    ) : AuthoredMarketplaceInspection

    data class Rejected(val reason: AuthoredMarketplaceRejection) : AuthoredMarketplaceInspection
}

internal sealed interface AuthoredMarketplaceRejection {
    data class NotDirectory(val path: Path) : AuthoredMarketplaceRejection

    data class ReadFailed(val path: Path) : AuthoredMarketplaceRejection

    data class RootClosureMismatch(
        val expected: List<String>,
        val actual: List<String>,
    ) : AuthoredMarketplaceRejection

    data class InvalidDefaultPackageFile(val path: Path) : AuthoredMarketplaceRejection

    data class InvalidDefaultPackage(val reason: IdentifierRejection) : AuthoredMarketplaceRejection

    data object NoPackages : AuthoredMarketplaceRejection

    data class TooManyPackages(
        val actual: Int,
        val maximum: Int,
    ) : AuthoredMarketplaceRejection

    data class InvalidPackageDirectory(
        val candidate: String,
        val reason: IdentifierRejection,
    ) : AuthoredMarketplaceRejection

    data class InvalidManifestFile(val path: Path) : AuthoredMarketplaceRejection

    data class ManifestRejected(
        val packageName: PackageName,
        val reason: PackageManifestRejection,
    ) : AuthoredMarketplaceRejection

    data class PackageDirectoryMismatch(
        val directory: PackageName,
        val manifest: PackageName,
    ) : AuthoredMarketplaceRejection

    data class MarketplaceMismatch(
        val expected: MarketplaceId,
        val actual: MarketplaceId,
    ) : AuthoredMarketplaceRejection

    data class PackageClosureMismatch(
        val packageName: PackageName,
        val expectedFiles: List<String>,
        val actualFiles: List<String>,
    ) : AuthoredMarketplaceRejection

    data class InvalidSourceFile(
        val packageName: PackageName,
        val path: PackageEntryPath,
    ) : AuthoredMarketplaceRejection

    data class SourceFileRejected(
        val packageName: PackageName,
        val path: PackageEntryPath,
        val reason: PackageSourceFileRejection,
    ) : AuthoredMarketplaceRejection

    data class PackageRejected(
        val packageName: PackageName,
        val reason: PackageArchiveRejection,
    ) : AuthoredMarketplaceRejection

    data class DefaultPackageMissing(val packageName: PackageName) : AuthoredMarketplaceRejection
}

private sealed interface DefaultPackageReading {
    data class Read(val packageName: PackageName) : DefaultPackageReading

    data class Rejected(val reason: AuthoredMarketplaceRejection) : DefaultPackageReading
}

private fun readDefaultPackage(path: Path): DefaultPackageReading {
    val bytes = readBounded(path, MAX_DEFAULT_PACKAGE_BYTES)
        ?: return DefaultPackageReading.Rejected(AuthoredMarketplaceRejection.InvalidDefaultPackageFile(path))
    val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        ?: return DefaultPackageReading.Rejected(AuthoredMarketplaceRejection.InvalidDefaultPackageFile(path))
    if (!text.endsWith('\n') || text.dropLast(1).contains('\n') || '\r' in text) {
        return DefaultPackageReading.Rejected(AuthoredMarketplaceRejection.InvalidDefaultPackageFile(path))
    }
    return when (val parsed = PackageName.parse(text.dropLast(1))) {
        is IdentifierParse.Accepted -> DefaultPackageReading.Read(parsed.value)
        is IdentifierParse.Rejected ->
            DefaultPackageReading.Rejected(AuthoredMarketplaceRejection.InvalidDefaultPackage(parsed.reason))
    }
}

private data class PackageClosure(
    val files: Set<String>,
    val directories: Set<String>,
)

private fun readPackageClosure(root: Path): PackageClosure? =
    try {
        val files = sortedSetOf<String>()
        val directories = sortedSetOf<String>()
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.comparing { path: Path -> root.relativize(path).toString() }).forEach { path ->
                if (path == root) return@forEach
                if (Files.isSymbolicLink(path)) throw InvalidSourceClosure()
                val relative = root.relativize(path).joinToString("/") { segment -> segment.toString() }
                when {
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> directories += relative
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> files += relative
                    else -> throw InvalidSourceClosure()
                }
            }
        }
        PackageClosure(files, directories)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: InvalidSourceClosure) {
        null
    }

private fun listDirectory(directory: Path): List<Path>? =
    try {
        Files.list(directory).use { stream ->
            stream.sorted(Comparator.comparing { path: Path -> path.fileName.toString() }).toList()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

private fun readBounded(
    path: Path,
    maximumBytes: Int,
): ByteArray? =
    try {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        val size = Files.size(path)
        if (size !in 1..maximumBytes.toLong()) return null
        Files.readAllBytes(path).takeIf { bytes -> bytes.size.toLong() == size }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

private class InvalidSourceClosure : RuntimeException()

private const val DEFAULT_PACKAGE_FILE = "default-package"
private const val PACKAGES_DIRECTORY = "packages"
private const val PACKAGE_MANIFEST_FILE = "package.json"
private const val MAX_DEFAULT_PACKAGE_BYTES = 256
