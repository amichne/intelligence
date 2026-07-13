package intelligence.cli.portable

internal object PackageArchiveParser {
    fun parse(bytes: ByteArray): PackageArchiveParsing {
        val zip =
            when (val parsed = CanonicalZipArchive.parse(bytes)) {
                is CanonicalZipParsing.Parsed -> parsed
                is CanonicalZipParsing.Rejected -> {
                    return PackageArchiveParsing.Rejected(
                        PackageArchiveParseRejection.InvalidZip(parsed.reason),
                    )
                }
            }
        val manifestEntry = zip.entries.singleOrNull { entry ->
            entry.path.render() == PACKAGE_MANIFEST_ENTRY_PATH
        } ?: return PackageArchiveParsing.Rejected(PackageArchiveParseRejection.MissingManifest)
        if (manifestEntry.mode != CanonicalZipEntryMode.REGULAR) {
            return PackageArchiveParsing.Rejected(PackageArchiveParseRejection.ExecutableManifest)
        }
        val manifest =
            when (val parsed = PackageManifest.parse(manifestEntry.contentCopy())) {
                is PackageManifestParsing.Parsed -> parsed.manifest
                is PackageManifestParsing.Rejected -> {
                    return PackageArchiveParsing.Rejected(
                        PackageArchiveParseRejection.InvalidManifest(parsed.reason),
                    )
                }
            }
        val sourceFiles = mutableListOf<PackageSourceFile>()
        zip.entries
            .filterNot { entry -> entry.path == manifestEntry.path }
            .forEach { entry ->
                when (
                    val created =
                        PackageSourceFile.create(
                            path = entry.path,
                            content = entry.contentCopy(),
                            executable = entry.mode == CanonicalZipEntryMode.EXECUTABLE,
                        )
                ) {
                    is PackageSourceFileCreation.Created -> sourceFiles += created.file
                    is PackageSourceFileCreation.Rejected -> {
                        return PackageArchiveParsing.Rejected(
                            PackageArchiveParseRejection.InvalidSourceFile(
                                path = entry.path,
                                reason = created.reason,
                            ),
                        )
                    }
                }
            }
        val packageArchive =
            when (val materialized = PackageArchive.materialize(manifest, sourceFiles)) {
                is PackageArchiveMaterialization.Materialized -> materialized.archive
                is PackageArchiveMaterialization.Rejected -> {
                    return PackageArchiveParsing.Rejected(
                        PackageArchiveParseRejection.InvalidContent(materialized.reason),
                    )
                }
            }
        if (!bytes.contentEquals(packageArchive.bytes())) {
            return PackageArchiveParsing.Rejected(PackageArchiveParseRejection.NonCanonicalPackage)
        }
        return PackageArchiveParsing.Parsed(packageArchive)
    }
}

internal sealed interface PackageArchiveParsing {
    data class Parsed(val archive: PackageArchive) : PackageArchiveParsing

    data class Rejected(val reason: PackageArchiveParseRejection) : PackageArchiveParsing
}

internal sealed interface PackageArchiveParseRejection {
    data class InvalidZip(val reason: CanonicalZipParseRejection) : PackageArchiveParseRejection

    data object MissingManifest : PackageArchiveParseRejection

    data object ExecutableManifest : PackageArchiveParseRejection

    data class InvalidManifest(val reason: PackageManifestRejection) : PackageArchiveParseRejection

    data class InvalidSourceFile(
        val path: PackageEntryPath,
        val reason: PackageSourceFileRejection,
    ) : PackageArchiveParseRejection

    data class InvalidContent(val reason: PackageArchiveRejection) : PackageArchiveParseRejection

    data object NonCanonicalPackage : PackageArchiveParseRejection
}

private const val PACKAGE_MANIFEST_ENTRY_PATH = "package.json"
