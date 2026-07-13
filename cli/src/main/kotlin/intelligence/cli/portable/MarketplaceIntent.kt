package intelligence.cli.portable

internal sealed interface MarketplaceIntentSource {
    data class GitHubRelease(
        val repository: GitHubRepositoryUrl,
        val tag: SnapshotId,
    ) : MarketplaceIntentSource

    data class LocalSnapshot(
        val directory: ConsumerRelativeDirectory,
        val indexSha256: Sha256Digest,
    ) : MarketplaceIntentSource
}

internal class MarketplaceIntentSelection private constructor(
    val marketplaceId: MarketplaceId,
    val source: MarketplaceIntentSource,
    packages: List<PackageName>,
) {
    val packages: List<PackageName> = packages.toList()

    companion object {
        fun create(
            marketplaceId: MarketplaceId,
            source: MarketplaceIntentSource,
            packages: List<PackageName>,
        ): MarketplaceIntentSelectionCreation {
            if (packages.isEmpty()) {
                return MarketplaceIntentSelectionCreation.Rejected(
                    MarketplaceIntentSelectionRejection.NoPackages,
                )
            }
            if (packages.size > MAX_PACKAGES_PER_INTENT_SELECTION) {
                return MarketplaceIntentSelectionCreation.Rejected(
                    MarketplaceIntentSelectionRejection.TooManyPackages(
                        actual = packages.size,
                        maximum = MAX_PACKAGES_PER_INTENT_SELECTION,
                    ),
                )
            }
            val ordered = packages.sortedBy(PackageName::render)
            val seen = mutableSetOf<PackageName>()
            ordered.forEach { packageName ->
                if (!seen.add(packageName)) {
                    return MarketplaceIntentSelectionCreation.Rejected(
                        MarketplaceIntentSelectionRejection.DuplicatePackage(packageName),
                    )
                }
            }
            return MarketplaceIntentSelectionCreation.Created(
                MarketplaceIntentSelection(marketplaceId, source, ordered),
            )
        }
    }
}

internal sealed interface MarketplaceIntentSelectionCreation {
    data class Created(val selection: MarketplaceIntentSelection) : MarketplaceIntentSelectionCreation

    data class Rejected(
        val reason: MarketplaceIntentSelectionRejection,
    ) : MarketplaceIntentSelectionCreation
}

internal sealed interface MarketplaceIntentSelectionRejection {
    data object NoPackages : MarketplaceIntentSelectionRejection

    data class TooManyPackages(
        val actual: Int,
        val maximum: Int,
    ) : MarketplaceIntentSelectionRejection

    data class DuplicatePackage(val packageName: PackageName) : MarketplaceIntentSelectionRejection
}

internal class MarketplaceIntent private constructor(
    selections: List<MarketplaceIntentSelection>,
    private val document: CanonicalJsonDocument,
) {
    val selections: List<MarketplaceIntentSelection> = selections.toList()

    fun canonicalBytes(): ByteArray = document.bytes()

    fun sha256(): Sha256Digest = document.sha256()

    companion object {
        fun parse(bytes: ByteArray): MarketplaceIntentParsing = MarketplaceIntentParser.parse(bytes)

        fun materialize(selections: List<MarketplaceIntentSelection>): MarketplaceIntentMaterialization {
            if (selections.isEmpty()) {
                return MarketplaceIntentMaterialization.Rejected(MarketplaceIntentRejection.NoSelections)
            }
            if (selections.size > MAX_MARKETPLACE_INTENT_SELECTIONS) {
                return MarketplaceIntentMaterialization.Rejected(
                    MarketplaceIntentRejection.TooManySelections(
                        actual = selections.size,
                        maximum = MAX_MARKETPLACE_INTENT_SELECTIONS,
                    ),
                )
            }
            val ordered = selections.sortedBy { selection -> selection.marketplaceId.render() }
            val seen = mutableSetOf<MarketplaceId>()
            ordered.forEach { selection ->
                if (!seen.add(selection.marketplaceId)) {
                    return MarketplaceIntentMaterialization.Rejected(
                        MarketplaceIntentRejection.DuplicateMarketplace(selection.marketplaceId),
                    )
                }
            }
            val document =
                when (
                    val created =
                        CanonicalJsonDocument.create(
                            canonicalJsonObject(
                                "schemaVersion" to canonicalJsonInteger(MARKETPLACE_INTENT_SCHEMA_VERSION.toLong()),
                                "selections" to
                                    CanonicalJsonArray(ordered.map(MarketplaceIntentSelection::canonicalValue)),
                                "type" to canonicalJsonString(MARKETPLACE_INTENT_TYPE),
                            ),
                        )
                ) {
                    is CanonicalJsonDocumentCreation.Created -> created.document
                    is CanonicalJsonDocumentCreation.Rejected -> {
                        val reason = created.reason as CanonicalJsonDocumentRejection.SizeExceeded
                        return MarketplaceIntentMaterialization.Rejected(
                            MarketplaceIntentRejection.JsonDocumentTooLarge(
                                reason.actualBytes,
                                reason.maximumBytes,
                            ),
                        )
                    }
                }
            return MarketplaceIntentMaterialization.Materialized(MarketplaceIntent(ordered, document))
        }
    }
}

internal sealed interface MarketplaceIntentMaterialization {
    data class Materialized(val intent: MarketplaceIntent) : MarketplaceIntentMaterialization

    data class Rejected(val reason: MarketplaceIntentRejection) : MarketplaceIntentMaterialization
}

internal sealed interface MarketplaceIntentParsing {
    data class Parsed(val intent: MarketplaceIntent) : MarketplaceIntentParsing

    data class Rejected(val reason: MarketplaceIntentRejection) : MarketplaceIntentParsing
}

internal sealed interface MarketplaceIntentRejection {
    data class JsonDocumentTooLarge(
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : MarketplaceIntentRejection

    data object InvalidUtf8 : MarketplaceIntentRejection

    data object MalformedJson : MarketplaceIntentRejection

    data object RootMustBeObject : MarketplaceIntentRejection

    data object NonCanonicalJson : MarketplaceIntentRejection

    data class MissingField(val path: String, val field: String) : MarketplaceIntentRejection

    data class UnknownField(val path: String, val field: String) : MarketplaceIntentRejection

    data class WrongFieldType(val path: String, val expected: String) : MarketplaceIntentRejection

    data class UnsupportedType(val actual: String) : MarketplaceIntentRejection

    data class UnsupportedSchemaVersion(val actual: Long) : MarketplaceIntentRejection

    data object NoSelections : MarketplaceIntentRejection

    data class TooManySelections(
        val actual: Int,
        val maximum: Int,
    ) : MarketplaceIntentRejection

    data class DuplicateMarketplace(val marketplaceId: MarketplaceId) : MarketplaceIntentRejection

    data class InvalidMarketplaceId(
        val selectionIndex: Int,
        val reason: IdentifierRejection,
    ) : MarketplaceIntentRejection

    data class SelectionRejected(
        val selectionIndex: Int,
        val reason: MarketplaceIntentSelectionRejection,
    ) : MarketplaceIntentRejection

    data class InvalidPackageName(
        val selectionIndex: Int,
        val packageIndex: Int,
        val reason: IdentifierRejection,
    ) : MarketplaceIntentRejection

    data class UnsupportedSourceType(
        val selectionIndex: Int,
        val actual: String,
    ) : MarketplaceIntentRejection

    data class InvalidGitHubRepository(
        val selectionIndex: Int,
        val reason: GitHubRepositoryUrlRejection,
    ) : MarketplaceIntentRejection

    data class InvalidSnapshotTag(
        val selectionIndex: Int,
        val reason: IdentifierRejection,
    ) : MarketplaceIntentRejection

    data class InvalidLocalDirectory(
        val selectionIndex: Int,
        val reason: ConsumerRelativeDirectoryRejection,
    ) : MarketplaceIntentRejection

    data class InvalidIndexDigest(
        val selectionIndex: Int,
        val reason: Sha256DigestRejection,
    ) : MarketplaceIntentRejection
}

private fun MarketplaceIntentSelection.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "marketplaceId" to canonicalJsonString(marketplaceId.render()),
        "packages" to CanonicalJsonArray(packages.map { name -> canonicalJsonString(name.render()) }),
        "source" to source.canonicalValue(),
    )

private fun MarketplaceIntentSource.canonicalValue(): CanonicalJsonValue =
    when (this) {
        is MarketplaceIntentSource.GitHubRelease ->
            canonicalJsonObject(
                "repository" to canonicalJsonString(repository.render()),
                "tag" to canonicalJsonString(tag.render()),
                "type" to canonicalJsonString(GITHUB_RELEASE_SOURCE_TYPE),
            )
        is MarketplaceIntentSource.LocalSnapshot ->
            canonicalJsonObject(
                "indexSha256" to canonicalJsonString(indexSha256.render()),
                "path" to canonicalJsonString(directory.render()),
                "type" to canonicalJsonString(LOCAL_SNAPSHOT_SOURCE_TYPE),
            )
    }

internal const val MARKETPLACE_INTENT_SCHEMA_VERSION = 1
internal const val MARKETPLACE_INTENT_TYPE = "MARKETPLACE_INTENT"
internal const val GITHUB_RELEASE_SOURCE_TYPE = "GITHUB_RELEASE"
internal const val LOCAL_SNAPSHOT_SOURCE_TYPE = "LOCAL_SNAPSHOT"
internal const val MAX_MARKETPLACE_INTENT_SELECTIONS = 128
internal const val MAX_PACKAGES_PER_INTENT_SELECTION = 512
