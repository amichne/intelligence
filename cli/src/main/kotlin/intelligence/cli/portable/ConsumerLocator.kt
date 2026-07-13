package intelligence.cli.portable

@JvmInline
internal value class GitHubRepositoryUrl private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): GitHubRepositoryUrlParsing =
            when {
                candidate.length > MAX_GITHUB_REPOSITORY_URL_CHARACTERS ->
                    GitHubRepositoryUrlParsing.Rejected(GitHubRepositoryUrlRejection.TooLong)
                !canonicalGitHubRepositoryPattern.matches(candidate) || candidate.endsWith(".git") ->
                    GitHubRepositoryUrlParsing.Rejected(GitHubRepositoryUrlRejection.NonCanonical)
                else -> GitHubRepositoryUrlParsing.Parsed(GitHubRepositoryUrl(candidate))
            }
    }
}

internal sealed interface GitHubRepositoryUrlParsing {
    data class Parsed(val url: GitHubRepositoryUrl) : GitHubRepositoryUrlParsing

    data class Rejected(val reason: GitHubRepositoryUrlRejection) : GitHubRepositoryUrlParsing
}

internal enum class GitHubRepositoryUrlRejection {
    TooLong,
    NonCanonical,
}

@JvmInline
internal value class ConsumerRelativeDirectory private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): ConsumerRelativeDirectoryParsing {
            if (candidate.isEmpty()) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.Empty)
            }
            if (candidate.length > MAX_CONSUMER_RELATIVE_PATH_BYTES ||
                candidate.encodeToByteArray().size > MAX_CONSUMER_RELATIVE_PATH_BYTES
            ) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.TooLong)
            }
            if (candidate.startsWith('/')) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.Absolute)
            }
            if ('\\' in candidate) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.Backslash)
            }
            if (candidate.endsWith('/') || "//" in candidate) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.EmptySegment)
            }
            val segments = candidate.split('/')
            if (segments.any { segment -> segment == "." || segment == ".." }) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.DotSegment)
            }
            if (segments.any { segment -> !consumerPathSegmentPattern.matches(segment) }) {
                return ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.InvalidSegment)
            }
            return ConsumerRelativeDirectoryParsing.Parsed(ConsumerRelativeDirectory(candidate))
        }
    }
}

internal sealed interface ConsumerRelativeDirectoryParsing {
    data class Parsed(val directory: ConsumerRelativeDirectory) : ConsumerRelativeDirectoryParsing

    data class Rejected(val reason: ConsumerRelativeDirectoryRejection) : ConsumerRelativeDirectoryParsing
}

internal enum class ConsumerRelativeDirectoryRejection {
    Empty,
    TooLong,
    Absolute,
    Backslash,
    EmptySegment,
    DotSegment,
    InvalidSegment,
}

private const val MAX_GITHUB_REPOSITORY_URL_CHARACTERS = 180
private const val MAX_CONSUMER_RELATIVE_PATH_BYTES = 240
private val canonicalGitHubRepositoryPattern =
    Regex("https://github\\.com/[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?/[a-z0-9](?:[a-z0-9._-]{0,98}[a-z0-9])?")
private val consumerPathSegmentPattern = Regex("[A-Za-z0-9._-]+")
