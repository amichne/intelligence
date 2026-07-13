package intelligence.cli.portable

@JvmInline
internal value class PackageEntryPath private constructor(private val text: String) {
    fun render(): String = text

    companion object {
        fun parse(raw: String): PackageEntryPathParse {
            if (raw.isEmpty()) return PackageEntryPathParse.Rejected(PackageEntryPathRejection.EMPTY)
            if (raw.isTooLong()) return PackageEntryPathParse.Rejected(PackageEntryPathRejection.TOO_LONG)
            if (raw.startsWith('/')) return PackageEntryPathParse.Rejected(PackageEntryPathRejection.ABSOLUTE)
            if ('\\' in raw) return PackageEntryPathParse.Rejected(PackageEntryPathRejection.BACKSLASH)
            if (raw.endsWith('/') || "//" in raw) {
                return PackageEntryPathParse.Rejected(PackageEntryPathRejection.EMPTY_SEGMENT)
            }

            val segments = raw.split('/')
            if (segments.any { it == "." || it == ".." }) {
                return PackageEntryPathParse.Rejected(PackageEntryPathRejection.DOT_SEGMENT)
            }
            if (segments.any { !packageEntrySegmentPattern.matches(it) }) {
                return PackageEntryPathParse.Rejected(PackageEntryPathRejection.INVALID_SEGMENT)
            }

            return PackageEntryPathParse.Accepted(PackageEntryPath(raw))
        }
    }
}

internal sealed interface PackageEntryPathParse {
    data class Accepted(val value: PackageEntryPath) : PackageEntryPathParse

    data class Rejected(val reason: PackageEntryPathRejection) : PackageEntryPathParse
}

internal enum class PackageEntryPathRejection {
    EMPTY,
    TOO_LONG,
    ABSOLUTE,
    BACKSLASH,
    EMPTY_SEGMENT,
    DOT_SEGMENT,
    INVALID_SEGMENT,
}

private const val MAX_PACKAGE_ENTRY_PATH_BYTES = 240
private val packageEntrySegmentPattern = Regex("[A-Za-z0-9._-]+")

private fun String.isTooLong(): Boolean =
    length > MAX_PACKAGE_ENTRY_PATH_BYTES || toByteArray(Charsets.UTF_8).size > MAX_PACKAGE_ENTRY_PATH_BYTES
