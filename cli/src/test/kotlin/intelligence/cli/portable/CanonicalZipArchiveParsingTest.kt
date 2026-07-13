package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CanonicalZipArchiveParsingTest {
    @Test
    fun `canonical archive reparses with paths content and modes intact`() {
        val original =
            archive(
                listOf(
                    entry("skills/review/SKILL.md", "instructions", CanonicalZipEntryMode.REGULAR),
                    entry("skills/review/scripts/check.sh", "#!/bin/sh", CanonicalZipEntryMode.EXECUTABLE),
                ),
            )

        val parsed = assertIs<CanonicalZipParsing.Parsed>(CanonicalZipArchive.parse(original.bytes()))

        assertContentEquals(original.bytes(), parsed.archive.bytes())
        assertEquals(
            listOf("skills/review/SKILL.md", "skills/review/scripts/check.sh"),
            parsed.entries.map { it.path.render() },
        )
        assertEquals(
            listOf(CanonicalZipEntryMode.REGULAR, CanonicalZipEntryMode.EXECUTABLE),
            parsed.entries.map(CanonicalZipEntry::mode),
        )
        assertEquals(listOf("instructions", "#!/bin/sh"), parsed.entries.map { it.contentCopy().decodeToString() })
    }

    @Test
    fun `parser rejects missing and inconsistent end records`() {
        assertEquals(
            CanonicalZipParsing.Rejected(CanonicalZipParseRejection.ArchiveTooSmall(actualBytes = 3)),
            CanonicalZipArchive.parse(byteArrayOf(1, 2, 3)),
        )

        val bytes = archive(listOf(entry("a.txt", "a", CanonicalZipEntryMode.REGULAR))).bytes()
        val trailing = bytes + byteArrayOf(0)
        assertEquals(
            CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidEndRecord),
            CanonicalZipArchive.parse(trailing),
        )

        val tooMany = bytes.copyOf()
        val end = tooMany.size - END_RECORD_BYTES
        tooMany.writeUInt16(end + 8, 4_097)
        tooMany.writeUInt16(end + 10, 4_097)
        assertEquals(
            CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.TooManyEntries(actual = 4_097, maximum = 4_096),
            ),
            CanonicalZipArchive.parse(tooMany),
        )
    }

    @Test
    fun `parser rejects non-canonical central metadata and file modes`() {
        val original = archive(listOf(entry("a.txt", "a", CanonicalZipEntryMode.REGULAR))).bytes()
        val central = original.centralDirectoryOffset()

        val compressed = original.copyOf()
        compressed.writeUInt16(central + 10, 8)
        assertEquals(
            CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidCentralEntry(index = 0)),
            CanonicalZipArchive.parse(compressed),
        )

        val invalidMode = original.copyOf()
        invalidMode.writeUInt32(central + 38, 0)
        assertEquals(
            CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.InvalidMode(index = 0, unixMode = 0),
            ),
            CanonicalZipArchive.parse(invalidMode),
        )
    }

    @Test
    fun `parser rejects unsafe and invalid UTF-8 entry paths`() {
        val unsafe = archive(listOf(entry("safe.txt", "a", CanonicalZipEntryMode.REGULAR))).bytes()
        val central = unsafe.centralDirectoryOffset()
        "../a.txt".encodeToByteArray().copyInto(unsafe, destinationOffset = LOCAL_HEADER_BYTES)
        "../a.txt".encodeToByteArray().copyInto(unsafe, destinationOffset = central + CENTRAL_HEADER_BYTES)
        assertEquals(
            CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.InvalidPath(
                    index = 0,
                    rawPath = "../a.txt",
                    reason = PackageEntryPathRejection.DOT_SEGMENT,
                ),
            ),
            CanonicalZipArchive.parse(unsafe),
        )

        val invalidUtf8 = archive(listOf(entry("a.txt", "a", CanonicalZipEntryMode.REGULAR))).bytes()
        val invalidCentral = invalidUtf8.centralDirectoryOffset()
        invalidUtf8[LOCAL_HEADER_BYTES] = 0xff.toByte()
        invalidUtf8[invalidCentral + CENTRAL_HEADER_BYTES] = 0xff.toByte()
        assertEquals(
            CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidUtf8Path(index = 0)),
            CanonicalZipArchive.parse(invalidUtf8),
        )
    }

    @Test
    fun `parser verifies stored content CRC`() {
        val bytes = archive(listOf(entry("a.txt", "a", CanonicalZipEntryMode.REGULAR))).bytes()
        val contentOffset = LOCAL_HEADER_BYTES + "a.txt".length
        bytes[contentOffset] = 'b'.code.toByte()
        val path = path("a.txt")

        assertEquals(
            CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.CrcMismatch(
                    path = path,
                    expected = 3_904_355_907L,
                    actual = 1_908_338_681L,
                ),
            ),
            CanonicalZipArchive.parse(bytes),
        )
    }

    @Test
    fun `parsed entry and archive bytes remain immutable copies`() {
        val parsed = assertIs<CanonicalZipParsing.Parsed>(
            CanonicalZipArchive.parse(
                archive(listOf(entry("a.txt", "original", CanonicalZipEntryMode.REGULAR))).bytes(),
            ),
        )
        val entryBytes = parsed.entries.single().contentCopy()
        val archiveBytes = parsed.archive.bytes()
        entryBytes.fill(0)
        archiveBytes.fill(0)

        assertEquals("original", parsed.entries.single().contentCopy().decodeToString())
        assertIs<CanonicalZipParsing.Parsed>(CanonicalZipArchive.parse(parsed.archive.bytes()))
    }

    private fun entry(
        rawPath: String,
        content: String,
        mode: CanonicalZipEntryMode,
    ): CanonicalZipEntry =
        assertIs<CanonicalZipEntryCreation.Accepted>(
            CanonicalZipEntry.create(path(rawPath), content.encodeToByteArray(), mode),
        ).entry

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun archive(entries: List<CanonicalZipEntry>): CanonicalZipArchive =
        assertIs<CanonicalZipCreation.Created>(CanonicalZipArchive.create(entries)).archive
}

private fun ByteArray.centralDirectoryOffset(): Int {
    val end = size - END_RECORD_BYTES
    return readUInt32(end + 16).toInt()
}

private fun ByteArray.readUInt16(offset: Int): Int =
    (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.readUInt32(offset: Int): Long =
    readUInt16(offset).toLong() or (readUInt16(offset + 2).toLong() shl 16)

private fun ByteArray.writeUInt16(
    offset: Int,
    value: Int,
) {
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
}

private fun ByteArray.writeUInt32(
    offset: Int,
    value: Long,
) {
    repeat(4) { byteIndex ->
        this[offset + byteIndex] = ((value ushr (byteIndex * 8)) and 0xff).toByte()
    }
}

private const val LOCAL_HEADER_BYTES = 30
private const val CENTRAL_HEADER_BYTES = 46
private const val END_RECORD_BYTES = 22
