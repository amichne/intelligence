package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import java.nio.file.Path
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
        assertTrue(result.stdout.contains("Browse, validate, materialize, and publish"))
        assertTrue(result.stdout.contains("validate"))
        assertTrue(result.stdout.contains("marketplace"))
    }

    @Test
    fun `marketplace help exposes browse command`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Browse marketplace offerings and manage provider projections"))
        assertTrue(result.stdout.contains("browse"))
        assertTrue(result.stdout.contains("materialize"))
        assertTrue(result.stdout.contains("publish"))
        assertFalse(result.stdout.contains("publish-branch"))
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
    fun `marketplace publish help exposes default and branch publication`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("marketplace publish --help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("default harness marketplaces"))
        assertTrue(result.stdout.contains("--codex"))
        assertTrue(result.stdout.contains("--copilot"))
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
        assertTrue(result.stdout.contains("Marketplace: amichne-intelligence"))
        assertTrue(result.stdout.contains("Plugins"))
        assertTrue(result.stdout.contains("engineering-baseline"))
        assertTrue(result.stdout.contains("Standalone skills"))
        assertTrue(result.stdout.contains("repository-onboarding"))
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
        assertTrue(result.stdout.contains("OK source marketplace"))
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
        lateinit var command: List<String>
        lateinit var cwd: Path

        override fun run(command: List<String>, cwd: Path): Int {
            this.command = command
            this.cwd = cwd
            return exitCode
        }
    }
}
