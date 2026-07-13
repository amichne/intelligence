package intelligence.cli.portable

internal enum class PortableProvider(
    private val wireName: String,
) {
    CODEX("codex"),
    GITHUB_COPILOT("github-copilot"),
    ;

    fun render(): String = wireName

    companion object {
        fun parse(candidate: String): PortableProvider? = entries.singleOrNull { it.wireName == candidate }
    }
}

internal class ProviderArchiveArtifact private constructor(
    val provider: PortableProvider,
    val assetName: ReleaseAssetName,
    private val content: ByteArray,
) {
    val byteSize: Int
        get() = content.size

    val sha256: Sha256Digest = Sha256Digest.compute(content)

    fun bytes(): ByteArray = content.copyOf()

    internal fun evidence(): SnapshotAssetEvidence =
        SnapshotAssetEvidence(assetName, byteSize, sha256)

    companion object {
        fun fromCanonicalArchive(
            provider: PortableProvider,
            archive: CanonicalZipArchive,
        ): ProviderArchiveArtifact =
            ProviderArchiveArtifact(
                provider = provider,
                assetName = ReleaseAssetName.providerArchive(provider),
                content = archive.bytes(),
            )
    }
}

internal data class SnapshotAssetEvidence(
    val name: ReleaseAssetName,
    val byteSize: Int,
    val sha256: Sha256Digest,
)

internal data class SnapshotPackageRecord(
    val name: PackageName,
    val description: PortableDescription,
    val tags: List<PackageTag>,
    val archive: SnapshotAssetEvidence,
)

internal data class SnapshotProjectionRecord(
    val provider: PortableProvider,
    val archive: SnapshotAssetEvidence,
)

internal class MarketplaceSnapshotIndex private constructor(
    val marketplaceId: MarketplaceId,
    val snapshotId: SnapshotId,
    val defaultPackage: PackageName,
    val packages: List<SnapshotPackageRecord>,
    val projections: List<SnapshotProjectionRecord>,
    val checksumAsset: ReleaseAssetName,
    private val document: CanonicalJsonDocument,
) {
    val byteSize: Int
        get() = document.byteSize

    fun canonicalBytes(): ByteArray = document.bytes()

    fun sha256(): Sha256Digest = document.sha256()

    companion object {
        fun parse(bytes: ByteArray): MarketplaceSnapshotIndexParsing =
            MarketplaceSnapshotIndexParser.parse(bytes)

        fun materialize(
            marketplaceId: MarketplaceId,
            snapshotId: SnapshotId,
            defaultPackage: PackageName,
            packages: List<PackageArchive>,
            projections: List<ProviderArchiveArtifact>,
        ): MarketplaceSnapshotIndexMaterialization {
            if (packages.isEmpty()) {
                return MarketplaceSnapshotIndexMaterialization.Rejected(
                    MarketplaceSnapshotIndexRejection.NoPackages,
                )
            }
            if (packages.size > MAX_PACKAGES_PER_SNAPSHOT) {
                return MarketplaceSnapshotIndexMaterialization.Rejected(
                    MarketplaceSnapshotIndexRejection.TooManyPackages(
                        actual = packages.size,
                        maximum = MAX_PACKAGES_PER_SNAPSHOT,
                    ),
                )
            }

            val orderedPackages = packages.sortedBy { archive -> archive.packageName.render() }
            val seenPackageNames = mutableSetOf<PackageName>()
            orderedPackages.forEach { archive ->
                if (!seenPackageNames.add(archive.packageName)) {
                    return MarketplaceSnapshotIndexMaterialization.Rejected(
                        MarketplaceSnapshotIndexRejection.DuplicatePackage(archive.packageName),
                    )
                }
                if (archive.marketplaceId != marketplaceId) {
                    return MarketplaceSnapshotIndexMaterialization.Rejected(
                        MarketplaceSnapshotIndexRejection.PackageMarketplaceMismatch(
                            packageName = archive.packageName,
                            expected = marketplaceId,
                            actual = archive.marketplaceId,
                        ),
                    )
                }
            }
            if (orderedPackages.none { archive -> archive.packageName == defaultPackage }) {
                return MarketplaceSnapshotIndexMaterialization.Rejected(
                    MarketplaceSnapshotIndexRejection.DefaultPackageMissing(defaultPackage),
                )
            }

            val seenProviders = mutableSetOf<PortableProvider>()
            projections.forEach { projection ->
                if (!seenProviders.add(projection.provider)) {
                    return MarketplaceSnapshotIndexMaterialization.Rejected(
                        MarketplaceSnapshotIndexRejection.DuplicateProvider(projection.provider),
                    )
                }
            }
            PortableProvider.entries.firstOrNull { provider -> provider !in seenProviders }?.let { missing ->
                return MarketplaceSnapshotIndexMaterialization.Rejected(
                    MarketplaceSnapshotIndexRejection.MissingProvider(missing),
                )
            }
            val orderedProjections = projections.sortedBy { projection -> projection.provider.render() }

            val packageRecords =
                orderedPackages.map { archive ->
                    SnapshotPackageRecord(
                        name = archive.packageName,
                        description = archive.description,
                        tags = archive.tags.toList(),
                        archive =
                            SnapshotAssetEvidence(
                                name = archive.assetName,
                                byteSize = archive.byteSize,
                                sha256 = archive.sha256,
                            ),
                    )
                }
            val projectionRecords =
                orderedProjections.map { artifact ->
                    SnapshotProjectionRecord(artifact.provider, artifact.evidence())
                }
            validateDistinctAssets(packageRecords, projectionRecords)?.let { rejection ->
                return MarketplaceSnapshotIndexMaterialization.Rejected(rejection)
            }

            return when (
                val created =
                    fromValidated(
                        marketplaceId = marketplaceId,
                        snapshotId = snapshotId,
                        defaultPackage = defaultPackage,
                        packages = packageRecords,
                        projections = projectionRecords,
                    )
            ) {
                is MarketplaceSnapshotIndexConstruction.Constructed ->
                    MarketplaceSnapshotIndexMaterialization.Materialized(created.index)
                is MarketplaceSnapshotIndexConstruction.Rejected ->
                    MarketplaceSnapshotIndexMaterialization.Rejected(created.reason)
            }
        }

        internal fun fromValidated(
            marketplaceId: MarketplaceId,
            snapshotId: SnapshotId,
            defaultPackage: PackageName,
            packages: List<SnapshotPackageRecord>,
            projections: List<SnapshotProjectionRecord>,
        ): MarketplaceSnapshotIndexConstruction {
            val checksumAsset = ReleaseAssetName.checksumManifest()
            val document =
                when (
                    val created =
                        CanonicalJsonDocument.create(
                            canonicalJsonObject(
                                "checksumAsset" to canonicalJsonString(checksumAsset.render()),
                                "defaultPackage" to canonicalJsonString(defaultPackage.render()),
                                "marketplaceId" to canonicalJsonString(marketplaceId.render()),
                                "packages" to CanonicalJsonArray(packages.map(SnapshotPackageRecord::canonicalValue)),
                                "projections" to
                                    CanonicalJsonArray(projections.map(SnapshotProjectionRecord::canonicalValue)),
                                "schemaVersion" to canonicalJsonInteger(MARKETPLACE_SNAPSHOT_SCHEMA_VERSION.toLong()),
                                "snapshotId" to canonicalJsonString(snapshotId.render()),
                                "type" to canonicalJsonString(MARKETPLACE_SNAPSHOT_TYPE),
                            ),
                        )
                ) {
                    is CanonicalJsonDocumentCreation.Created -> created.document
                    is CanonicalJsonDocumentCreation.Rejected -> {
                        val reason =
                            when (val rejection = created.reason) {
                                is CanonicalJsonDocumentRejection.SizeExceeded -> rejection
                            }
                        return MarketplaceSnapshotIndexConstruction.Rejected(
                            MarketplaceSnapshotIndexRejection.JsonDocumentTooLarge(
                                actualBytes = reason.actualBytes,
                                maximumBytes = reason.maximumBytes,
                            ),
                        )
                    }
                }
            return MarketplaceSnapshotIndexConstruction.Constructed(
                MarketplaceSnapshotIndex(
                    marketplaceId = marketplaceId,
                    snapshotId = snapshotId,
                    defaultPackage = defaultPackage,
                    packages = packages.map { record -> record.copy(tags = record.tags.toList()) },
                    projections = projections.toList(),
                    checksumAsset = checksumAsset,
                    document = document,
                ),
            )
        }
    }
}

internal sealed interface MarketplaceSnapshotIndexConstruction {
    data class Constructed(val index: MarketplaceSnapshotIndex) : MarketplaceSnapshotIndexConstruction

    data class Rejected(val reason: MarketplaceSnapshotIndexRejection) : MarketplaceSnapshotIndexConstruction
}

internal sealed interface MarketplaceSnapshotIndexMaterialization {
    data class Materialized(val index: MarketplaceSnapshotIndex) : MarketplaceSnapshotIndexMaterialization

    data class Rejected(val reason: MarketplaceSnapshotIndexRejection) : MarketplaceSnapshotIndexMaterialization
}

internal sealed interface MarketplaceSnapshotIndexParsing {
    data class Parsed(val index: MarketplaceSnapshotIndex) : MarketplaceSnapshotIndexParsing

    data class Rejected(val reason: MarketplaceSnapshotIndexRejection) : MarketplaceSnapshotIndexParsing
}

internal sealed interface MarketplaceSnapshotIndexRejection {
    data class JsonDocumentTooLarge(
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : MarketplaceSnapshotIndexRejection

    data object InvalidUtf8 : MarketplaceSnapshotIndexRejection

    data object MalformedJson : MarketplaceSnapshotIndexRejection

    data object RootMustBeObject : MarketplaceSnapshotIndexRejection

    data object NonCanonicalJson : MarketplaceSnapshotIndexRejection

    data class MissingField(val path: String, val field: String) : MarketplaceSnapshotIndexRejection

    data class UnknownField(val path: String, val field: String) : MarketplaceSnapshotIndexRejection

    data class WrongFieldType(
        val path: String,
        val expected: String,
    ) : MarketplaceSnapshotIndexRejection

    data class UnsupportedType(val actual: String) : MarketplaceSnapshotIndexRejection

    data class UnsupportedSchemaVersion(val actual: Long) : MarketplaceSnapshotIndexRejection

    data class InvalidMarketplaceId(val reason: IdentifierRejection) : MarketplaceSnapshotIndexRejection

    data class InvalidSnapshotId(val reason: IdentifierRejection) : MarketplaceSnapshotIndexRejection

    data class InvalidDefaultPackage(val reason: IdentifierRejection) : MarketplaceSnapshotIndexRejection

    data class InvalidPackageName(
        val index: Int,
        val reason: IdentifierRejection,
    ) : MarketplaceSnapshotIndexRejection

    data class InvalidDescription(
        val path: String,
        val reason: PortableDescriptionRejection,
    ) : MarketplaceSnapshotIndexRejection

    data class InvalidTag(
        val packageIndex: Int,
        val tagIndex: Int,
        val reason: IdentifierRejection,
    ) : MarketplaceSnapshotIndexRejection

    data class TagsNotCanonical(val packageName: PackageName) : MarketplaceSnapshotIndexRejection

    data object NoPackages : MarketplaceSnapshotIndexRejection

    data class TooManyPackages(
        val actual: Int,
        val maximum: Int,
    ) : MarketplaceSnapshotIndexRejection

    data class DuplicatePackage(val packageName: PackageName) : MarketplaceSnapshotIndexRejection

    data object PackagesNotCanonical : MarketplaceSnapshotIndexRejection

    data class PackageMarketplaceMismatch(
        val packageName: PackageName,
        val expected: MarketplaceId,
        val actual: MarketplaceId,
    ) : MarketplaceSnapshotIndexRejection

    data class DefaultPackageMissing(val packageName: PackageName) : MarketplaceSnapshotIndexRejection

    data class UnsupportedProvider(val actual: String) : MarketplaceSnapshotIndexRejection

    data class DuplicateProvider(val provider: PortableProvider) : MarketplaceSnapshotIndexRejection

    data class MissingProvider(val provider: PortableProvider) : MarketplaceSnapshotIndexRejection

    data object ProjectionsNotCanonical : MarketplaceSnapshotIndexRejection

    data class InvalidAssetName(
        val path: String,
        val reason: ReleaseAssetNameRejection,
    ) : MarketplaceSnapshotIndexRejection

    data class UnexpectedAssetName(
        val path: String,
        val expected: ReleaseAssetName,
        val actual: ReleaseAssetName,
    ) : MarketplaceSnapshotIndexRejection

    data class InvalidAssetSize(
        val path: String,
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : MarketplaceSnapshotIndexRejection

    data class InvalidDigest(
        val path: String,
        val reason: Sha256DigestRejection,
    ) : MarketplaceSnapshotIndexRejection

    data class DuplicateAssetName(val name: ReleaseAssetName) : MarketplaceSnapshotIndexRejection

    data class DuplicateAssetDigest(val digest: Sha256Digest) : MarketplaceSnapshotIndexRejection

    data class UnexpectedChecksumAsset(
        val expected: ReleaseAssetName,
        val actual: ReleaseAssetName,
    ) : MarketplaceSnapshotIndexRejection
}

private fun SnapshotPackageRecord.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "archive" to archive.canonicalValue(),
        "description" to canonicalJsonString(description.render()),
        "name" to canonicalJsonString(name.render()),
        "tags" to CanonicalJsonArray(tags.map { tag -> canonicalJsonString(tag.render()) }),
    )

private fun SnapshotProjectionRecord.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "archive" to archive.canonicalValue(),
        "provider" to canonicalJsonString(provider.render()),
    )

private fun SnapshotAssetEvidence.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "name" to canonicalJsonString(name.render()),
        "sha256" to canonicalJsonString(sha256.render()),
        "size" to canonicalJsonInteger(byteSize.toLong()),
    )

internal fun validateDistinctAssets(
    packages: List<SnapshotPackageRecord>,
    projections: List<SnapshotProjectionRecord>,
): MarketplaceSnapshotIndexRejection? {
    val assets = packages.map(SnapshotPackageRecord::archive) + projections.map(SnapshotProjectionRecord::archive)
    val seenNames = mutableSetOf<ReleaseAssetName>()
    val seenDigests = mutableSetOf<Sha256Digest>()
    assets.forEach { asset ->
        if (!seenNames.add(asset.name)) {
            return MarketplaceSnapshotIndexRejection.DuplicateAssetName(asset.name)
        }
        if (!seenDigests.add(asset.sha256)) {
            return MarketplaceSnapshotIndexRejection.DuplicateAssetDigest(asset.sha256)
        }
    }
    return null
}

internal const val MARKETPLACE_SNAPSHOT_SCHEMA_VERSION = 1
internal const val MARKETPLACE_SNAPSHOT_TYPE = "INTELLIGENCE_MARKETPLACE_SNAPSHOT"
internal const val MAX_MARKETPLACE_SNAPSHOT_JSON_BYTES = 4 * 1024 * 1024
internal const val MAX_PACKAGES_PER_SNAPSHOT = 512
internal const val MAX_RELEASE_ARTIFACT_BYTES = 256 * 1024 * 1024
