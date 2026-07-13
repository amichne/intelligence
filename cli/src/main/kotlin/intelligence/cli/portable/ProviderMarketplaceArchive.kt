package intelligence.cli.portable

internal class ProviderMarketplaceArchive private constructor(
    val marketplaceId: MarketplaceId,
    val snapshotId: SnapshotId,
    val provider: PortableProvider,
    packageEvidence: List<ProviderMarketplacePackageEvidence>,
    files: List<ProjectedFile>,
    canonicalArchive: CanonicalZipArchive,
) {
    private val packageEvidence: List<ProviderMarketplacePackageEvidence> = packageEvidence.toList()
    private val files: List<ProjectedFile> = files.toList()
    private val content: ByteArray = canonicalArchive.bytes()

    val assetName: ReleaseAssetName = ReleaseAssetName.providerArchive(provider)

    val byteSize: Int
        get() = content.size

    val sha256: Sha256Digest = Sha256Digest.compute(content)

    fun bytes(): ByteArray = content.copyOf()

    fun catalogBytes(): ByteArray = file(provider.catalogPath()).bytes()

    fun receiptBytes(): ByteArray = file(providerArchivePath(PROJECTION_RECEIPT_PATH)).bytes()

    fun checksumBytes(): ByteArray = file(providerArchivePath(PROJECTION_CHECKSUM_PATH)).bytes()

    fun packageEvidence(): List<ProviderMarketplacePackageEvidence> = packageEvidence.toList()

    internal fun evidence(): SnapshotAssetEvidence = SnapshotAssetEvidence(assetName, byteSize, sha256)

    fun verify(bytes: ByteArray): ProviderMarketplaceArchiveVerification =
        when (val parsed = CanonicalZipArchive.parse(bytes)) {
            is CanonicalZipParsing.Rejected ->
                ProviderMarketplaceArchiveVerification.Rejected(
                    ProviderMarketplaceArchiveVerificationRejection.InvalidArchive(parsed.reason),
                )
            is CanonicalZipParsing.Parsed -> {
                val actualFiles =
                    parsed.entries.map { entry ->
                        when (
                            val created =
                                ProjectedFile.create(
                                    path = entry.path,
                                    content = entry.contentCopy(),
                                    mode = entry.mode,
                                )
                        ) {
                            is ProjectedFileCreation.Created -> created.file
                            is ProjectedFileCreation.Rejected ->
                                error("A parsed canonical ZIP entry exceeded the shared file bound")
                        }
                    }
                when (val verification = verifyProviderProjection(files, actualFiles)) {
                    ProviderProjectionVerification.Verified -> ProviderMarketplaceArchiveVerification.Verified
                    is ProviderProjectionVerification.Rejected ->
                        ProviderMarketplaceArchiveVerification.Rejected(
                            ProviderMarketplaceArchiveVerificationRejection.InvalidTree(verification.reason),
                        )
                }
            }
        }

    private fun file(path: PackageEntryPath): ProjectedFile =
        checkNotNull(files.singleOrNull { file -> file.path == path })

    companion object {
        fun materialize(
            marketplaceId: MarketplaceId,
            snapshotId: SnapshotId,
            packages: List<PackageArchive>,
            provider: PortableProvider,
        ): ProviderMarketplaceArchiveMaterialization {
            if (packages.isEmpty()) {
                return ProviderMarketplaceArchiveMaterialization.Rejected(
                    ProviderMarketplaceArchiveRejection.NoPackages,
                )
            }
            if (packages.size > MAX_PACKAGES_PER_SNAPSHOT) {
                return ProviderMarketplaceArchiveMaterialization.Rejected(
                    ProviderMarketplaceArchiveRejection.TooManyPackages(
                        actual = packages.size,
                        maximum = MAX_PACKAGES_PER_SNAPSHOT,
                    ),
                )
            }

            val orderedPackages = packages.sortedBy { archive -> archive.packageName.render() }
            val seenNames = mutableSetOf<PackageName>()
            orderedPackages.forEach { archive ->
                if (!seenNames.add(archive.packageName)) {
                    return ProviderMarketplaceArchiveMaterialization.Rejected(
                        ProviderMarketplaceArchiveRejection.DuplicatePackage(archive.packageName),
                    )
                }
                if (archive.marketplaceId != marketplaceId) {
                    return ProviderMarketplaceArchiveMaterialization.Rejected(
                        ProviderMarketplaceArchiveRejection.PackageMarketplaceMismatch(
                            packageName = archive.packageName,
                            expected = marketplaceId,
                            actual = archive.marketplaceId,
                        ),
                    )
                }
            }

            val packageProjections =
                orderedPackages.map { archive ->
                    archive to ProviderPackageProjection.project(snapshotId, archive, provider)
                }
            val pluginFiles = mutableListOf<ProjectedFile>()
            packageProjections.forEach { (archive, projection) ->
                projection.files().forEach { file ->
                    val projectedPath =
                        when (
                            val parsed =
                                PackageEntryPath.parse(
                                    "plugins/${archive.packageName.render()}/${file.path.render()}",
                                )
                        ) {
                            is PackageEntryPathParse.Accepted -> parsed.value
                            is PackageEntryPathParse.Rejected -> {
                                return ProviderMarketplaceArchiveMaterialization.Rejected(
                                    ProviderMarketplaceArchiveRejection.ProjectedPathRejected(
                                        packageName = archive.packageName,
                                        sourcePath = file.path,
                                        reason = parsed.reason,
                                    ),
                                )
                            }
                        }
                    pluginFiles += trustedProviderArchiveFile(projectedPath, file.bytes(), file.mode)
                }
            }

            val catalogFile =
                trustedProviderArchiveFile(
                    path = provider.catalogPath(),
                    content = providerCatalogBytes(marketplaceId, orderedPackages, provider),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val receiptFile =
                trustedProviderArchiveFile(
                    path = providerArchivePath(PROJECTION_RECEIPT_PATH),
                    content =
                        providerArchiveReceiptBytes(
                            marketplaceId,
                            snapshotId,
                            provider,
                            packageProjections,
                        ),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val filesWithoutChecksums =
                (pluginFiles + catalogFile + receiptFile).sortedBy { file -> file.path.render() }
            val checksumFile =
                trustedProviderArchiveFile(
                    path = providerArchivePath(PROJECTION_CHECKSUM_PATH),
                    content = providerArchiveChecksums(filesWithoutChecksums),
                    mode = CanonicalZipEntryMode.REGULAR,
                )
            val files = (filesWithoutChecksums + checksumFile).sortedBy { file -> file.path.render() }

            val zipEntries = mutableListOf<CanonicalZipEntry>()
            files.forEach { file ->
                when (val created = CanonicalZipEntry.create(file.path, file.bytes(), file.mode)) {
                    is CanonicalZipEntryCreation.Accepted -> zipEntries += created.entry
                    is CanonicalZipEntryCreation.Rejected -> {
                        return ProviderMarketplaceArchiveMaterialization.Rejected(
                            ProviderMarketplaceArchiveRejection.ZipEntryRejected(file.path, created.reason),
                        )
                    }
                }
            }
            val archive =
                when (val created = CanonicalZipArchive.create(zipEntries)) {
                    is CanonicalZipCreation.Created -> created.archive
                    is CanonicalZipCreation.Rejected -> {
                        return ProviderMarketplaceArchiveMaterialization.Rejected(
                            ProviderMarketplaceArchiveRejection.ZipRejected(created.reason),
                        )
                    }
                }
            val providerArchive =
                ProviderMarketplaceArchive(
                    marketplaceId = marketplaceId,
                    snapshotId = snapshotId,
                    provider = provider,
                    packageEvidence =
                        orderedPackages.map { archive ->
                            ProviderMarketplacePackageEvidence(archive.packageName, archive.sha256)
                        },
                    files = files,
                    canonicalArchive = archive,
                )
            check(providerArchive.verify(providerArchive.bytes()) == ProviderMarketplaceArchiveVerification.Verified)
            return ProviderMarketplaceArchiveMaterialization.Materialized(providerArchive)
        }
    }
}

internal data class ProviderMarketplacePackageEvidence(
    val packageName: PackageName,
    val packageArchiveSha256: Sha256Digest,
)

internal sealed interface ProviderMarketplaceArchiveMaterialization {
    data class Materialized(val archive: ProviderMarketplaceArchive) : ProviderMarketplaceArchiveMaterialization

    data class Rejected(
        val reason: ProviderMarketplaceArchiveRejection,
    ) : ProviderMarketplaceArchiveMaterialization
}

internal sealed interface ProviderMarketplaceArchiveRejection {
    data object NoPackages : ProviderMarketplaceArchiveRejection

    data class TooManyPackages(
        val actual: Int,
        val maximum: Int,
    ) : ProviderMarketplaceArchiveRejection

    data class DuplicatePackage(val packageName: PackageName) : ProviderMarketplaceArchiveRejection

    data class PackageMarketplaceMismatch(
        val packageName: PackageName,
        val expected: MarketplaceId,
        val actual: MarketplaceId,
    ) : ProviderMarketplaceArchiveRejection

    data class ProjectedPathRejected(
        val packageName: PackageName,
        val sourcePath: PackageEntryPath,
        val reason: PackageEntryPathRejection,
    ) : ProviderMarketplaceArchiveRejection

    data class ZipEntryRejected(
        val path: PackageEntryPath,
        val reason: CanonicalZipEntryRejection,
    ) : ProviderMarketplaceArchiveRejection

    data class ZipRejected(val reason: CanonicalZipRejection) : ProviderMarketplaceArchiveRejection
}

internal sealed interface ProviderMarketplaceArchiveVerification {
    data object Verified : ProviderMarketplaceArchiveVerification

    data class Rejected(
        val reason: ProviderMarketplaceArchiveVerificationRejection,
    ) : ProviderMarketplaceArchiveVerification
}

internal sealed interface ProviderMarketplaceArchiveVerificationRejection {
    data class InvalidArchive(
        val reason: CanonicalZipParseRejection,
    ) : ProviderMarketplaceArchiveVerificationRejection

    data class InvalidTree(
        val reason: ProviderProjectionRejection,
    ) : ProviderMarketplaceArchiveVerificationRejection
}

private fun providerCatalogBytes(
    marketplaceId: MarketplaceId,
    packages: List<PackageArchive>,
    provider: PortableProvider,
): ByteArray {
    val plugins =
        packages.map { archive ->
            val name = archive.packageName.render()
            when (provider) {
                PortableProvider.CODEX ->
                    canonicalJsonObject(
                        "category" to canonicalJsonString("Productivity"),
                        "name" to canonicalJsonString(name),
                        "policy" to
                            canonicalJsonObject(
                                "authentication" to canonicalJsonString("ON_INSTALL"),
                                "installation" to canonicalJsonString("AVAILABLE"),
                            ),
                        "source" to
                            canonicalJsonObject(
                                "path" to canonicalJsonString("./plugins/$name"),
                                "source" to canonicalJsonString("local"),
                            ),
                    )
                PortableProvider.GITHUB_COPILOT ->
                    canonicalJsonObject(
                        "name" to canonicalJsonString(name),
                        "source" to canonicalJsonString("./plugins/$name"),
                        "strict" to CanonicalJsonBoolean(true),
                        "version" to
                            canonicalJsonString(
                                ProviderAdapterVersion.fromPackageDigest(archive.sha256).render(),
                            ),
                    )
            }
        }
    val root =
        when (provider) {
            PortableProvider.CODEX ->
                canonicalJsonObject(
                    "interface" to
                        canonicalJsonObject(
                            "displayName" to canonicalJsonString(marketplaceId.render()),
                        ),
                    "name" to canonicalJsonString(marketplaceId.render()),
                    "plugins" to CanonicalJsonArray(plugins),
                )
            PortableProvider.GITHUB_COPILOT ->
                canonicalJsonObject(
                    "name" to canonicalJsonString(marketplaceId.render()),
                    "owner" to
                        canonicalJsonObject(
                            "name" to canonicalJsonString(marketplaceId.render()),
                        ),
                    "plugins" to CanonicalJsonArray(plugins),
                )
        }
    return providerArchiveDocumentBytes(root)
}

private fun providerArchiveReceiptBytes(
    marketplaceId: MarketplaceId,
    snapshotId: SnapshotId,
    provider: PortableProvider,
    packageProjections: List<Pair<PackageArchive, ProviderPackageProjection>>,
): ByteArray {
    val packages =
        packageProjections.map { (archive, projection) ->
            val name = archive.packageName.render()
            canonicalJsonObject(
                "adapterVersion" to canonicalJsonString(projection.adapterVersion.render()),
                "archive" to
                    canonicalJsonObject(
                        "name" to canonicalJsonString(archive.assetName.render()),
                        "sha256" to canonicalJsonString(archive.sha256.render()),
                        "size" to canonicalJsonInteger(archive.byteSize.toLong()),
                    ),
                "name" to canonicalJsonString(name),
                "pluginPath" to canonicalJsonString("plugins/$name"),
                "projectionSha256" to canonicalJsonString(projection.treeDigest().render()),
            )
        }
    return providerArchiveDocumentBytes(
        canonicalJsonObject(
            "generator" to canonicalJsonString(PROJECTION_GENERATOR),
            "marketplaceId" to canonicalJsonString(marketplaceId.render()),
            "packages" to CanonicalJsonArray(packages),
            "provider" to canonicalJsonString(provider.render()),
            "schemaVersion" to canonicalJsonInteger(PROJECTION_SCHEMA_VERSION.toLong()),
            "snapshotId" to canonicalJsonString(snapshotId.render()),
            "type" to canonicalJsonString(PROVIDER_MARKETPLACE_PROJECTION_RECEIPT_TYPE),
        ),
    )
}

private fun providerArchiveChecksums(files: List<ProjectedFile>): ByteArray =
    buildString {
        files.forEach { file ->
            append(file.sha256.render())
            append("  ")
            append(file.path.render())
            append('\n')
        }
    }.encodeToByteArray()

private fun providerArchiveDocumentBytes(root: CanonicalJsonObject): ByteArray =
    when (val created = CanonicalJsonDocument.create(root)) {
        is CanonicalJsonDocumentCreation.Created -> created.document.bytes()
        is CanonicalJsonDocumentCreation.Rejected -> error("Generated provider archive document exceeded the V1 bound")
    }

private fun PortableProvider.catalogPath(): PackageEntryPath =
    providerArchivePath(
        when (this) {
            PortableProvider.CODEX -> ".agents/plugins/marketplace.json"
            PortableProvider.GITHUB_COPILOT -> ".github/plugin/marketplace.json"
        },
    )

private fun providerArchivePath(raw: String): PackageEntryPath =
    when (val parsed = PackageEntryPath.parse(raw)) {
        is PackageEntryPathParse.Accepted -> parsed.value
        is PackageEntryPathParse.Rejected -> error("Generated provider archive path is invalid: $raw")
    }

private fun trustedProviderArchiveFile(
    path: PackageEntryPath,
    content: ByteArray,
    mode: CanonicalZipEntryMode,
): ProjectedFile =
    when (val created = ProjectedFile.create(path, content, mode)) {
        is ProjectedFileCreation.Created -> created.file
        is ProjectedFileCreation.Rejected ->
            error("Generated provider archive file exceeded the V1 bound: ${path.render()}")
    }

internal const val PROVIDER_MARKETPLACE_PROJECTION_RECEIPT_TYPE =
    "INTELLIGENCE_PROVIDER_MARKETPLACE_PROJECTION"
