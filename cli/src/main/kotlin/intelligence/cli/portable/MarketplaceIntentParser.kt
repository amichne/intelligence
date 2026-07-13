package intelligence.cli.portable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object MarketplaceIntentParser {
    fun parse(bytes: ByteArray): MarketplaceIntentParsing {
        val root =
            when (
                val parsed =
                    StrictCanonicalJson.parseObject(
                        bytes = bytes,
                        maximumBytes = MAX_MARKETPLACE_INTENT_JSON_BYTES,
                    )
            ) {
                is StrictCanonicalJsonObjectParsing.Parsed -> parsed.root
                is StrictCanonicalJsonObjectParsing.Rejected -> {
                    return MarketplaceIntentParsing.Rejected(parsed.reason.toIntentRejection())
                }
            }
        val selections =
            try {
                MarketplaceIntentDecoder.decode(root)
            } catch (rejected: MarketplaceIntentRejected) {
                return MarketplaceIntentParsing.Rejected(rejected.reason)
            }
        val intent =
            when (val materialized = MarketplaceIntent.materialize(selections)) {
                is MarketplaceIntentMaterialization.Materialized -> materialized.intent
                is MarketplaceIntentMaterialization.Rejected -> {
                    return MarketplaceIntentParsing.Rejected(materialized.reason)
                }
            }
        if (!bytes.contentEquals(intent.canonicalBytes())) {
            return MarketplaceIntentParsing.Rejected(MarketplaceIntentRejection.NonCanonicalJson)
        }
        return MarketplaceIntentParsing.Parsed(intent)
    }
}

private object MarketplaceIntentDecoder {
    fun decode(root: JsonObject): List<MarketplaceIntentSelection> {
        root.requireIntentFields("$", ROOT_FIELDS)
        val type = root.requiredIntentString("$", "type")
        if (type != MARKETPLACE_INTENT_TYPE) {
            intentReject(MarketplaceIntentRejection.UnsupportedType(type))
        }
        val schemaVersion = root.requiredIntentLong("$", "schemaVersion")
        if (schemaVersion != MARKETPLACE_INTENT_SCHEMA_VERSION.toLong()) {
            intentReject(MarketplaceIntentRejection.UnsupportedSchemaVersion(schemaVersion))
        }
        val elements = root.requiredIntentArray("$", "selections")
        if (elements.isEmpty()) intentReject(MarketplaceIntentRejection.NoSelections)
        if (elements.size > MAX_MARKETPLACE_INTENT_SELECTIONS) {
            intentReject(
                MarketplaceIntentRejection.TooManySelections(
                    elements.size,
                    MAX_MARKETPLACE_INTENT_SELECTIONS,
                ),
            )
        }
        return elements.mapIndexed(::decodeSelection)
    }

    private fun decodeSelection(
        selectionIndex: Int,
        element: JsonElement,
    ): MarketplaceIntentSelection {
        val path = "$.selections[$selectionIndex]"
        val source = element.requiredIntentObject(path)
        source.requireIntentFields(path, SELECTION_FIELDS)
        val marketplaceId =
            when (val parsed = MarketplaceId.parse(source.requiredIntentString(path, "marketplaceId"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    intentReject(
                        MarketplaceIntentRejection.InvalidMarketplaceId(selectionIndex, parsed.reason),
                    )
                }
            }
        val packages =
            source.requiredIntentArray(path, "packages").mapIndexed { packageIndex, packageElement ->
                val candidate = packageElement.requiredIntentString("$path.packages[$packageIndex]")
                when (val parsed = PackageName.parse(candidate)) {
                    is IdentifierParse.Accepted -> parsed.value
                    is IdentifierParse.Rejected -> {
                        intentReject(
                            MarketplaceIntentRejection.InvalidPackageName(
                                selectionIndex,
                                packageIndex,
                                parsed.reason,
                            ),
                        )
                    }
                }
            }
        val intentSource = decodeSource(selectionIndex, source.requiredIntentObject(path, "source"))
        return when (val created = MarketplaceIntentSelection.create(marketplaceId, intentSource, packages)) {
            is MarketplaceIntentSelectionCreation.Created -> created.selection
            is MarketplaceIntentSelectionCreation.Rejected -> {
                intentReject(MarketplaceIntentRejection.SelectionRejected(selectionIndex, created.reason))
            }
        }
    }

    private fun decodeSource(
        selectionIndex: Int,
        source: JsonObject,
    ): MarketplaceIntentSource {
        val path = "$.selections[$selectionIndex].source"
        val type = source.requiredIntentString(path, "type")
        return when (type) {
            GITHUB_RELEASE_SOURCE_TYPE -> {
                source.requireIntentFields(path, GITHUB_SOURCE_FIELDS)
                val repository =
                    when (val parsed = GitHubRepositoryUrl.parse(source.requiredIntentString(path, "repository"))) {
                        is GitHubRepositoryUrlParsing.Parsed -> parsed.url
                        is GitHubRepositoryUrlParsing.Rejected -> {
                            intentReject(
                                MarketplaceIntentRejection.InvalidGitHubRepository(
                                    selectionIndex,
                                    parsed.reason,
                                ),
                            )
                        }
                    }
                val tag =
                    when (val parsed = SnapshotId.parse(source.requiredIntentString(path, "tag"))) {
                        is IdentifierParse.Accepted -> parsed.value
                        is IdentifierParse.Rejected -> {
                            intentReject(
                                MarketplaceIntentRejection.InvalidSnapshotTag(selectionIndex, parsed.reason),
                            )
                        }
                    }
                MarketplaceIntentSource.GitHubRelease(repository, tag)
            }
            LOCAL_SNAPSHOT_SOURCE_TYPE -> {
                source.requireIntentFields(path, LOCAL_SOURCE_FIELDS)
                val directory =
                    when (val parsed = ConsumerRelativeDirectory.parse(source.requiredIntentString(path, "path"))) {
                        is ConsumerRelativeDirectoryParsing.Parsed -> parsed.directory
                        is ConsumerRelativeDirectoryParsing.Rejected -> {
                            intentReject(
                                MarketplaceIntentRejection.InvalidLocalDirectory(
                                    selectionIndex,
                                    parsed.reason,
                                ),
                            )
                        }
                    }
                val digest =
                    when (val parsed = Sha256Digest.parse(source.requiredIntentString(path, "indexSha256"))) {
                        is Sha256DigestParsing.Parsed -> parsed.digest
                        is Sha256DigestParsing.Rejected -> {
                            intentReject(
                                MarketplaceIntentRejection.InvalidIndexDigest(selectionIndex, parsed.reason),
                            )
                        }
                    }
                MarketplaceIntentSource.LocalSnapshot(directory, digest)
            }
            else ->
                intentReject(
                    MarketplaceIntentRejection.UnsupportedSourceType(selectionIndex, type),
                )
        }
    }
}

private class MarketplaceIntentRejected(
    val reason: MarketplaceIntentRejection,
) : RuntimeException(null, null, false, false)

private fun intentReject(reason: MarketplaceIntentRejection): Nothing = throw MarketplaceIntentRejected(reason)

private fun StrictCanonicalJsonRejection.toIntentRejection(): MarketplaceIntentRejection =
    when (this) {
        is StrictCanonicalJsonRejection.DocumentTooLarge ->
            MarketplaceIntentRejection.JsonDocumentTooLarge(actualBytes.toLong(), maximumBytes)
        StrictCanonicalJsonRejection.InvalidUtf8 -> MarketplaceIntentRejection.InvalidUtf8
        StrictCanonicalJsonRejection.MalformedJson -> MarketplaceIntentRejection.MalformedJson
        StrictCanonicalJsonRejection.RootMustBeObject -> MarketplaceIntentRejection.RootMustBeObject
        StrictCanonicalJsonRejection.NonCanonicalJson -> MarketplaceIntentRejection.NonCanonicalJson
    }

private fun JsonObject.requireIntentFields(
    path: String,
    expected: List<String>,
) {
    expected.firstOrNull { field -> field !in this }?.let { missing ->
        intentReject(MarketplaceIntentRejection.MissingField(path, missing))
    }
    keys.filterNot(expected::contains).sorted().firstOrNull()?.let { unknown ->
        intentReject(MarketplaceIntentRejection.UnknownField(path, unknown))
    }
}

private fun JsonObject.requiredIntentString(
    path: String,
    field: String,
): String = this[field].requiredIntentString("$path.$field")

private fun JsonElement?.requiredIntentString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        intentReject(MarketplaceIntentRejection.WrongFieldType(path, "string"))
    }
    return primitive.content
}

private fun JsonObject.requiredIntentLong(
    path: String,
    field: String,
): Long {
    val primitive = this[field] as? JsonPrimitive
    return primitive?.takeUnless(JsonPrimitive::isString)?.longOrNull
        ?: intentReject(MarketplaceIntentRejection.WrongFieldType("$path.$field", "safe integer"))
}

private fun JsonObject.requiredIntentArray(
    path: String,
    field: String,
): JsonArray =
    this[field] as? JsonArray
        ?: intentReject(MarketplaceIntentRejection.WrongFieldType("$path.$field", "array"))

private fun JsonObject.requiredIntentObject(
    path: String,
    field: String,
): JsonObject =
    this[field] as? JsonObject
        ?: intentReject(MarketplaceIntentRejection.WrongFieldType("$path.$field", "object"))

private fun JsonElement.requiredIntentObject(path: String): JsonObject =
    this as? JsonObject ?: intentReject(MarketplaceIntentRejection.WrongFieldType(path, "object"))

private val ROOT_FIELDS = listOf("schemaVersion", "selections", "type")
private val SELECTION_FIELDS = listOf("marketplaceId", "packages", "source")
private val GITHUB_SOURCE_FIELDS = listOf("repository", "tag", "type")
private val LOCAL_SOURCE_FIELDS = listOf("indexSha256", "path", "type")

internal const val MAX_MARKETPLACE_INTENT_JSON_BYTES = 4 * 1024 * 1024
