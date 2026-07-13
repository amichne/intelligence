package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PortableIdentifierTest {
    @Test
    fun `canonical identifiers preserve their exact text`() {
        assertAccepted("portable-marketplace", MarketplaceId.parse("portable-marketplace"), MarketplaceId::render)
        assertAccepted("snapshot-july", SnapshotId.parse("snapshot-july"), SnapshotId::render)
        assertAccepted("kotlin-engineering", PackageName.parse("kotlin-engineering"), PackageName::render)
        assertAccepted("type-safety", SkillName.parse("type-safety"), SkillName::render)
        assertAccepted("a".repeat(64), MarketplaceId.parse("a".repeat(64)), MarketplaceId::render)
    }

    @Test
    fun `identifiers reject non canonical input without normalization`() {
        val cases =
            listOf(
                "" to IdentifierRejection.EMPTY,
                "a".repeat(65) to IdentifierRejection.TOO_LONG,
                "Uppercase" to IdentifierRejection.INVALID_SYNTAX,
                "-leading" to IdentifierRejection.INVALID_SYNTAX,
                "trailing-" to IdentifierRejection.INVALID_SYNTAX,
                "two--hyphens" to IdentifierRejection.INVALID_SYNTAX,
                "white space" to IdentifierRejection.INVALID_SYNTAX,
                "café" to IdentifierRejection.INVALID_SYNTAX,
            )

        cases.forEach { (raw, expected) ->
            when (val actual = MarketplaceId.parse(raw)) {
                is IdentifierParse.Accepted -> fail("Expected '$raw' to be rejected")
                is IdentifierParse.Rejected -> assertEquals(expected, actual.reason)
            }
        }
    }

    private fun <T> assertAccepted(
        expected: String,
        actual: IdentifierParse<T>,
        render: (T) -> String,
    ) {
        when (actual) {
            is IdentifierParse.Accepted -> assertEquals(expected, render(actual.value))
            is IdentifierParse.Rejected -> fail("Expected an accepted identifier, got ${actual.reason}")
        }
    }
}
