package intelligence.cli.github

import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubCliTest {
    @Test
    fun `owner repo shorthand resolves through active gh enterprise host`() {
        val cli = GitHubCli(
            runner = CaptureRunner { command, _ ->
                when (command.take(4)) {
                    listOf("gh", "auth", "status", "--json") -> ProcessCapture(
                        exitCode = 0,
                        stdout = """{"hosts":{"github.enterprise.example":[{"state":"success","active":true,"host":"github.enterprise.example","login":"octo","tokenSource":"keyring","gitProtocol":"ssh"}]}}""",
                        stderr = "",
                    )
                    else -> ProcessCapture(exitCode = 1, stdout = "", stderr = "unexpected command")
                }
            },
        )

        assertEquals(
            "https://github.enterprise.example/acme/tools",
            cli.normalizeRepository("acme/tools", explicitHost = null),
        )
    }

    @Test
    fun `explicit host overrides active gh host`() {
        val cli = GitHubCli(runner = CaptureRunner { _, _ -> error("status should not be needed") })

        assertEquals(
            "https://github.override.example/acme/tools",
            cli.normalizeRepository("acme/tools", explicitHost = "github.override.example"),
        )
    }

    @Test
    fun `repository search passes GH_HOST for enterprise hosts`() {
        val calls = mutableListOf<Pair<List<String>, Map<String, String>>>()
        val cli = GitHubCli(
            runner = CaptureRunner { command, environment ->
                calls += command to environment
                when (command.take(4)) {
                    listOf("gh", "auth", "status", "--json") -> ProcessCapture(
                        exitCode = 0,
                        stdout = """{"hosts":{"github.enterprise.example":[{"state":"success","active":true,"host":"github.enterprise.example","login":"octo","tokenSource":"keyring","gitProtocol":"ssh"}]}}""",
                        stderr = "",
                    )
                    listOf("gh", "search", "repos", "kotlin") -> ProcessCapture(
                        exitCode = 0,
                        stdout = """[{"fullName":"acme/tools","url":"https://github.enterprise.example/acme/tools"}]""",
                        stderr = "",
                    )
                    else -> ProcessCapture(exitCode = 1, stdout = "", stderr = "unexpected command")
                }
            },
        )

        val result = cli.searchRepositories("kotlin", explicitHost = null, limit = 5)

        assertEquals("github.enterprise.example", result.host.value)
        assertEquals("acme/tools", result.repositories.single().nameWithOwner)
        assertTrue(calls.any { (command, environment) ->
            command.take(4) == listOf("gh", "search", "repos", "kotlin") &&
                environment["GH_HOST"] == "github.enterprise.example"
        })
    }

    private class CaptureRunner(
        private val response: (List<String>, Map<String, String>) -> ProcessCapture,
    ) : ProcessCaptureRunner {
        override fun run(command: List<String>, cwd: Path, environment: Map<String, String>): ProcessCapture =
            response(command, environment)
    }
}
