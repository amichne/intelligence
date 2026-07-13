package intelligence.cli.github

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubAssetContentType
import intelligence.cli.portable.GitHubAssetDownload
import intelligence.cli.portable.GitHubDraftCleanup
import intelligence.cli.portable.GitHubDraftRelease
import intelligence.cli.portable.GitHubExactRelease
import intelligence.cli.portable.GitHubMutationComplete
import intelligence.cli.portable.GitHubPublicationLookup
import intelligence.cli.portable.GitHubPublicationPreflight
import intelligence.cli.portable.GitHubPublicationRead
import intelligence.cli.portable.GitHubPublicationRequest
import intelligence.cli.portable.GitHubPublicationTransport
import intelligence.cli.portable.GitHubPublicationTransportRejection
import intelligence.cli.portable.GitHubPublishedRelease
import intelligence.cli.portable.GitHubPublishedTimestamp
import intelligence.cli.portable.GitHubPublishedTimestampParsing
import intelligence.cli.portable.GitHubReadTransportRejection
import intelligence.cli.portable.GitHubReleaseAsset
import intelligence.cli.portable.GitHubReleaseId
import intelligence.cli.portable.GitHubReleaseIdParsing
import intelligence.cli.portable.GitHubReleaseUrl
import intelligence.cli.portable.GitHubReleaseUrlParsing
import intelligence.cli.portable.GitHubRemoteMutation
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryId
import intelligence.cli.portable.GitHubRepositoryIdParsing
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.ReleaseFile
import intelligence.cli.portable.SnapshotId
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Comparator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal class GhGitHubPublicationTransport(
    private val runner: ProcessCaptureRunner = ProcessCaptureRunner.system(),
    private val cwd: Path = Path.of(".").toAbsolutePath().normalize(),
) : GitHubPublicationTransport {
    private val reader = GhGitHubReadTransport(runner, cwd)

    override fun preflight(request: GitHubPublicationRequest): GitHubPublicationPreflight {
        val repositoryId =
            when (val repository = repositoryEvidence(request.repository)) {
                is PublicationRepositoryEvidence.Read -> repository.repositoryId
                is PublicationRepositoryEvidence.Rejected -> {
                    return GitHubPublicationPreflight.Rejected(repository.reason)
                }
            }
        val commitCapture = api("GET", "repos/${request.repository.render()}/git/commits/${request.commit.render()}")
        if (commitCapture.exitCode != 0) {
            return GitHubPublicationPreflight.Rejected(GitHubPublicationTransportRejection.CommitUnavailable)
        }
        val commitPayload = commitCapture.objectPayload()
            ?: return GitHubPublicationPreflight.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val commitSha =
            when (val parsed = GitCommitSha.parse(commitPayload.string("sha").orEmpty())) {
                is GitCommitShaParsing.Parsed -> parsed.sha
                is GitCommitShaParsing.Rejected -> {
                    return GitHubPublicationPreflight.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
                }
            }
        if (commitSha != request.commit) {
            return GitHubPublicationPreflight.Rejected(GitHubPublicationTransportRejection.CommitUnavailable)
        }
        val policy = api("GET", "repos/${request.repository.render()}/immutable-releases")
        if (policy.exitCode != 0) {
            return GitHubPublicationPreflight.Rejected(
                if (policy.isAuthenticationFailure()) {
                    GitHubPublicationTransportRejection.AuthenticationFailed
                } else {
                    GitHubPublicationTransportRejection.ImmutableReleasesDisabled
                },
            )
        }
        val tag = api("GET", "repos/${request.repository.render()}/git/ref/tags/${request.snapshotId.render()}")
        if (tag.exitCode == 0) {
            return GitHubPublicationPreflight.Conflict(GitHubPublicationTransportRejection.TagOrReleaseExists)
        }
        if (!tag.isNotFound()) {
            return GitHubPublicationPreflight.Rejected(tag.publicationRejection())
        }
        return when (val lookup = lookup(request.repository, request.snapshotId)) {
            GitHubPublicationLookup.Absent -> GitHubPublicationPreflight.Ready(repositoryId)
            is GitHubPublicationLookup.Draft,
            is GitHubPublicationLookup.Published,
            -> GitHubPublicationPreflight.Conflict(GitHubPublicationTransportRejection.TagOrReleaseExists)
            is GitHubPublicationLookup.Rejected -> GitHubPublicationPreflight.Rejected(lookup.reason)
        }
    }

    override fun createDraft(request: GitHubPublicationRequest): GitHubRemoteMutation<GitHubDraftRelease> {
        val capture =
            api(
                "POST",
                "repos/${request.repository.render()}/releases",
                "-f",
                "tag_name=${request.snapshotId.render()}",
                "-f",
                "name=${request.snapshotId.render()}",
                "-f",
                "target_commitish=${request.commit.render()}",
                "-F",
                "draft=true",
                "-F",
                "prerelease=false",
                "-F",
                "generate_release_notes=false",
                "-f",
                "make_latest=false",
            )
        if (capture.exitCode != 0) {
            return if (capture.isUncertainMutation()) {
                GitHubRemoteMutation.Unknown(null)
            } else {
                GitHubRemoteMutation.Rejected(capture.publicationRejection())
            }
        }
        val payload = capture.objectPayload() ?: return GitHubRemoteMutation.Unknown(null)
        val releaseId = payload.releaseId() ?: return GitHubRemoteMutation.Unknown(null)
        if (payload.boolean("draft") != true ||
            payload.string("tag_name") != request.snapshotId.render() ||
            payload.string("target_commitish") != request.commit.render()
        ) {
            return GitHubRemoteMutation.Unknown(releaseId)
        }
        return GitHubRemoteMutation.Completed(
            GitHubDraftRelease(request.repository, releaseId, request.snapshotId, request.commit),
        )
    }

    override fun upload(
        draft: GitHubDraftRelease,
        file: ReleaseFile,
        contentType: GitHubAssetContentType,
    ): GitHubRemoteMutation<GitHubReleaseAsset> {
        val directory = createTemporaryDirectory() ?: return GitHubRemoteMutation.Rejected(
            GitHubPublicationTransportRejection.Unavailable,
        )
        val path = directory.resolve(file.name.render())
        try {
            Files.write(path, file.bytes())
        } catch (_: IOException) {
            deleteTree(directory)
            return GitHubRemoteMutation.Rejected(GitHubPublicationTransportRejection.Unavailable)
        } catch (_: SecurityException) {
            deleteTree(directory)
            return GitHubRemoteMutation.Rejected(GitHubPublicationTransportRejection.Unavailable)
        }
        val capture =
            runner.run(
                listOf(
                    "gh",
                    "release",
                    "upload",
                    draft.snapshotId.render(),
                    path.toString(),
                    "--repo",
                    draft.repository.render(),
                ),
                cwd,
                emptyMap(),
            )
        val localCleanup = deleteTree(directory)
        if (!localCleanup) return GitHubRemoteMutation.Unknown(draft.releaseId)
        if (capture.exitCode != 0) {
            return if (capture.isUncertainMutation()) {
                GitHubRemoteMutation.Unknown(draft.releaseId)
            } else {
                GitHubRemoteMutation.Rejected(capture.publicationRejection())
            }
        }
        val assets =
            when (val listing = listDraftAssets(draft)) {
                is GitHubPublicationRead.Read -> listing.value
                is GitHubPublicationRead.Rejected -> return GitHubRemoteMutation.Unknown(draft.releaseId)
            }
        val matching = assets.filter { asset -> asset.name == file.name }
        return if (matching.size == 1 &&
            matching.single().byteSize == file.byteSize &&
            matching.single().sha256 == file.sha256 &&
            matching.single().contentType == contentType
        ) {
            GitHubRemoteMutation.Completed(matching.single())
        } else {
            GitHubRemoteMutation.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        }
    }

    override fun listDraftAssets(draft: GitHubDraftRelease): GitHubPublicationRead<List<GitHubReleaseAsset>> {
        val capture = paginated("repos/${draft.repository.render()}/releases/${draft.releaseId.render()}/assets")
        if (capture.exitCode != 0) {
            return GitHubPublicationRead.Rejected(capture.publicationRejection())
        }
        val payloads = capture.paginatedObjects(MAX_PUBLICATION_ASSETS)
            ?: return GitHubPublicationRead.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val assets = payloads.map { payload -> decodeAsset(payload) ?: return GitHubPublicationRead.Rejected(
            GitHubPublicationTransportRejection.InvalidResponse,
        ) }
        return GitHubPublicationRead.Read(assets.sortedBy { asset -> asset.name.render() })
    }

    override fun downloadDraftAsset(
        draft: GitHubDraftRelease,
        asset: GitHubReleaseAsset,
    ): GitHubPublicationRead<ByteArray> {
        val exact =
            GitHubExactRelease(
                draft.repository,
                draft.releaseId,
                draft.snapshotId,
                draft.commit,
                immutable = false,
                draft = true,
                assets = listOf(asset),
            )
        return when (val download = reader.download(draft.repository, exact, asset)) {
            is GitHubAssetDownload.Downloaded -> GitHubPublicationRead.Read(download.bytes())
            is GitHubAssetDownload.Rejected ->
                GitHubPublicationRead.Rejected(download.reason.toPublicationRejection())
        }
    }

    override fun publish(draft: GitHubDraftRelease): GitHubRemoteMutation<GitHubMutationComplete> {
        val capture =
            api(
                "PATCH",
                "repos/${draft.repository.render()}/releases/${draft.releaseId.render()}",
                "-F",
                "draft=false",
                "-f",
                "make_latest=false",
            )
        if (capture.exitCode != 0) {
            return if (capture.isUncertainMutation()) {
                GitHubRemoteMutation.Unknown(draft.releaseId)
            } else {
                GitHubRemoteMutation.Rejected(capture.publicationRejection())
            }
        }
        val payload = capture.objectPayload() ?: return GitHubRemoteMutation.Unknown(draft.releaseId)
        return if (payload.releaseId() == draft.releaseId && payload.boolean("draft") == false) {
            GitHubRemoteMutation.Completed(GitHubMutationComplete)
        } else {
            GitHubRemoteMutation.Unknown(draft.releaseId)
        }
    }

    override fun lookup(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
    ): GitHubPublicationLookup {
        val repositoryEvidence =
            when (val evidence = repositoryEvidence(repository)) {
                is PublicationRepositoryEvidence.Read -> evidence
                is PublicationRepositoryEvidence.Rejected -> return GitHubPublicationLookup.Rejected(evidence.reason)
            }
        val capture = paginated("repos/${repository.render()}/releases")
        if (capture.exitCode != 0) return GitHubPublicationLookup.Rejected(capture.publicationRejection())
        val releases = capture.paginatedObjects(MAX_PUBLICATION_RELEASES)
            ?: return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val matching = releases.filter { payload -> payload.string("tag_name") == snapshotId.render() }
        if (matching.isEmpty()) return GitHubPublicationLookup.Absent
        if (matching.size != 1) return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val payload = matching.single()
        val releaseId = payload.releaseId()
            ?: return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        if (payload.boolean("draft") == true) return GitHubPublicationLookup.Draft(releaseId)
        val exact = decodeRelease(repository, payload)
            ?: return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val htmlUrl =
            when (
                val parsed =
                    GitHubReleaseUrl.parse(
                        payload.string("html_url").orEmpty(),
                        repository,
                        snapshotId,
                    )
            ) {
                is GitHubReleaseUrlParsing.Parsed -> parsed.url
                is GitHubReleaseUrlParsing.Rejected -> {
                    return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
                }
            }
        val publishedAt =
            when (val parsed = GitHubPublishedTimestamp.parse(payload.string("published_at").orEmpty())) {
                is GitHubPublishedTimestampParsing.Parsed -> parsed.timestamp
                is GitHubPublishedTimestampParsing.Rejected -> {
                    return GitHubPublicationLookup.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
                }
            }
        return GitHubPublicationLookup.Published(
            GitHubPublishedRelease(repositoryEvidence.repositoryId, exact, htmlUrl, publishedAt),
        )
    }

    override fun downloadPublishedAsset(
        release: GitHubPublishedRelease,
        asset: GitHubReleaseAsset,
    ): GitHubPublicationRead<ByteArray> =
        when (val download = reader.download(release.release.repository, release.release, asset)) {
            is GitHubAssetDownload.Downloaded -> GitHubPublicationRead.Read(download.bytes())
            is GitHubAssetDownload.Rejected ->
                GitHubPublicationRead.Rejected(download.reason.toPublicationRejection())
        }

    override fun cleanup(draft: GitHubDraftRelease): GitHubDraftCleanup {
        val read = api("GET", "repos/${draft.repository.render()}/releases/${draft.releaseId.render()}")
        if (read.exitCode != 0) return GitHubDraftCleanup.Unknown(draft.releaseId)
        val payload = read.objectPayload() ?: return GitHubDraftCleanup.Unknown(draft.releaseId)
        if (payload.releaseId() != draft.releaseId ||
            payload.boolean("draft") != true ||
            payload.string("tag_name") != draft.snapshotId.render() ||
            payload.string("target_commitish") != draft.commit.render()
        ) {
            return GitHubDraftCleanup.Retained(draft.releaseId)
        }
        val deleted = api("DELETE", "repos/${draft.repository.render()}/releases/${draft.releaseId.render()}")
        if (deleted.exitCode != 0) {
            return if (deleted.isUncertainMutation()) {
                GitHubDraftCleanup.Unknown(draft.releaseId)
            } else {
                GitHubDraftCleanup.Retained(draft.releaseId)
            }
        }
        return when (lookup(draft.repository, draft.snapshotId)) {
            GitHubPublicationLookup.Absent -> GitHubDraftCleanup.Cleared
            else -> GitHubDraftCleanup.Retained(draft.releaseId)
        }
    }

    private fun repositoryEvidence(repository: GitHubRepository): PublicationRepositoryEvidence {
        val capture = api("GET", "repos/${repository.render()}")
        if (capture.exitCode != 0) {
            return PublicationRepositoryEvidence.Rejected(capture.publicationRejection())
        }
        val payload = capture.objectPayload()
            ?: return PublicationRepositoryEvidence.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val fullName = payload.string("full_name")
            ?: return PublicationRepositoryEvidence.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
        val actual =
            when (val parsed = GitHubRepository.parse(fullName)) {
                is GitHubRepositoryParsing.Parsed -> parsed.repository
                is GitHubRepositoryParsing.Rejected -> {
                    return PublicationRepositoryEvidence.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
                }
            }
        if (!actual.render().equals(repository.render(), ignoreCase = true)) {
            return PublicationRepositoryEvidence.Rejected(GitHubPublicationTransportRejection.RepositoryMismatch)
        }
        val repositoryId =
            when (val parsed = GitHubRepositoryId.parse(payload.long("id") ?: 0L)) {
                is GitHubRepositoryIdParsing.Parsed -> parsed.id
                is GitHubRepositoryIdParsing.Rejected -> {
                    return PublicationRepositoryEvidence.Rejected(GitHubPublicationTransportRejection.InvalidResponse)
                }
            }
        return PublicationRepositoryEvidence.Read(repositoryId)
    }

    private fun api(
        method: String,
        endpoint: String,
        vararg fields: String,
    ): ProcessCapture =
        runner.run(listOf("gh", "api", "--method", method, endpoint) + fields, cwd, emptyMap())

    private fun paginated(endpoint: String): ProcessCapture =
        runner.run(
            listOf("gh", "api", "--method", "GET", "--paginate", "--slurp", "$endpoint?per_page=100"),
            cwd,
            emptyMap(),
        )
}

private sealed interface PublicationRepositoryEvidence {
    data class Read(val repositoryId: GitHubRepositoryId) : PublicationRepositoryEvidence

    data class Rejected(val reason: GitHubPublicationTransportRejection) : PublicationRepositoryEvidence
}

private fun ProcessCapture.paginatedObjects(maximum: Int): List<JsonObject>? {
    val root = runCatching { JsonFiles.compactJson.parseToJsonElement(stdout) as? JsonArray }.getOrNull() ?: return null
    val pages =
        if (root.all { element -> element is JsonArray }) {
            root.flatMap { element -> (element as JsonArray).toList() }
        } else {
            root.toList()
        }
    if (pages.size > maximum) return null
    return pages.map { element -> element as? JsonObject ?: return null }
}

private fun ProcessCapture.objectPayload(): JsonObject? =
    runCatching { JsonFiles.compactJson.parseToJsonElement(stdout) as? JsonObject }.getOrNull()

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.releaseId(): GitHubReleaseId? =
    when (val parsed = GitHubReleaseId.parse(long("id") ?: return null)) {
        is GitHubReleaseIdParsing.Parsed -> parsed.id
        is GitHubReleaseIdParsing.Rejected -> null
    }

private fun ProcessCapture.isAuthenticationFailure(): Boolean {
    val message = "$stdout\n$stderr".lowercase()
    return "authentication" in message || "not logged" in message || "http 401" in message || "http 403" in message
}

private fun ProcessCapture.isNotFound(): Boolean {
    val message = "$stdout\n$stderr".lowercase()
    return "http 404" in message || "not found" in message
}

private fun ProcessCapture.isUncertainMutation(): Boolean {
    val message = "$stdout\n$stderr".lowercase()
    return "timeout" in message || "timed out" in message || "connection reset" in message || "http 502" in message
}

private fun ProcessCapture.publicationRejection(): GitHubPublicationTransportRejection =
    when {
        exitCode == ProcessCaptureRunner.COMMAND_NOT_FOUND -> GitHubPublicationTransportRejection.Unavailable
        isAuthenticationFailure() -> GitHubPublicationTransportRejection.AuthenticationFailed
        else -> GitHubPublicationTransportRejection.MutationRejected
    }

private fun GitHubReadTransportRejection.toPublicationRejection(): GitHubPublicationTransportRejection =
    when (this) {
        GitHubReadTransportRejection.AuthenticationFailed -> GitHubPublicationTransportRejection.AuthenticationFailed
        GitHubReadTransportRejection.InvalidResponse -> GitHubPublicationTransportRejection.InvalidResponse
        GitHubReadTransportRejection.NotFound -> GitHubPublicationTransportRejection.InvalidResponse
        GitHubReadTransportRejection.Unavailable -> GitHubPublicationTransportRejection.Unavailable
    }

private fun createTemporaryDirectory(): Path? =
    try {
        Files.createTempDirectory("intelligence-github-upload-")
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

private fun deleteTree(root: Path): Boolean =
    try {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private const val MAX_PUBLICATION_RELEASES = 10_000
private const val MAX_PUBLICATION_ASSETS = 1_024
