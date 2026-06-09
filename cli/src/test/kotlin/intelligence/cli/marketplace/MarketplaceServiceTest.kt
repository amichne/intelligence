package intelligence.cli.marketplace

import intelligence.cli.validation.ValidationOptions
import intelligence.cli.validation.ValidationService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketplaceServiceTest {
    @Test
    fun `copilot provider alias resolves to github projection`() {
        assertEquals(MarketplaceProvider.GitHub, MarketplaceProvider.parse("copilot"))
    }

    @Test
    fun `materialize all serializes codex and github marketplace roots`() {
        val output = Files.createTempDirectory("intelligence-marketplace-test-")
        val service = MarketplaceService(output = {})

        service.materialize(
            repoRoot = repoRoot(),
            outRoot = output,
            provider = MarketplaceProvider.All,
        )

        assertTrue(output.resolve(".agents").resolve("plugins").resolve("marketplace.json").exists())
        assertTrue(
            output.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("kotlin-engineering")
                .exists()
        )
        assertTrue(output.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
        assertTrue(
            output.resolve(".github")
                .resolve("plugin")
                .resolve("plugins")
                .resolve("kotlin-engineering")
                .resolve("AGENTS.md")
                .exists()
        )

        assertEquals(
            0,
            ValidationService(output = {}).validate(
                ValidationOptions(
                    repo = repoRoot(),
                    portable = true,
                    hydrated = output,
                )
            )
        )
    }

    @Test
    fun `publish default writes harness marketplaces inside repository root`() {
        val repository = Files.createTempDirectory("intelligence-marketplace-default-")
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
                  "name": "core-plugin",
                  "plugin": {
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

        MarketplaceService(output = {}).publishDefault(repository)

        assertTrue(repository.resolve(".agents").resolve("plugins").resolve("marketplace.json").exists())
        assertTrue(
            repository.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("core-plugin")
                .resolve(".codex-plugin")
                .resolve("plugin.json")
                .exists()
        )
        assertTrue(repository.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
        assertTrue(
            repository.resolve(".github")
                .resolve("plugin")
                .resolve("plugins")
                .resolve("core-plugin")
                .exists()
        )

        assertEquals(
            0,
            ValidationService(output = {}).validate(
                ValidationOptions(
                    repo = repository,
                    portable = true,
                    hydrated = repository,
                )
            )
        )
    }

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { it.resolve("source").resolve("adaptable.marketplace.json").toFile().isFile }

    private fun writeJson(path: Path, content: String) {
        path.parent.createDirectories()
        path.writeText(content.trimIndent() + "\n")
    }
}
