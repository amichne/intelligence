package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test

class IntelligenceCommandTest {
    @Test
    fun `help exposes marketplace and validate command groups`() {
        val result = IntelligenceCommand(processRunner = RecordingProcessRunner()).test("--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("validate"))
        assertTrue(result.stdout.contains("marketplace"))
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
        assertTrue(result.stderr.contains("provider must be one of: all, codex, github"))
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
