package intelligence.cli.portable

internal class ProjectedFile private constructor(
    val path: PackageEntryPath,
    private val content: ByteArray,
    val mode: CanonicalZipEntryMode,
) {
    val byteSize: Int
        get() = content.size

    val sha256: Sha256Digest = Sha256Digest.compute(content)

    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun create(
            path: PackageEntryPath,
            content: ByteArray,
            mode: CanonicalZipEntryMode,
        ): ProjectedFileCreation =
            if (content.size > MAX_PROJECTED_FILE_BYTES) {
                ProjectedFileCreation.Rejected(
                    ProjectedFileRejection.TooLarge(
                        actualBytes = content.size,
                        maximumBytes = MAX_PROJECTED_FILE_BYTES,
                    ),
                )
            } else {
                ProjectedFileCreation.Created(ProjectedFile(path, content.copyOf(), mode))
            }
    }
}

internal sealed interface ProjectedFileCreation {
    data class Created(val file: ProjectedFile) : ProjectedFileCreation

    data class Rejected(val reason: ProjectedFileRejection) : ProjectedFileCreation
}

internal sealed interface ProjectedFileRejection {
    data class TooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : ProjectedFileRejection
}

internal const val MAX_PROJECTED_FILE_BYTES = 16 * 1024 * 1024
