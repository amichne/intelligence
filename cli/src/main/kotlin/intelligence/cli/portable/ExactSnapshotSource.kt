package intelligence.cli.portable

@JvmInline
internal value class GitHubRepository private constructor(
    private val coordinate: String,
) {
    fun render(): String = coordinate

    companion object {
        fun parse(candidate: String): GitHubRepositoryParsing {
            val parts = candidate.split('/')
            if (parts.size != 2) {
                return GitHubRepositoryParsing.Rejected(GitHubRepositoryRejection.INVALID_SHAPE)
            }
            val owner = parts[0]
            val repository = parts[1]
            if (owner.length !in 1..MAX_GITHUB_OWNER_LENGTH || !githubOwnerPattern.matches(owner)) {
                return GitHubRepositoryParsing.Rejected(GitHubRepositoryRejection.INVALID_OWNER)
            }
            if (
                repository.length !in 1..MAX_GITHUB_REPOSITORY_LENGTH ||
                repository == "." ||
                repository == ".." ||
                !githubRepositoryPattern.matches(repository)
            ) {
                return GitHubRepositoryParsing.Rejected(GitHubRepositoryRejection.INVALID_REPOSITORY)
            }
            return GitHubRepositoryParsing.Parsed(GitHubRepository(candidate))
        }
    }
}

internal sealed interface GitHubRepositoryParsing {
    data class Parsed(val repository: GitHubRepository) : GitHubRepositoryParsing

    data class Rejected(val reason: GitHubRepositoryRejection) : GitHubRepositoryParsing
}

internal enum class GitHubRepositoryRejection {
    INVALID_SHAPE,
    INVALID_OWNER,
    INVALID_REPOSITORY,
}

@JvmInline
internal value class RepositoryRelativeDirectory private constructor(
    private val path: PackageEntryPath,
) {
    fun render(): String = path.render()

    companion object {
        fun parse(candidate: String): RepositoryRelativeDirectoryParsing =
            when (val parsed = PackageEntryPath.parse(candidate)) {
                is PackageEntryPathParse.Accepted ->
                    RepositoryRelativeDirectoryParsing.Parsed(RepositoryRelativeDirectory(parsed.value))
                is PackageEntryPathParse.Rejected -> RepositoryRelativeDirectoryParsing.Rejected(parsed.reason)
            }
    }
}

internal sealed interface RepositoryRelativeDirectoryParsing {
    data class Parsed(val directory: RepositoryRelativeDirectory) : RepositoryRelativeDirectoryParsing

    data class Rejected(val reason: PackageEntryPathRejection) : RepositoryRelativeDirectoryParsing
}

internal sealed interface ExactSnapshotSource {
    data class GitHubRelease(
        val repository: GitHubRepository,
        val snapshotId: SnapshotId,
    ) : ExactSnapshotSource

    data class LocalSnapshot(
        val directory: RepositoryRelativeDirectory,
        val indexSha256: Sha256Digest,
    ) : ExactSnapshotSource

    companion object {
        fun parseGitHub(
            repository: String,
            snapshotId: String,
        ): ExactSnapshotSourceParsing {
            val parsedRepository =
                when (val parsed = GitHubRepository.parse(repository)) {
                    is GitHubRepositoryParsing.Parsed -> parsed.repository
                    is GitHubRepositoryParsing.Rejected -> {
                        return ExactSnapshotSourceParsing.Rejected(
                            ExactSnapshotSourceRejection.InvalidRepository(parsed.reason),
                        )
                    }
                }
            val parsedSnapshot =
                when (val parsed = SnapshotId.parse(snapshotId)) {
                    is IdentifierParse.Accepted -> parsed.value
                    is IdentifierParse.Rejected -> {
                        return ExactSnapshotSourceParsing.Rejected(
                            ExactSnapshotSourceRejection.InvalidSnapshotId(parsed.reason),
                        )
                    }
                }
            if (parsedSnapshot.render() == MOVING_LATEST_SELECTOR) {
                return ExactSnapshotSourceParsing.Rejected(
                    ExactSnapshotSourceRejection.MovingSelectorNotAllowed(parsedSnapshot.render()),
                )
            }
            return ExactSnapshotSourceParsing.Parsed(GitHubRelease(parsedRepository, parsedSnapshot))
        }

        fun parseLocal(
            directory: String,
            indexSha256: String,
        ): ExactSnapshotSourceParsing {
            val parsedDirectory =
                when (val parsed = RepositoryRelativeDirectory.parse(directory)) {
                    is RepositoryRelativeDirectoryParsing.Parsed -> parsed.directory
                    is RepositoryRelativeDirectoryParsing.Rejected -> {
                        return ExactSnapshotSourceParsing.Rejected(
                            ExactSnapshotSourceRejection.InvalidLocalDirectory(parsed.reason),
                        )
                    }
                }
            val parsedDigest =
                when (val parsed = Sha256Digest.parse(indexSha256)) {
                    is Sha256DigestParsing.Parsed -> parsed.digest
                    is Sha256DigestParsing.Rejected -> {
                        return ExactSnapshotSourceParsing.Rejected(
                            ExactSnapshotSourceRejection.InvalidIndexDigest(parsed.reason),
                        )
                    }
                }
            return ExactSnapshotSourceParsing.Parsed(LocalSnapshot(parsedDirectory, parsedDigest))
        }
    }
}

internal sealed interface ExactSnapshotSourceParsing {
    data class Parsed(val source: ExactSnapshotSource) : ExactSnapshotSourceParsing

    data class Rejected(val reason: ExactSnapshotSourceRejection) : ExactSnapshotSourceParsing
}

internal sealed interface ExactSnapshotSourceRejection {
    data class InvalidRepository(val reason: GitHubRepositoryRejection) : ExactSnapshotSourceRejection

    data class InvalidSnapshotId(val reason: IdentifierRejection) : ExactSnapshotSourceRejection

    data class MovingSelectorNotAllowed(val selector: String) : ExactSnapshotSourceRejection

    data class InvalidLocalDirectory(val reason: PackageEntryPathRejection) : ExactSnapshotSourceRejection

    data class InvalidIndexDigest(val reason: Sha256DigestRejection) : ExactSnapshotSourceRejection
}

private const val MAX_GITHUB_OWNER_LENGTH = 39
private const val MAX_GITHUB_REPOSITORY_LENGTH = 100
private const val MOVING_LATEST_SELECTOR = "latest"
private val githubOwnerPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")
private val githubRepositoryPattern = Regex("[A-Za-z0-9._-]+")
