package intelligence.cli.portable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal object PackageManifestParser {
    fun parse(bytes: ByteArray): PackageManifestParsing {
        val root =
            when (
                val parsed =
                    StrictCanonicalJson.parseObject(
                        bytes = bytes,
                        maximumBytes = MAX_PACKAGE_JSON_BYTES,
                    )
            ) {
                is StrictCanonicalJsonObjectParsing.Parsed -> parsed.root
                is StrictCanonicalJsonObjectParsing.Rejected -> {
                    return PackageManifestParsing.Rejected(parsed.reason.toPackageManifestRejection())
                }
            }

        val decoded =
            try {
                PackageManifestDecoder.decode(root)
            } catch (rejected: PackageManifestRejected) {
                return PackageManifestParsing.Rejected(rejected.reason)
            }
        val manifest =
            when (
                val constructed =
                    PackageManifest.fromValidated(
                        marketplaceId = decoded.marketplaceId,
                        name = decoded.name,
                        description = decoded.description,
                        tags = decoded.tags,
                        skills = decoded.skills,
                    )
            ) {
                is PackageManifestConstruction.Created -> constructed.manifest
                is PackageManifestConstruction.Rejected -> {
                    return PackageManifestParsing.Rejected(constructed.reason)
                }
            }
        if (!bytes.contentEquals(manifest.canonicalBytes())) {
            return PackageManifestParsing.Rejected(PackageManifestRejection.NonCanonicalJson)
        }
        return PackageManifestParsing.Parsed(manifest)
    }
}

private object PackageManifestDecoder {
    fun decode(root: JsonObject): DecodedPackageManifest {
        root.requireExactFields("$", ROOT_FIELDS)
        val type = root.requiredString("$", "type")
        if (type != PACKAGE_MANIFEST_TYPE) reject(PackageManifestRejection.UnsupportedType(type))
        val schemaVersion = root.requiredLong("$", "schemaVersion")
        if (schemaVersion != PACKAGE_SCHEMA_VERSION.toLong()) {
            reject(PackageManifestRejection.UnsupportedSchemaVersion(schemaVersion))
        }

        val marketplaceId =
            when (val parsed = MarketplaceId.parse(root.requiredString("$", "marketplaceId"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> reject(PackageManifestRejection.InvalidMarketplaceId(parsed.reason))
            }
        val packageName =
            when (val parsed = PackageName.parse(root.requiredString("$", "name"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> reject(PackageManifestRejection.InvalidPackageName(parsed.reason))
            }
        val description = decodeDescription(root.requiredString("$", "description"), "$.description")
        val tags = decodeTags(root.requiredArray("$", "tags"))
        val skillElements = root.requiredArray("$", "skills")
        if (skillElements.isEmpty()) reject(PackageManifestRejection.NoSkills)
        if (skillElements.size > MAX_SKILLS_PER_PACKAGE) {
            reject(PackageManifestRejection.TooManySkills(skillElements.size, MAX_SKILLS_PER_PACKAGE))
        }
        val skills = skillElements.mapIndexed(::decodeSkill)
        val skillNames = skills.map { skill -> skill.name.render() }
        if (skillNames != skillNames.sorted() || skillNames.size != skillNames.toSet().size) {
            reject(PackageManifestRejection.SkillsNotCanonical)
        }
        validatePathCollisions(skills)

        return DecodedPackageManifest(
            marketplaceId = marketplaceId,
            name = packageName,
            description = description,
            tags = tags,
            skills = skills,
        )
    }

    private fun decodeTags(elements: JsonArray): List<PackageTag> {
        val tags =
            elements.mapIndexed { index, element ->
                val candidate = element.requiredString("$.tags[$index]")
                when (val parsed = PackageTag.parse(candidate)) {
                    is PackageTagParsing.Parsed -> parsed.tag
                    is PackageTagParsing.Rejected -> {
                        reject(PackageManifestRejection.InvalidTag(index, parsed.reason))
                    }
                }
            }
        val values = tags.map(PackageTag::render)
        if (values != values.sorted() || values.size != values.toSet().size) {
            reject(PackageManifestRejection.TagsNotCanonical)
        }
        return tags
    }

    private fun decodeSkill(
        index: Int,
        element: JsonElement,
    ): PortableSkillManifest {
        val path = "$.skills[$index]"
        val source = element.requiredObject(path)
        source.requireExactFields(path, SKILL_FIELDS)
        val name =
            when (val parsed = SkillName.parse(source.requiredString(path, "name"))) {
                is IdentifierParse.Accepted -> parsed.value
                is IdentifierParse.Rejected -> reject(PackageManifestRejection.InvalidSkillName(index, parsed.reason))
            }
        val description = decodeDescription(source.requiredString(path, "description"), "$path.description")
        val primary = decodeFile(source.requiredObject(path, "primary"), "$path.primary", minimumBytes = 1)
        val expectedPrimaryPath = "skills/${name.render()}/SKILL.md"
        if (primary.path.render() != expectedPrimaryPath) {
            reject(PackageManifestRejection.InvalidPrimaryPath(name.render(), primary.path.render()))
        }
        if (primary.executable) reject(PackageManifestRejection.ExecutablePrimary(name.render()))

        val assets =
            source.requiredArray(path, "assets").mapIndexed { assetIndex, assetElement ->
                val assetPath = "$path.assets[$assetIndex]"
                decodeFile(assetElement.requiredObject(assetPath), assetPath, minimumBytes = 0)
            }
        val assetPaths = assets.map { asset -> asset.path.render() }
        if (assetPaths != assetPaths.sorted() || assetPaths.size != assetPaths.toSet().size) {
            reject(PackageManifestRejection.AssetsNotCanonical(name.render()))
        }
        val skillRoot = "skills/${name.render()}/"
        assets.forEach { asset ->
            if (!asset.path.render().startsWith(skillRoot)) {
                reject(PackageManifestRejection.AssetOutsideSkill(name.render(), asset.path.render()))
            }
        }

        return PortableSkillManifest(name, description, primary, assets)
    }

    private fun decodeFile(
        source: JsonObject,
        path: String,
        minimumBytes: Int,
    ): PackageFileEvidence {
        source.requireExactFields(path, FILE_FIELDS)
        val parsedPath =
            when (val parsed = PackageEntryPath.parse(source.requiredString(path, "path"))) {
                is PackageEntryPathParse.Accepted -> parsed.value
                is PackageEntryPathParse.Rejected -> {
                    reject(PackageManifestRejection.InvalidPath("$path.path", parsed.reason))
                }
            }
        val digest =
            when (val parsed = Sha256Digest.parse(source.requiredString(path, "sha256"))) {
                is Sha256DigestParsing.Parsed -> parsed.digest
                is Sha256DigestParsing.Rejected -> {
                    reject(PackageManifestRejection.InvalidDigest("$path.sha256", parsed.reason))
                }
            }
        val byteSize = source.requiredLong(path, "size")
        if (byteSize !in minimumBytes.toLong()..MAX_PACKAGE_FILE_BYTES.toLong()) {
            reject(
                PackageManifestRejection.InvalidFileSize(
                    path = "$path.size",
                    actual = byteSize,
                    minimum = minimumBytes,
                    maximum = MAX_PACKAGE_FILE_BYTES,
                ),
            )
        }
        val executable = source.requiredBoolean(path, "executable")
        return PackageFileEvidence(parsedPath, byteSize.toInt(), digest, executable)
    }

    private fun decodeDescription(
        candidate: String,
        path: String,
    ): PortableDescription =
        when (val parsed = PortableDescription.parse(candidate)) {
            is PortableDescriptionParsing.Parsed -> parsed.description
            is PortableDescriptionParsing.Rejected -> {
                reject(PackageManifestRejection.InvalidDescription(path, parsed.reason))
            }
        }

    private fun validatePathCollisions(skills: List<PortableSkillManifest>) {
        val firstPathByFoldedPath = linkedMapOf<String, String>()
        skills.forEach { skill ->
            sequenceOf(skill.primary).plus(skill.assets.asSequence()).forEach { file ->
                val path = file.path.render()
                val folded = path.asciiLowercase()
                val first = firstPathByFoldedPath.putIfAbsent(folded, path)
                if (first != null) reject(PackageManifestRejection.PathCollision(first, path))
            }
        }
    }
}

private data class DecodedPackageManifest(
    val marketplaceId: MarketplaceId,
    val name: PackageName,
    val description: PortableDescription,
    val tags: List<PackageTag>,
    val skills: List<PortableSkillManifest>,
)

private class PackageManifestRejected(
    val reason: PackageManifestRejection,
) : RuntimeException(null, null, false, false)

private fun reject(reason: PackageManifestRejection): Nothing = throw PackageManifestRejected(reason)

private fun JsonObject.requireExactFields(
    path: String,
    expected: List<String>,
) {
    expected.firstOrNull { field -> field !in this }?.let { missing ->
        reject(PackageManifestRejection.MissingField(path, missing))
    }
    keys.filterNot(expected::contains).sorted().firstOrNull()?.let { unknown ->
        reject(PackageManifestRejection.UnknownField(path, unknown))
    }
}

private fun JsonObject.requiredString(
    path: String,
    field: String,
): String = this[field].requiredString("$path.$field")

private fun JsonElement?.requiredString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        reject(PackageManifestRejection.WrongFieldType(path, "string"))
    }
    return primitive.content
}

private fun JsonObject.requiredLong(
    path: String,
    field: String,
): Long {
    val value = this[field] as? JsonPrimitive
    return value?.takeUnless(JsonPrimitive::isString)?.longOrNull
        ?: reject(PackageManifestRejection.WrongFieldType("$path.$field", "safe integer"))
}

private fun JsonObject.requiredBoolean(
    path: String,
    field: String,
): Boolean {
    val value = this[field] as? JsonPrimitive
    return value?.takeUnless(JsonPrimitive::isString)?.booleanOrNull
        ?: reject(PackageManifestRejection.WrongFieldType("$path.$field", "boolean"))
}

private fun JsonObject.requiredArray(
    path: String,
    field: String,
): JsonArray =
    this[field] as? JsonArray
        ?: reject(PackageManifestRejection.WrongFieldType("$path.$field", "array"))

private fun JsonObject.requiredObject(
    path: String,
    field: String,
): JsonObject =
    this[field] as? JsonObject
        ?: reject(PackageManifestRejection.WrongFieldType("$path.$field", "object"))

private fun JsonElement.requiredObject(path: String): JsonObject =
    this as? JsonObject ?: reject(PackageManifestRejection.WrongFieldType(path, "object"))

private fun StrictCanonicalJsonRejection.toPackageManifestRejection(): PackageManifestRejection =
    when (this) {
        is StrictCanonicalJsonRejection.DocumentTooLarge ->
            PackageManifestRejection.JsonDocumentTooLarge(
                actualBytes = actualBytes,
                maximumBytes = maximumBytes,
            )
        StrictCanonicalJsonRejection.InvalidUtf8 -> PackageManifestRejection.InvalidUtf8
        StrictCanonicalJsonRejection.MalformedJson -> PackageManifestRejection.MalformedJson
        StrictCanonicalJsonRejection.RootMustBeObject -> PackageManifestRejection.RootMustBeObject
        StrictCanonicalJsonRejection.NonCanonicalJson -> PackageManifestRejection.NonCanonicalJson
    }

private fun String.asciiLowercase(): String =
    buildString(length) {
        this@asciiLowercase.forEach { character ->
            append(if (character in 'A'..'Z') character + ('a' - 'A') else character)
        }
    }

private val ROOT_FIELDS =
    listOf("description", "marketplaceId", "name", "schemaVersion", "skills", "tags", "type")
private val SKILL_FIELDS = listOf("assets", "description", "name", "primary")
private val FILE_FIELDS = listOf("executable", "path", "sha256", "size")
