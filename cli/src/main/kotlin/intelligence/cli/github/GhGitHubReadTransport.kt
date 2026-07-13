package intelligence.cli.github

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubAssetContentType
import intelligence.cli.portable.GitHubAssetDownload
import intelligence.cli.portable.GitHubAssetId
import intelligence.cli.portable.GitHubAssetIdParsing
import intelligence.cli.portable.GitHubExactRelease
import intelligence.cli.portable.GitHubReadTransport
import intelligence.cli.portable.GitHubReadTransportRejection
import intelligence.cli.portable.GitHubReleaseAsset
import intelligence.cli.portable.GitHubReleaseCandidate
import intelligence.cli.portable.GitHubReleaseId
import intelligence.cli.portable.GitHubReleaseIdParsing
import intelligence.cli.portable.GitHubReleaseListing
import intelligence.cli.portable.GitHubReleaseResolution
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.MAX_RELEASE_ARTIFACT_BYTES
import intelligence.cli.portable.ReleaseAssetName
import intelligence.cli.portable.ReleaseAssetNameParsing
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.Sha256DigestParsing
import intelligence.cli.portable.SnapshotId
import intelligence.cli.portable.IdentifierParse
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal class GhGitHubReadTransport(
    private val runner: ProcessCaptureRunner = ProcessCaptureRunner.system(),
    private val cwd: Path = Path.of(".").toAbsolutePath().normalize(),
) : GitHubReadTransport {
    override fun list(repository: GitHubRepository): GitHubReleaseListing {
        canonicalRepository(repository)?.let { rejection ->
            return GitHubReleaseListing.Rejected(rejection)
        }
        val capture = runGhApi("repos/${repository.render()}/releases?per_page=$MAX_DISCOVERY_RELEASES")
        if (capture.exitCode != 0) return GitHubReleaseListing.Rejected(capture.rejection())
        val releases =
            runCatching { JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonArray }
                .getOrNull()
                ?: return GitHubReleaseListing.Rejected(GitHubReadTransportRejection.InvalidResponse)
        val candidates = mutableListOf<GitHubReleaseCandidate>()
        releases.forEach { element ->
            val payload = element as? JsonObject ?: return@forEach
            if (payload.boolean("draft") != false) return@forEach
            val tag = payload.string("tag_name") ?: return@forEach
            val snapshot =
                when (val parsed = SnapshotId.parse(tag)) {
                    is IdentifierParse.Accepted -> parsed.value
                    is IdentifierParse.Rejected -> return@forEach
                }
            if (snapshot.render() == "latest") return@forEach
            val advertisedName = payload.string("name")?.takeIf { name -> name.isNotBlank() } ?: tag
            candidates += GitHubReleaseCandidate(snapshot, advertisedName.take(MAX_ADVERTISED_NAME_CHARACTERS))
        }
        return GitHubReleaseListing.Listed(
            candidates.sortedBy { candidate -> candidate.snapshotId.render() },
        )
    }

    override fun resolve(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
    ): GitHubReleaseResolution {
        canonicalRepository(repository)?.let { rejection ->
            return GitHubReleaseResolution.Rejected(rejection)
        }
        val capture = runGhApi("repos/${repository.render()}/releases/tags/${snapshotId.render()}")
        if (capture.exitCode != 0) return GitHubReleaseResolution.Rejected(capture.rejection())
        val payload =
            runCatching { JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonObject }
                .getOrNull()
                ?: return GitHubReleaseResolution.Rejected(GitHubReadTransportRejection.InvalidResponse)
        val release = decodeRelease(repository, payload)
            ?: return GitHubReleaseResolution.Rejected(GitHubReadTransportRejection.InvalidResponse)
        return GitHubReleaseResolution.Resolved(release)
    }

    override fun download(
        repository: GitHubRepository,
        release: GitHubExactRelease,
        asset: GitHubReleaseAsset,
    ): GitHubAssetDownload {
        val directory =
            try {
                Files.createTempDirectory("intelligence-github-asset-")
            } catch (_: IOException) {
                return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.Unavailable)
            } catch (_: SecurityException) {
                return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.Unavailable)
            }
        val capture =
            runner.run(
                listOf(
                    "gh",
                    "release",
                    "download",
                    release.snapshotId.render(),
                    "--repo",
                    repository.render(),
                    "--pattern",
                    asset.name.render(),
                    "--dir",
                    directory.toString(),
                ),
                cwd,
                emptyMap(),
            )
        if (capture.exitCode != 0) {
            deleteTree(directory)
            return GitHubAssetDownload.Rejected(capture.rejection())
        }
        val expected = directory.resolve(asset.name.render())
        val entries =
            try {
                Files.list(directory).use { stream -> stream.toList() }
            } catch (_: IOException) {
                deleteTree(directory)
                return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.InvalidResponse)
            } catch (_: SecurityException) {
                deleteTree(directory)
                return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.InvalidResponse)
            }
        if (entries != listOf(expected) || !Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS)) {
            deleteTree(directory)
            return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.InvalidResponse)
        }
        val bytes = readBounded(expected, asset.byteSize)
        val deleted = deleteTree(directory)
        if (bytes == null || !deleted) {
            return GitHubAssetDownload.Rejected(GitHubReadTransportRejection.InvalidResponse)
        }
        return GitHubAssetDownload.Downloaded(bytes)
    }

    private fun canonicalRepository(repository: GitHubRepository): GitHubReadTransportRejection? {
        val capture = runGhApi("repos/${repository.render()}")
        if (capture.exitCode != 0) return capture.rejection()
        val payload =
            runCatching { JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonObject }
                .getOrNull()
                ?: return GitHubReadTransportRejection.InvalidResponse
        val fullName = payload.string("full_name") ?: return GitHubReadTransportRejection.InvalidResponse
        val parsed = GitHubRepository.parse(fullName)
        val actual =
            when (parsed) {
                is GitHubRepositoryParsing.Parsed -> parsed.repository
                is GitHubRepositoryParsing.Rejected -> return GitHubReadTransportRejection.InvalidResponse
            }
        return if (actual.render().equals(repository.render(), ignoreCase = true)) {
            null
        } else {
            GitHubReadTransportRejection.InvalidResponse
        }
    }

    private fun runGhApi(endpoint: String): ProcessCapture =
        runner.run(listOf("gh", "api", "--method", "GET", endpoint), cwd, emptyMap())
}

private fun decodeRelease(
    repository: GitHubRepository,
    payload: JsonObject,
): GitHubExactRelease? {
    val releaseId =
        when (val parsed = GitHubReleaseId.parse(payload.long("id") ?: return null)) {
            is GitHubReleaseIdParsing.Parsed -> parsed.id
            is GitHubReleaseIdParsing.Rejected -> return null
        }
    val snapshotId =
        when (val parsed = SnapshotId.parse(payload.string("tag_name") ?: return null)) {
            is IdentifierParse.Accepted -> parsed.value
            is IdentifierParse.Rejected -> return null
        }
    val commitSha =
        when (val parsed = GitCommitSha.parse(payload.string("target_commitish") ?: return null)) {
            is GitCommitShaParsing.Parsed -> parsed.sha
            is GitCommitShaParsing.Rejected -> return null
        }
    val immutable = payload.boolean("immutable") ?: return null
    val draft = payload.boolean("draft") ?: return null
    val assetPayloads = payload["assets"] as? JsonArray ?: return null
    val assets = assetPayloads.map { element -> decodeAsset(element as? JsonObject ?: return null) ?: return null }
    return GitHubExactRelease(repository, releaseId, snapshotId, commitSha, immutable, draft, assets)
}

private fun decodeAsset(payload: JsonObject): GitHubReleaseAsset? {
    if (payload.string("state") != "uploaded") return null
    val id =
        when (val parsed = GitHubAssetId.parse(payload.long("id") ?: return null)) {
            is GitHubAssetIdParsing.Parsed -> parsed.id
            is GitHubAssetIdParsing.Rejected -> return null
        }
    val name =
        when (val parsed = ReleaseAssetName.parse(payload.string("name") ?: return null)) {
            is ReleaseAssetNameParsing.Parsed -> parsed.name
            is ReleaseAssetNameParsing.Rejected -> return null
        }
    val size = payload.long("size") ?: return null
    if (size !in 1..MAX_RELEASE_ARTIFACT_BYTES.toLong()) return null
    val digest = payload.string("digest")?.removePrefix("sha256:") ?: return null
    val sha256 =
        when (val parsed = Sha256Digest.parse(digest)) {
            is Sha256DigestParsing.Parsed -> parsed.digest
            is Sha256DigestParsing.Rejected -> return null
        }
    val contentType = GitHubAssetContentType.parse(payload.string("content_type") ?: return null) ?: return null
    return GitHubReleaseAsset(id, name, size.toInt(), sha256, contentType)
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

private fun ProcessCapture.rejection(): GitHubReadTransportRejection {
    if (exitCode == ProcessCaptureRunner.COMMAND_NOT_FOUND) return GitHubReadTransportRejection.Unavailable
    val message = "$stdout\n$stderr".lowercase()
    return when {
        "authentication" in message || "not logged" in message || "http 401" in message ->
            GitHubReadTransportRejection.AuthenticationFailed
        "http 404" in message || "not found" in message -> GitHubReadTransportRejection.NotFound
        else -> GitHubReadTransportRejection.Unavailable
    }
}

private fun readBounded(
    path: Path,
    expectedSize: Int,
): ByteArray? =
    try {
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            if (channel.size() != expectedSize.toLong()) return null
            val bytes = ByteArray(expectedSize)
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) return null
            }
            bytes
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

private fun deleteTree(root: Path): Boolean =
    try {
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

private const val MAX_DISCOVERY_RELEASES = 100
private const val MAX_ADVERTISED_NAME_CHARACTERS = 200
