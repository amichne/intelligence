package intelligence.cli.portable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object MarketplaceSnapshotIndexParser {
    fun parse(bytes: ByteArray): MarketplaceSnapshotIndexParsing {
        val root =
            when (
                val parsed =
                    StrictCanonicalJson.parseObject(
                        bytes = bytes,
                        maximumBytes = MAX_MARKETPLACE_SNAPSHOT_JSON_BYTES,
                    )
            ) {
                is StrictCanonicalJsonObjectParsing.Parsed -> parsed.root
                is StrictCanonicalJsonObjectParsing.Rejected -> {
                    return MarketplaceSnapshotIndexParsing.Rejected(parsed.reason.toIndexRejection())
                }
            }
        val decoded =
            try {
                MarketplaceSnapshotIndexDecoder.decode(root)
            } catch (rejected: MarketplaceSnapshotIndexRejected) {
                return MarketplaceSnapshotIndexParsing.Rejected(rejected.reason)
            }
        val index =
            when (
                val constructed =
                    MarketplaceSnapshotIndex.fromValidated(
                        marketplaceId = decoded.marketplaceId,
                        snapshotId = decoded.snapshotId,
                        defaultPackage = decoded.defaultPackage,
                        packages = decoded.packages,
                        projections = decoded.projections,
                    )
            ) {
                is MarketplaceSnapshotIndexConstruction.Constructed -> constructed.index
                is MarketplaceSnapshotIndexConstruction.Rejected -> {
                    return MarketplaceSnapshotIndexParsing.Rejected(constructed.reason)
                }
            }
        if (!bytes.contentEquals(index.canonicalBytes())) {
            return MarketplaceSnapshotIndexParsing.Rejected(
                MarketplaceSnapshotIndexRejection.NonCanonicalJson,
            )
        }
        return MarketplaceSnapshotIndexParsing.Parsed(index)
    }
}

private object MarketplaceSnapshotIndexDecoder {
    fun decode(root: JsonObject): DecodedMarketplaceSnapshotIndex {
        root.requireExactFields("$", ROOT_FIELDS)
        val type = root.requiredString("$", "type")
        if (type != MARKETPLACE_SNAPSHOT_TYPE) {
            reject(MarketplaceSnapshotIndexRejection.UnsupportedType(type))
        }
        val schemaVersion = root.requiredLong("$", "schemaVersion")
        if (schemaVersion != MARKETPLACE_SNAPSHOT_SCHEMA_VERSION.toLong()) {
            reject(MarketplaceSnapshotIndexRejection.UnsupportedSchemaVersion(schemaVersion))
        }

        val marketplaceId =
            when (val parsed = MarketplaceId.parse(root.requiredString("$", "marketplaceId"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    reject(MarketplaceSnapshotIndexRejection.InvalidMarketplaceId(parsed.reason))
                }
            }
        val snapshotId =
            when (val parsed = SnapshotId.parse(root.requiredString("$", "snapshotId"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    reject(MarketplaceSnapshotIndexRejection.InvalidSnapshotId(parsed.reason))
                }
            }
        val defaultPackage =
            when (val parsed = PackageName.parse(root.requiredString("$", "defaultPackage"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    reject(MarketplaceSnapshotIndexRejection.InvalidDefaultPackage(parsed.reason))
                }
            }
        val checksumAsset = decodeAssetName(root.requiredString("$", "checksumAsset"), "$.checksumAsset")
        val expectedChecksumAsset = ReleaseAssetName.checksumManifest()
        if (checksumAsset != expectedChecksumAsset) {
            reject(
                MarketplaceSnapshotIndexRejection.UnexpectedChecksumAsset(
                    expected = expectedChecksumAsset,
                    actual = checksumAsset,
                ),
            )
        }

        val packageElements = root.requiredArray("$", "packages")
        if (packageElements.isEmpty()) reject(MarketplaceSnapshotIndexRejection.NoPackages)
        if (packageElements.size > MAX_PACKAGES_PER_SNAPSHOT) {
            reject(
                MarketplaceSnapshotIndexRejection.TooManyPackages(
                    actual = packageElements.size,
                    maximum = MAX_PACKAGES_PER_SNAPSHOT,
                ),
            )
        }
        val packages = packageElements.mapIndexed(::decodePackage)
        validatePackages(packages)
        if (packages.none { record -> record.name == defaultPackage }) {
            reject(MarketplaceSnapshotIndexRejection.DefaultPackageMissing(defaultPackage))
        }

        val projections = root.requiredArray("$", "projections").mapIndexed(::decodeProjection)
        validateProjections(projections)
        validateDistinctAssets(packages, projections)?.let(::reject)

        return DecodedMarketplaceSnapshotIndex(
            marketplaceId = marketplaceId,
            snapshotId = snapshotId,
            defaultPackage = defaultPackage,
            packages = packages,
            projections = projections,
        )
    }

    private fun decodePackage(
        index: Int,
        element: JsonElement,
    ): SnapshotPackageRecord {
        val path = "$.packages[$index]"
        val source = element.requiredObject(path)
        source.requireExactFields(path, PACKAGE_FIELDS)
        val name =
            when (val parsed = PackageName.parse(source.requiredString(path, "name"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> {
                    reject(MarketplaceSnapshotIndexRejection.InvalidPackageName(index, parsed.reason))
                }
            }
        val description = decodeDescription(source.requiredString(path, "description"), "$path.description")
        val tags = decodeTags(index, name, source.requiredArray(path, "tags"))
        val archive = decodeAsset(source.requiredObject(path, "archive"), "$path.archive")
        val expectedName = ReleaseAssetName.packageArchive(name)
        if (archive.name != expectedName) {
            reject(
                MarketplaceSnapshotIndexRejection.UnexpectedAssetName(
                    path = "$path.archive.name",
                    expected = expectedName,
                    actual = archive.name,
                ),
            )
        }
        return SnapshotPackageRecord(name, description, tags, archive)
    }

    private fun decodeProjection(
        index: Int,
        element: JsonElement,
    ): SnapshotProjectionRecord {
        val path = "$.projections[$index]"
        val source = element.requiredObject(path)
        source.requireExactFields(path, PROJECTION_FIELDS)
        val providerText = source.requiredString(path, "provider")
        val provider = PortableProvider.parse(providerText)
            ?: reject(MarketplaceSnapshotIndexRejection.UnsupportedProvider(providerText))
        val archive = decodeAsset(source.requiredObject(path, "archive"), "$path.archive")
        val expectedName = ReleaseAssetName.providerArchive(provider)
        if (archive.name != expectedName) {
            reject(
                MarketplaceSnapshotIndexRejection.UnexpectedAssetName(
                    path = "$path.archive.name",
                    expected = expectedName,
                    actual = archive.name,
                ),
            )
        }
        return SnapshotProjectionRecord(provider, archive)
    }

    private fun decodeAsset(
        source: JsonObject,
        path: String,
    ): SnapshotAssetEvidence {
        source.requireExactFields(path, ASSET_FIELDS)
        val name = decodeAssetName(source.requiredString(path, "name"), "$path.name")
        val size = source.requiredLong(path, "size")
        if (size !in 1L..MAX_RELEASE_ARTIFACT_BYTES.toLong()) {
            reject(
                MarketplaceSnapshotIndexRejection.InvalidAssetSize(
                    path = "$path.size",
                    actualBytes = size,
                    maximumBytes = MAX_RELEASE_ARTIFACT_BYTES,
                ),
            )
        }
        val digest =
            when (val parsed = Sha256Digest.parse(source.requiredString(path, "sha256"))) {
                is Sha256DigestParsing.Parsed -> parsed.digest
                is Sha256DigestParsing.Rejected -> {
                    reject(
                        MarketplaceSnapshotIndexRejection.InvalidDigest(
                            path = "$path.sha256",
                            reason = parsed.reason,
                        ),
                    )
                }
            }
        return SnapshotAssetEvidence(name, size.toInt(), digest)
    }

    private fun decodeTags(
        packageIndex: Int,
        packageName: PackageName,
        elements: JsonArray,
    ): List<PackageTag> {
        val tags =
            elements.mapIndexed { tagIndex, element ->
                val candidate = element.requiredString("$.packages[$packageIndex].tags[$tagIndex]")
                when (val parsed = PackageTag.parse(candidate)) {
                    is PackageTagParsing.Parsed -> parsed.tag
                    is PackageTagParsing.Rejected -> {
                        reject(
                            MarketplaceSnapshotIndexRejection.InvalidTag(
                                packageIndex = packageIndex,
                                tagIndex = tagIndex,
                                reason = parsed.reason,
                            ),
                        )
                    }
                }
            }
        val rendered = tags.map(PackageTag::render)
        if (rendered != rendered.sorted() || rendered.size != rendered.toSet().size) {
            reject(MarketplaceSnapshotIndexRejection.TagsNotCanonical(packageName))
        }
        return tags
    }

    private fun decodeDescription(
        candidate: String,
        path: String,
    ): PortableDescription =
        when (val parsed = PortableDescription.parse(candidate)) {
            is PortableDescriptionParsing.Parsed -> parsed.description
            is PortableDescriptionParsing.Rejected -> {
                reject(MarketplaceSnapshotIndexRejection.InvalidDescription(path, parsed.reason))
            }
        }

    private fun decodeAssetName(
        candidate: String,
        path: String,
    ): ReleaseAssetName =
        when (val parsed = ReleaseAssetName.parse(candidate)) {
            is ReleaseAssetNameParsing.Parsed -> parsed.name
            is ReleaseAssetNameParsing.Rejected -> {
                reject(MarketplaceSnapshotIndexRejection.InvalidAssetName(path, parsed.reason))
            }
        }

    private fun validatePackages(packages: List<SnapshotPackageRecord>) {
        val seen = mutableSetOf<PackageName>()
        packages.forEach { record ->
            if (!seen.add(record.name)) {
                reject(MarketplaceSnapshotIndexRejection.DuplicatePackage(record.name))
            }
        }
        if (packages.map { record -> record.name.render() } != packages.map { it.name.render() }.sorted()) {
            reject(MarketplaceSnapshotIndexRejection.PackagesNotCanonical)
        }
    }

    private fun validateProjections(projections: List<SnapshotProjectionRecord>) {
        val seen = mutableSetOf<PortableProvider>()
        projections.forEach { record ->
            if (!seen.add(record.provider)) {
                reject(MarketplaceSnapshotIndexRejection.DuplicateProvider(record.provider))
            }
        }
        PortableProvider.entries.firstOrNull { provider -> provider !in seen }?.let { missing ->
            reject(MarketplaceSnapshotIndexRejection.MissingProvider(missing))
        }
        if (projections.map(SnapshotProjectionRecord::provider) != PortableProvider.entries) {
            reject(MarketplaceSnapshotIndexRejection.ProjectionsNotCanonical)
        }
    }
}

private data class DecodedMarketplaceSnapshotIndex(
    val marketplaceId: MarketplaceId,
    val snapshotId: SnapshotId,
    val defaultPackage: PackageName,
    val packages: List<SnapshotPackageRecord>,
    val projections: List<SnapshotProjectionRecord>,
)

private class MarketplaceSnapshotIndexRejected(
    val reason: MarketplaceSnapshotIndexRejection,
) : RuntimeException(null, null, false, false)

private fun reject(reason: MarketplaceSnapshotIndexRejection): Nothing =
    throw MarketplaceSnapshotIndexRejected(reason)

private fun StrictCanonicalJsonRejection.toIndexRejection(): MarketplaceSnapshotIndexRejection =
    when (this) {
        is StrictCanonicalJsonRejection.DocumentTooLarge ->
            MarketplaceSnapshotIndexRejection.JsonDocumentTooLarge(
                actualBytes = actualBytes.toLong(),
                maximumBytes = maximumBytes,
            )
        StrictCanonicalJsonRejection.InvalidUtf8 -> MarketplaceSnapshotIndexRejection.InvalidUtf8
        StrictCanonicalJsonRejection.MalformedJson -> MarketplaceSnapshotIndexRejection.MalformedJson
        StrictCanonicalJsonRejection.RootMustBeObject -> MarketplaceSnapshotIndexRejection.RootMustBeObject
        StrictCanonicalJsonRejection.NonCanonicalJson -> MarketplaceSnapshotIndexRejection.NonCanonicalJson
    }

private fun JsonObject.requireExactFields(
    path: String,
    expected: List<String>,
) {
    expected.firstOrNull { field -> field !in this }?.let { missing ->
        reject(MarketplaceSnapshotIndexRejection.MissingField(path, missing))
    }
    keys.filterNot(expected::contains).sorted().firstOrNull()?.let { unknown ->
        reject(MarketplaceSnapshotIndexRejection.UnknownField(path, unknown))
    }
}

private fun JsonObject.requiredString(
    path: String,
    field: String,
): String = this[field].requiredString("$path.$field")

private fun JsonElement?.requiredString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        reject(MarketplaceSnapshotIndexRejection.WrongFieldType(path, "string"))
    }
    return primitive.content
}

private fun JsonObject.requiredLong(
    path: String,
    field: String,
): Long {
    val value = this[field] as? JsonPrimitive
    return value?.takeUnless(JsonPrimitive::isString)?.longOrNull
        ?: reject(MarketplaceSnapshotIndexRejection.WrongFieldType("$path.$field", "safe integer"))
}

private fun JsonObject.requiredArray(
    path: String,
    field: String,
): JsonArray =
    this[field] as? JsonArray
        ?: reject(MarketplaceSnapshotIndexRejection.WrongFieldType("$path.$field", "array"))

private fun JsonObject.requiredObject(
    path: String,
    field: String,
): JsonObject =
    this[field] as? JsonObject
        ?: reject(MarketplaceSnapshotIndexRejection.WrongFieldType("$path.$field", "object"))

private fun JsonElement.requiredObject(path: String): JsonObject =
    this as? JsonObject ?: reject(MarketplaceSnapshotIndexRejection.WrongFieldType(path, "object"))

private val ROOT_FIELDS =
    listOf(
        "checksumAsset",
        "defaultPackage",
        "marketplaceId",
        "packages",
        "projections",
        "schemaVersion",
        "snapshotId",
        "type",
    )
private val PACKAGE_FIELDS = listOf("archive", "description", "name", "tags")
private val PROJECTION_FIELDS = listOf("archive", "provider")
private val ASSET_FIELDS = listOf("name", "sha256", "size")
