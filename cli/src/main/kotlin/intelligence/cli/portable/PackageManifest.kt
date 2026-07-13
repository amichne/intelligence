package intelligence.cli.portable

@JvmInline
internal value class PortableDescription private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): PortableDescriptionParsing {
            if (candidate.isBlank()) {
                return PortableDescriptionParsing.Rejected(PortableDescriptionRejection.EMPTY)
            }
            if (candidate.codePointCount(0, candidate.length) > MAX_PORTABLE_DESCRIPTION_CHARACTERS) {
                return PortableDescriptionParsing.Rejected(PortableDescriptionRejection.TOO_LONG)
            }
            if ('\u0000' in candidate) {
                return PortableDescriptionParsing.Rejected(PortableDescriptionRejection.NUL)
            }
            return when (CanonicalJsonString.create(candidate)) {
                is CanonicalJsonStringCreation.Created ->
                    PortableDescriptionParsing.Parsed(PortableDescription(candidate))
                is CanonicalJsonStringCreation.Rejected ->
                    PortableDescriptionParsing.Rejected(PortableDescriptionRejection.INVALID_UNICODE)
            }
        }
    }
}

internal sealed interface PortableDescriptionParsing {
    data class Parsed(val description: PortableDescription) : PortableDescriptionParsing

    data class Rejected(val reason: PortableDescriptionRejection) : PortableDescriptionParsing
}

internal enum class PortableDescriptionRejection {
    EMPTY,
    TOO_LONG,
    NUL,
    INVALID_UNICODE,
}

@JvmInline
internal value class PackageTag private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): PackageTagParsing =
            when (val parsed = parseIdentifier(candidate, ::PackageTag)) {
                is IdentifierParse.Accepted -> PackageTagParsing.Parsed(parsed.value)
                is IdentifierParse.Rejected -> PackageTagParsing.Rejected(parsed.reason)
            }
    }
}

internal sealed interface PackageTagParsing {
    data class Parsed(val tag: PackageTag) : PackageTagParsing

    data class Rejected(val reason: IdentifierRejection) : PackageTagParsing
}

internal data class PackageFileEvidence(
    val path: PackageEntryPath,
    val byteSize: Int,
    val sha256: Sha256Digest,
    val executable: Boolean,
)

internal data class PortableSkillManifest(
    val name: SkillName,
    val description: PortableDescription,
    val primary: PackageFileEvidence,
    val assets: List<PackageFileEvidence>,
)

internal class PackageManifest private constructor(
    val marketplaceId: MarketplaceId,
    val name: PackageName,
    val description: PortableDescription,
    val tags: List<PackageTag>,
    val skills: List<PortableSkillManifest>,
    private val document: CanonicalJsonDocument,
) {
    fun canonicalBytes(): ByteArray = document.bytes()

    companion object {
        fun parse(bytes: ByteArray): PackageManifestParsing = PackageManifestParser.parse(bytes)

        internal fun fromValidated(
            marketplaceId: MarketplaceId,
            name: PackageName,
            description: PortableDescription,
            tags: List<PackageTag>,
            skills: List<PortableSkillManifest>,
        ): PackageManifestConstruction {
            val document =
                when (
                    val created =
                        CanonicalJsonDocument.create(
                            canonicalJsonObject(
                                "description" to canonicalJsonString(description.render()),
                                "marketplaceId" to canonicalJsonString(marketplaceId.render()),
                                "name" to canonicalJsonString(name.render()),
                                "schemaVersion" to canonicalJsonInteger(PACKAGE_SCHEMA_VERSION.toLong()),
                                "skills" to CanonicalJsonArray(skills.map(PortableSkillManifest::canonicalValue)),
                                "tags" to CanonicalJsonArray(tags.map { tag -> canonicalJsonString(tag.render()) }),
                                "type" to canonicalJsonString(PACKAGE_MANIFEST_TYPE),
                            ),
                        )
                ) {
                    is CanonicalJsonDocumentCreation.Created -> created.document
                    is CanonicalJsonDocumentCreation.Rejected -> {
                        val reason =
                            when (val rejection = created.reason) {
                                is CanonicalJsonDocumentRejection.SizeExceeded -> rejection
                            }
                        return PackageManifestConstruction.Rejected(
                            PackageManifestRejection.JsonDocumentTooLarge(
                                actualBytes = reason.actualBytes.toInt(),
                                maximumBytes = reason.maximumBytes,
                            ),
                        )
                    }
                }

            val entryCount = 1 + skills.sumOf { skill -> 1 + skill.assets.size }
            if (entryCount > MAX_PACKAGE_ENTRIES) {
                return PackageManifestConstruction.Rejected(
                    PackageManifestRejection.TooManyEntries(entryCount, MAX_PACKAGE_ENTRIES),
                )
            }
            val expandedByteSize =
                document.byteSize.toLong() +
                    skills.sumOf { skill ->
                        skill.primary.byteSize.toLong() + skill.assets.sumOf { asset -> asset.byteSize.toLong() }
                    }
            if (expandedByteSize > MAX_PACKAGE_EXPANDED_BYTES) {
                return PackageManifestConstruction.Rejected(
                    PackageManifestRejection.ExpandedSizeExceeded(
                        actualBytes = expandedByteSize,
                        maximumBytes = MAX_PACKAGE_EXPANDED_BYTES,
                    ),
                )
            }

            return PackageManifestConstruction.Created(
                PackageManifest(
                    marketplaceId = marketplaceId,
                    name = name,
                    description = description,
                    tags = tags.toList(),
                    skills = skills.map { skill -> skill.copy(assets = skill.assets.toList()) },
                    document = document,
                ),
            )
        }
    }
}

internal sealed interface PackageManifestConstruction {
    data class Created(val manifest: PackageManifest) : PackageManifestConstruction

    data class Rejected(val reason: PackageManifestRejection) : PackageManifestConstruction
}

internal sealed interface PackageManifestParsing {
    data class Parsed(val manifest: PackageManifest) : PackageManifestParsing

    data class Rejected(val reason: PackageManifestRejection) : PackageManifestParsing
}

internal sealed interface PackageManifestRejection {
    data class JsonDocumentTooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : PackageManifestRejection

    data object InvalidUtf8 : PackageManifestRejection

    data object MalformedJson : PackageManifestRejection

    data object RootMustBeObject : PackageManifestRejection

    data class MissingField(val path: String, val field: String) : PackageManifestRejection

    data class UnknownField(val path: String, val field: String) : PackageManifestRejection

    data class WrongFieldType(
        val path: String,
        val expected: String,
    ) : PackageManifestRejection

    data class UnsupportedType(val actual: String) : PackageManifestRejection

    data class UnsupportedSchemaVersion(val actual: Long) : PackageManifestRejection

    data class InvalidMarketplaceId(val reason: IdentifierRejection) : PackageManifestRejection

    data class InvalidPackageName(val reason: IdentifierRejection) : PackageManifestRejection

    data class InvalidDescription(
        val path: String,
        val reason: PortableDescriptionRejection,
    ) : PackageManifestRejection

    data class InvalidTag(
        val index: Int,
        val reason: IdentifierRejection,
    ) : PackageManifestRejection

    data object TagsNotCanonical : PackageManifestRejection

    data object NoSkills : PackageManifestRejection

    data class TooManySkills(val actual: Int, val maximum: Int) : PackageManifestRejection

    data class InvalidSkillName(
        val index: Int,
        val reason: IdentifierRejection,
    ) : PackageManifestRejection

    data object SkillsNotCanonical : PackageManifestRejection

    data class InvalidPath(
        val path: String,
        val reason: PackageEntryPathRejection,
    ) : PackageManifestRejection

    data class InvalidDigest(
        val path: String,
        val reason: Sha256DigestRejection,
    ) : PackageManifestRejection

    data class InvalidFileSize(
        val path: String,
        val actual: Long,
        val minimum: Int,
        val maximum: Int,
    ) : PackageManifestRejection

    data class InvalidPrimaryPath(
        val skill: String,
        val actual: String,
    ) : PackageManifestRejection

    data class ExecutablePrimary(val skill: String) : PackageManifestRejection

    data class AssetOutsideSkill(
        val skill: String,
        val path: String,
    ) : PackageManifestRejection

    data class AssetsNotCanonical(val skill: String) : PackageManifestRejection

    data class PathCollision(
        val first: String,
        val second: String,
    ) : PackageManifestRejection

    data class TooManyEntries(val actual: Int, val maximum: Int) : PackageManifestRejection

    data class ExpandedSizeExceeded(
        val actualBytes: Long,
        val maximumBytes: Long,
    ) : PackageManifestRejection

    data object NonCanonicalJson : PackageManifestRejection
}

private fun PortableSkillManifest.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "assets" to CanonicalJsonArray(assets.map(PackageFileEvidence::canonicalValue)),
        "description" to canonicalJsonString(description.render()),
        "name" to canonicalJsonString(name.render()),
        "primary" to primary.canonicalValue(),
    )

private fun PackageFileEvidence.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "executable" to CanonicalJsonBoolean(executable),
        "path" to canonicalJsonString(path.render()),
        "sha256" to canonicalJsonString(sha256.render()),
        "size" to canonicalJsonInteger(byteSize.toLong()),
    )

internal const val PACKAGE_SCHEMA_VERSION = 1
internal const val PACKAGE_MANIFEST_TYPE = "INTELLIGENCE_PACKAGE"
internal const val MAX_SKILLS_PER_PACKAGE = 512
internal const val MAX_PACKAGE_FILE_BYTES = 16 * 1024 * 1024
internal const val MAX_PACKAGE_ENTRIES = 4_096
internal const val MAX_PACKAGE_JSON_BYTES = 4 * 1024 * 1024
internal const val MAX_PACKAGE_EXPANDED_BYTES = 128L * 1024 * 1024

private const val MAX_PORTABLE_DESCRIPTION_CHARACTERS = 1_024
