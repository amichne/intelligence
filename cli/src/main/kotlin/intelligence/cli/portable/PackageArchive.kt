package intelligence.cli.portable

internal class PackageArchive private constructor(
    val packageName: PackageName,
    val assetName: ReleaseAssetName,
    private val content: ByteArray,
) {
    val byteSize: Int
        get() = content.size

    val sha256: Sha256Digest = Sha256Digest.compute(content)

    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun materialize(
            manifest: PackageManifest,
            sourceFiles: List<PackageSourceFile>,
        ): PackageArchiveMaterialization {
            val sourceByPath = linkedMapOf<PackageEntryPath, PackageSourceFile>()
            sourceFiles.forEach { source ->
                if (sourceByPath.putIfAbsent(source.path, source) != null) {
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.DuplicateFile(source.path),
                    )
                }
            }

            val declaredFiles =
                manifest.skills
                    .flatMap { skill -> listOf(skill.primary) + skill.assets }
                    .sortedBy { evidence -> evidence.path.render() }
            val declaredByPath = declaredFiles.associateBy(PackageFileEvidence::path)
            declaredFiles.firstOrNull { evidence -> evidence.path !in sourceByPath }?.let { missing ->
                return PackageArchiveMaterialization.Rejected(
                    PackageArchiveRejection.MissingFile(missing.path),
                )
            }
            sourceByPath.keys
                .sortedBy(PackageEntryPath::render)
                .firstOrNull { path -> path !in declaredByPath }
                ?.let { unexpected ->
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.UnexpectedFile(unexpected),
                    )
                }

            declaredFiles.forEach { evidence ->
                val source = checkNotNull(sourceByPath[evidence.path])
                if (source.byteSize != evidence.byteSize) {
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.FileSizeMismatch(
                            path = evidence.path,
                            expectedBytes = evidence.byteSize,
                            actualBytes = source.byteSize,
                        ),
                    )
                }
                val actualDigest = source.sha256()
                if (actualDigest != evidence.sha256) {
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.FileDigestMismatch(
                            path = evidence.path,
                            expected = evidence.sha256,
                            actual = actualDigest,
                        ),
                    )
                }
                if (source.executable != evidence.executable) {
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.FileModeMismatch(
                            path = evidence.path,
                            expectedExecutable = evidence.executable,
                            actualExecutable = source.executable,
                        ),
                    )
                }
            }

            manifest.skills.forEach { skill ->
                val primary = checkNotNull(sourceByPath[skill.primary.path])
                when (
                    val parsed =
                        PortableSkillDocument.parse(
                            bytes = primary.bytes(),
                            expectedName = skill.name,
                            expectedDescription = skill.description,
                        )
                ) {
                    is PortableSkillDocumentParsing.Parsed -> Unit
                    is PortableSkillDocumentParsing.Rejected -> {
                        return PackageArchiveMaterialization.Rejected(
                            PackageArchiveRejection.InvalidSkillDocument(
                                skill = skill.name,
                                reason = parsed.reason,
                            ),
                        )
                    }
                }
            }

            val packageJsonPath = trustedPackageEntryPath(PACKAGE_MANIFEST_ARCHIVE_PATH)
            val entries = mutableListOf<CanonicalZipEntry>()
            when (
                val created =
                    CanonicalZipEntry.create(
                        path = packageJsonPath,
                        content = manifest.canonicalBytes(),
                        mode = CanonicalZipEntryMode.REGULAR,
                    )
            ) {
                is CanonicalZipEntryCreation.Accepted -> entries += created.entry
                is CanonicalZipEntryCreation.Rejected -> {
                    return PackageArchiveMaterialization.Rejected(
                        PackageArchiveRejection.ZipEntryRejected(packageJsonPath, created.reason),
                    )
                }
            }
            declaredFiles.forEach { evidence ->
                val source = checkNotNull(sourceByPath[evidence.path])
                when (
                    val created =
                        CanonicalZipEntry.create(
                            path = source.path,
                            content = source.bytes(),
                            mode =
                                if (source.executable) {
                                    CanonicalZipEntryMode.EXECUTABLE
                                } else {
                                    CanonicalZipEntryMode.REGULAR
                                },
                        )
                ) {
                    is CanonicalZipEntryCreation.Accepted -> entries += created.entry
                    is CanonicalZipEntryCreation.Rejected -> {
                        return PackageArchiveMaterialization.Rejected(
                            PackageArchiveRejection.ZipEntryRejected(source.path, created.reason),
                        )
                    }
                }
            }

            val archive =
                when (val created = CanonicalZipArchive.create(entries)) {
                    is CanonicalZipCreation.Created -> created.archive
                    is CanonicalZipCreation.Rejected -> {
                        return PackageArchiveMaterialization.Rejected(
                            PackageArchiveRejection.ZipRejected(created.reason),
                        )
                    }
                }
            return PackageArchiveMaterialization.Materialized(
                PackageArchive(
                    packageName = manifest.name,
                    assetName = ReleaseAssetName.packageArchive(manifest.name),
                    content = archive.bytes(),
                ),
            )
        }
    }
}

internal sealed interface PackageArchiveMaterialization {
    data class Materialized(val archive: PackageArchive) : PackageArchiveMaterialization

    data class Rejected(val reason: PackageArchiveRejection) : PackageArchiveMaterialization
}

internal sealed interface PackageArchiveRejection {
    data class DuplicateFile(val path: PackageEntryPath) : PackageArchiveRejection

    data class MissingFile(val path: PackageEntryPath) : PackageArchiveRejection

    data class UnexpectedFile(val path: PackageEntryPath) : PackageArchiveRejection

    data class FileSizeMismatch(
        val path: PackageEntryPath,
        val expectedBytes: Int,
        val actualBytes: Int,
    ) : PackageArchiveRejection

    data class FileDigestMismatch(
        val path: PackageEntryPath,
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : PackageArchiveRejection

    data class FileModeMismatch(
        val path: PackageEntryPath,
        val expectedExecutable: Boolean,
        val actualExecutable: Boolean,
    ) : PackageArchiveRejection

    data class InvalidSkillDocument(
        val skill: SkillName,
        val reason: PortableSkillDocumentRejection,
    ) : PackageArchiveRejection

    data class ZipEntryRejected(
        val path: PackageEntryPath,
        val reason: CanonicalZipEntryRejection,
    ) : PackageArchiveRejection

    data class ZipRejected(val reason: CanonicalZipRejection) : PackageArchiveRejection
}

private fun trustedPackageEntryPath(value: String): PackageEntryPath =
    when (val parsed = PackageEntryPath.parse(value)) {
        is PackageEntryPathParse.Accepted -> parsed.value
        is PackageEntryPathParse.Rejected -> error("Generated package path is invalid: ${parsed.reason}")
    }

private const val PACKAGE_MANIFEST_ARCHIVE_PATH = "package.json"
