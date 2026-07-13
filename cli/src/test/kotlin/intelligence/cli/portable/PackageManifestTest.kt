package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageManifestTest {
    @Test
    fun `canonical skill-only package manifest parses into proof-carrying values`() {
        val bytes = validManifest().encodeToByteArray()
        val manifest = assertIs<PackageManifestParsing.Parsed>(PackageManifest.parse(bytes)).manifest

        assertEquals("example-marketplace", manifest.marketplaceId.render())
        assertEquals("review-tools", manifest.name.render())
        assertEquals("A review package", manifest.description.render())
        assertEquals(listOf("kotlin", "review"), manifest.tags.map(PackageTag::render))
        assertEquals(listOf("review"), manifest.skills.map { it.name.render() })
        assertEquals("skills/review/SKILL.md", manifest.skills.single().primary.path.render())
        assertEquals(listOf("skills/review/scripts/check.sh"), manifest.skills.single().assets.map { it.path.render() })
        assertContentEquals(bytes, manifest.canonicalBytes())
    }

    @Test
    fun `manifest rejects unknown fields and unsupported contract identity`() {
        val unknown = validManifest().replace("\"marketplaceId\"", "\"hooks\":[],\"marketplaceId\"")
        assertEquals(
            PackageManifestRejection.UnknownField(path = "$", field = "hooks"),
            assertRejected(unknown),
        )

        val wrongType = validManifest().replace("INTELLIGENCE_PACKAGE", "LEGACY_PLUGIN")
        assertEquals(
            PackageManifestRejection.UnsupportedType("LEGACY_PLUGIN"),
            assertRejected(wrongType),
        )

        val wrongVersion = validManifest().replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        assertEquals(
            PackageManifestRejection.UnsupportedSchemaVersion(2),
            assertRejected(wrongVersion),
        )
    }

    @Test
    fun `manifest requires canonical JSON bytes and detects duplicate keys`() {
        val whitespace = validManifest().replace("{\"description\"", "{ \"description\"")
        assertEquals(PackageManifestRejection.NonCanonicalJson, assertRejected(whitespace))

        val duplicate =
            validManifest().replace(
                "\"name\":\"review-tools\"",
                "\"name\":\"review-tools\",\"name\":\"review-tools\"",
            )
        assertEquals(PackageManifestRejection.NonCanonicalJson, assertRejected(duplicate))

        val conflictingDuplicate =
            validManifest().replace(
                "\"name\":\"review-tools\"",
                "\"name\":\"review-tools\",\"name\":\"INVALID\"",
            )
        assertEquals(PackageManifestRejection.NonCanonicalJson, assertRejected(conflictingDuplicate))
    }

    @Test
    fun `manifest rejects invalid identities descriptions and ordering`() {
        assertEquals(
            PackageManifestRejection.InvalidPackageName(IdentifierRejection.INVALID_SYNTAX),
            assertRejected(validManifest().replace("review-tools", "ReviewTools")),
        )
        assertEquals(
            PackageManifestRejection.InvalidDescription(
                path = "$.description",
                reason = PortableDescriptionRejection.EMPTY,
            ),
            assertRejected(validManifest().replace("A review package", "")),
        )
        assertEquals(
            PackageManifestRejection.TagsNotCanonical,
            assertRejected(validManifest().replace("[\"kotlin\",\"review\"]", "[\"review\",\"kotlin\"]")),
        )
        assertEquals(
            PackageManifestRejection.InvalidDescription(
                path = "$.description",
                reason = PortableDescriptionRejection.TOO_LONG,
            ),
            assertRejected(validManifest().replace("A review package", "x".repeat(1_025))),
        )
    }

    @Test
    fun `manifest rejects invalid primary ownership and executable primary content`() {
        assertEquals(
            PackageManifestRejection.InvalidPrimaryPath(
                skill = "review",
                actual = "skills/other/SKILL.md",
            ),
            assertRejected(validManifest().replace("skills/review/SKILL.md", "skills/other/SKILL.md")),
        )
        assertEquals(
            PackageManifestRejection.ExecutablePrimary(skill = "review"),
            assertRejected(
                validManifest().replace(
                    "\"executable\":false,\"path\":\"skills/review/SKILL.md\"",
                    "\"executable\":true,\"path\":\"skills/review/SKILL.md\"",
                ),
            ),
        )
    }

    @Test
    fun `manifest rejects supporting assets outside their skill and path collisions`() {
        assertEquals(
            PackageManifestRejection.AssetOutsideSkill(
                skill = "review",
                path = "skills/other/check.sh",
            ),
            assertRejected(validManifest().replace("skills/review/scripts/check.sh", "skills/other/check.sh")),
        )

        val collidingAssets =
            "[{\"executable\":true,\"path\":\"skills/review/Tool.sh\",\"sha256\":\"${"a".repeat(64)}\",\"size\":12}," +
                "{\"executable\":true,\"path\":\"skills/review/tool.sh\",\"sha256\":\"${"c".repeat(64)}\",\"size\":13}]"
        assertEquals(
            PackageManifestRejection.PathCollision(
                first = "skills/review/Tool.sh",
                second = "skills/review/tool.sh",
            ),
            assertRejected(validManifest().replace(singleAssetArray(), collidingAssets)),
        )

        val reversedAssets =
            "[{\"executable\":true,\"path\":\"skills/review/z.sh\",\"sha256\":\"${"c".repeat(64)}\",\"size\":13}," +
                "{\"executable\":true,\"path\":\"skills/review/a.sh\",\"sha256\":\"${"a".repeat(64)}\",\"size\":12}]"
        assertEquals(
            PackageManifestRejection.AssetsNotCanonical(skill = "review"),
            assertRejected(validManifest().replace(singleAssetArray(), reversedAssets)),
        )
    }

    @Test
    fun `manifest rejects invalid digest size and entry limits`() {
        assertEquals(
            PackageManifestRejection.InvalidDigest(
                path = "$.skills[0].primary.sha256",
                reason = Sha256DigestRejection.InvalidCharacter(index = 0, character = 'B'),
            ),
            assertRejected(validManifest().replace("\"b${"b".repeat(63)}\"", "\"B${"b".repeat(63)}\"")),
        )
        assertEquals(
            PackageManifestRejection.InvalidFileSize(
                path = "$.skills[0].primary.size",
                actual = 0,
                minimum = 1,
                maximum = 16 * 1024 * 1024,
            ),
            assertRejected(validManifest().replace("\"size\":24", "\"size\":0")),
        )

        val repeatedSkill = validSkill(name = "skill", primarySize = 1)
        val tooManySkills = (0..512).joinToString(separator = ",", prefix = "[", postfix = "]") { index ->
            repeatedSkill.replace("\"skill\"", "\"skill-$index\"")
                .replace("skills/skill/", "skills/skill-$index/")
        }
        assertEquals(
            PackageManifestRejection.TooManySkills(actual = 513, maximum = 512),
            assertRejected(validManifest().replace("[${validSkill()}]", tooManySkills)),
        )
    }

    @Test
    fun `manifest rejects malformed UTF-8 and documents beyond the JSON limit`() {
        assertEquals(
            PackageManifestRejection.InvalidUtf8,
            assertIs<PackageManifestParsing.Rejected>(PackageManifest.parse(byteArrayOf(0xc3.toByte()))).reason,
        )
        assertEquals(
            PackageManifestRejection.RootMustBeObject,
            assertRejected("[]\n"),
        )
        assertEquals(
            PackageManifestRejection.JsonDocumentTooLarge(
                actualBytes = 4 * 1024 * 1024 + 1,
                maximumBytes = 4 * 1024 * 1024,
            ),
            assertIs<PackageManifestParsing.Rejected>(
                PackageManifest.parse(ByteArray(4 * 1024 * 1024 + 1) { 'x'.code.toByte() }),
            ).reason,
        )
    }

    private fun assertRejected(manifest: String): PackageManifestRejection =
        assertIs<PackageManifestParsing.Rejected>(PackageManifest.parse(manifest.encodeToByteArray())).reason

    private fun validManifest(): String =
        "{\"description\":\"A review package\",\"marketplaceId\":\"example-marketplace\",\"name\":\"review-tools\",\"schemaVersion\":1,\"skills\":[${validSkill()}],\"tags\":[\"kotlin\",\"review\"],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"

    private fun validSkill(
        name: String = "review",
        primarySize: Int = 24,
    ): String =
        "{\"assets\":${singleAssetArray(name)},\"description\":\"Review code\",\"name\":\"$name\",\"primary\":{\"executable\":false,\"path\":\"skills/$name/SKILL.md\",\"sha256\":\"${"b".repeat(64)}\",\"size\":$primarySize}}"

    private fun singleAssetArray(skill: String = "review"): String =
        "[{\"executable\":true,\"path\":\"skills/$skill/scripts/check.sh\",\"sha256\":\"${"a".repeat(64)}\",\"size\":12}]"
}
