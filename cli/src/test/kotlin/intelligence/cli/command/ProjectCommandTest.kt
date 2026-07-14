package intelligence.cli.command

import intelligence.cli.io.JsonFiles
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir

class ProjectCommandTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `root help exposes only projection`() {
        val result = IntelligenceCommand().test("--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.startsWith("Project provider-neutral agent tooling"))
        assertTrue(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("project ") })
        listOf("doctor", "setup", "validate", "marketplace", "rpc", "install", "publish").forEach { command ->
            assertFalse(result.stdout.lineSequence().any { line -> line.trimStart().startsWith("$command ") })
        }
    }

    @Test
    fun `project reports argument failures as structured stdout`() {
        val missing = IntelligenceCommand().test("project")
        val unsupported = IntelligenceCommand().test(
            "project --source /tmp/source --harness cursor --out /tmp/output",
        )

        assertEquals(1, missing.statusCode)
        assertTrue(missing.stdout.contains("code: SOURCE_REQUIRED"))
        assertEquals("", missing.stderr)
        assertEquals(1, unsupported.statusCode)
        assertTrue(unsupported.stdout.contains("code: HARNESS_UNSUPPORTED"))
        assertEquals("", unsupported.stderr)
    }

    @Test
    fun `project converts one source marketplace to codex`() {
        val source = minimalMarketplaceSource()
        val output = temporaryDirectory.resolve("codex")

        val result = IntelligenceCommand().test(
            "project --source $source --harness codex --out $output",
        )

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("status: projected"))
        assertTrue(result.stdout.contains("harness: codex"))
        assertTrue(output.resolve(".agents/plugins/marketplace.json").exists())
        assertTrue(output.resolve(".agents/plugins/core-plugin/.codex-plugin/plugin.json").exists())
        assertFalse(output.resolve(".agents/plugins/unexposed-plugin").exists())
    }

    @Test
    fun `project converts one source marketplace to github copilot`() {
        val source = minimalMarketplaceSource()
        val output = temporaryDirectory.resolve("github-copilot")

        val result = IntelligenceCommand().test(
            "project --source $source --harness github-copilot --out $output",
        )

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.contains("status: projected"))
        assertTrue(result.stdout.contains("harness: github-copilot"))
        assertTrue(output.resolve(".github/plugin/marketplace.json").exists())
        assertTrue(output.resolve(".github/plugin/core-plugin/skills/core-skill/SKILL.md").exists())
    }

    @Test
    fun `project rejects overlapping source and output without touching source`() {
        val source = minimalMarketplaceSource()
        val sentinel = source.resolve("keep.txt").toFile().also { it.writeText("keep") }

        val result = IntelligenceCommand().test(
            "project --source $source --harness codex --out $source",
        )

        assertEquals(1, result.statusCode)
        assertTrue(result.stdout.contains("code: PATHS_OVERLAP"))
        assertEquals("keep", sentinel.readText())
    }

    @Test
    fun `project rejects invalid source without replacing existing output`() {
        val source = temporaryDirectory.resolve("invalid-source").also { it.createDirectories() }
        val output = temporaryDirectory.resolve("existing-output").also { it.createDirectories() }
        val sentinel = output.resolve("keep.txt").toFile().also { it.writeText("keep") }

        val result = IntelligenceCommand().test(
            "project --source $source --harness codex --out $output",
        )

        assertEquals(1, result.statusCode)
        assertTrue(result.stdout.contains("code: SOURCE_INVALID"))
        assertEquals("keep", sentinel.readText())
    }

    @Test
    fun `version option prints packaged version`() {
        val result = IntelligenceCommand().test("--version")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.trim().startsWith("intelligence version "))
    }

    private fun minimalMarketplaceSource(): Path =
        temporaryDirectory.resolve("source-${System.nanoTime()}").also { repository ->
            writeJson(
                repository.resolve("source/adaptable.marketplace.json"),
                """
                {
                  "type": "MARKETPLACE",
                  "schemaVersion": 1,
                  "name": "fixture-marketplace",
                  "owner": { "name": "Fixture Owner" },
                  "plugins": [
                    {
                      "type": "PLUGIN_ENTRY",
                      "name": "core-plugin",
                      "plugin": {
                        "type": "PLUGIN_REFERENCE",
                        "name": "core-plugin",
                        "source": { "type": "LOCAL_SOURCE", "path": "./plugins/core-plugin" },
                        "version": "0.1.0"
                      },
                      "tags": ["engineering"]
                    }
                  ],
                  "skills": [],
                  "agents": [],
                  "hooks": [],
                  "instructions": []
                }
                """.trimIndent(),
            )
            writeJson(
                repository.resolve("source/plugins/core-plugin/plugin.json"),
                """
                {
                  "type": "PLUGIN",
                  "schemaVersion": 1,
                  "name": "core-plugin",
                  "version": "0.1.0",
                  "description": "Core plugin.",
                  "skills": [
                    {
                      "type": "SKILL",
                      "source": { "type": "LOCAL_SOURCE", "path": "./" },
                      "path": "skills/core-skill",
                      "name": "core-skill"
                    }
                  ]
                }
                """.trimIndent(),
            )
            writeJson(
                repository.resolve("source/plugins/unexposed-plugin/plugin.json"),
                """
                {
                  "type": "PLUGIN",
                  "schemaVersion": 1,
                  "name": "unexposed-plugin",
                  "version": "0.1.0",
                  "description": "Authored material outside this projection."
                }
                """.trimIndent(),
            )
            repository.resolve("source/skills/core-skill").createDirectories()
            repository.resolve("source/skills/core-skill/SKILL.md").toFile().writeText(
                """
                ---
                name: core-skill
                description: Core fixture skill.
                ---

                # Core skill
                """.trimIndent() + "\n",
            )
        }

    private fun writeJson(path: Path, content: String) {
        path.parent.createDirectories()
        JsonFiles.writeObject(path, JsonFiles.json.parseToJsonElement(content).let { element -> element as kotlinx.serialization.json.JsonObject })
    }
}
