package intelligence.cli.github

import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubAssetContentType
import intelligence.cli.portable.GitHubAssetDownload
import intelligence.cli.portable.GitHubAssetId
import intelligence.cli.portable.GitHubAssetIdParsing
import intelligence.cli.portable.GitHubExactRelease
import intelligence.cli.portable.GitHubReleaseAsset
import intelligence.cli.portable.GitHubReleaseId
import intelligence.cli.portable.GitHubReleaseIdParsing
import intelligence.cli.portable.GitHubReleaseListing
import intelligence.cli.portable.GitHubReleaseResolution
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.ReleaseAssetName
import intelligence.cli.portable.ReleaseAssetNameParsing
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.SnapshotId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GhGitHubReadTransportTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `adapter performs explicit repository and exact tag reads`() {
        val commands = mutableListOf<List<String>>()
        val runner = ProcessCaptureRunner { command, _, _ ->
            commands += command
            when (command.last()) {
                "repos/amichne/example" -> success("""{"full_name":"amichne/example"}""")
                "repos/amichne/example/releases?per_page=100" ->
                    success(
                        """[{"draft":false,"name":"Snapshot One","tag_name":"snapshot-one"},""" +
                            """{"draft":true,"name":"Draft","tag_name":"snapshot-two"}]""",
                    )
                "repos/amichne/example/releases/tags/snapshot-one" -> success(releaseJson())
                else -> ProcessCapture(1, "", "unexpected")
            }
        }
        val transport = GhGitHubReadTransport(runner, temporaryDirectory)
        val repository = repository("amichne/example")

        val listed = assertIs<GitHubReleaseListing.Listed>(transport.list(repository))
        assertEquals(listOf("snapshot-one"), listed.candidates.map { it.snapshotId.render() })
        val release = assertIs<GitHubReleaseResolution.Resolved>(
            transport.resolve(repository, snapshotId("snapshot-one")),
        ).release

        assertTrue(release.immutable)
        assertEquals(2, release.assets.size)
        assertEquals(
            listOf(
                listOf("gh", "api", "--method", "GET", "repos/amichne/example"),
                listOf("gh", "api", "--method", "GET", "repos/amichne/example/releases?per_page=100"),
                listOf("gh", "api", "--method", "GET", "repos/amichne/example"),
                listOf("gh", "api", "--method", "GET", "repos/amichne/example/releases/tags/snapshot-one"),
            ),
            commands,
        )
    }

    @Test
    fun `adapter downloads exactly one named release asset into an owned temporary directory`() {
        val bytes = "index bytes".encodeToByteArray()
        val commands = mutableListOf<List<String>>()
        val runner = ProcessCaptureRunner { command, _, _ ->
            commands += command
            val directory = Path.of(command[command.indexOf("--dir") + 1])
            Files.write(directory.resolve("marketplace.json"), bytes)
            success("")
        }
        val repository = repository("amichne/example")
        val asset =
            GitHubReleaseAsset(
                assetId(7),
                assetName("marketplace.json"),
                bytes.size,
                Sha256Digest.compute(bytes),
                GitHubAssetContentType.JSON,
            )
        val release =
            GitHubExactRelease(
                repository,
                releaseId(42),
                snapshotId("snapshot-one"),
                commitSha("0123456789abcdef0123456789abcdef01234567"),
                immutable = true,
                draft = false,
                listOf(asset),
            )

        val downloaded = assertIs<GitHubAssetDownload.Downloaded>(
            GhGitHubReadTransport(runner, temporaryDirectory).download(repository, release, asset),
        )

        assertContentEquals(bytes, downloaded.bytes())
        assertEquals("gh", commands.single().first())
        assertEquals("marketplace.json", commands.single()[commands.single().indexOf("--pattern") + 1])
        assertEquals("snapshot-one", commands.single()[3])
    }

    private fun releaseJson(): String {
        val index = "index bytes".encodeToByteArray()
        val checksums = "checksums".encodeToByteArray()
        return """
            {
              "assets": [
                {
                  "content_type": "application/json",
                  "digest": "sha256:${Sha256Digest.compute(index).render()}",
                  "id": 7,
                  "name": "marketplace.json",
                  "size": ${index.size},
                  "state": "uploaded"
                },
                {
                  "content_type": "text/plain",
                  "digest": "sha256:${Sha256Digest.compute(checksums).render()}",
                  "id": 8,
                  "name": "SHA256SUMS",
                  "size": ${checksums.size},
                  "state": "uploaded"
                }
              ],
              "draft": false,
              "id": 42,
              "immutable": true,
              "tag_name": "snapshot-one",
              "target_commitish": "0123456789abcdef0123456789abcdef01234567"
            }
        """.trimIndent()
    }

    private fun success(stdout: String): ProcessCapture = ProcessCapture(0, stdout, "")

    private fun repository(raw: String): GitHubRepository =
        assertIs<GitHubRepositoryParsing.Parsed>(GitHubRepository.parse(raw)).repository

    private fun releaseId(raw: Long): GitHubReleaseId =
        assertIs<GitHubReleaseIdParsing.Parsed>(GitHubReleaseId.parse(raw)).id

    private fun assetId(raw: Long): GitHubAssetId =
        assertIs<GitHubAssetIdParsing.Parsed>(GitHubAssetId.parse(raw)).id

    private fun commitSha(raw: String): GitCommitSha =
        assertIs<GitCommitShaParsing.Parsed>(GitCommitSha.parse(raw)).sha

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun assetName(raw: String): ReleaseAssetName =
        assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse(raw)).name
}
