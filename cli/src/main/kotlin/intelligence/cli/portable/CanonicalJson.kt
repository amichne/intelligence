package intelligence.cli.portable

import java.io.ByteArrayOutputStream

internal sealed interface CanonicalJsonValue

internal data object CanonicalJsonNull : CanonicalJsonValue

internal data class CanonicalJsonBoolean(
    val value: Boolean,
) : CanonicalJsonValue

internal class CanonicalJsonString private constructor(
    internal val value: String,
) : CanonicalJsonValue {
    internal fun canonicalToken(): CanonicalJsonStringTokenRendering {
        val writer = BoundedCanonicalJsonWriter(MAX_CANONICAL_JSON_DOCUMENT_BYTES)
        writer.write(this)
        return if (writer.byteCount > MAX_CANONICAL_JSON_DOCUMENT_BYTES) {
            CanonicalJsonStringTokenRendering.Rejected(
                CanonicalJsonDocumentRejection.SizeExceeded(
                    actualBytes = writer.byteCount,
                    maximumBytes = MAX_CANONICAL_JSON_DOCUMENT_BYTES,
                ),
            )
        } else {
            CanonicalJsonStringTokenRendering.Rendered(
                CanonicalJsonStringToken(writer.completedBytes().decodeToString()),
            )
        }
    }

    companion object {
        fun create(candidate: String): CanonicalJsonStringCreation {
            val unpairedSurrogate = candidate.firstUnpairedSurrogateIndex()
            return if (unpairedSurrogate == null) {
                CanonicalJsonStringCreation.Created(CanonicalJsonString(candidate))
            } else {
                CanonicalJsonStringCreation.Rejected(
                    CanonicalJsonStringRejection.UnpairedSurrogate(unpairedSurrogate),
                )
            }
        }
    }
}

@JvmInline
internal value class CanonicalJsonStringToken internal constructor(
    private val text: String,
) {
    fun render(): String = text
}

internal sealed interface CanonicalJsonStringTokenRendering {
    data class Rendered(val token: CanonicalJsonStringToken) : CanonicalJsonStringTokenRendering

    data class Rejected(val reason: CanonicalJsonDocumentRejection.SizeExceeded) : CanonicalJsonStringTokenRendering
}

internal sealed interface CanonicalJsonStringCreation {
    data class Created(val value: CanonicalJsonString) : CanonicalJsonStringCreation

    data class Rejected(val reason: CanonicalJsonStringRejection) : CanonicalJsonStringCreation
}

internal sealed interface CanonicalJsonStringRejection {
    data class UnpairedSurrogate(val index: Int) : CanonicalJsonStringRejection
}

internal class CanonicalJsonInteger private constructor(
    internal val value: Long,
) : CanonicalJsonValue {
    companion object {
        fun create(candidate: Long): CanonicalJsonIntegerCreation =
            if (candidate in MIN_SAFE_JSON_INTEGER..MAX_SAFE_JSON_INTEGER) {
                CanonicalJsonIntegerCreation.Created(CanonicalJsonInteger(candidate))
            } else {
                CanonicalJsonIntegerCreation.Rejected(
                    CanonicalJsonIntegerRejection.OutsideSafeRange(candidate),
                )
            }
    }
}

internal sealed interface CanonicalJsonIntegerCreation {
    data class Created(val value: CanonicalJsonInteger) : CanonicalJsonIntegerCreation

    data class Rejected(val reason: CanonicalJsonIntegerRejection) : CanonicalJsonIntegerCreation
}

internal sealed interface CanonicalJsonIntegerRejection {
    data class OutsideSafeRange(val value: Long) : CanonicalJsonIntegerRejection
}

internal class CanonicalJsonArray(values: List<CanonicalJsonValue>) : CanonicalJsonValue {
    internal val values: List<CanonicalJsonValue> = values.toList()
}

internal data class CanonicalJsonMember(
    val key: CanonicalJsonString,
    val value: CanonicalJsonValue,
)

internal class CanonicalJsonObject private constructor(
    internal val members: List<CanonicalJsonMember>,
) : CanonicalJsonValue {
    companion object {
        fun create(members: List<CanonicalJsonMember>): CanonicalJsonObjectCreation {
            val uniqueKeys = HashSet<String>(members.size)
            members.forEach { member ->
                if (!uniqueKeys.add(member.key.value)) {
                    return CanonicalJsonObjectCreation.Rejected(
                        CanonicalJsonObjectRejection.DuplicateKey(member.key.value),
                    )
                }
            }

            return CanonicalJsonObjectCreation.Created(
                CanonicalJsonObject(
                    members.sortedWith { left, right -> left.key.value.compareTo(right.key.value) },
                ),
            )
        }
    }
}

internal sealed interface CanonicalJsonObjectCreation {
    data class Created(val value: CanonicalJsonObject) : CanonicalJsonObjectCreation

    data class Rejected(val reason: CanonicalJsonObjectRejection) : CanonicalJsonObjectCreation
}

internal sealed interface CanonicalJsonObjectRejection {
    data class DuplicateKey(val key: String) : CanonicalJsonObjectRejection
}

internal class CanonicalJsonDocument private constructor(
    private val content: ByteArray,
) {
    val byteSize: Int = content.size

    fun bytes(): ByteArray = content.copyOf()

    fun sha256(): Sha256Digest = Sha256Digest.compute(content)

    companion object {
        fun create(root: CanonicalJsonObject): CanonicalJsonDocumentCreation {
            val writer = BoundedCanonicalJsonWriter(MAX_CANONICAL_JSON_DOCUMENT_BYTES)
            writer.write(root)
            writer.writeAscii("\n")
            return if (writer.byteCount > MAX_CANONICAL_JSON_DOCUMENT_BYTES) {
                CanonicalJsonDocumentCreation.Rejected(
                    CanonicalJsonDocumentRejection.SizeExceeded(
                        actualBytes = writer.byteCount,
                        maximumBytes = MAX_CANONICAL_JSON_DOCUMENT_BYTES,
                    ),
                )
            } else {
                CanonicalJsonDocumentCreation.Created(
                    CanonicalJsonDocument(writer.completedBytes()),
                )
            }
        }
    }
}

internal sealed interface CanonicalJsonDocumentCreation {
    data class Created(val document: CanonicalJsonDocument) : CanonicalJsonDocumentCreation

    data class Rejected(val reason: CanonicalJsonDocumentRejection) : CanonicalJsonDocumentCreation
}

internal sealed interface CanonicalJsonDocumentRejection {
    data class SizeExceeded(
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : CanonicalJsonDocumentRejection
}

internal fun canonicalJsonString(value: String): CanonicalJsonString =
    when (val created = CanonicalJsonString.create(value)) {
        is CanonicalJsonStringCreation.Created -> created.value
        is CanonicalJsonStringCreation.Rejected -> error("Trusted value is not valid I-JSON")
    }

internal fun canonicalJsonInteger(value: Long): CanonicalJsonInteger =
    when (val created = CanonicalJsonInteger.create(value)) {
        is CanonicalJsonIntegerCreation.Created -> created.value
        is CanonicalJsonIntegerCreation.Rejected -> error("Trusted integer is outside the I-JSON safe range")
    }

internal fun canonicalJsonObject(vararg members: Pair<String, CanonicalJsonValue>): CanonicalJsonObject =
    when (
        val created =
            CanonicalJsonObject.create(
                members.map { (key, value) -> CanonicalJsonMember(canonicalJsonString(key), value) },
            )
    ) {
        is CanonicalJsonObjectCreation.Created -> created.value
        is CanonicalJsonObjectCreation.Rejected -> error("Generated canonical JSON object contains duplicate keys")
    }

private class BoundedCanonicalJsonWriter(
    private val maximumBytes: Int,
) {
    private val output = ByteArrayOutputStream()
    var byteCount: Long = 0
        private set

    fun write(value: CanonicalJsonValue) {
        when (value) {
            CanonicalJsonNull -> writeAscii("null")
            is CanonicalJsonBoolean -> writeAscii(if (value.value) "true" else "false")
            is CanonicalJsonString -> writeString(value.value)
            is CanonicalJsonInteger -> writeAscii(value.value.toString())
            is CanonicalJsonArray -> writeArray(value)
            is CanonicalJsonObject -> writeObject(value)
        }
    }

    fun writeAscii(value: String) {
        value.forEach { character -> writeByte(character.code) }
    }

    fun completedBytes(): ByteArray {
        check(byteCount <= maximumBytes)
        return output.toByteArray()
    }

    private fun writeArray(array: CanonicalJsonArray) {
        writeByte('['.code)
        array.values.forEachIndexed { index, value ->
            if (index > 0) writeByte(','.code)
            write(value)
        }
        writeByte(']'.code)
    }

    private fun writeObject(value: CanonicalJsonObject) {
        writeByte('{'.code)
        value.members.forEachIndexed { index, member ->
            if (index > 0) writeByte(','.code)
            writeString(member.key.value)
            writeByte(':'.code)
            write(member.value)
        }
        writeByte('}'.code)
    }

    private fun writeString(value: String) {
        writeByte('"'.code)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            val escaped =
                when (character) {
                    '"' -> "\\\""
                    '\\' -> "\\\\"
                    '\b' -> "\\b"
                    '\t' -> "\\t"
                    '\n' -> "\\n"
                    '\u000c' -> "\\f"
                    '\r' -> "\\r"
                    else -> null
                }
            when {
                escaped != null -> writeAscii(escaped)
                character.code <= LAST_CONTROL_CHARACTER -> {
                    writeAscii("\\u00")
                    writeByte(HEX_DIGITS[character.code ushr 4].code)
                    writeByte(HEX_DIGITS[character.code and 0x0f].code)
                }
                character.code <= LAST_ASCII_CHARACTER -> writeByte(character.code)
                character.isHighSurrogate() -> {
                    writeUtf8(value.substring(index, index + 2))
                    index += 1
                }
                else -> writeUtf8(character.toString())
            }
            index += 1
        }
        writeByte('"'.code)
    }

    private fun writeUtf8(value: String) {
        value.encodeToByteArray().forEach { byte -> writeByte(byte.toInt() and 0xff) }
    }

    private fun writeByte(value: Int) {
        byteCount += 1
        if (byteCount <= maximumBytes) output.write(value)
    }
}

private fun String.firstUnpairedSurrogateIndex(): Int? {
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return index
                index += 2
            }
            character.isLowSurrogate() -> return index
            else -> index += 1
        }
    }
    return null
}

internal const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L
internal const val MIN_SAFE_JSON_INTEGER = -MAX_SAFE_JSON_INTEGER

private const val MAX_CANONICAL_JSON_DOCUMENT_BYTES = 4 * 1024 * 1024
private const val LAST_CONTROL_CHARACTER = 0x1f
private const val LAST_ASCII_CHARACTER = 0x7f
private const val HEX_DIGITS = "0123456789abcdef"
