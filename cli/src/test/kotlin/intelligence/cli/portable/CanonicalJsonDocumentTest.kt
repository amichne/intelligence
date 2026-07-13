package intelligence.cli.portable

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class CanonicalJsonDocumentTest {
    @Test
    fun `object keys use RFC 8785 UTF-16 order and arrays preserve declared order`() {
        val root =
            objectValue(
                "€" to CanonicalJsonNull,
                "\r" to CanonicalJsonNull,
                "😀" to CanonicalJsonNull,
                "1" to CanonicalJsonNull,
                "ö" to CanonicalJsonNull,
                "\u0080" to CanonicalJsonNull,
                "values" to
                    CanonicalJsonArray(
                        listOf(
                            CanonicalJsonBoolean(true),
                            CanonicalJsonBoolean(false),
                            integer(0),
                        ),
                    ),
            )

        val document = createdDocument(root)

        assertEquals(
            "{\"\\r\":null,\"1\":null,\"values\":[true,false,0],\"\u0080\":null,\"ö\":null,\"€\":null,\"😀\":null}\n",
            document.bytes().decodeToString(),
        )
    }

    @Test
    fun `object bytes are independent of caller member order`() {
        val members =
            (0L..31L).map { value ->
                CanonicalJsonMember(
                    key = stringValue("key-${31L - value}"),
                    value = integer(value),
                )
            }
        val expected = createdDocument(createdObject(members)).bytes()
        val random = Random(7)

        repeat(100) {
            val actual = createdDocument(createdObject(members.shuffled(random))).bytes()
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun `strings use canonical escaping without escaping slash or valid unicode`() {
        val document =
            createdDocument(
                objectValue(
                    "value" to string("\"\\\b\t\n\u000c\r\u0000/€"),
                ),
            )

        assertEquals(
            "{\"value\":\"\\\"\\\\\\b\\t\\n\\f\\r\\u0000/€\"}\n",
            document.bytes().decodeToString(),
        )
    }

    @Test
    fun `canonical bytes have a stable digest and are immutable`() {
        val document =
            createdDocument(
                objectValue(
                    "b" to string("two"),
                    "a" to integer(1),
                ),
            )
        val first = document.bytes()

        assertEquals("{\"a\":1,\"b\":\"two\"}\n", first.decodeToString())
        assertEquals(
            "db4433facf87dc57ee94496d2d38642a6d95103d4f9088db334029c261c3ecd6",
            document.sha256().render(),
        )

        first[0] = 0
        assertNotEquals(0, document.bytes()[0])
        assertContentEquals("{\"a\":1,\"b\":\"two\"}\n".encodeToByteArray(), document.bytes())
    }

    @Test
    fun `array construction snapshots the caller collection`() {
        val callerValues = mutableListOf<CanonicalJsonValue>(CanonicalJsonBoolean(true))
        val array = CanonicalJsonArray(callerValues)
        callerValues.clear()

        val document = createdDocument(objectValue("values" to array))

        assertEquals("{\"values\":[true]}\n", document.bytes().decodeToString())
    }

    @Test
    fun `object construction rejects duplicate keys`() {
        val key = stringValue("duplicate")

        val result =
            CanonicalJsonObject.create(
                listOf(
                    CanonicalJsonMember(key, CanonicalJsonNull),
                    CanonicalJsonMember(key, CanonicalJsonBoolean(true)),
                ),
            )

        val rejection = assertIs<CanonicalJsonObjectCreation.Rejected>(result).reason
        assertEquals(CanonicalJsonObjectRejection.DuplicateKey("duplicate"), rejection)
    }

    @Test
    fun `string construction rejects unpaired UTF-16 surrogates`() {
        val high = CanonicalJsonString.create("before\uD800after")
        val low = CanonicalJsonString.create("before\uDC00after")

        assertEquals(
            CanonicalJsonStringRejection.UnpairedSurrogate(index = 6),
            assertIs<CanonicalJsonStringCreation.Rejected>(high).reason,
        )
        assertEquals(
            CanonicalJsonStringRejection.UnpairedSurrogate(index = 6),
            assertIs<CanonicalJsonStringCreation.Rejected>(low).reason,
        )
    }

    @Test
    fun `integers are limited to the exact I-JSON safe range`() {
        assertEquals(MAX_SAFE_JSON_INTEGER, integer(MAX_SAFE_JSON_INTEGER).value)
        assertEquals(MIN_SAFE_JSON_INTEGER, integer(MIN_SAFE_JSON_INTEGER).value)

        assertEquals(
            CanonicalJsonIntegerRejection.OutsideSafeRange(MAX_SAFE_JSON_INTEGER + 1),
            assertIs<CanonicalJsonIntegerCreation.Rejected>(
                CanonicalJsonInteger.create(MAX_SAFE_JSON_INTEGER + 1),
            ).reason,
        )
        assertEquals(
            CanonicalJsonIntegerRejection.OutsideSafeRange(MIN_SAFE_JSON_INTEGER - 1),
            assertIs<CanonicalJsonIntegerCreation.Rejected>(
                CanonicalJsonInteger.create(MIN_SAFE_JSON_INTEGER - 1),
            ).reason,
        )
    }

    @Test
    fun `document size limit accepts the boundary and rejects one byte beyond it`() {
        val exactContent = "x".repeat(MAX_CANONICAL_JSON_BYTES - OBJECT_STRING_OVERHEAD_BYTES)
        val exact = CanonicalJsonDocument.create(objectValue("value" to string(exactContent)))
        assertEquals(MAX_CANONICAL_JSON_BYTES, assertIs<CanonicalJsonDocumentCreation.Created>(exact).document.byteSize)

        val tooLargeContent = "$exactContent!"
        val tooLarge = CanonicalJsonDocument.create(objectValue("value" to string(tooLargeContent)))

        assertEquals(
            CanonicalJsonDocumentRejection.SizeExceeded(
                actualBytes = MAX_CANONICAL_JSON_BYTES.toLong() + 1,
                maximumBytes = MAX_CANONICAL_JSON_BYTES,
            ),
            assertIs<CanonicalJsonDocumentCreation.Rejected>(tooLarge).reason,
        )
    }

    @Test
    fun `sha256 parser accepts only exact lowercase evidence`() {
        val empty = Sha256Digest.compute(byteArrayOf())
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            empty.render(),
        )
        assertEquals(empty, assertIs<Sha256DigestParsing.Parsed>(Sha256Digest.parse(empty.render())).digest)

        assertEquals(
            Sha256DigestRejection.InvalidLength(actual = 63, expected = 64),
            assertIs<Sha256DigestParsing.Rejected>(Sha256Digest.parse("a".repeat(63))).reason,
        )
        assertEquals(
            Sha256DigestRejection.InvalidCharacter(index = 0, character = 'A'),
            assertIs<Sha256DigestParsing.Rejected>(Sha256Digest.parse("A" + "a".repeat(63))).reason,
        )
        assertEquals(
            Sha256DigestRejection.InvalidCharacter(index = 63, character = 'g'),
            assertIs<Sha256DigestParsing.Rejected>(Sha256Digest.parse("a".repeat(63) + "g")).reason,
        )
    }

    @Test
    fun `computed sha256 evidence always round trips through the strict parser`() {
        val random = Random(11)

        repeat(100) { size ->
            val digest = Sha256Digest.compute(random.nextBytes(size))
            val reparsed = assertIs<Sha256DigestParsing.Parsed>(Sha256Digest.parse(digest.render()))
            assertEquals(digest, reparsed.digest)
        }
    }

    private fun createdDocument(root: CanonicalJsonObject): CanonicalJsonDocument =
        assertIs<CanonicalJsonDocumentCreation.Created>(CanonicalJsonDocument.create(root)).document

    private fun objectValue(vararg members: Pair<String, CanonicalJsonValue>): CanonicalJsonObject =
        createdObject(members.map { (key, value) -> CanonicalJsonMember(stringValue(key), value) })

    private fun createdObject(members: List<CanonicalJsonMember>): CanonicalJsonObject =
        assertIs<CanonicalJsonObjectCreation.Created>(CanonicalJsonObject.create(members)).value

    private fun string(value: String): CanonicalJsonString = stringValue(value)

    private fun stringValue(value: String): CanonicalJsonString =
        assertIs<CanonicalJsonStringCreation.Created>(CanonicalJsonString.create(value)).value

    private fun integer(value: Long): CanonicalJsonInteger =
        assertIs<CanonicalJsonIntegerCreation.Created>(CanonicalJsonInteger.create(value)).value

    private companion object {
        const val MAX_CANONICAL_JSON_BYTES = 4 * 1024 * 1024
        const val OBJECT_STRING_OVERHEAD_BYTES = 13
    }
}
