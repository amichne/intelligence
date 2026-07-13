package intelligence.cli.portable

internal class PackageSourceFile private constructor(
    val path: PackageEntryPath,
    private val content: ByteArray,
    val executable: Boolean,
) {
    val byteSize: Int
        get() = content.size

    fun bytes(): ByteArray = content.copyOf()

    fun sha256(): Sha256Digest = Sha256Digest.compute(content)

    companion object {
        fun create(
            path: PackageEntryPath,
            content: ByteArray,
            executable: Boolean,
        ): PackageSourceFileCreation =
            if (content.size > MAX_PACKAGE_FILE_BYTES) {
                PackageSourceFileCreation.Rejected(
                    PackageSourceFileRejection.TooLarge(
                        actualBytes = content.size,
                        maximumBytes = MAX_PACKAGE_FILE_BYTES,
                    ),
                )
            } else {
                PackageSourceFileCreation.Created(
                    PackageSourceFile(path, content.copyOf(), executable),
                )
            }
    }
}

internal sealed interface PackageSourceFileCreation {
    data class Created(val file: PackageSourceFile) : PackageSourceFileCreation

    data class Rejected(val reason: PackageSourceFileRejection) : PackageSourceFileCreation
}

internal sealed interface PackageSourceFileRejection {
    data class TooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : PackageSourceFileRejection
}
