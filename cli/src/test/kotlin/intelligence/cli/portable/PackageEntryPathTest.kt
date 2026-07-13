package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PackageEntryPathTest {
    @Test
    fun `canonical package entry path preserves exact relative text`() {
        when (val actual = PackageEntryPath.parse("skills/type-safety/SKILL.md")) {
            is PackageEntryPathParse.Accepted -> assertEquals("skills/type-safety/SKILL.md", actual.value.render())
            is PackageEntryPathParse.Rejected -> fail("Expected an accepted path, got ${actual.reason}")
        }

        val maximumLengthPath = "a".repeat(240)
        when (val actual = PackageEntryPath.parse(maximumLengthPath)) {
            is PackageEntryPathParse.Accepted -> assertEquals(maximumLengthPath, actual.value.render())
            is PackageEntryPathParse.Rejected -> fail("Expected an accepted path, got ${actual.reason}")
        }
    }

    @Test
    fun `unsafe package entry paths are rejected before archive use`() {
        val cases =
            listOf(
                "" to PackageEntryPathRejection.EMPTY,
                "a".repeat(241) to PackageEntryPathRejection.TOO_LONG,
                "/skills/type-safety/SKILL.md" to PackageEntryPathRejection.ABSOLUTE,
                "skills\\type-safety\\SKILL.md" to PackageEntryPathRejection.BACKSLASH,
                "skills//SKILL.md" to PackageEntryPathRejection.EMPTY_SEGMENT,
                "skills/./SKILL.md" to PackageEntryPathRejection.DOT_SEGMENT,
                "skills/../SKILL.md" to PackageEntryPathRejection.DOT_SEGMENT,
                "skills/type safety/SKILL.md" to PackageEntryPathRejection.INVALID_SEGMENT,
                "skills/café/SKILL.md" to PackageEntryPathRejection.INVALID_SEGMENT,
            )

        cases.forEach { (raw, expected) ->
            when (val actual = PackageEntryPath.parse(raw)) {
                is PackageEntryPathParse.Accepted -> fail("Expected '$raw' to be rejected")
                is PackageEntryPathParse.Rejected -> assertEquals(expected, actual.reason)
            }
        }
    }
}
