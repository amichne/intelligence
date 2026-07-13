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
        if (JsonDuplicateKeyScanner.scan(text) == JsonDuplicateKeyScanning.DuplicateFound) {
            return StrictCanonicalJsonObjectParsing.Rejected(
                StrictCanonicalJsonRejection.MalformedJson,
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

private enum class JsonDuplicateKeyScanning {
    NoDuplicate,
    DuplicateFound,
    Malformed,
}

private class JsonDuplicateKeyScanner private constructor(
    private val text: String,
) {
    private var index: Int = 0
    private var duplicateFound: Boolean = false

    fun scan(): JsonDuplicateKeyScanning {
        skipWhitespace()
        if (!parseValue(depth = 0)) return JsonDuplicateKeyScanning.Malformed
        skipWhitespace()
        if (index != text.length) return JsonDuplicateKeyScanning.Malformed
        return if (duplicateFound) {
            JsonDuplicateKeyScanning.DuplicateFound
        } else {
            JsonDuplicateKeyScanning.NoDuplicate
        }
    }

    private fun parseValue(depth: Int): Boolean {
        if (depth > MAX_JSON_NESTING_DEPTH || index >= text.length) return false
        return when (text[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '"' -> parseString() != null
            't' -> consumeLiteral("true")
            'f' -> consumeLiteral("false")
            'n' -> consumeLiteral("null")
            '-', in '0'..'9' -> parseNumber()
            else -> false
        }
    }

    private fun parseObject(depth: Int): Boolean {
        index++
        skipWhitespace()
        if (consumeIf('}')) return true
        val keys = mutableSetOf<String>()
        while (index < text.length) {
            val key = parseString() ?: return false
            if (!keys.add(key)) duplicateFound = true
            skipWhitespace()
            if (!consumeIf(':')) return false
            skipWhitespace()
            if (!parseValue(depth)) return false
            skipWhitespace()
            when {
                consumeIf('}') -> return true
                consumeIf(',') -> skipWhitespace()
                else -> return false
            }
        }
        return false
    }

    private fun parseArray(depth: Int): Boolean {
        index++
        skipWhitespace()
        if (consumeIf(']')) return true
        while (index < text.length) {
            if (!parseValue(depth)) return false
            skipWhitespace()
            when {
                consumeIf(']') -> return true
                consumeIf(',') -> skipWhitespace()
                else -> return false
            }
        }
        return false
    }

    private fun parseString(): String? {
        if (!consumeIf('"')) return null
        val value = StringBuilder()
        while (index < text.length) {
            val character = text[index++]
            when {
                character == '"' -> return value.toString()
                character == '\\' -> {
                    if (index >= text.length) return null
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> value.append(escaped)
                        'b' -> value.append('\b')
                        'f' -> value.append('\u000c')
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        'u' -> {
                            if (index + 4 > text.length) return null
                            val code = text.substring(index, index + 4).toIntOrNull(16) ?: return null
                            value.append(code.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                }
                character.code < 0x20 -> return null
                else -> value.append(character)
            }
        }
        return null
    }

    private fun parseNumber(): Boolean {
        val start = index
        if (consumeIf('-') && index >= text.length) return false
        if (consumeIf('0')) {
            if (index < text.length && text[index] in '0'..'9') return false
        } else {
            if (!consumeDigits()) return false
        }
        if (consumeIf('.')) {
            if (!consumeDigits()) return false
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            if (!consumeDigits()) return false
        }
        return index > start
    }

    private fun consumeDigits(): Boolean {
        val start = index
        while (index < text.length && text[index] in '0'..'9') index++
        return index > start
    }

    private fun consumeLiteral(literal: String): Boolean {
        if (!text.startsWith(literal, index)) return false
        index += literal.length
        return true
    }

    private fun consumeIf(expected: Char): Boolean {
        if (index >= text.length || text[index] != expected) return false
        index++
        return true
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in jsonWhitespace) index++
    }

    companion object {
        fun scan(text: String): JsonDuplicateKeyScanning = JsonDuplicateKeyScanner(text).scan()
    }
}

private const val MAX_JSON_NESTING_DEPTH = 256
private val jsonWhitespace = setOf(' ', '\t', '\n', '\r')
