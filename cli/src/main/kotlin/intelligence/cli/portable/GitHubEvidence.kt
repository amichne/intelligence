package intelligence.cli.portable

@JvmInline
internal value class GitHubRepositoryId private constructor(
    private val value: Long,
) {
    fun render(): Long = value

    companion object {
        fun parse(candidate: Long): GitHubRepositoryIdParsing =
            if (candidate in 1..MAX_SAFE_EVIDENCE_ID) {
                GitHubRepositoryIdParsing.Parsed(GitHubRepositoryId(candidate))
            } else {
                GitHubRepositoryIdParsing.Rejected(PositiveEvidenceIdRejection.OutOfRange(candidate))
            }
    }
}

internal sealed interface GitHubRepositoryIdParsing {
    data class Parsed(val id: GitHubRepositoryId) : GitHubRepositoryIdParsing

    data class Rejected(val reason: PositiveEvidenceIdRejection) : GitHubRepositoryIdParsing
}

@JvmInline
internal value class GitHubReleaseId private constructor(
    private val value: Long,
) {
    fun render(): Long = value

    companion object {
        fun parse(candidate: Long): GitHubReleaseIdParsing =
            if (candidate in 1..MAX_SAFE_EVIDENCE_ID) {
                GitHubReleaseIdParsing.Parsed(GitHubReleaseId(candidate))
            } else {
                GitHubReleaseIdParsing.Rejected(PositiveEvidenceIdRejection.OutOfRange(candidate))
            }
    }
}

internal sealed interface GitHubReleaseIdParsing {
    data class Parsed(val id: GitHubReleaseId) : GitHubReleaseIdParsing

    data class Rejected(val reason: PositiveEvidenceIdRejection) : GitHubReleaseIdParsing
}

@JvmInline
internal value class GitHubAssetId private constructor(
    private val value: Long,
) {
    fun render(): Long = value

    companion object {
        fun parse(candidate: Long): GitHubAssetIdParsing =
            if (candidate in 1..MAX_SAFE_EVIDENCE_ID) {
                GitHubAssetIdParsing.Parsed(GitHubAssetId(candidate))
            } else {
                GitHubAssetIdParsing.Rejected(PositiveEvidenceIdRejection.OutOfRange(candidate))
            }
    }
}

internal sealed interface GitHubAssetIdParsing {
    data class Parsed(val id: GitHubAssetId) : GitHubAssetIdParsing

    data class Rejected(val reason: PositiveEvidenceIdRejection) : GitHubAssetIdParsing
}

internal sealed interface PositiveEvidenceIdRejection {
    data class OutOfRange(val actual: Long) : PositiveEvidenceIdRejection
}

@JvmInline
internal value class GitCommitSha private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): GitCommitShaParsing =
            if (lowercaseCommitShaPattern.matches(candidate)) {
                GitCommitShaParsing.Parsed(GitCommitSha(candidate))
            } else {
                GitCommitShaParsing.Rejected(GitCommitShaRejection.InvalidSyntax)
            }
    }
}

internal sealed interface GitCommitShaParsing {
    data class Parsed(val sha: GitCommitSha) : GitCommitShaParsing

    data class Rejected(val reason: GitCommitShaRejection) : GitCommitShaParsing
}

internal enum class GitCommitShaRejection {
    InvalidSyntax,
}

private const val MAX_SAFE_EVIDENCE_ID = 9_007_199_254_740_991L
private val lowercaseCommitShaPattern = Regex("[0-9a-f]{40}")
