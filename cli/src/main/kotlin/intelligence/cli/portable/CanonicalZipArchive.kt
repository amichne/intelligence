package intelligence.cli.portable

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

internal enum class CanonicalZipEntryMode(
    internal val unixMode: Int,
) {
    REGULAR(33_188),
    EXECUTABLE(33_261),
}

internal class CanonicalZipEntry private constructor(
    val path: PackageEntryPath,
    private val content: ByteArray,
    val mode: CanonicalZipEntryMode,
) {
    internal val byteSize: Int
        get() = content.size

    internal fun contentCopy(): ByteArray = content.copyOf()

    companion object {
        fun create(
            path: PackageEntryPath,
            content: ByteArray,
            mode: CanonicalZipEntryMode,
        ): CanonicalZipEntryCreation =
            if (content.size > MAX_ZIP_ENTRY_BYTES) {
                CanonicalZipEntryCreation.Rejected(
                    CanonicalZipEntryRejection.TooLarge(
                        actualBytes = content.size,
                        maximumBytes = MAX_ZIP_ENTRY_BYTES,
                    ),
                )
            } else {
                CanonicalZipEntryCreation.Accepted(CanonicalZipEntry(path, content.copyOf(), mode))
            }
    }
}

internal sealed interface CanonicalZipEntryCreation {
    data class Accepted(val entry: CanonicalZipEntry) : CanonicalZipEntryCreation

    data class Rejected(val reason: CanonicalZipEntryRejection) : CanonicalZipEntryCreation
}

internal sealed interface CanonicalZipEntryRejection {
    data class TooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : CanonicalZipEntryRejection
}

internal class CanonicalZipArchive private constructor(
    private val content: ByteArray,
) {
    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun create(entries: List<CanonicalZipEntry>): CanonicalZipCreation {
            if (entries.isEmpty()) {
                return CanonicalZipCreation.Rejected(CanonicalZipRejection.EmptyArchive)
            }
            if (entries.size > MAX_ZIP_ENTRIES) {
                return CanonicalZipCreation.Rejected(
                    CanonicalZipRejection.TooManyEntries(
                        actualEntries = entries.size,
                        maximumEntries = MAX_ZIP_ENTRIES,
                    ),
                )
            }

            val expandedBytes = entries.sumOf { it.byteSize.toLong() }
            if (expandedBytes > MAX_ZIP_EXPANDED_BYTES) {
                return CanonicalZipCreation.Rejected(
                    CanonicalZipRejection.ExpandedSizeExceeded(
                        actualBytes = expandedBytes,
                        maximumBytes = MAX_ZIP_EXPANDED_BYTES,
                    ),
                )
            }

            val ordered = entries.sortedWith(canonicalEntryComparator)
            val exactPaths = mutableSetOf<PackageEntryPath>()
            val pathsByAsciiLowercase = mutableMapOf<String, PackageEntryPath>()
            ordered.forEach { entry ->
                val current = entry.path
                if (!exactPaths.add(current)) {
                    return CanonicalZipCreation.Rejected(CanonicalZipRejection.DuplicatePath(current))
                }

                val folded = current.render().asciiLowercase()
                val previous = pathsByAsciiLowercase.putIfAbsent(folded, current)
                if (previous != null) {
                    return CanonicalZipCreation.Rejected(
                        CanonicalZipRejection.AsciiCaseCollision(previous, current),
                    )
                }
            }

            return CanonicalZipCreation.Created(CanonicalZipArchive(CanonicalZipWriter.write(ordered)))
        }
    }
}

internal sealed interface CanonicalZipCreation {
    data class Created(val archive: CanonicalZipArchive) : CanonicalZipCreation

    data class Rejected(val reason: CanonicalZipRejection) : CanonicalZipCreation
}

internal sealed interface CanonicalZipRejection {
    data object EmptyArchive : CanonicalZipRejection

    data class TooManyEntries(
        val actualEntries: Int,
        val maximumEntries: Int,
    ) : CanonicalZipRejection

    data class ExpandedSizeExceeded(
        val actualBytes: Long,
        val maximumBytes: Long,
    ) : CanonicalZipRejection

    data class DuplicatePath(val path: PackageEntryPath) : CanonicalZipRejection

    data class AsciiCaseCollision(
        val first: PackageEntryPath,
        val second: PackageEntryPath,
    ) : CanonicalZipRejection
}

private object CanonicalZipWriter {
    fun write(entries: List<CanonicalZipEntry>): ByteArray {
        val output = ByteArrayOutputStream()
        val records =
            entries.map { entry ->
                val name = entry.path.render().encodeToByteArray()
                val content = entry.contentCopy()
                val crc32 = CRC32().apply { update(content) }.value
                val localHeaderOffset = output.size()

                output.writeUInt32(LOCAL_FILE_HEADER_SIGNATURE)
                output.writeUInt16(VERSION_NEEDED)
                output.writeUInt16(UTF8_FLAG)
                output.writeUInt16(STORED_METHOD)
                output.writeUInt16(FIXED_DOS_TIME)
                output.writeUInt16(FIXED_DOS_DATE)
                output.writeUInt32(crc32)
                output.writeUInt32(content.size.toLong())
                output.writeUInt32(content.size.toLong())
                output.writeUInt16(name.size)
                output.writeUInt16(NO_EXTRA_FIELDS)
                output.write(name)
                output.write(content)

                CentralDirectoryRecord(
                    name = name,
                    crc32 = crc32,
                    size = content.size,
                    unixMode = entry.mode.unixMode,
                    localHeaderOffset = localHeaderOffset,
                )
            }

        val centralDirectoryOffset = output.size()
        records.forEach { record -> output.writeCentralDirectoryRecord(record) }
        val centralDirectorySize = output.size() - centralDirectoryOffset

        output.writeUInt32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        output.writeUInt16(THIS_DISK)
        output.writeUInt16(CENTRAL_DIRECTORY_DISK)
        output.writeUInt16(records.size)
        output.writeUInt16(records.size)
        output.writeUInt32(centralDirectorySize.toLong())
        output.writeUInt32(centralDirectoryOffset.toLong())
        output.writeUInt16(NO_COMMENT)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeCentralDirectoryRecord(record: CentralDirectoryRecord) {
        writeUInt32(CENTRAL_DIRECTORY_SIGNATURE)
        writeUInt16(VERSION_MADE_BY_UNIX)
        writeUInt16(VERSION_NEEDED)
        writeUInt16(UTF8_FLAG)
        writeUInt16(STORED_METHOD)
        writeUInt16(FIXED_DOS_TIME)
        writeUInt16(FIXED_DOS_DATE)
        writeUInt32(record.crc32)
        writeUInt32(record.size.toLong())
        writeUInt32(record.size.toLong())
        writeUInt16(record.name.size)
        writeUInt16(NO_EXTRA_FIELDS)
        writeUInt16(NO_COMMENT)
        writeUInt16(THIS_DISK)
        writeUInt16(NO_INTERNAL_ATTRIBUTES)
        writeUInt32(record.unixMode.toLong() shl 16)
        writeUInt32(record.localHeaderOffset.toLong())
        write(record.name)
    }
}

private data class CentralDirectoryRecord(
    val name: ByteArray,
    val crc32: Long,
    val size: Int,
    val unixMode: Int,
    val localHeaderOffset: Int,
)

private val canonicalEntryComparator =
    Comparator<CanonicalZipEntry> { left, right ->
        compareUtf8(left.path.render().encodeToByteArray(), right.path.render().encodeToByteArray())
    }

private fun compareUtf8(left: ByteArray, right: ByteArray): Int {
    val sharedSize = minOf(left.size, right.size)
    for (index in 0 until sharedSize) {
        val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

private fun String.asciiLowercase(): String =
    buildString(length) {
        this@asciiLowercase.forEach { character ->
            append(if (character in 'A'..'Z') character + ('a' - 'A') else character)
        }
    }

private fun ByteArrayOutputStream.writeUInt16(value: Int) {
    check(value in 0..0xffff) { "ZIP UInt16 value out of range: $value" }
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun ByteArrayOutputStream.writeUInt32(value: Long) {
    check(value in 0..0xffff_ffffL) { "ZIP UInt32 value out of range: $value" }
    repeat(4) { byteIndex ->
        write(((value ushr (byteIndex * 8)) and 0xff).toInt())
    }
}

private const val MAX_ZIP_ENTRY_BYTES = 16 * 1024 * 1024
private const val MAX_ZIP_ENTRIES = 4_096
private const val MAX_ZIP_EXPANDED_BYTES = 128L * 1024 * 1024

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
private const val VERSION_NEEDED = 10
private const val VERSION_MADE_BY_UNIX = 0x0314
private const val UTF8_FLAG = 0x0800
private const val STORED_METHOD = 0
private const val FIXED_DOS_TIME = 0
private const val FIXED_DOS_DATE = 0x21
private const val NO_EXTRA_FIELDS = 0
private const val NO_COMMENT = 0
private const val THIS_DISK = 0
private const val CENTRAL_DIRECTORY_DISK = 0
private const val NO_INTERNAL_ATTRIBUTES = 0
