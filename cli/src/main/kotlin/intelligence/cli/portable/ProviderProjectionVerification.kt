package intelligence.cli.portable

internal sealed interface ProviderProjectionVerification {
    data object Verified : ProviderProjectionVerification

    data class Rejected(val reason: ProviderProjectionRejection) : ProviderProjectionVerification
}

internal sealed interface ProviderProjectionRejection {
    data class DuplicateFile(val path: PackageEntryPath) : ProviderProjectionRejection

    data class MissingFile(val path: PackageEntryPath) : ProviderProjectionRejection

    data class UnexpectedFile(val path: PackageEntryPath) : ProviderProjectionRejection

    data class ModeMismatch(
        val path: PackageEntryPath,
        val expected: CanonicalZipEntryMode,
        val actual: CanonicalZipEntryMode,
    ) : ProviderProjectionRejection

    data class SizeMismatch(
        val path: PackageEntryPath,
        val expectedBytes: Int,
        val actualBytes: Int,
    ) : ProviderProjectionRejection

    data class DigestMismatch(
        val path: PackageEntryPath,
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : ProviderProjectionRejection

    data class ContentMismatch(val path: PackageEntryPath) : ProviderProjectionRejection
}

internal fun verifyProviderProjection(
    expectedFiles: List<ProjectedFile>,
    actualFiles: List<ProjectedFile>,
): ProviderProjectionVerification {
    val actualByPath = linkedMapOf<PackageEntryPath, ProjectedFile>()
    actualFiles.forEach { file ->
        if (actualByPath.putIfAbsent(file.path, file) != null) {
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.DuplicateFile(file.path),
            )
        }
    }
    val expectedByPath = expectedFiles.associateBy(ProjectedFile::path)
    expectedFiles.firstOrNull { file -> file.path !in actualByPath }?.let { missing ->
        return ProviderProjectionVerification.Rejected(
            ProviderProjectionRejection.MissingFile(missing.path),
        )
    }
    actualByPath.keys.sortedBy(PackageEntryPath::render).firstOrNull { path -> path !in expectedByPath }
        ?.let { unexpected ->
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.UnexpectedFile(unexpected),
            )
        }
    expectedFiles.forEach { expected ->
        val actual = checkNotNull(actualByPath[expected.path])
        if (actual.mode != expected.mode) {
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.ModeMismatch(expected.path, expected.mode, actual.mode),
            )
        }
        if (actual.byteSize != expected.byteSize) {
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.SizeMismatch(
                    expected.path,
                    expected.byteSize,
                    actual.byteSize,
                ),
            )
        }
        if (actual.sha256 != expected.sha256) {
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.DigestMismatch(
                    expected.path,
                    expected.sha256,
                    actual.sha256,
                ),
            )
        }
        if (!actual.bytes().contentEquals(expected.bytes())) {
            return ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.ContentMismatch(expected.path),
            )
        }
    }
    return ProviderProjectionVerification.Verified
}
