package intelligence.cli.portable

import java.nio.charset.CharacterCodingException
import java.util.zip.CRC32

internal object CanonicalZipArchiveParser {
    fun parse(bytes: ByteArray): CanonicalZipParsing {
        if (bytes.size > MAX_ZIP_ARCHIVE_BYTES) {
            return CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.ArchiveTooLarge(bytes.size, MAX_ZIP_ARCHIVE_BYTES),
            )
        }
        if (bytes.size < END_OF_CENTRAL_DIRECTORY_BYTES) {
            return CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.ArchiveTooSmall(bytes.size),
            )
        }

        val endOffset = bytes.size - END_OF_CENTRAL_DIRECTORY_BYTES
        val end = parseEndRecord(bytes, endOffset)
            ?: return CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidEndRecord)
        if (end.entryCount == 0) {
            return CanonicalZipParsing.Rejected(CanonicalZipParseRejection.EmptyArchive)
        }
        if (end.entryCount > MAX_ZIP_ENTRIES) {
            return CanonicalZipParsing.Rejected(
                CanonicalZipParseRejection.TooManyEntries(end.entryCount, MAX_ZIP_ENTRIES),
            )
        }

        val centralRecords = mutableListOf<ParsedCentralEntry>()
        var centralCursor = end.centralDirectoryOffset
        var expandedBytes = 0L
        repeat(end.entryCount) { index ->
            val parsed = parseCentralEntry(bytes, centralCursor, end.centralDirectoryEnd, index)
                ?: return CanonicalZipParsing.Rejected(
                    CanonicalZipParseRejection.InvalidCentralEntry(index),
                )
            when (parsed) {
                is CentralEntryParsing.Parsed -> {
                    centralRecords += parsed.entry
                    centralCursor = parsed.nextOffset
                    expandedBytes += parsed.entry.byteSize.toLong()
                    if (expandedBytes > MAX_ZIP_EXPANDED_BYTES) {
                        return CanonicalZipParsing.Rejected(
                            CanonicalZipParseRejection.ExpandedSizeExceeded(
                                actualBytes = expandedBytes,
                                maximumBytes = MAX_ZIP_EXPANDED_BYTES,
                            ),
                        )
                    }
                }
                is CentralEntryParsing.Rejected -> return CanonicalZipParsing.Rejected(parsed.reason)
            }
        }
        if (centralCursor != end.centralDirectoryEnd) {
            return CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidCentralDirectory)
        }

        val entries = mutableListOf<CanonicalZipEntry>()
        var expectedLocalOffset = 0
        centralRecords.forEachIndexed { index, record ->
            if (record.localHeaderOffset != expectedLocalOffset) {
                return CanonicalZipParsing.Rejected(
                    CanonicalZipParseRejection.InvalidLocalEntry(index),
                )
            }
            when (
                val parsed =
                    parseLocalEntry(
                        bytes = bytes,
                        offset = expectedLocalOffset,
                        centralDirectoryOffset = end.centralDirectoryOffset,
                        index = index,
                        expected = record,
                    )
            ) {
                is LocalEntryParsing.Parsed -> {
                    entries += parsed.entry
                    expectedLocalOffset = parsed.nextOffset
                }
                is LocalEntryParsing.Rejected -> return CanonicalZipParsing.Rejected(parsed.reason)
            }
        }
        if (expectedLocalOffset != end.centralDirectoryOffset) {
            return CanonicalZipParsing.Rejected(CanonicalZipParseRejection.InvalidLocalDirectoryBoundary)
        }

        val archive =
            when (val created = CanonicalZipArchive.create(entries)) {
                is CanonicalZipCreation.Created -> created.archive
                is CanonicalZipCreation.Rejected -> {
                    return CanonicalZipParsing.Rejected(
                        CanonicalZipParseRejection.ArchiveRejected(created.reason),
                    )
                }
            }
        if (!bytes.contentEquals(archive.bytes())) {
            return CanonicalZipParsing.Rejected(CanonicalZipParseRejection.NonCanonicalBytes)
        }
        return CanonicalZipParsing.Parsed(archive, entries)
    }

    private fun parseEndRecord(
        bytes: ByteArray,
        offset: Int,
    ): ParsedEndRecord? {
        if (bytes.readUInt32(offset) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) return null
        if (bytes.readUInt16(offset + 4) != THIS_DISK) return null
        if (bytes.readUInt16(offset + 6) != CENTRAL_DIRECTORY_DISK) return null
        val diskEntries = bytes.readUInt16(offset + 8) ?: return null
        val entryCount = bytes.readUInt16(offset + 10) ?: return null
        if (diskEntries != entryCount) return null
        val centralSize = bytes.readUInt32(offset + 12) ?: return null
        val centralOffset = bytes.readUInt32(offset + 16) ?: return null
        if (bytes.readUInt16(offset + 20) != NO_COMMENT) return null
        if (centralSize > Int.MAX_VALUE || centralOffset > Int.MAX_VALUE) return null
        val centralEnd = centralOffset + centralSize
        if (centralEnd != offset.toLong()) return null
        return ParsedEndRecord(
            entryCount = entryCount,
            centralDirectoryOffset = centralOffset.toInt(),
            centralDirectoryEnd = centralEnd.toInt(),
        )
    }

    private fun parseCentralEntry(
        bytes: ByteArray,
        offset: Int,
        centralEnd: Int,
        index: Int,
    ): CentralEntryParsing? {
        if (!bytes.containsRange(offset, CENTRAL_DIRECTORY_HEADER_BYTES, centralEnd)) return null
        if (bytes.readUInt32(offset) != CENTRAL_DIRECTORY_SIGNATURE) return null
        if (bytes.readUInt16(offset + 4) != VERSION_MADE_BY_UNIX) return null
        if (bytes.readUInt16(offset + 6) != VERSION_NEEDED) return null
        if (bytes.readUInt16(offset + 8) != UTF8_FLAG) return null
        if (bytes.readUInt16(offset + 10) != STORED_METHOD) return null
        if (bytes.readUInt16(offset + 12) != FIXED_DOS_TIME) return null
        if (bytes.readUInt16(offset + 14) != FIXED_DOS_DATE) return null
        val crc32 = bytes.readUInt32(offset + 16) ?: return null
        val compressedSize = bytes.readUInt32(offset + 20) ?: return null
        val expandedSize = bytes.readUInt32(offset + 24) ?: return null
        if (compressedSize != expandedSize || expandedSize > Int.MAX_VALUE) return null
        if (expandedSize > MAX_ZIP_ENTRY_BYTES) {
            return CentralEntryParsing.Rejected(
                CanonicalZipParseRejection.EntryTooLarge(
                    index = index,
                    actualBytes = expandedSize,
                    maximumBytes = MAX_ZIP_ENTRY_BYTES,
                ),
            )
        }
        val nameLength = bytes.readUInt16(offset + 28) ?: return null
        val extraLength = bytes.readUInt16(offset + 30) ?: return null
        val commentLength = bytes.readUInt16(offset + 32) ?: return null
        if (nameLength == 0 || extraLength != NO_EXTRA_FIELDS || commentLength != NO_COMMENT) return null
        if (bytes.readUInt16(offset + 34) != THIS_DISK) return null
        if (bytes.readUInt16(offset + 36) != NO_INTERNAL_ATTRIBUTES) return null
        val externalAttributes = bytes.readUInt32(offset + 38) ?: return null
        if ((externalAttributes and 0xffffL) != 0L) return null
        val unixMode = (externalAttributes ushr 16).toInt()
        val mode = CanonicalZipEntryMode.entries.singleOrNull { candidate -> candidate.unixMode == unixMode }
            ?: return CentralEntryParsing.Rejected(
                CanonicalZipParseRejection.InvalidMode(index, unixMode),
            )
        val localOffset = bytes.readUInt32(offset + 42) ?: return null
        if (localOffset > Int.MAX_VALUE) return null
        val nameOffset = offset + CENTRAL_DIRECTORY_HEADER_BYTES
        if (!bytes.containsRange(nameOffset, nameLength, centralEnd)) return null
        val nameBytes = bytes.copyOfRange(nameOffset, nameOffset + nameLength)
        val rawPath =
            try {
                nameBytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                return CentralEntryParsing.Rejected(CanonicalZipParseRejection.InvalidUtf8Path(index))
            }
        val path =
            when (val parsed = PackageEntryPath.parse(rawPath)) {
                is PackageEntryPathParse.Accepted -> parsed.value
                is PackageEntryPathParse.Rejected -> {
                    return CentralEntryParsing.Rejected(
                        CanonicalZipParseRejection.InvalidPath(index, rawPath, parsed.reason),
                    )
                }
            }
        return CentralEntryParsing.Parsed(
            entry =
                ParsedCentralEntry(
                    path = path,
                    nameBytes = nameBytes,
                    crc32 = crc32,
                    byteSize = expandedSize.toInt(),
                    mode = mode,
                    localHeaderOffset = localOffset.toInt(),
                ),
            nextOffset = nameOffset + nameLength,
        )
    }

    private fun parseLocalEntry(
        bytes: ByteArray,
        offset: Int,
        centralDirectoryOffset: Int,
        index: Int,
        expected: ParsedCentralEntry,
    ): LocalEntryParsing {
        if (!bytes.containsRange(offset, LOCAL_FILE_HEADER_BYTES, centralDirectoryOffset)) {
            return LocalEntryParsing.Rejected(CanonicalZipParseRejection.InvalidLocalEntry(index))
        }
        if (
            bytes.readUInt32(offset) != LOCAL_FILE_HEADER_SIGNATURE ||
            bytes.readUInt16(offset + 4) != VERSION_NEEDED ||
            bytes.readUInt16(offset + 6) != UTF8_FLAG ||
            bytes.readUInt16(offset + 8) != STORED_METHOD ||
            bytes.readUInt16(offset + 10) != FIXED_DOS_TIME ||
            bytes.readUInt16(offset + 12) != FIXED_DOS_DATE ||
            bytes.readUInt32(offset + 14) != expected.crc32 ||
            bytes.readUInt32(offset + 18) != expected.byteSize.toLong() ||
            bytes.readUInt32(offset + 22) != expected.byteSize.toLong() ||
            bytes.readUInt16(offset + 26) != expected.nameBytes.size ||
            bytes.readUInt16(offset + 28) != NO_EXTRA_FIELDS
        ) {
            return LocalEntryParsing.Rejected(CanonicalZipParseRejection.InvalidLocalEntry(index))
        }
        val nameOffset = offset + LOCAL_FILE_HEADER_BYTES
        if (!bytes.containsRange(nameOffset, expected.nameBytes.size, centralDirectoryOffset)) {
            return LocalEntryParsing.Rejected(CanonicalZipParseRejection.InvalidLocalEntry(index))
        }
        val localName = bytes.copyOfRange(nameOffset, nameOffset + expected.nameBytes.size)
        if (!localName.contentEquals(expected.nameBytes)) {
            return LocalEntryParsing.Rejected(CanonicalZipParseRejection.InvalidLocalEntry(index))
        }
        val contentOffset = nameOffset + expected.nameBytes.size
        if (!bytes.containsRange(contentOffset, expected.byteSize, centralDirectoryOffset)) {
            return LocalEntryParsing.Rejected(CanonicalZipParseRejection.InvalidLocalEntry(index))
        }
        val content = bytes.copyOfRange(contentOffset, contentOffset + expected.byteSize)
        val actualCrc = CRC32().apply { update(content) }.value
        if (actualCrc != expected.crc32) {
            return LocalEntryParsing.Rejected(
                CanonicalZipParseRejection.CrcMismatch(
                    path = expected.path,
                    expected = expected.crc32,
                    actual = actualCrc,
                ),
            )
        }
        val entry =
            when (val created = CanonicalZipEntry.create(expected.path, content, expected.mode)) {
                is CanonicalZipEntryCreation.Accepted -> created.entry
                is CanonicalZipEntryCreation.Rejected -> {
                    return LocalEntryParsing.Rejected(
                        CanonicalZipParseRejection.EntryRejected(expected.path, created.reason),
                    )
                }
            }
        return LocalEntryParsing.Parsed(entry, contentOffset + expected.byteSize)
    }
}

internal sealed interface CanonicalZipParsing {
    class Parsed(
        val archive: CanonicalZipArchive,
        entries: List<CanonicalZipEntry>,
    ) : CanonicalZipParsing {
        val entries: List<CanonicalZipEntry> = entries.toList()
    }

    data class Rejected(val reason: CanonicalZipParseRejection) : CanonicalZipParsing
}

internal sealed interface CanonicalZipParseRejection {
    data class ArchiveTooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : CanonicalZipParseRejection

    data class ArchiveTooSmall(val actualBytes: Int) : CanonicalZipParseRejection

    data object InvalidEndRecord : CanonicalZipParseRejection

    data object EmptyArchive : CanonicalZipParseRejection

    data class TooManyEntries(
        val actual: Int,
        val maximum: Int,
    ) : CanonicalZipParseRejection

    data object InvalidCentralDirectory : CanonicalZipParseRejection

    data class InvalidCentralEntry(val index: Int) : CanonicalZipParseRejection

    data class InvalidLocalEntry(val index: Int) : CanonicalZipParseRejection

    data object InvalidLocalDirectoryBoundary : CanonicalZipParseRejection

    data class InvalidUtf8Path(val index: Int) : CanonicalZipParseRejection

    data class InvalidPath(
        val index: Int,
        val rawPath: String,
        val reason: PackageEntryPathRejection,
    ) : CanonicalZipParseRejection

    data class InvalidMode(
        val index: Int,
        val unixMode: Int,
    ) : CanonicalZipParseRejection

    data class EntryTooLarge(
        val index: Int,
        val actualBytes: Long,
        val maximumBytes: Int,
    ) : CanonicalZipParseRejection

    data class ExpandedSizeExceeded(
        val actualBytes: Long,
        val maximumBytes: Long,
    ) : CanonicalZipParseRejection

    data class CrcMismatch(
        val path: PackageEntryPath,
        val expected: Long,
        val actual: Long,
    ) : CanonicalZipParseRejection

    data class EntryRejected(
        val path: PackageEntryPath,
        val reason: CanonicalZipEntryRejection,
    ) : CanonicalZipParseRejection

    data class ArchiveRejected(val reason: CanonicalZipRejection) : CanonicalZipParseRejection

    data object NonCanonicalBytes : CanonicalZipParseRejection
}

private data class ParsedEndRecord(
    val entryCount: Int,
    val centralDirectoryOffset: Int,
    val centralDirectoryEnd: Int,
)

private data class ParsedCentralEntry(
    val path: PackageEntryPath,
    val nameBytes: ByteArray,
    val crc32: Long,
    val byteSize: Int,
    val mode: CanonicalZipEntryMode,
    val localHeaderOffset: Int,
)

private sealed interface CentralEntryParsing {
    data class Parsed(
        val entry: ParsedCentralEntry,
        val nextOffset: Int,
    ) : CentralEntryParsing

    data class Rejected(val reason: CanonicalZipParseRejection) : CentralEntryParsing
}

private sealed interface LocalEntryParsing {
    data class Parsed(
        val entry: CanonicalZipEntry,
        val nextOffset: Int,
    ) : LocalEntryParsing

    data class Rejected(val reason: CanonicalZipParseRejection) : LocalEntryParsing
}

private fun ByteArray.containsRange(
    offset: Int,
    length: Int,
    exclusiveLimit: Int = size,
): Boolean =
    offset >= 0 && length >= 0 && exclusiveLimit in 0..size && offset.toLong() + length <= exclusiveLimit.toLong()

private fun ByteArray.readUInt16(offset: Int): Int? {
    if (!containsRange(offset, 2)) return null
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}

private fun ByteArray.readUInt32(offset: Int): Long? {
    if (!containsRange(offset, 4)) return null
    return (readUInt16(offset)?.toLong() ?: return null) or
        ((readUInt16(offset + 2)?.toLong() ?: return null) shl 16)
}
