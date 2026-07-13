package intelligence.cli.portable

import java.nio.charset.CharacterCodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal class PortableSkillDocument private constructor(
    private val content: ByteArray,
) {
    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun parse(
            bytes: ByteArray,
            expectedName: SkillName,
            expectedDescription: PortableDescription,
        ): PortableSkillDocumentParsing {
            if (bytes.any { byte -> byte == 0.toByte() }) {
                return PortableSkillDocumentParsing.Rejected(PortableSkillDocumentRejection.Nul)
            }
            val text =
                try {
                    bytes.decodeToString(throwOnInvalidSequence = true)
                } catch (_: CharacterCodingException) {
                    return PortableSkillDocumentParsing.Rejected(PortableSkillDocumentRejection.InvalidUtf8)
                }
            if ('\r' in text) {
                return PortableSkillDocumentParsing.Rejected(PortableSkillDocumentRejection.CarriageReturn)
            }

            val lines = text.split('\n')
            if (
                lines.size < MINIMUM_SKILL_DOCUMENT_LINES ||
                lines[0] != FRONTMATTER_DELIMITER ||
                !lines[1].startsWith(NAME_PREFIX) ||
                !lines[2].startsWith(DESCRIPTION_PREFIX) ||
                lines[3] != FRONTMATTER_DELIMITER ||
                lines[4].isNotEmpty()
            ) {
                return PortableSkillDocumentParsing.Rejected(
                    PortableSkillDocumentRejection.MalformedFrontmatter,
                )
            }

            val actualName = lines[1].removePrefix(NAME_PREFIX)
            if (actualName != expectedName.render()) {
                return PortableSkillDocumentParsing.Rejected(
                    PortableSkillDocumentRejection.NameMismatch(
                        expected = expectedName.render(),
                        actual = actualName,
                    ),
                )
            }

            val descriptionToken = lines[2].removePrefix(DESCRIPTION_PREFIX)
            val actualDescription = descriptionToken.parseCanonicalJsonString()
                ?: return PortableSkillDocumentParsing.Rejected(
                    PortableSkillDocumentRejection.NonCanonicalDescription,
                )
            if (actualDescription != expectedDescription.render()) {
                return PortableSkillDocumentParsing.Rejected(
                    PortableSkillDocumentRejection.DescriptionMismatch(
                        expected = expectedDescription.render(),
                        actual = actualDescription,
                    ),
                )
            }

            val instructions = lines.subList(5, lines.size).joinToString(separator = "\n")
            if (instructions.isBlank()) {
                return PortableSkillDocumentParsing.Rejected(
                    PortableSkillDocumentRejection.EmptyInstructions,
                )
            }
            return PortableSkillDocumentParsing.Parsed(PortableSkillDocument(bytes.copyOf()))
        }
    }
}

internal sealed interface PortableSkillDocumentParsing {
    data class Parsed(val document: PortableSkillDocument) : PortableSkillDocumentParsing

    data class Rejected(val reason: PortableSkillDocumentRejection) : PortableSkillDocumentParsing
}

internal sealed interface PortableSkillDocumentRejection {
    data object InvalidUtf8 : PortableSkillDocumentRejection

    data object Nul : PortableSkillDocumentRejection

    data object CarriageReturn : PortableSkillDocumentRejection

    data object MalformedFrontmatter : PortableSkillDocumentRejection

    data class NameMismatch(
        val expected: String,
        val actual: String,
    ) : PortableSkillDocumentRejection

    data object NonCanonicalDescription : PortableSkillDocumentRejection

    data class DescriptionMismatch(
        val expected: String,
        val actual: String,
    ) : PortableSkillDocumentRejection

    data object EmptyInstructions : PortableSkillDocumentRejection
}

private fun String.parseCanonicalJsonString(): String? {
    val primitive =
        try {
            Json.parseToJsonElement(this) as? JsonPrimitive
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
    if (!primitive.isString) return null
    val canonical =
        when (val created = CanonicalJsonString.create(primitive.content)) {
            is CanonicalJsonStringCreation.Created ->
                when (val rendered = created.value.canonicalToken()) {
                    is CanonicalJsonStringTokenRendering.Rendered -> rendered.token.render()
                    is CanonicalJsonStringTokenRendering.Rejected -> return null
                }
            is CanonicalJsonStringCreation.Rejected -> return null
        }
    return primitive.content.takeIf { canonical == this }
}

private const val FRONTMATTER_DELIMITER = "---"
private const val NAME_PREFIX = "name: "
private const val DESCRIPTION_PREFIX = "description: "
private const val MINIMUM_SKILL_DOCUMENT_LINES = 6
