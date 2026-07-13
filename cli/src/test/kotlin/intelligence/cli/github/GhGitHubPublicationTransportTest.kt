package intelligence.cli.github

import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubPublicationPreflight
import intelligence.cli.portable.GitHubPublicationRequest
import intelligence.cli.portable.GitHubRemoteMutation
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.SnapshotId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GhGitHubPublicationTransportTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `preflight proves repository commit policy tag and release absence before mutation`() {
        val commands = mutableListOf<List<String>>()
        val runner = ProcessCaptureRunner { command, _, _ ->
            commands += command
            when {
                command.last() == "repos/amichne/example" -> success("""{"full_name":"amichne/example","id":9}""")
                command.last().contains("/git/commits/") ->
                    success("""{"sha":"0123456789abcdef0123456789abcdef01234567"}""")
                command.last().endsWith("/immutable-releases") -> success("{}")
                command.last().contains("/git/ref/tags/") -> ProcessCapture(1, "", "HTTP 404: Not Found")
                command.contains("--paginate") -> success("[[]]")
                else -> ProcessCapture(1, "", "unexpected: $command")
            }
        }

        val ready = assertIs<GitHubPublicationPreflight.Ready>(
            GhGitHubPublicationTransport(runner, temporaryDirectory).preflight(request()),
        )

        assertEquals(9, ready.repositoryId.render())
        assertEquals(6, commands.size)
        assertTrue(commands[1].last().contains("/git/commits/"))
        assertTrue(commands[2].last().endsWith("/immutable-releases"))
        assertTrue(commands[3].last().contains("/git/ref/tags/"))
        assertTrue(commands.none { command -> command.contains("POST") })
    }

    @Test
    fun `draft creation sends one exact non latest immutable promotion request`() {
        val commands = mutableListOf<List<String>>()
        val runner = ProcessCaptureRunner { command, _, _ ->
            commands += command
            success(
                """
                {
                  "draft": true,
                  "id": 42,
                  "tag_name": "snapshot-one",
                  "target_commitish": "0123456789abcdef0123456789abcdef01234567"
                }
                """.trimIndent(),
            )
        }

        val created = assertIs<GitHubRemoteMutation.Completed<*>>(
            GhGitHubPublicationTransport(runner, temporaryDirectory).createDraft(request()),
        )

        assertTrue(created.value != null)
        val command = commands.single()
        assertEquals(listOf("gh", "api", "--method", "POST", "repos/amichne/example/releases"), command.take(5))
        assertTrue(command.contains("tag_name=snapshot-one"))
        assertTrue(command.contains("target_commitish=0123456789abcdef0123456789abcdef01234567"))
        assertTrue(command.contains("draft=true"))
        assertTrue(command.contains("make_latest=false"))
    }

    private fun request(): GitHubPublicationRequest =
        GitHubPublicationRequest(
            repository("amichne/example"),
            commitSha("0123456789abcdef0123456789abcdef01234567"),
            snapshotId("snapshot-one"),
        )

    private fun success(stdout: String): ProcessCapture = ProcessCapture(0, stdout, "")

    private fun repository(raw: String): GitHubRepository =
        assertIs<GitHubRepositoryParsing.Parsed>(GitHubRepository.parse(raw)).repository

    private fun commitSha(raw: String): GitCommitSha =
        assertIs<GitCommitShaParsing.Parsed>(GitCommitSha.parse(raw)).sha

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value
}
