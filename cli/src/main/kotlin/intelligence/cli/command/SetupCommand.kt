package intelligence.cli.command

import intelligence.cli.BuildInfo
import intelligence.cli.portable.ConsumerRelativeDirectory
import intelligence.cli.portable.ConsumerRelativeDirectoryParsing
import intelligence.cli.portable.DigestCacheWritePolicy
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.GitHubRepositoryUrl
import intelligence.cli.portable.GitHubRepositoryUrlParsing
import intelligence.cli.portable.GitHubSnapshotInspection
import intelligence.cli.portable.GitHubSnapshotResolver
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.LocalConsumerOperations
import intelligence.cli.portable.MarketplaceIntentSource
import intelligence.cli.portable.MarketplaceReleaseDirectory
import intelligence.cli.portable.MarketplaceReleaseDirectoryInspection
import intelligence.cli.portable.PackageName
import intelligence.cli.portable.PackageSelectionRequest
import intelligence.cli.portable.PackageSelectionRequestCreation
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.Sha256DigestParsing
import intelligence.cli.portable.SnapshotId
import java.nio.file.InvalidPathException
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.put

internal class SetupCommand(
    private val environment: PortableCommandEnvironment,
    private val packagedDefault: PackagedMarketplaceDefault? = PackagedMarketplaceDefault.fromBuildInfo(),
) : PortableConsumerCommand("setup", "setup") {
    private val localSnapshotRaw by option("--local-snapshot", help = "Repository-relative exact local snapshot directory.")
    private val indexSha256Raw by option("--index-sha256", help = "Expected snapshot-index SHA-256.")
    private val githubRaw by option("--github", help = "Exact GitHub OWNER/REPOSITORY source.")
    private val snapshotRaw by option("--snapshot", help = "Exact immutable snapshot ID.")
    private val packageNamesRaw by option("--package", help = "Whole package name to select.").multiple()
    private val all by option("--all", help = "Select every package in the exact snapshot.").flag(default = false)

    override fun help(context: Context): String =
        "Create first-time exact package selection and lock evidence atomically."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val source = source(invocation).valueOrFailure { return it }
        val inspection = inspect(invocation, source).valueOrFailure { return it }
        if (source.expectedIndexSha256 != null && source.expectedIndexSha256 != inspection.indexSha256) {
            return setupContract("Snapshot index does not match the packaged or explicit digest")
        }
        val request = selectionRequest(inspection.defaultPackage).valueOrFailure { return it }
        val cache =
            when (val opened = environment.cache()) {
                is PortableCacheOpening.Opened -> opened.cache
                is PortableCacheOpening.Rejected -> return setupExternal("Digest cache root is unavailable")
            }
        val planning =
            LocalConsumerOperations.planSelect(
                invocation.repository,
                inspection.marketplaceId,
                source.intentSource,
                request,
                cache,
                if (invocation.dryRun) DigestCacheWritePolicy.VERIFY_ONLY else DigestCacheWritePolicy.STORE,
                if (invocation.offline) null else environment.githubReadTransport,
            )
        return completeMutation(invocation, planning)
    }

    private fun source(invocation: PortableInvocation): SetupBoundary<SetupSource> {
        val local = listOf(localSnapshotRaw, indexSha256Raw)
        val remote = listOf(githubRaw, snapshotRaw)
        if (local.all { it == null } && remote.all { it == null }) {
            val metadata = packagedDefault
                ?: return SetupBoundary.Failed(
                    setupExternal(
                        "This CLI build has no packaged default snapshot; provide an exact --github or --local-snapshot source",
                    ),
                )
            if (invocation.offline) {
                return SetupBoundary.Failed(setupExternal("Packaged default setup requires network access"))
            }
            return SetupBoundary.Parsed(
                SetupSource(
                    MarketplaceIntentSource.GitHubRelease(metadata.repositoryUrl, metadata.snapshotId),
                    metadata.indexSha256,
                    metadata.repository,
                    metadata.snapshotId,
                ),
            )
        }
        if (local.all { it != null } && remote.all { it == null }) {
            val directory =
                when (val parsed = ConsumerRelativeDirectory.parse(checkNotNull(localSnapshotRaw))) {
                    is ConsumerRelativeDirectoryParsing.Parsed -> parsed.directory
                    is ConsumerRelativeDirectoryParsing.Rejected ->
                        return SetupBoundary.Failed(
                            setupContract("--local-snapshot must be repository-relative", parsed.reason),
                        )
                }
            val digest =
                when (val parsed = Sha256Digest.parse(checkNotNull(indexSha256Raw))) {
                    is Sha256DigestParsing.Parsed -> parsed.digest
                    is Sha256DigestParsing.Rejected ->
                        return SetupBoundary.Failed(
                            setupContract("--index-sha256 must be a lowercase SHA-256 digest", parsed.reason),
                        )
                }
            return SetupBoundary.Parsed(
                SetupSource(MarketplaceIntentSource.LocalSnapshot(directory, digest), digest, null, null),
            )
        }
        if (remote.all { it != null } && local.all { it == null }) {
            if (invocation.offline) return SetupBoundary.Failed(setupExternal("Remote setup is unavailable offline"))
            val repository =
                when (val parsed = GitHubRepository.parse(checkNotNull(githubRaw))) {
                    is GitHubRepositoryParsing.Parsed -> parsed.repository
                    is GitHubRepositoryParsing.Rejected ->
                        return SetupBoundary.Failed(setupContract("--github must be OWNER/REPOSITORY", parsed.reason))
                }
            val repositoryUrl =
                when (val parsed = GitHubRepositoryUrl.parse("https://github.com/${repository.render()}")) {
                    is GitHubRepositoryUrlParsing.Parsed -> parsed.url
                    is GitHubRepositoryUrlParsing.Rejected ->
                        return SetupBoundary.Failed(setupContract("--github must use canonical lowercase coordinates", parsed.reason))
                }
            val snapshot =
                when (val parsed = SnapshotId.parse(checkNotNull(snapshotRaw))) {
                    is IdentifierParse.Accepted -> parsed.value
                    is IdentifierParse.Rejected ->
                        return SetupBoundary.Failed(setupContract("--snapshot must be an immutable snapshot ID", parsed.reason))
                }
            if (snapshot.render() == "latest") {
                return SetupBoundary.Failed(setupContract("--snapshot must not use the moving latest selector"))
            }
            return SetupBoundary.Parsed(
                SetupSource(
                    MarketplaceIntentSource.GitHubRelease(repositoryUrl, snapshot),
                    null,
                    repository,
                    snapshot,
                ),
            )
        }
        return SetupBoundary.Failed(
            setupUsage(
                "provide one complete source: --local-snapshot with --index-sha256, or --github with --snapshot",
            ),
        )
    }

    private fun inspect(
        invocation: PortableInvocation,
        source: SetupSource,
    ): SetupBoundary<SetupInspection> =
        when (val intentSource = source.intentSource) {
            is MarketplaceIntentSource.LocalSnapshot -> {
                val directory =
                    try {
                        invocation.repository.resolve(intentSource.directory.render()).normalize()
                    } catch (_: InvalidPathException) {
                        return SetupBoundary.Failed(setupContract("Local snapshot path is invalid"))
                    }
                when (val inspected = MarketplaceReleaseDirectory.inspect(directory)) {
                    is MarketplaceReleaseDirectoryInspection.Inspected ->
                        SetupBoundary.Parsed(
                            SetupInspection(
                                inspected.release.marketplaceId,
                                inspected.release.defaultPackage,
                                inspected.release.index.sha256(),
                            ),
                        )
                    is MarketplaceReleaseDirectoryInspection.Rejected ->
                        SetupBoundary.Failed(setupContract("Local snapshot was rejected", inspected.reason))
                }
            }
            is MarketplaceIntentSource.GitHubRelease -> {
                val repository = checkNotNull(source.repository)
                val snapshot = checkNotNull(source.snapshotId)
                when (val inspected = GitHubSnapshotResolver.inspect(repository, snapshot, environment.githubReadTransport)) {
                    is GitHubSnapshotInspection.Inspected ->
                        SetupBoundary.Parsed(
                            SetupInspection(
                                inspected.index.marketplaceId,
                                inspected.index.defaultPackage,
                                inspected.index.sha256(),
                            ),
                        )
                    is GitHubSnapshotInspection.Rejected ->
                        SetupBoundary.Failed(setupContract("GitHub snapshot was rejected", inspected.reason))
                }
            }
        }

    private fun selectionRequest(defaultPackage: PackageName): SetupBoundary<PackageSelectionRequest> {
        if (all && packageNamesRaw.isNotEmpty()) {
            return SetupBoundary.Failed(setupUsage("--all and --package are mutually exclusive"))
        }
        if (all) return SetupBoundary.Parsed(PackageSelectionRequest.All)
        val packages =
            if (packageNamesRaw.isEmpty()) {
                listOf(defaultPackage)
            } else {
                val parsed = mutableListOf<PackageName>()
                packageNamesRaw.forEach { raw ->
                    when (val name = PackageName.parse(raw)) {
                        is IdentifierParse.Accepted -> parsed += name.value
                        is IdentifierParse.Rejected ->
                            return SetupBoundary.Failed(setupContract("Invalid --package name", name.reason))
                    }
                }
                parsed
            }
        return when (val created = PackageSelectionRequest.explicit(packages)) {
            is PackageSelectionRequestCreation.Created -> SetupBoundary.Parsed(created.request)
            is PackageSelectionRequestCreation.Rejected ->
                SetupBoundary.Failed(setupContract("Package selection was rejected", created.reason))
        }
    }
}

internal data class PackagedMarketplaceDefault(
    val repository: GitHubRepository,
    val repositoryUrl: GitHubRepositoryUrl,
    val snapshotId: SnapshotId,
    val indexSha256: Sha256Digest,
) {
    companion object {
        fun fromBuildInfo(): PackagedMarketplaceDefault? {
            val repository =
                when (val parsed = GitHubRepository.parse(BuildInfo.DEFAULT_MARKETPLACE_GITHUB)) {
                    is GitHubRepositoryParsing.Parsed -> parsed.repository
                    is GitHubRepositoryParsing.Rejected -> return null
                }
            val url =
                when (val parsed = GitHubRepositoryUrl.parse("https://github.com/${repository.render()}")) {
                    is GitHubRepositoryUrlParsing.Parsed -> parsed.url
                    is GitHubRepositoryUrlParsing.Rejected -> return null
                }
            val snapshot =
                when (val parsed = SnapshotId.parse(BuildInfo.DEFAULT_MARKETPLACE_SNAPSHOT)) {
                    is IdentifierParse.Accepted -> parsed.value
                    is IdentifierParse.Rejected -> return null
                }
            if (snapshot.render() == "latest") return null
            val digest =
                when (val parsed = Sha256Digest.parse(BuildInfo.DEFAULT_MARKETPLACE_INDEX_SHA256)) {
                    is Sha256DigestParsing.Parsed -> parsed.digest
                    is Sha256DigestParsing.Rejected -> return null
                }
            return PackagedMarketplaceDefault(repository, url, snapshot, digest)
        }
    }
}

private data class SetupSource(
    val intentSource: MarketplaceIntentSource,
    val expectedIndexSha256: Sha256Digest?,
    val repository: GitHubRepository?,
    val snapshotId: SnapshotId?,
)

private data class SetupInspection(
    val marketplaceId: intelligence.cli.portable.MarketplaceId,
    val defaultPackage: PackageName,
    val indexSha256: Sha256Digest,
)

private sealed interface SetupBoundary<out T> {
    data class Parsed<T>(val value: T) : SetupBoundary<T>

    data class Failed(val outcome: PortableCommandOutcome.Failure) : SetupBoundary<Nothing>
}

private inline fun <T> SetupBoundary<T>.valueOrFailure(
    failure: (PortableCommandOutcome.Failure) -> Nothing,
): T =
    when (this) {
        is SetupBoundary.Parsed -> value
        is SetupBoundary.Failed -> failure(outcome)
    }

private fun setupUsage(message: String): PortableCommandOutcome.Failure =
    setupFailure(PortableErrorCode.USAGE_ERROR, message)

private fun setupContract(
    message: String,
    reason: Any? = null,
): PortableCommandOutcome.Failure =
    setupFailure(PortableErrorCode.CONTRACT_VIOLATION, message, reason)

private fun setupExternal(message: String): PortableCommandOutcome.Failure =
    setupFailure(PortableErrorCode.EXTERNAL_UNAVAILABLE, message)

private fun setupFailure(
    code: PortableErrorCode,
    message: String,
    reason: Any? = null,
): PortableCommandOutcome.Failure =
    PortableCommandOutcome.Failure(
        PortableCommandError(
            code,
            message,
            kotlinx.serialization.json.buildJsonObject {
                reason?.let { put("reason", it::class.simpleName ?: it.toString()) }
            },
        ),
    )
