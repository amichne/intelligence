package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.io.ProcessRunner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir

class IntelligenceCommandTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `root help exposes only the stable V1 command groups`() {
        val result = IntelligenceCommand(processRunner = unavailableProcess()).test("--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.startsWith("Operate portable plugin marketplaces"))
        listOf("doctor", "setup", "validate", "marketplace").forEach { command ->
            assertTrue(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("$command ") })
        }
        assertFalse(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("rpc ") })
    }

    @Test
    fun `marketplace help exposes exactly package level V1 operations`() {
        val result = IntelligenceCommand(processRunner = unavailableProcess()).test("marketplace --help")

        assertEquals(0, result.statusCode)
        val expected =
            listOf(
                "discover",
                "inspect",
                "select",
                "remove",
                "update",
                "resolve",
                "recover",
                "reconstruct",
                "project",
                "materialize",
                "publish",
                "verify-publication",
                "author",
            )
        expected.forEach { command ->
            assertTrue(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("$command ") })
        }
        listOf("browse", "search", "installed", "versions", "remote", "import", "install", "pin", "unpin")
            .forEach { command ->
                assertFalse(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("$command ") })
        }
        assertFalse(result.stdout.contains("primitive", ignoreCase = true))
    }

    @Test
    fun `doctor emits one redacted stable JSON envelope`() {
        val github =
            GitHubCli(
                runner =
                    captureRunner {
                        ProcessCapture(
                            exitCode = 0,
                            stdout =
                                """{"hosts":{"github.com":[{"state":"success","active":true,"host":"github.com","login":"octo","tokenSource":"keyring","gitProtocol":"ssh","scopes":"repo"}]}}""",
                            stderr = "",
                        )
                    },
            )
        val result =
            IntelligenceCommand(
                processRunner = unavailableProcess(),
                github = github,
                portableCacheRoot = temporaryDirectory.resolve("cache"),
            ).test("doctor --repository $temporaryDirectory --format json")

        assertEquals(0, result.statusCode)
        assertEquals(1, result.stdout.lineSequence().count { it.isNotBlank() })
        assertTrue(result.stdout.contains("\"command\":\"doctor\""))
        assertTrue(result.stdout.contains("\"status\":\"READY\""))
        assertFalse(result.stdout.contains("scopes"))
        assertFalse(result.stdout.contains("token", ignoreCase = true))
    }

    @Test
    fun `catalog discovery is deterministic read only and explicitly untrusted`() {
        val catalog = temporaryDirectory.resolve("catalog.json")
        catalog.writeText(
            """{"candidates":[{"advertisedName":"Kotlin tools","repository":"amichne/slopsentral","snapshotId":"snapshot-one"}],"schemaVersion":1,"type":"INTELLIGENCE_MARKETPLACE_CATALOG"}""",
        )
        val before = Files.readAllBytes(catalog)

        val result =
            IntelligenceCommand(processRunner = unavailableProcess()).test(
                "marketplace discover --catalog $catalog --query kotlin --format json",
            )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"command\":\"marketplace.discover\""))
        assertTrue(result.stdout.contains("\"repository\":\"https://github.com/amichne/slopsentral\""))
        assertTrue(result.stdout.contains("\"trusted\":false"))
        assertTrue(before.contentEquals(Files.readAllBytes(catalog)))
    }

    @Test
    fun `author guidance points only to existing contracts`() {
        val result = IntelligenceCommand(processRunner = unavailableProcess()).test("marketplace author --format json")

        assertEquals(0, result.statusCode)
        listOf(
            "docs/reference/portable-package-marketplace-v1.md",
            "docs/reference/validation-trust-boundary-v1.md",
            "docs/reference/immutable-snapshot-publication-v1.md",
        ).forEach { path ->
            assertTrue(result.stdout.contains(path))
            assertTrue(Files.isRegularFile(repoRoot().resolve(path)))
        }
    }

    @Test
    fun `version option prints packaged version`() {
        val result = IntelligenceCommand(processRunner = unavailableProcess()).test("--version")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.trim().startsWith("intelligence version "))
    }

    private fun unavailableProcess(): ProcessRunner = ProcessRunner { _, _ -> 127 }

    private fun captureRunner(response: (List<String>) -> ProcessCapture): ProcessCaptureRunner =
        ProcessCaptureRunner { command, _, _ -> response(command) }

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { Files.isDirectory(it.resolve("docs/reference")) }
}
