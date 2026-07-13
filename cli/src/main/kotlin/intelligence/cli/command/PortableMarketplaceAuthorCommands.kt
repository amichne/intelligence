package intelligence.cli.command

import intelligence.cli.io.JsonFiles
import intelligence.cli.portable.AuthoredMarketplace
import intelligence.cli.portable.AuthoredMarketplaceInspection
import intelligence.cli.portable.AuthoredMarketplaceRejection
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubMarketplaceDiscovery
import intelligence.cli.portable.GitHubPublication
import intelligence.cli.portable.GitHubPublicationReceipt
import intelligence.cli.portable.GitHubPublicationRejection
import intelligence.cli.portable.GitHubPublicationTransportRejection
import intelligence.cli.portable.GitHubPublicationVerification
import intelligence.cli.portable.GitHubPublicationVerifier
import intelligence.cli.portable.GitHubPublisher
import intelligence.cli.portable.GitHubReadTransportRejection
import intelligence.cli.portable.GitHubReleaseAsset
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.GitHubSnapshotInspection
import intelligence.cli.portable.GitHubSnapshotResolver
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.MarketplaceRelease
import intelligence.cli.portable.MarketplaceReleaseDirectory
import intelligence.cli.portable.MarketplaceReleaseDirectoryInspection
import intelligence.cli.portable.MarketplaceReleaseDirectoryMaterialization
import intelligence.cli.portable.MarketplaceReleaseDirectoryRejection
import intelligence.cli.portable.MarketplaceReleaseMaterialization
import intelligence.cli.portable.MarketplaceReleaseVerification
import intelligence.cli.portable.ReleaseFile
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.Sha256DigestParsing
import intelligence.cli.portable.SnapshotAssetEvidence
import intelligence.cli.portable.SnapshotId
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun portableMarketplaceAuthorCommands(
    environment: PortableCommandEnvironment,
): List<CliktCommand> =
    listOf(
        DiscoverPortableMarketplaceCommand(environment),
        InspectPortableMarketplaceCommand(environment),
        MaterializePortableMarketplaceCommand(),
        PublishPortableMarketplaceCommand(environment),
        VerifyPortableMarketplacePublicationCommand(environment),
        AuthorPortableMarketplaceCommand(),
    )

private class DiscoverPortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("discover", "marketplace.discover") {
    private val catalogRaw by option("--catalog", help = "Explicit typed marketplace catalog JSON file.")
    private val githubRaw by option("--github", help = "Explicit GitHub OWNER/REPOSITORY to inspect.")
    private val queryRaw by option("--query", help = "Optional deterministic candidate filter.")

    override fun help(context: Context): String =
        "Discover untrusted candidates from one explicit catalog or GitHub repository."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        if ((catalogRaw == null) == (githubRaw == null)) {
            return portableUsage("provide exactly one of --catalog or --github")
        }
        val candidates =
            if (githubRaw != null) {
                if (invocation.offline) return portableExternal("GitHub discovery is unavailable offline")
                val repository = parseGitHubRepository(githubRaw).valueOrFailure { return it }
                when (
                    val discovered =
                        GitHubMarketplaceDiscovery.discover(repository, queryRaw, environment.githubReadTransport)
                ) {
                    is GitHubMarketplaceDiscovery.Discovered ->
                        discovered.candidates.map { candidate ->
                            CatalogCandidate(repository, candidate.snapshotId, candidate.advertisedName)
                        }
                    is GitHubMarketplaceDiscovery.Rejected -> return githubReadFailure(discovered.reason)
                }
            } else {
                val path = parseAbsolutePath(catalogRaw, "--catalog").valueOrFailure { return it }
                parseCatalog(path).valueOrFailure { return it }
                    .filter { candidate -> candidate.matches(queryRaw) }
            }
        return PortableCommandOutcome.Success(
            buildJsonObject {
                putJsonArray("candidates") {
                    candidates.sortedWith(compareBy({ it.repository.render() }, { it.snapshotId.render() }))
                        .forEach { candidate ->
                            add(
                                buildJsonObject {
                                    put("repository", "https://github.com/${candidate.repository.render()}")
                                    put("snapshotId", candidate.snapshotId.render())
                                    put("advertisedName", candidate.advertisedName)
                                    put("trusted", false)
                                },
                            )
                        }
                }
            },
            "marketplace.discover: ${candidates.size} untrusted candidate(s)",
        )
    }
}

private class InspectPortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("inspect", "marketplace.inspect") {
    private val localSnapshotRaw by option("--local-snapshot", help = "Exact local snapshot directory.")
    private val indexSha256Raw by option("--index-sha256", help = "Expected snapshot-index SHA-256.")
    private val githubRaw by option("--github", help = "Exact GitHub OWNER/REPOSITORY source.")
    private val snapshotRaw by option("--snapshot", help = "Exact immutable snapshot ID.")

    override fun help(context: Context): String =
        "Validate and describe one exact local or immutable GitHub snapshot."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val local = listOf(localSnapshotRaw, indexSha256Raw)
        val remote = listOf(githubRaw, snapshotRaw)
        if (local.all { it != null } && remote.all { it == null }) {
            val directory = parseAbsolutePath(localSnapshotRaw, "--local-snapshot").valueOrFailure { return it }
            val expected = parseDigest(indexSha256Raw).valueOrFailure { return it }
            val release =
                when (val inspected = MarketplaceReleaseDirectory.inspect(directory)) {
                    is MarketplaceReleaseDirectoryInspection.Inspected -> inspected.release
                    is MarketplaceReleaseDirectoryInspection.Rejected ->
                        return portableContract("Local snapshot was rejected", inspected.reason)
                }
            if (release.index.sha256() != expected) {
                return portableContract("Local snapshot index digest does not match --index-sha256", expected.render())
            }
            return inspectedRelease(
                release,
                buildJsonObject {
                    put("type", "LOCAL_SNAPSHOT")
                    put("directory", directory.toString())
                    put("indexSha256", expected.render())
                },
            )
        }
        if (remote.all { it != null } && local.all { it == null }) {
            if (invocation.offline) return portableExternal("GitHub inspection is unavailable offline")
            val repository = parseGitHubRepository(githubRaw).valueOrFailure { return it }
            val snapshot = parseSnapshot(snapshotRaw).valueOrFailure { return it }
            return when (val inspected = GitHubSnapshotResolver.inspect(repository, snapshot, environment.githubReadTransport)) {
                is GitHubSnapshotInspection.Inspected ->
                    inspectedIndex(
                        inspected.index,
                        buildJsonObject {
                            put("type", "GITHUB_RELEASE")
                            put("repository", "https://github.com/${repository.render()}")
                            put("snapshotId", snapshot.render())
                            put("releaseId", inspected.release.releaseId.render())
                            put("commit", inspected.release.tagCommitSha.render())
                        },
                    )
                is GitHubSnapshotInspection.Rejected -> portableContract("GitHub snapshot was rejected", inspected.reason)
            }
        }
        return portableUsage(
            "provide exactly one complete source: --local-snapshot with --index-sha256, or --github with --snapshot",
        )
    }
}

private class MaterializePortableMarketplaceCommand :
    PortableConsumerCommand("materialize", "marketplace.materialize") {
    private val sourceRaw by option("--source", help = "Validated canonical release source directory.")
    private val snapshotRaw by option("--snapshot", help = "New exact immutable snapshot ID.")
    private val outputRaw by option("--out", help = "Absent-or-identical release output directory.")

    override fun help(context: Context): String =
        "Materialize a complete deterministic release from one closed provider-neutral source tree."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val source = parseAbsolutePath(sourceRaw, "--source").valueOrFailure { return it }
        val output = parseAbsolutePath(outputRaw, "--out").valueOrFailure { return it }
        val snapshot = parseSnapshot(snapshotRaw).valueOrFailure { return it }
        val input =
            when (val inspected = AuthoredMarketplace.inspect(source)) {
                is AuthoredMarketplaceInspection.Inspected -> inspected.marketplace
                is AuthoredMarketplaceInspection.Rejected ->
                    return authoredSourceFailure(inspected.reason)
            }
        val packages = input.packages
        val expected =
            when (
                val materialized =
                    MarketplaceRelease.materialize(
                        input.marketplaceId,
                        snapshot,
                        input.defaultPackage,
                        packages,
                    )
            ) {
                is MarketplaceReleaseMaterialization.Materialized -> materialized.release
                is MarketplaceReleaseMaterialization.Rejected ->
                    return portableContract("Release materialization was rejected", materialized.reason)
            }
        if (invocation.dryRun) {
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                val existing = MarketplaceReleaseDirectory.inspect(output)
                if (existing !is MarketplaceReleaseDirectoryInspection.Inspected ||
                    expected.verify(existing.release.files()) != MarketplaceReleaseVerification.Verified
                ) {
                    return portableStateConflict("Materialization output already exists with different content")
                }
            } else if (output.parent == null || !Files.isDirectory(output.parent, LinkOption.NOFOLLOW_LINKS)) {
                return portableExternal("Materialization output parent is unavailable")
            }
        } else {
            when (
                val materialized =
                    MarketplaceReleaseDirectory.materialize(
                        output,
                        expected.marketplaceId,
                        expected.snapshotId,
                        expected.defaultPackage,
                        packages,
                    )
            ) {
                is MarketplaceReleaseDirectoryMaterialization.Written,
                is MarketplaceReleaseDirectoryMaterialization.Unchanged,
                -> Unit
                is MarketplaceReleaseDirectoryMaterialization.Rejected ->
                    return materializationFailure(materialized.reason)
            }
        }
        return PortableCommandOutcome.Success(
            releaseResult(expected, output, invocation.dryRun),
            "marketplace.materialize: ${expected.files().size} canonical asset(s)",
        )
    }
}

private class PublishPortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("publish", "marketplace.publish") {
    private val releaseDirectoryRaw by option("--release-dir", help = "Complete canonical release directory.")
    private val githubRaw by option("--github", help = "Exact GitHub OWNER/REPOSITORY destination.")
    private val commitRaw by option("--commit", help = "Exact lowercase 40-character Git commit SHA.")

    override fun help(context: Context): String =
        "Publish one preflighted immutable GitHub snapshot without replacement or retry."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        if (invocation.offline) return portableExternal("Publication is unavailable offline")
        val releaseDirectory = parseAbsolutePath(releaseDirectoryRaw, "--release-dir").valueOrFailure { return it }
        val repository = parseGitHubRepository(githubRaw).valueOrFailure { return it }
        val commit =
            when (val parsed = GitCommitSha.parse(commitRaw.orEmpty())) {
                is GitCommitShaParsing.Parsed -> parsed.sha
                is GitCommitShaParsing.Rejected -> return portableContract("--commit must be a lowercase 40-character SHA", parsed.reason)
            }
        return when (
            val publication =
                GitHubPublisher.publish(
                    releaseDirectory,
                    repository,
                    commit,
                    environment.githubPublicationTransport,
                    invocation.dryRun,
                )
        ) {
            is GitHubPublication.Prepared ->
                PortableCommandOutcome.Success(
                    preparedPublicationResult(publication),
                    "marketplace.publish: dry-run preflight complete",
                )
            is GitHubPublication.Published ->
                PortableCommandOutcome.Success(
                    publicationReceiptResult(publication.receipt, dryRun = false),
                    "marketplace.publish: immutable release verified",
                )
            is GitHubPublication.RemoteStateUnknown ->
                portableFailure(
                    PortableErrorCode.REMOTE_STATE_UNKNOWN,
                    "Remote mutation began but final state is unknown; run marketplace verify-publication",
                    publication,
                )
            is GitHubPublication.PublishedUnverified ->
                portableFailure(
                    PortableErrorCode.PUBLISHED_UNVERIFIED,
                    "Release was published but final immutable evidence could not be verified",
                    publication,
                )
            is GitHubPublication.DraftRetained ->
                portableFailure(
                    PortableErrorCode.REMOTE_STATE_UNKNOWN,
                    "Publication failed and the draft could not be proven removed",
                    publication,
                )
            is GitHubPublication.Rejected -> publicationFailure(publication.reason)
        }
    }
}

private class VerifyPortableMarketplacePublicationCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("verify-publication", "marketplace.verify-publication") {
    private val githubRaw by option("--github", help = "Exact GitHub OWNER/REPOSITORY source.")
    private val snapshotRaw by option("--snapshot", help = "Exact immutable snapshot ID.")

    override fun help(context: Context): String =
        "Re-download and verify every asset of one immutable GitHub snapshot."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        if (invocation.offline) return portableExternal("Remote publication verification is unavailable offline")
        val repository = parseGitHubRepository(githubRaw).valueOrFailure { return it }
        val snapshot = parseSnapshot(snapshotRaw).valueOrFailure { return it }
        return when (
            val verification =
                GitHubPublicationVerifier.verify(repository, snapshot, environment.githubPublicationTransport)
        ) {
            is GitHubPublicationVerification.Verified ->
                PortableCommandOutcome.Success(
                    verifiedPublicationResult(verification),
                    "marketplace.verify-publication: immutable release verified",
                )
            is GitHubPublicationVerification.Rejected ->
                portableFailure(
                    PortableErrorCode.PUBLISHED_UNVERIFIED,
                    "Published release evidence was rejected",
                    verification.reason,
                )
        }
    }
}

private class AuthorPortableMarketplaceCommand :
    PortableConsumerCommand("author", "marketplace.author") {
    override fun help(context: Context): String =
        "Print the existing provider-neutral authoring, validation, and publication contracts."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val records =
            listOf(
                "source" to "docs/reference/portable-package-marketplace-v1.md",
                "validation" to "docs/reference/validation-trust-boundary-v1.md",
                "materialization" to "docs/reference/immutable-snapshot-publication-v1.md",
                "publication" to "docs/reference/immutable-snapshot-publication-v1.md",
            )
        return PortableCommandOutcome.Success(
            buildJsonObject {
                putJsonArray("documents") {
                    records.forEach { (topic, path) ->
                        add(buildJsonObject { put("topic", topic); put("path", path) })
                    }
                }
            },
            records.joinToString("\n") { (topic, path) -> "$topic: $path" },
        )
    }
}

private data class CatalogCandidate(
    val repository: GitHubRepository,
    val snapshotId: SnapshotId,
    val advertisedName: String,
) {
    fun matches(query: String?): Boolean {
        val normalized = query?.trim()?.lowercase().orEmpty()
        return normalized.isEmpty() ||
            repository.render().lowercase().contains(normalized) ||
            snapshotId.render().lowercase().contains(normalized) ||
            advertisedName.lowercase().contains(normalized)
    }
}

private sealed interface PortableBoundary<out T> {
    data class Parsed<T>(val value: T) : PortableBoundary<T>

    data class Failed(val outcome: PortableCommandOutcome.Failure) : PortableBoundary<Nothing>
}

private inline fun <T> PortableBoundary<T>.valueOrFailure(
    failure: (PortableCommandOutcome.Failure) -> Nothing,
): T =
    when (this) {
        is PortableBoundary.Parsed -> value
        is PortableBoundary.Failed -> failure(outcome)
    }

private fun parseGitHubRepository(raw: String?): PortableBoundary<GitHubRepository> =
    when (val parsed = GitHubRepository.parse(raw.orEmpty())) {
        is GitHubRepositoryParsing.Parsed ->
            if (parsed.repository.render() == parsed.repository.render().lowercase()) {
                PortableBoundary.Parsed(parsed.repository)
            } else {
                PortableBoundary.Failed(portableContract("--github must use canonical lowercase coordinates"))
            }
        is GitHubRepositoryParsing.Rejected ->
            PortableBoundary.Failed(portableContract("--github must be OWNER/REPOSITORY", parsed.reason))
    }

private fun parseSnapshot(raw: String?): PortableBoundary<SnapshotId> =
    when (val parsed = SnapshotId.parse(raw.orEmpty())) {
        is IdentifierParse.Accepted ->
            if (parsed.value.render() == "latest") {
                PortableBoundary.Failed(portableContract("--snapshot must not use the moving latest selector"))
            } else {
                PortableBoundary.Parsed(parsed.value)
            }
        is IdentifierParse.Rejected ->
            PortableBoundary.Failed(portableContract("--snapshot must be an immutable snapshot ID", parsed.reason))
    }

private fun parseDigest(raw: String?): PortableBoundary<Sha256Digest> =
    when (val parsed = Sha256Digest.parse(raw.orEmpty())) {
        is Sha256DigestParsing.Parsed -> PortableBoundary.Parsed(parsed.digest)
        is Sha256DigestParsing.Rejected ->
            PortableBoundary.Failed(portableContract("--index-sha256 must be a lowercase SHA-256 digest", parsed.reason))
    }

private fun parseAbsolutePath(
    raw: String?,
    option: String,
): PortableBoundary<Path> {
    if (raw == null) return PortableBoundary.Failed(portableUsage("missing $option"))
    return try {
        PortableBoundary.Parsed(Path.of(raw).toAbsolutePath().normalize())
    } catch (_: InvalidPathException) {
        PortableBoundary.Failed(portableUsage("$option is not a valid filesystem path"))
    }
}

private fun parseCatalog(path: Path): PortableBoundary<List<CatalogCandidate>> {
    val bytes =
        runCatching {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return@runCatching null
            val size = Files.size(path)
            if (size !in 1..MAX_CATALOG_BYTES.toLong()) return@runCatching null
            Files.readAllBytes(path).takeIf { content -> content.size.toLong() == size }
        }.getOrNull()
            ?: return PortableBoundary.Failed(portableExternal("Catalog file is unavailable"))
    val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        ?: return PortableBoundary.Failed(portableContract("Catalog JSON must be valid UTF-8"))
    val root =
        runCatching { JsonFiles.compactJson.parseToJsonElement(text) as? JsonObject }
            .getOrNull()
            ?: return PortableBoundary.Failed(portableContract("Catalog JSON must be one object"))
    if (root.keys != setOf("candidates", "schemaVersion", "type") ||
        root.int("schemaVersion") != 1 ||
        root.string("type") != "INTELLIGENCE_MARKETPLACE_CATALOG"
    ) {
        return PortableBoundary.Failed(portableContract("Catalog header violates the V1 closed contract"))
    }
    val records = root["candidates"] as? JsonArray
        ?: return PortableBoundary.Failed(portableContract("Catalog candidates must be an array"))
    if (records.size > MAX_CATALOG_CANDIDATES) {
        return PortableBoundary.Failed(portableContract("Catalog exceeds the V1 candidate limit"))
    }
    val candidates = mutableListOf<CatalogCandidate>()
    records.forEach { element ->
        val record = element as? JsonObject
            ?: return PortableBoundary.Failed(portableContract("Catalog candidate must be an object"))
        if (record.keys != setOf("advertisedName", "repository", "snapshotId")) {
            return PortableBoundary.Failed(portableContract("Catalog candidate fields violate the V1 closed contract"))
        }
        val repository = parseGitHubRepository(record.string("repository")).valueOrFailure {
            return PortableBoundary.Failed(it)
        }
        val snapshot = parseSnapshot(record.string("snapshotId")).valueOrFailure {
            return PortableBoundary.Failed(it)
        }
        val name = record.string("advertisedName")?.takeIf(String::isNotBlank)
            ?: return PortableBoundary.Failed(portableContract("Catalog advertisedName must not be blank"))
        if (name.codePointCount(0, name.length) > MAX_ADVERTISED_NAME_CHARACTERS || '\u0000' in name) {
            return PortableBoundary.Failed(portableContract("Catalog advertisedName exceeds the V1 boundary"))
        }
        candidates += CatalogCandidate(repository, snapshot, name)
    }
    val ordered = candidates.sortedWith(compareBy({ it.repository.render() }, { it.snapshotId.render() }))
    if (ordered != candidates || ordered.distinctBy { it.repository to it.snapshotId }.size != ordered.size) {
        return PortableBoundary.Failed(portableContract("Catalog candidates must be unique and canonically ordered"))
    }
    return PortableBoundary.Parsed(ordered)
}

private fun inspectedRelease(
    release: MarketplaceRelease,
    source: JsonObject,
): PortableCommandOutcome.Success = inspectedIndex(release.index, source)

private fun inspectedIndex(
    index: intelligence.cli.portable.MarketplaceSnapshotIndex,
    source: JsonObject,
): PortableCommandOutcome.Success =
    PortableCommandOutcome.Success(
        buildJsonObject {
            put("source", source)
            put("marketplaceId", index.marketplaceId.render())
            put("snapshotId", index.snapshotId.render())
            put("indexSha256", index.sha256().render())
            put("defaultPackage", index.defaultPackage.render())
            putJsonArray("packages") {
                index.packages.forEach { record ->
                    add(
                        buildJsonObject {
                            put("name", record.name.render())
                            put("asset", record.archive.name.render())
                            put("size", record.archive.byteSize)
                            put("sha256", record.archive.sha256.render())
                        },
                    )
                }
            }
        },
        "marketplace.inspect: ${index.marketplaceId.render()}@${index.snapshotId.render()}",
    )

private fun releaseResult(
    release: MarketplaceRelease,
    output: Path,
    dryRun: Boolean,
): JsonObject =
    buildJsonObject {
        put("marketplaceId", release.marketplaceId.render())
        put("snapshotId", release.snapshotId.render())
        put("output", output.toString())
        put("dryRun", dryRun)
        putJsonArray("assets") { release.files().forEach { file -> add(file.assetJson()) } }
    }

private fun ReleaseFile.assetJson(): JsonObject =
    buildJsonObject {
        put("name", name.render())
        put("size", byteSize)
        put("sha256", sha256.render())
    }

private fun GitHubReleaseAsset.assetJson(): JsonObject =
    buildJsonObject {
        put("id", assetId.render())
        put("name", name.render())
        put("size", byteSize)
        put("sha256", sha256.render())
        put("contentType", contentType.render())
    }

private fun SnapshotAssetEvidence.assetJson(): JsonObject =
    buildJsonObject {
        put("name", name.render())
        put("size", byteSize)
        put("sha256", sha256.render())
    }

private fun preparedPublicationResult(publication: GitHubPublication.Prepared): JsonObject =
    buildJsonObject {
        put("repository", publication.repository.render())
        put("repositoryId", publication.repositoryId.render())
        put("snapshotId", publication.snapshotId.render())
        put("commit", publication.commit.render())
        put("immutable", false)
        put("dryRun", true)
        putJsonArray("assets") { publication.assets.forEach { asset -> add(asset.assetJson()) } }
        putJsonArray("completedGates") { add("LOCAL_VALIDATE"); add("REMOTE_PREFLIGHT") }
    }

private fun publicationReceiptResult(
    receipt: GitHubPublicationReceipt,
    dryRun: Boolean,
): JsonObject =
    buildJsonObject {
        put("repository", receipt.repository.render())
        put("repositoryId", receipt.repositoryId.render())
        put("releaseId", receipt.releaseId.render())
        put("releaseUrl", receipt.htmlUrl.render())
        put("snapshotId", receipt.snapshotId.render())
        put("commit", receipt.commit.render())
        put("publishedAt", receipt.publishedAt.render())
        put("immutable", receipt.immutable)
        put("dryRun", dryRun)
        putJsonArray("assets") { receipt.assets.forEach { asset -> add(asset.assetJson()) } }
        putJsonArray("completedGates") { receipt.completedGates.forEach(::add) }
    }

private fun verifiedPublicationResult(verification: GitHubPublicationVerification.Verified): JsonObject =
    buildJsonObject {
        val published = verification.release
        put("repository", published.release.repository.render())
        put("repositoryId", published.repositoryId.render())
        put("releaseId", published.release.releaseId.render())
        put("releaseUrl", published.htmlUrl.render())
        put("snapshotId", published.release.snapshotId.render())
        put("commit", published.release.tagCommitSha.render())
        put("publishedAt", published.publishedAt.render())
        put("immutable", published.release.immutable)
        putJsonArray("assets") { published.release.assets.forEach { asset -> add(asset.assetJson()) } }
        putJsonArray("completedGates") { add("IMMUTABLE_VERIFY"); add("REMOTE_REDOWNLOAD") }
    }

private fun publicationFailure(reason: GitHubPublicationRejection): PortableCommandOutcome.Failure =
    when (reason) {
        is GitHubPublicationRejection.PreflightConflict -> publicationTransportFailure(reason.reason)
        is GitHubPublicationRejection.PreflightRejected -> publicationTransportFailure(reason.reason)
        is GitHubPublicationRejection.RemoteRejected -> publicationTransportFailure(reason.reason)
        is GitHubPublicationRejection.LocalReleaseRejected,
        is GitHubPublicationRejection.UploadEvidenceMismatch,
        GitHubPublicationRejection.DraftAssetSetMismatch,
        is GitHubPublicationRejection.DownloadEvidenceMismatch,
        is GitHubPublicationRejection.FinalReleaseRejected,
        -> portableContract("Publication was rejected", reason)
    }

private fun publicationTransportFailure(
    reason: GitHubPublicationTransportRejection,
): PortableCommandOutcome.Failure =
    when (reason) {
        GitHubPublicationTransportRejection.AuthenticationFailed ->
            portableFailure(PortableErrorCode.AUTHENTICATION_FAILED, "GitHub authentication is unavailable", reason)
        GitHubPublicationTransportRejection.RepositoryMismatch,
        GitHubPublicationTransportRejection.CommitUnavailable,
        GitHubPublicationTransportRejection.ImmutableReleasesDisabled,
        GitHubPublicationTransportRejection.TagOrReleaseExists,
        -> portableFailure(
            PortableErrorCode.REMOTE_PRECONDITION_FAILED,
            "Publication precondition failed",
            reason,
        )
        GitHubPublicationTransportRejection.Unavailable,
        GitHubPublicationTransportRejection.InvalidResponse,
        GitHubPublicationTransportRejection.MutationRejected,
        -> portableFailure(PortableErrorCode.EXTERNAL_UNAVAILABLE, "Publication transport is unavailable", reason)
    }

private fun githubReadFailure(reason: GitHubReadTransportRejection): PortableCommandOutcome.Failure =
    portableFailure(
        if (reason == GitHubReadTransportRejection.AuthenticationFailed) {
            PortableErrorCode.AUTHENTICATION_FAILED
        } else {
            PortableErrorCode.EXTERNAL_UNAVAILABLE
        },
        if (reason == GitHubReadTransportRejection.AuthenticationFailed) {
            "GitHub authentication is unavailable"
        } else {
            "GitHub read transport is unavailable"
        },
        reason,
    )

private fun portableUsage(message: String): PortableCommandOutcome.Failure =
    portableFailure(PortableErrorCode.USAGE_ERROR, message)

private fun portableContract(
    message: String,
    reason: Any? = null,
): PortableCommandOutcome.Failure =
    portableFailure(PortableErrorCode.CONTRACT_VIOLATION, message, reason)

private fun portableExternal(message: String): PortableCommandOutcome.Failure =
    portableFailure(PortableErrorCode.EXTERNAL_UNAVAILABLE, message)

private fun portableStateConflict(
    message: String,
    reason: Any? = null,
): PortableCommandOutcome.Failure =
    portableFailure(PortableErrorCode.STATE_CONFLICT, message, reason)

private fun portableFailure(
    code: PortableErrorCode,
    message: String,
    reason: Any? = null,
): PortableCommandOutcome.Failure =
    PortableCommandOutcome.Failure(
        PortableCommandError(
            code,
            message,
            buildJsonObject { reason?.let { put("reason", it::class.simpleName ?: it.toString()) } },
        ),
    )

private fun materializationFailure(
    reason: MarketplaceReleaseDirectoryRejection,
): PortableCommandOutcome.Failure =
    when (reason) {
        is MarketplaceReleaseDirectoryRejection.OutputExists ->
            portableStateConflict("Release output already exists with different content", reason)
        is MarketplaceReleaseDirectoryRejection.BuildRejected,
        is MarketplaceReleaseDirectoryRejection.NonDeterministicBuild,
        is MarketplaceReleaseDirectoryRejection.StagingContentRejected,
        -> portableContract("Release materialization contract was rejected", reason)
        is MarketplaceReleaseDirectoryRejection.ParentUnavailable,
        is MarketplaceReleaseDirectoryRejection.StagingCreationFailed,
        is MarketplaceReleaseDirectoryRejection.StagingWriteFailed,
        is MarketplaceReleaseDirectoryRejection.StagingReadRejected,
        is MarketplaceReleaseDirectoryRejection.AtomicPromotionFailed,
        is MarketplaceReleaseDirectoryRejection.StagingCleanupFailed,
        -> portableExternal("Release output filesystem is unavailable")
    }

private fun authoredSourceFailure(
    reason: AuthoredMarketplaceRejection,
): PortableCommandOutcome.Failure =
    when (reason) {
        is AuthoredMarketplaceRejection.NotDirectory,
        is AuthoredMarketplaceRejection.ReadFailed,
        -> portableExternal("Authored marketplace source is unavailable")
        else -> portableContract("Authored marketplace source was rejected", reason)
    }

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int? =
    (this[name] as? JsonPrimitive)?.intOrNull

private const val MAX_CATALOG_BYTES = 1024 * 1024
private const val MAX_CATALOG_CANDIDATES = 100
private const val MAX_ADVERTISED_NAME_CHARACTERS = 200
