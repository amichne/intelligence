package intelligence.cli.portable

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class CanonicalZipArchiveTest {
    @Test
    fun `archive order and metadata are canonical`() {
        val entries =
            listOf(
                entry("z.sh", "z", CanonicalZipEntryMode.EXECUTABLE),
                entry("a.txt", "a", CanonicalZipEntryMode.REGULAR),
            )

        val forward = createdArchive(entries)
        val reversed = createdArchive(entries.reversed())

        assertContentEquals(forward.bytes(), reversed.bytes())
        assertEquals(
            listOf("a.txt" to "a", "z.sh" to "z"),
            readEntries(forward.bytes()),
        )

        val centralEntries = readCentralEntries(forward.bytes())
        assertEquals(listOf("a.txt", "z.sh"), centralEntries.map(CentralEntry::name))
        assertEquals(listOf(33_188, 33_261), centralEntries.map(CentralEntry::unixMode))
        centralEntries.forEach { entry ->
            assertEquals(3, entry.originSystem)
            assertEquals(UTF8_FLAG, entry.flags)
            assertEquals(ZipEntry.STORED, entry.method)
            assertEquals(0, entry.rawDosTime)
            assertEquals(0x21, entry.rawDosDate)
            assertEquals(0, entry.extraLength)
            assertEquals(0, entry.commentLength)
        }
        assertEquals(
            "c744a8a5ec717d1c973ad6d50be74e985ad0b03d22fc61443ad400424266e948",
            forward.bytes().sha256(),
        )
    }

    @Test
    fun `entry and archive bytes are immutable copies`() {
        val source = "original".encodeToByteArray()
        val entry = acceptedEntry(path("payload.txt"), source, CanonicalZipEntryMode.REGULAR)
        source.fill(0)

        val archive = createdArchive(listOf(entry))
        val firstRead = archive.bytes()
        firstRead.fill(0)

        assertEquals(listOf("payload.txt" to "original"), readEntries(archive.bytes()))
        assertNotEquals(0, archive.bytes().first().toInt())
    }

    @Test
    fun `empty archive is rejected`() {
        assertEquals(
            CanonicalZipCreation.Rejected(CanonicalZipRejection.EmptyArchive),
            CanonicalZipArchive.create(emptyList()),
        )
    }

    @Test
    fun `duplicate path is rejected`() {
        val duplicate = path("skills/review/SKILL.md")

        assertEquals(
            CanonicalZipCreation.Rejected(CanonicalZipRejection.DuplicatePath(duplicate)),
            CanonicalZipArchive.create(
                listOf(
                    acceptedEntry(duplicate, byteArrayOf(1), CanonicalZipEntryMode.REGULAR),
                    acceptedEntry(duplicate, byteArrayOf(2), CanonicalZipEntryMode.REGULAR),
                ),
            ),
        )
    }

    @Test
    fun `ASCII case collision is rejected`() {
        val first = path("skills/Review/SKILL.md")
        val intervening = path("skills/Review0/SKILL.md")
        val second = path("skills/review/SKILL.md")

        assertEquals(
            CanonicalZipCreation.Rejected(CanonicalZipRejection.AsciiCaseCollision(first, second)),
            CanonicalZipArchive.create(
                listOf(
                    acceptedEntry(first, byteArrayOf(1), CanonicalZipEntryMode.REGULAR),
                    acceptedEntry(intervening, byteArrayOf(2), CanonicalZipEntryMode.REGULAR),
                    acceptedEntry(second, byteArrayOf(3), CanonicalZipEntryMode.REGULAR),
                ),
            ),
        )
    }

    @Test
    fun `entry size limit accepts exact maximum and rejects overflow`() {
        val exactMaximum = ByteArray(16 * 1024 * 1024)
        val overflow = ByteArray(exactMaximum.size + 1)
        val path = path("payload.bin")

        assertIs<CanonicalZipEntryCreation.Accepted>(
            CanonicalZipEntry.create(path, exactMaximum, CanonicalZipEntryMode.REGULAR),
        )
        assertEquals(
            CanonicalZipEntryCreation.Rejected(
                CanonicalZipEntryRejection.TooLarge(overflow.size, exactMaximum.size),
            ),
            CanonicalZipEntry.create(path, overflow, CanonicalZipEntryMode.REGULAR),
        )
    }

    @Test
    fun `entry count limit accepts exact maximum and rejects overflow`() {
        val entries =
            (0 until 4_097).map { index ->
                entry("entry-${index.toString().padStart(4, '0')}.txt", "", CanonicalZipEntryMode.REGULAR)
            }

        assertIs<CanonicalZipCreation.Created>(CanonicalZipArchive.create(entries.dropLast(1)))
        assertEquals(
            CanonicalZipCreation.Rejected(CanonicalZipRejection.TooManyEntries(4_097, 4_096)),
            CanonicalZipArchive.create(entries),
        )
    }

    private fun entry(
        rawPath: String,
        content: String,
        mode: CanonicalZipEntryMode,
    ): CanonicalZipEntry = acceptedEntry(path(rawPath), content.encodeToByteArray(), mode)

    private fun acceptedEntry(
        path: PackageEntryPath,
        content: ByteArray,
        mode: CanonicalZipEntryMode,
    ): CanonicalZipEntry =
        assertIs<CanonicalZipEntryCreation.Accepted>(CanonicalZipEntry.create(path, content, mode)).entry

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun createdArchive(entries: List<CanonicalZipEntry>): CanonicalZipArchive =
        assertIs<CanonicalZipCreation.Created>(CanonicalZipArchive.create(entries)).archive

    private fun readEntries(bytes: ByteArray): List<Pair<String, String>> =
        buildList {
            ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    add(entry.name to archive.readBytes().decodeToString())
                    entry = archive.nextEntry
                }
            }
        }

    private fun readCentralEntries(bytes: ByteArray): List<CentralEntry> {
        val endOffset = bytes.findSignatureFromEnd(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        val entryCount = bytes.readUInt16(endOffset + 10)
        var offset = bytes.readUInt32(endOffset + 16)

        return buildList(entryCount) {
            repeat(entryCount) {
                assertEquals(CENTRAL_DIRECTORY_SIGNATURE, bytes.readUInt32(offset))
                val nameLength = bytes.readUInt16(offset + 28)
                val extraLength = bytes.readUInt16(offset + 30)
                val commentLength = bytes.readUInt16(offset + 32)
                val nameStart = offset + 46
                val name = bytes.copyOfRange(nameStart, nameStart + nameLength).decodeToString()
                val externalAttributes = bytes.readUInt32(offset + 38)
                add(
                    CentralEntry(
                        name = name,
                        originSystem = bytes.readUInt16(offset + 4) ushr 8,
                        flags = bytes.readUInt16(offset + 8),
                        method = bytes.readUInt16(offset + 10),
                        rawDosTime = bytes.readUInt16(offset + 12),
                        rawDosDate = bytes.readUInt16(offset + 14),
                        extraLength = extraLength,
                        commentLength = commentLength,
                        unixMode = externalAttributes ushr 16,
                    ),
                )
                offset = nameStart + nameLength + extraLength + commentLength
            }
        }
    }
}


private data class CentralEntry(
    val name: String,
    val originSystem: Int,
    val flags: Int,
    val method: Int,
    val rawDosTime: Int,
    val rawDosDate: Int,
    val extraLength: Int,
    val commentLength: Int,
    val unixMode: Int,
)

private fun ByteArray.findSignatureFromEnd(signature: Int): Int =
    (size - 4 downTo 0).first { offset -> readUInt32(offset) == signature }

private fun ByteArray.readUInt16(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.readUInt32(offset: Int): Int =
    readUInt16(offset) or (readUInt16(offset + 2) shl 16)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val UTF8_FLAG = 0x0800
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
