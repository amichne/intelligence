package intelligence.cli.portable

@JvmInline
internal value class ProviderAdapterVersion private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun fromPackageDigest(digest: Sha256Digest): ProviderAdapterVersion =
            ProviderAdapterVersion("0.0.0-intelligence.sha${digest.render()}")
    }
}

internal class ProviderPackageProjection private constructor(
    val snapshotId: SnapshotId,
    val packageName: PackageName,
    val provider: PortableProvider,
    val adapterVersion: ProviderAdapterVersion,
    files: List<ProjectedFile>,
) {
    private val files: List<ProjectedFile> = files.toList()
    private val filesByPath: Map<PackageEntryPath, ProjectedFile> = files.associateBy(ProjectedFile::path)

    fun files(): List<ProjectedFile> = files.toList()

    fun file(path: PackageEntryPath): ProjectedFile = filesByPath.getValue(path)

    fun receiptBytes(): ByteArray = file(trustedPath(PROJECTION_RECEIPT_PATH)).bytes()

    fun checksumBytes(): ByteArray = file(trustedPath(PROJECTION_CHECKSUM_PATH)).bytes()

    fun treeDigest(): Sha256Digest = Sha256Digest.compute(checksumBytes())

    fun verify(actualFiles: List<ProjectedFile>): ProviderProjectionVerification =
        verifyProviderProjection(files, actualFiles)

    companion object {
        fun project(
            snapshotId: SnapshotId,
            packageArchive: PackageArchive,
            provider: PortableProvider,
        ): ProviderPackageProjection {
            val adapterVersion = ProviderAdapterVersion.fromPackageDigest(packageArchive.sha256)
            val projectedSourceFiles =
                packageArchive.sourceFiles().map { source ->
                    trustedProjectedFile(
                        path = source.path,
                        content = source.bytes(),
                        mode =
                            if (source.executable) {
                                CanonicalZipEntryMode.EXECUTABLE
                            } else {
                                CanonicalZipEntryMode.REGULAR
                            },
                    )
                }
            val manifestFile =
                trustedProjectedFile(
                    path = trustedPath(provider.manifestPath()),
                    content = providerManifestBytes(packageArchive, provider, adapterVersion),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val receiptFile =
                trustedProjectedFile(
                    path = trustedPath(PROJECTION_RECEIPT_PATH),
                    content = projectionReceiptBytes(snapshotId, packageArchive, provider, adapterVersion),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val filesWithoutChecksums =
                (projectedSourceFiles + manifestFile + receiptFile).sortedBy { file -> file.path.render() }
            val checksumFile =
                trustedProjectedFile(
                    path = trustedPath(PROJECTION_CHECKSUM_PATH),
                    content = projectionChecksums(filesWithoutChecksums),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val files = (filesWithoutChecksums + checksumFile).sortedBy { file -> file.path.render() }
            return ProviderPackageProjection(
                snapshotId = snapshotId,
                packageName = packageArchive.packageName,
                provider = provider,
                adapterVersion = adapterVersion,
                files = files,
            )
        }
    }
}

private fun providerManifestBytes(
    packageArchive: PackageArchive,
    provider: PortableProvider,
    adapterVersion: ProviderAdapterVersion,
): ByteArray =
    canonicalDocumentBytes(
        canonicalJsonObject(
            "description" to canonicalJsonString(packageArchive.description.render()),
            "name" to canonicalJsonString(packageArchive.packageName.render()),
            "skills" to canonicalJsonString(provider.skillsPath()),
            "version" to canonicalJsonString(adapterVersion.render()),
        ),
    )

private fun projectionReceiptBytes(
    snapshotId: SnapshotId,
    packageArchive: PackageArchive,
    provider: PortableProvider,
    adapterVersion: ProviderAdapterVersion,
): ByteArray {
    val files =
        packageArchive.sourceFiles().sortedBy { file -> file.path.render() }.map { file ->
            canonicalJsonObject(
                "executable" to CanonicalJsonBoolean(file.executable),
                "generatedPath" to canonicalJsonString(file.path.render()),
                "sha256" to canonicalJsonString(file.sha256().render()),
                "size" to canonicalJsonInteger(file.byteSize.toLong()),
                "sourcePath" to canonicalJsonString(file.path.render()),
            )
        }
    val skills =
        packageArchive.manifest.skills.map { skill ->
            canonicalJsonObject(
                "generatedPath" to canonicalJsonString(skill.primary.path.render()),
                "name" to canonicalJsonString(skill.name.render()),
                "sourcePath" to canonicalJsonString(skill.primary.path.render()),
            )
        }
    return canonicalDocumentBytes(
        canonicalJsonObject(
            "adapterVersion" to canonicalJsonString(adapterVersion.render()),
            "files" to CanonicalJsonArray(files),
            "generator" to canonicalJsonString(PROJECTION_GENERATOR),
            "marketplaceId" to canonicalJsonString(packageArchive.marketplaceId.render()),
            "packageArchive" to
                canonicalJsonObject(
                    "name" to canonicalJsonString(packageArchive.assetName.render()),
                    "sha256" to canonicalJsonString(packageArchive.sha256.render()),
                    "size" to canonicalJsonInteger(packageArchive.byteSize.toLong()),
                ),
            "packageName" to canonicalJsonString(packageArchive.packageName.render()),
            "provider" to canonicalJsonString(provider.render()),
            "schemaVersion" to canonicalJsonInteger(PROJECTION_SCHEMA_VERSION.toLong()),
            "skills" to CanonicalJsonArray(skills),
            "snapshotId" to canonicalJsonString(snapshotId.render()),
            "type" to canonicalJsonString(PROJECTION_RECEIPT_TYPE),
        ),
    )
}

private fun projectionChecksums(files: List<ProjectedFile>): ByteArray =
    buildString {
        files.forEach { file ->
            append(file.sha256.render())
            append("  ")
            append(file.path.render())
            append('\n')
        }
    }.encodeToByteArray()

private fun canonicalDocumentBytes(root: CanonicalJsonObject): ByteArray =
    when (val created = CanonicalJsonDocument.create(root)) {
        is CanonicalJsonDocumentCreation.Created -> created.document.bytes()
        is CanonicalJsonDocumentCreation.Rejected -> error("Generated provider document exceeds the V1 bound")
    }

private fun PortableProvider.manifestPath(): String =
    when (this) {
        PortableProvider.CODEX -> ".codex-plugin/plugin.json"
        PortableProvider.GITHUB_COPILOT -> "plugin.json"
    }

private fun PortableProvider.skillsPath(): String =
    when (this) {
        PortableProvider.CODEX -> "./skills/"
        PortableProvider.GITHUB_COPILOT -> "skills/"
    }

private fun trustedPath(raw: String): PackageEntryPath =
    when (val parsed = PackageEntryPath.parse(raw)) {
        is PackageEntryPathParse.Accepted -> parsed.value
        is PackageEntryPathParse.Rejected -> error("Generated projection path is invalid: $raw")
    }

private fun trustedProjectedFile(
    path: PackageEntryPath,
    content: ByteArray,
    mode: CanonicalZipEntryMode,
): ProjectedFile =
    when (val created = ProjectedFile.create(path, content, mode)) {
        is ProjectedFileCreation.Created -> created.file
        is ProjectedFileCreation.Rejected -> error("Generated projection file exceeds the V1 bound: ${path.render()}")
    }

internal const val PROJECTION_SCHEMA_VERSION = 1
internal const val PROJECTION_RECEIPT_TYPE = "INTELLIGENCE_PACKAGE_PROJECTION"
internal const val PROJECTION_GENERATOR = "intelligence-kotlin-v1"
internal const val PROJECTION_RECEIPT_PATH = ".intelligence/projection.json"
internal const val PROJECTION_CHECKSUM_PATH = ".intelligence/checksums.sha256"
