package intelligence.cli.portable

import java.nio.charset.CharacterCodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal object StrictCanonicalJson {
    fun parseObject(
        bytes: ByteArray,
        maximumBytes: Int,
    ): StrictCanonicalJsonObjectParsing {
        if (bytes.size > maximumBytes) {
            return StrictCanonicalJsonObjectParsing.Rejected(
                StrictCanonicalJsonRejection.DocumentTooLarge(bytes.size, maximumBytes),
            )
        }
        val text =
            try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return StrictCanonicalJsonObjectParsing.Rejected(
                    StrictCanonicalJsonRejection.InvalidUtf8,
                )
            }
        val element =
            try {
                strictJson.parseToJsonElement(text)
            } catch (_: SerializationException) {
                return StrictCanonicalJsonObjectParsing.Rejected(
                    StrictCanonicalJsonRejection.MalformedJson,
                )
            } catch (_: IllegalArgumentException) {
                return StrictCanonicalJsonObjectParsing.Rejected(
                    StrictCanonicalJsonRejection.MalformedJson,
                )
            }
        val root = element as? JsonObject
            ?: return StrictCanonicalJsonObjectParsing.Rejected(
                StrictCanonicalJsonRejection.RootMustBeObject,
            )
        val canonical = canonicalDocument(root)
            ?: return StrictCanonicalJsonObjectParsing.Rejected(
                StrictCanonicalJsonRejection.NonCanonicalJson,
            )
        if (!bytes.contentEquals(canonical.bytes())) {
            return StrictCanonicalJsonObjectParsing.Rejected(
                StrictCanonicalJsonRejection.NonCanonicalJson,
            )
        }
        return StrictCanonicalJsonObjectParsing.Parsed(root)
    }
}

internal sealed interface StrictCanonicalJsonObjectParsing {
    data class Parsed(val root: JsonObject) : StrictCanonicalJsonObjectParsing

    data class Rejected(val reason: StrictCanonicalJsonRejection) : StrictCanonicalJsonObjectParsing
}

internal sealed interface StrictCanonicalJsonRejection {
    data class DocumentTooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : StrictCanonicalJsonRejection

    data object InvalidUtf8 : StrictCanonicalJsonRejection

    data object MalformedJson : StrictCanonicalJsonRejection

    data object RootMustBeObject : StrictCanonicalJsonRejection

    data object NonCanonicalJson : StrictCanonicalJsonRejection
}

private fun canonicalDocument(root: JsonObject): CanonicalJsonDocument? {
    val canonicalRoot = canonicalValue(root) as? CanonicalJsonObject ?: return null
    return when (val created = CanonicalJsonDocument.create(canonicalRoot)) {
        is CanonicalJsonDocumentCreation.Created -> created.document
        is CanonicalJsonDocumentCreation.Rejected -> null
    }
}

private fun canonicalValue(value: JsonElement): CanonicalJsonValue? =
    when (value) {
        JsonNull -> CanonicalJsonNull
        is JsonObject -> {
            val members =
                value.map { (key, memberValue) ->
                    val canonicalKey = key.toCanonicalString() ?: return null
                    val canonicalMemberValue = canonicalValue(memberValue) ?: return null
                    CanonicalJsonMember(canonicalKey, canonicalMemberValue)
                }
            when (val created = CanonicalJsonObject.create(members)) {
                is CanonicalJsonObjectCreation.Created -> created.value
                is CanonicalJsonObjectCreation.Rejected -> null
            }
        }
        is JsonArray -> {
            val values = value.map { element -> canonicalValue(element) ?: return null }
            CanonicalJsonArray(values)
        }
        is JsonPrimitive ->
            when {
                value.isString -> value.content.toCanonicalString()
                value.booleanOrNull != null -> CanonicalJsonBoolean(checkNotNull(value.booleanOrNull))
                value.longOrNull != null ->
                    when (val created = CanonicalJsonInteger.create(checkNotNull(value.longOrNull))) {
                        is CanonicalJsonIntegerCreation.Created -> created.value
                        is CanonicalJsonIntegerCreation.Rejected -> null
                    }
                else -> null
            }
    }

private fun String.toCanonicalString(): CanonicalJsonString? =
    when (val created = CanonicalJsonString.create(this)) {
        is CanonicalJsonStringCreation.Created -> created.value
        is CanonicalJsonStringCreation.Rejected -> null
    }

private val strictJson = Json {
    isLenient = false
    allowTrailingComma = false
}
