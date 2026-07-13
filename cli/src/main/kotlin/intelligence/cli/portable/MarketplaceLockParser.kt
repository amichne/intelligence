package intelligence.cli.portable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal object MarketplaceLockParser {
    fun parse(bytes: ByteArray): MarketplaceLockParsing {
        val root =
            when (
                val parsed =
                    StrictCanonicalJson.parseObject(
                        bytes = bytes,
                        maximumBytes = MAX_MARKETPLACE_LOCK_JSON_BYTES,
                    )
            ) {
                is StrictCanonicalJsonObjectParsing.Parsed -> parsed.root
                is StrictCanonicalJsonObjectParsing.Rejected -> {
                    return MarketplaceLockParsing.Rejected(parsed.reason.toLockRejection())
                }
            }
        val entries =
            try {
                MarketplaceLockDecoder.decode(root)
            } catch (rejected: MarketplaceLockRejected) {
                return MarketplaceLockParsing.Rejected(rejected.reason)
            }
        val lock =
            when (val materialized = MarketplaceLock.materialize(entries)) {
                is MarketplaceLockMaterialization.Materialized -> materialized.lock
                is MarketplaceLockMaterialization.Rejected -> {
                    return MarketplaceLockParsing.Rejected(materialized.reason)
                }
            }
        if (!bytes.contentEquals(lock.canonicalBytes())) {
            return MarketplaceLockParsing.Rejected(MarketplaceLockRejection.NonCanonicalJson)
        }
        return MarketplaceLockParsing.Parsed(lock)
    }
}

private object MarketplaceLockDecoder {
    fun decode(root: JsonObject): List<MarketplaceLockEntry> {
        root.requireLockFields("$", ROOT_FIELDS)
        val type = root.requiredLockString("$", "type")
        if (type != MARKETPLACE_LOCK_TYPE) lockReject(MarketplaceLockRejection.UnsupportedType(type))
        val version = root.requiredLockLong("$", "schemaVersion")
        if (version != MARKETPLACE_LOCK_SCHEMA_VERSION.toLong()) {
            lockReject(MarketplaceLockRejection.UnsupportedSchemaVersion(version))
        }
        val elements = root.requiredLockArray("$", "entries")
        if (elements.isEmpty()) lockReject(MarketplaceLockRejection.NoEntries)
        if (elements.size > MAX_MARKETPLACE_INTENT_SELECTIONS) {
            lockReject(
                MarketplaceLockRejection.TooManyEntries(
                    elements.size,
                    MAX_MARKETPLACE_INTENT_SELECTIONS,
                ),
            )
        }
        return elements.mapIndexed(::decodeEntry)
    }

    private fun decodeEntry(
        entryIndex: Int,
        element: JsonElement,
    ): MarketplaceLockEntry {
        val path = "$.entries[$entryIndex]"
        val entry = element.requiredLockObject(path)
        entry.requireLockFields(path, ENTRY_FIELDS)
        val marketplaceId =
            when (val parsed = MarketplaceId.parse(entry.requiredLockString(path, "marketplaceId"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    lockReject(MarketplaceLockRejection.InvalidMarketplaceId(entryIndex, parsed.reason))
                }
            }
        val source = decodeSource(entryIndex, entry.requiredLockObject(path, "source"))
        val github = source is MarketplaceLockSource.GitHubRelease
        val index = decodeAsset(entry.requiredLockObject(path, "index"), "$path.index", github)
        val checksum = decodeAsset(entry.requiredLockObject(path, "checksum"), "$path.checksum", github)
        val packages =
            entry.requiredLockArray(path, "packages").mapIndexed { packageIndex, packageElement ->
                decodePackage(entryIndex, packageIndex, packageElement, github)
            }
        return when (val created = MarketplaceLockEntry.create(marketplaceId, source, index, checksum, packages)) {
            is MarketplaceLockEntryCreation.Created -> created.entry
            is MarketplaceLockEntryCreation.Rejected -> {
                lockReject(MarketplaceLockRejection.EntryRejected(entryIndex, created.reason))
            }
        }
    }

    private fun decodeSource(
        entryIndex: Int,
        source: JsonObject,
    ): MarketplaceLockSource {
        val path = "$.entries[$entryIndex].source"
        val type = source.requiredLockString(path, "type")
        return when (type) {
            GITHUB_RELEASE_SOURCE_TYPE -> {
                source.requireLockFields(path, GITHUB_SOURCE_FIELDS)
                if (!source.requiredLockBoolean(path, "immutable")) {
                    lockReject(MarketplaceLockRejection.ImmutableReleaseRequired(entryIndex))
                }
                val repository =
                    when (val parsed = GitHubRepositoryUrl.parse(source.requiredLockString(path, "repository"))) {
                        is GitHubRepositoryUrlParsing.Parsed -> parsed.url
                        is GitHubRepositoryUrlParsing.Rejected -> {
                            lockReject(
                                MarketplaceLockRejection.InvalidGitHubRepository(entryIndex, parsed.reason),
                            )
                        }
                    }
                val tag =
                    when (val parsed = SnapshotId.parse(source.requiredLockString(path, "tag"))) {
                        is IdentifierParse.Accepted -> parsed.value
                        is IdentifierParse.Rejected -> {
                            lockReject(MarketplaceLockRejection.InvalidSnapshotTag(entryIndex, parsed.reason))
                        }
                    }
                val releaseId =
                    when (val parsed = GitHubReleaseId.parse(source.requiredLockLong(path, "releaseId"))) {
                        is GitHubReleaseIdParsing.Parsed -> parsed.id
                        is GitHubReleaseIdParsing.Rejected -> {
                            lockReject(MarketplaceLockRejection.InvalidReleaseId(entryIndex, parsed.reason))
                        }
                    }
                val commit =
                    when (val parsed = GitCommitSha.parse(source.requiredLockString(path, "tagCommitSha"))) {
                        is GitCommitShaParsing.Parsed -> parsed.sha
                        is GitCommitShaParsing.Rejected -> {
                            lockReject(MarketplaceLockRejection.InvalidCommitSha(entryIndex, parsed.reason))
                        }
                    }
                MarketplaceLockSource.GitHubRelease(repository, tag, releaseId, commit)
            }
            LOCAL_SNAPSHOT_SOURCE_TYPE -> {
                source.requireLockFields(path, LOCAL_SOURCE_FIELDS)
                val directory =
                    when (val parsed = ConsumerRelativeDirectory.parse(source.requiredLockString(path, "path"))) {
                        is ConsumerRelativeDirectoryParsing.Parsed -> parsed.directory
                        is ConsumerRelativeDirectoryParsing.Rejected -> {
                            lockReject(MarketplaceLockRejection.InvalidLocalDirectory(entryIndex, parsed.reason))
                        }
                    }
                MarketplaceLockSource.LocalSnapshot(directory)
            }
            else -> lockReject(MarketplaceLockRejection.UnsupportedSourceType(entryIndex, type))
        }
    }

    private fun decodePackage(
        entryIndex: Int,
        packageIndex: Int,
        element: JsonElement,
        github: Boolean,
    ): LockedPackage {
        val path = "$.entries[$entryIndex].packages[$packageIndex]"
        val source = element.requiredLockObject(path)
        source.requireLockFields(path, PACKAGE_FIELDS)
        val name =
            when (val parsed = PackageName.parse(source.requiredLockString(path, "name"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    lockReject(
                        MarketplaceLockRejection.InvalidPackageName(entryIndex, packageIndex, parsed.reason),
                    )
                }
            }
        val archive = decodeAsset(source.requiredLockObject(path, "archive"), "$path.archive", github)
        return LockedPackage(name, archive)
    }

    private fun decodeAsset(
        source: JsonObject,
        path: String,
        github: Boolean,
    ): LockedAsset {
        source.requireLockFields(path, if (github) GITHUB_ASSET_FIELDS else LOCAL_ASSET_FIELDS)
        val name =
            when (val parsed = ReleaseAssetName.parse(source.requiredLockString(path, "name"))) {
                is ReleaseAssetNameParsing.Parsed -> parsed.name
                is ReleaseAssetNameParsing.Rejected -> {
                    lockReject(MarketplaceLockRejection.InvalidAssetName("$path.name", parsed.reason))
                }
            }
        val size = source.requiredLockLong(path, "size")
        if (size !in 1..MAX_RELEASE_ARTIFACT_BYTES.toLong()) {
            lockReject(MarketplaceLockRejection.InvalidAssetSize("$path.size", size))
        }
        val digest =
            when (val parsed = Sha256Digest.parse(source.requiredLockString(path, "sha256"))) {
                is Sha256DigestParsing.Parsed -> parsed.digest
                is Sha256DigestParsing.Rejected -> {
                    lockReject(MarketplaceLockRejection.InvalidAssetDigest("$path.sha256", parsed.reason))
                }
            }
        return if (github) {
            val assetId =
                when (val parsed = GitHubAssetId.parse(source.requiredLockLong(path, "assetId"))) {
                    is GitHubAssetIdParsing.Parsed -> parsed.id
                    is GitHubAssetIdParsing.Rejected -> {
                        lockReject(MarketplaceLockRejection.InvalidAssetId("$path.assetId", parsed.reason))
                    }
                }
            LockedAsset.GitHub(assetId, name, size.toInt(), digest)
        } else {
            LockedAsset.Local(name, size.toInt(), digest)
        }
    }
}

private class MarketplaceLockRejected(
    val reason: MarketplaceLockRejection,
) : RuntimeException(null, null, false, false)

private fun lockReject(reason: MarketplaceLockRejection): Nothing = throw MarketplaceLockRejected(reason)

private fun StrictCanonicalJsonRejection.toLockRejection(): MarketplaceLockRejection =
    when (this) {
        is StrictCanonicalJsonRejection.DocumentTooLarge ->
            MarketplaceLockRejection.JsonDocumentTooLarge(actualBytes.toLong(), maximumBytes)
        StrictCanonicalJsonRejection.InvalidUtf8 -> MarketplaceLockRejection.InvalidUtf8
        StrictCanonicalJsonRejection.MalformedJson -> MarketplaceLockRejection.MalformedJson
        StrictCanonicalJsonRejection.RootMustBeObject -> MarketplaceLockRejection.RootMustBeObject
        StrictCanonicalJsonRejection.NonCanonicalJson -> MarketplaceLockRejection.NonCanonicalJson
    }

private fun JsonObject.requireLockFields(
    path: String,
    expected: List<String>,
) {
    expected.firstOrNull { field -> field !in this }?.let { missing ->
        lockReject(MarketplaceLockRejection.MissingField(path, missing))
    }
    keys.filterNot(expected::contains).sorted().firstOrNull()?.let { unknown ->
        lockReject(MarketplaceLockRejection.UnknownField(path, unknown))
    }
}

private fun JsonObject.requiredLockString(
    path: String,
    field: String,
): String = this[field].requiredLockString("$path.$field")

private fun JsonElement?.requiredLockString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        lockReject(MarketplaceLockRejection.WrongFieldType(path, "string"))
    }
    return primitive.content
}

private fun JsonObject.requiredLockLong(
    path: String,
    field: String,
): Long {
    val primitive = this[field] as? JsonPrimitive
    return primitive?.takeUnless(JsonPrimitive::isString)?.longOrNull
        ?: lockReject(MarketplaceLockRejection.WrongFieldType("$path.$field", "safe integer"))
}

private fun JsonObject.requiredLockBoolean(
    path: String,
    field: String,
): Boolean {
    val primitive = this[field] as? JsonPrimitive
    return primitive?.takeUnless(JsonPrimitive::isString)?.booleanOrNull
        ?: lockReject(MarketplaceLockRejection.WrongFieldType("$path.$field", "boolean"))
}

private fun JsonObject.requiredLockArray(
    path: String,
    field: String,
): JsonArray =
    this[field] as? JsonArray
        ?: lockReject(MarketplaceLockRejection.WrongFieldType("$path.$field", "array"))

private fun JsonObject.requiredLockObject(
    path: String,
    field: String,
): JsonObject =
    this[field] as? JsonObject
        ?: lockReject(MarketplaceLockRejection.WrongFieldType("$path.$field", "object"))

private fun JsonElement.requiredLockObject(path: String): JsonObject =
    this as? JsonObject ?: lockReject(MarketplaceLockRejection.WrongFieldType(path, "object"))

private val ROOT_FIELDS = listOf("entries", "schemaVersion", "type")
private val ENTRY_FIELDS = listOf("checksum", "index", "marketplaceId", "packages", "source")
private val PACKAGE_FIELDS = listOf("archive", "name")
private val GITHUB_SOURCE_FIELDS =
    listOf("immutable", "releaseId", "repository", "tag", "tagCommitSha", "type")
private val LOCAL_SOURCE_FIELDS = listOf("path", "type")
private val GITHUB_ASSET_FIELDS = listOf("assetId", "name", "sha256", "size")
private val LOCAL_ASSET_FIELDS = listOf("name", "sha256", "size")
