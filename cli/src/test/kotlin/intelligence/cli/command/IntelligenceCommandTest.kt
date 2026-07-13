package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.io.ProcessRunner
import java.nio.file.Path
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test

class IntelligenceCommandTest {
    @Test
    fun `help exposes marketplace and validate command groups`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.startsWith("Operate portable plugin marketplaces"))
        assertTrue(result.stdout.contains("Usage: intelligence [OPTIONS] [COMMAND]"))
        assertSectionOrder(result.stdout, "Commands:", "Options:")
        assertTrue(result.stdout.contains("setup"))
        assertTrue(result.stdout.contains("validate"))
        assertTrue(result.stdout.contains("marketplace"))
        assertTrue(result.stdout.contains("doctor"))
        assertTrue(result.stdout.contains("rpc"))
    }

    @Test
    fun `bare command prints cli first help`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Usage: intelligence [OPTIONS] [COMMAND]"))
        assertTrue(result.stdout.contains("setup"))
        assertTrue(result.stdout.contains("marketplace"))
        assertTrue(result.stdout.contains("doctor"))
    }

    @Test
    fun `version option prints packaged version`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("--version")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.trim().startsWith("intelligence version "))
    }

    @Test
    fun `doctor json reports GitHub host state without token fields`() {
        val result = IntelligenceCommand(
            processRunner = RecordingProcessRunner(),
            github = GitHubCli(
                runner = CaptureRunner {
                    ProcessCapture(
                        exitCode = 0,
                        stdout = """
                        {
                          "hosts": {
                            "github.enterprise.example": [
                              {
                                "state": "success",
                                "active": true,
                                "host": "github.enterprise.example",
                                "login": "octo",
                                "tokenSource": "keyring",
                                "gitProtocol": "ssh",
                                "scopes": "repo"
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                        stderr = "",
                    )
                },
            ),
        ).test("doctor --format json")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"defaultHost\": \"github.enterprise.example\""))
        assertTrue(result.stdout.contains("\"login\": \"octo\""))
        assertFalse(result.stdout.contains("scopes"))
        assertFalse(result.stdout.contains("token\""))
    }

    @Test
    fun `rpc help exposes stdio contract`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("rpc --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("JSON-RPC stdio contract"))
    }

    @Test
    fun `marketplace help exposes browse command`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.startsWith("Browse, manage, import, project, and publish portable marketplaces"))
        assertTrue(result.stdout.contains("Usage: intelligence marketplace [OPTIONS] [COMMAND]"))
        assertSectionOrder(result.stdout, "Commands:", "Options:")
        assertTrue(result.stdout.contains("browse"))
        assertTrue(result.stdout.contains("import"))
        assertTrue(result.stdout.contains("install"))
        assertTrue(result.stdout.contains("search"))
        assertTrue(result.stdout.contains("inspect"))
        assertTrue(result.stdout.contains("installed"))
        assertTrue(result.stdout.contains("versions"))
        assertTrue(result.stdout.contains("remote"))
        assertTrue(result.stdout.contains("update"))
        assertTrue(result.stdout.contains("pin"))
        assertTrue(result.stdout.contains("unpin"))
        assertFalse(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("ui ") })
        assertTrue(result.stdout.contains("materialize"))
        assertTrue(result.stdout.contains("publish"))
        assertFalse(result.stdout.contains("publish-branch"))
    }

    @Test
    fun `marketplace help exposes package only local consumer operations`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace --help")

        assertEquals(0, result.statusCode)
        listOf("select", "remove", "update", "resolve", "recover", "reconstruct", "project").forEach { command ->
            assertTrue(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("$command ") })
        }
        assertFalse(result.stdout.contains("primitive"))
    }

    @Test
    fun `marketplace browse help explains repository based discovery`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace browse --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("without typing marketplace"))
        assertTrue(result.stdout.contains("paths"))
        assertTrue(result.stdout.contains("owner/repo shorthand"))
        assertTrue(result.stdout.contains("Auto tries published provider marketplaces"))
    }

    @Test
    fun `setup help exposes default locked consumer flow`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("setup --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("locked Intelligence marketplace"))
        assertTrue(result.stdout.contains("--marketplace"))
        assertTrue(result.stdout.contains("amichne/slopsentral"))
        assertTrue(result.stdout.contains("--plugin"))
        assertTrue(result.stdout.contains("kotlin-engineering"))
        assertTrue(result.stdout.contains("--version"))
        assertTrue(result.stdout.contains("--ref"))
        assertTrue(result.stdout.contains("--host"))
        assertTrue(result.stdout.contains("--no-validate"))
    }

    @Test
    fun `marketplace search can use gh repository search JSON`() {
        val result = IntelligenceCommand(
            processRunner = RecordingProcessRunner(),
            github = GitHubCli(
                runner = CaptureRunner { command ->
                    when (command.take(4)) {
                        listOf("gh", "auth", "status", "--json") -> ProcessCapture(
                            exitCode = 0,
                            stdout = """{"hosts":{"github.enterprise.example":[{"state":"success","active":true,"host":"github.enterprise.example","login":"octo","tokenSource":"keyring","gitProtocol":"ssh"}]}}""",
                            stderr = "",
                        )
                        listOf("gh", "search", "repos", "kotlin") -> ProcessCapture(
                            exitCode = 0,
                            stdout = """[{"fullName":"acme/tools","url":"https://github.enterprise.example/acme/tools","description":"Tools","visibility":"private"}]""",
                            stderr = "",
                        )
                        else -> ProcessCapture(exitCode = 1, stdout = "", stderr = "unexpected command: $command")
                    }
                },
            ),
        ).test("marketplace search kotlin --format json")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"host\": \"github.enterprise.example\""))
        assertTrue(result.stdout.contains("\"nameWithOwner\": \"acme/tools\""))
    }

    @Test
    fun `marketplace search can filter a local catalog`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test(
            listOf(
                "marketplace",
                "search",
                "kotlin",
                "--repository",
                repoRoot().toString(),
                "--provider",
                "source",
                "--format",
                "json",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"query\": \"kotlin\""))
        assertTrue(result.stdout.contains("kotlin-engineering"))
    }

    @Test
    fun `marketplace installed list exposes installed plugins`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test(
            listOf(
                "marketplace",
                "installed",
                "list",
                "--repo",
                repoRoot().toString(),
                "--format",
                "json",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("kotlin-engineering"))
    }

    @Test
    fun `marketplace publish help exposes default and branch publication`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace publish --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default harness marketplaces"))
        assertTrue(result.stdout.contains("--codex"))
        assertTrue(result.stdout.contains("--copilot"))
    }

    @Test
    fun `marketplace import help exposes direct repository import defaults`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace import --help")

        assertEquals(0, result.statusCode)
        assertTrue(
            result.stdout.contains(
                "Usage: intelligence marketplace import [OPTIONS] <marketplace/plugin|repository/plugin>"
            )
        )
        assertTrue(result.stdout.contains("repository/plugin"))
        assertTrue(result.stdout.contains("--repo <PATH>"))
        assertTrue(result.stdout.contains("--ref"))
        assertTrue(result.stdout.contains("--no-validate"))
        assertTrue(result.stdout.contains("Defaults"))
        assertTrue(result.stdout.contains("main"))
    }

    @Test
    fun `marketplace install help exposes repository install defaults`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace install --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Usage: intelligence marketplace install [OPTIONS] <repository>"))
        assertTrue(result.stdout.contains("adaptable marketplace"))
        assertTrue(result.stdout.contains("--ref"))
        assertTrue(result.stdout.contains("--no-validate"))
        assertTrue(result.stdout.contains("Defaults"))
        assertTrue(result.stdout.contains("main"))
    }

    @Test
    fun `marketplace update help exposes exact package preserving snapshot move`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace update --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Usage: intelligence marketplace update"))
        assertTrue(result.stdout.contains("preserving package names"))
        assertTrue(result.stdout.contains("--local-snapshot"))
        assertTrue(result.stdout.contains("--index-sha256"))
        assertTrue(result.stdout.contains("--dry-run"))
        assertFalse(result.stdout.contains("--all"))
    }

    @Test
    fun `marketplace pin and unpin help expose validation skip`() {
        val pin = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace pin --help")
        val unpin = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace unpin --help")

        assertEquals(0, pin.statusCode)
        assertEquals(0, unpin.statusCode)
        assertTrue(pin.stdout.contains("--no-validate"))
        assertTrue(unpin.stdout.contains("--no-validate"))
    }

    @Test
    fun `marketplace help omits terminal ui flow`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace --help")

        assertEquals(0, result.statusCode)
        assertFalse(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("ui ") })
        assertFalse(result.stdout.contains("full-screen marketplace browser"))
    }

    @Test
    fun `marketplace browse accepts repository path and prints source offerings`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test(
            listOf(
                "marketplace",
                "browse",
                repoRoot().toString(),
                "--provider",
                "source",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Marketplace: intelligence-cli"))
        assertTrue(result.stdout.contains("Plugins"))
        assertTrue(result.stdout.contains("kotlin-engineering"))
    }

    @Test
    fun `validate runs marketplace source checks`() {
        val result = IntelligenceCommand().test(
            listOf(
                "validate",
                "--repo",
                repoRoot().toString(),
                "--portable",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("OK adaptable marketplace"))
    }

    @Test
    fun `marketplace import validates target repository by default`() {
        val repository = emptyConsumerRepository()
        val remote = bareGitMarketplace("shared-tools")

        val result = IntelligenceCommand().test(
            listOf(
                "marketplace",
                "import",
                "${remote}/review-stack",
                "--repo",
                repository.toString(),
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("imported shared-tools/review-stack"))
        assertTrue(result.stdout.contains("OK adaptable marketplace"))
    }

    @Test
    fun `marketplace import can skip default validation`() {
        val repository = emptyConsumerRepository()
        val remote = bareGitMarketplace("shared-tools")

        val result = IntelligenceCommand().test(
            listOf(
                "marketplace",
                "import",
                "${remote}/review-stack",
                "--repo",
                repository.toString(),
                "--no-validate",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("imported shared-tools/review-stack"))
        assertFalse(result.stdout.contains("OK adaptable marketplace"))
    }

    @Test
    fun `setup imports default consumer plugin and validates target repository`() {
        val repository = emptyConsumerRepository()
        val remote = bareGitMarketplace("shared-tools")

        val result = IntelligenceCommand().test(
            listOf(
                "setup",
                "--repo",
                repository.toString(),
                "--marketplace",
                remote.toString(),
                "--plugin",
                "review-stack",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("imported shared-tools/review-stack"))
        assertTrue(result.stdout.contains("OK adaptable marketplace"))
        assertTrue(result.stdout.contains("setup complete"))
        assertTrue(repository.resolve(".intelligence").resolve("adaptable.marketplace.json").exists())
        assertTrue(repository.resolve(".intelligence").resolve("marketplace-lock.json").exists())
    }

    @Test
    fun `setup can skip default validation`() {
        val repository = emptyConsumerRepository()
        val remote = bareGitMarketplace("shared-tools")

        val result = IntelligenceCommand().test(
            listOf(
                "setup",
                "--repo",
                repository.toString(),
                "--marketplace",
                remote.toString(),
                "--plugin",
                "review-stack",
                "--no-validate",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("imported shared-tools/review-stack"))
        assertTrue(result.stdout.contains("setup complete"))
        assertFalse(result.stdout.contains("OK adaptable marketplace"))
    }

    @Test
    fun `marketplace materialize defaults to all providers and derived output root`() {
        val repository = marketplaceWithLocalPlugin()

        val result = IntelligenceCommand().test(
            listOf(
                "marketplace",
                "materialize",
                "--repo",
                repository.toString(),
            )
        )

        val output = repository.resolve("build").resolve("intelligence").resolve("marketplace")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains(output.toString()))
        assertTrue(output.resolve(".agents").resolve("plugins").resolve("marketplace.json").exists())
        assertTrue(output.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
    }

    @Test
    fun `marketplace publish check validates before writing provider payloads`() {
        val repository = marketplaceWithLocalPlugin()

        val result = IntelligenceCommand().test(
            listOf(
                "marketplace",
                "publish",
                "--repo",
                repository.toString(),
                "--check",
            )
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("OK adaptable marketplace"))
        assertTrue(result.stdout.contains("OK hydrated marketplace"))
        assertTrue(repository.resolve(".agents").resolve("plugins").resolve("marketplace.json").exists())
        assertTrue(repository.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
    }

    @Test
    fun `validate rejects removed compatibility flags`() {
        val result = IntelligenceCommand().test("validate --manifests-only")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("--manifests-only"))
    }

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { it.resolve("source").resolve("adaptable.marketplace.json").toFile().isFile }

    private fun assertSectionOrder(text: String, first: String, second: String) {
        val firstIndex = text.indexOf(first)
        val secondIndex = text.indexOf(second)
        assertTrue(firstIndex >= 0, "missing `$first` in help output")
        assertTrue(secondIndex >= 0, "missing `$second` in help output")
        assertTrue(firstIndex < secondIndex, "`$first` should appear before `$second`")
    }

    @Test
    fun `invalid marketplace provider fails before materialization`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test(
            "marketplace materialize --provider nope --out /tmp/intelligence-marketplace"
        )

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("provider must be one of: all, codex, github, copilot"))
    }

    private class RecordingProcessRunner(
        private val exitCode: Int = 0,
    ) : ProcessRunner {
        var command: List<String>? = null
        var cwd: Path? = null

        override fun run(command: List<String>, cwd: Path): Int {
            this.command = command
            this.cwd = cwd
            return exitCode
        }
    }

    private class CaptureRunner(
        private val response: (List<String>) -> ProcessCapture,
    ) : ProcessCaptureRunner {
        override fun run(command: List<String>, cwd: Path, environment: Map<String, String>): ProcessCapture =
            response(command)
    }

    private fun emptyConsumerRepository(): Path =
        Files.createTempDirectory("intelligence-marketplace-consumer-")

    private fun marketplaceWithLocalPlugin(): Path {
        val repository = Files.createTempDirectory("intelligence-marketplace-source-")
        repository.resolve("schemas").createDirectories()
        writeJson(
            repository.resolve("source").resolve("adaptable.marketplace.json"),
            """
            {
              "type": "MARKETPLACE",
              "schemaVersion": 1,
              "name": "fixture-marketplace",
              "owner": {
                "name": "Fixture Owner"
              },
              "plugins": [
                {
                  "type": "PLUGIN_ENTRY",
                  "name": "core-plugin",
                  "plugin": {
                    "type": "PLUGIN_REFERENCE",
                    "name": "core-plugin",
                    "source": {
                      "type": "LOCAL_SOURCE",
                      "path": "plugins/core-plugin"
                    },
                    "version": "0.1.0"
                  },
                  "tags": [
                    "kotlin"
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repository.resolve("source")
                .resolve("plugins")
                .resolve("core-plugin")
                .resolve("plugin.json"),
            """
            {
              "type": "PLUGIN",
              "schemaVersion": 1,
              "name": "core-plugin",
              "version": "0.1.0",
              "description": "Core plugin."
            }
            """.trimIndent(),
        )
        return repository
    }

    private fun writeExternalMarketplace(root: Path, version: String = "1.2.0") {
        writeJson(
            root.resolve("source").resolve("adaptable.marketplace.json"),
            """
            {
              "type": "MARKETPLACE",
              "schemaVersion": 1,
              "name": "shared-tools",
              "owner": {
                "name": "Shared Tools"
              },
              "plugins": [
                {
                  "type": "PLUGIN_ENTRY",
                  "name": "review-stack",
                  "plugin": {
                    "type": "PLUGIN_REFERENCE",
                    "name": "review-stack",
                    "source": {
                      "type": "LOCAL_SOURCE",
                      "path": "./plugins/review-stack"
                    },
                    "version": "$version"
                  },
                  "description": "Review stack.",
                  "tags": [
                    "review"
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            root.resolve("source")
                .resolve("plugins")
                .resolve("review-stack")
                .resolve("plugin.json"),
            """
            {
              "type": "PLUGIN",
              "schemaVersion": 1,
              "name": "review-stack",
              "version": "$version",
              "description": "Review stack.",
              "skills": []
            }
            """.trimIndent(),
        )
    }

    private fun writeJson(path: Path, content: String) {
        path.parent.createDirectories()
        path.writeText(content.trimIndent() + "\n")
    }

    private fun bareGitMarketplace(name: String): Path {
        val worktree = Files.createTempDirectory("intelligence-remote-worktree-")
        val bare = Files.createTempDirectory("intelligence-remote-bare-").resolve("$name.git")
        writeExternalMarketplace(worktree)
        runGit(worktree, "init", "--initial-branch", "main")
        runGit(worktree, "config", "user.name", "Fixture")
        runGit(worktree, "config", "user.email", "fixture@example.invalid")
        runGit(worktree, "add", ".")
        runGit(worktree, "commit", "-m", "Initial marketplace")
        runGit(worktree, "clone", "--bare", worktree.toString(), bare.toString())
        assertEquals("$name.git", bare.name)
        return bare
    }

    private fun runGit(cwd: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(cwd.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        assertEquals(0, process.waitFor(), "git ${args.joinToString(" ")} failed in $cwd")
    }
}
