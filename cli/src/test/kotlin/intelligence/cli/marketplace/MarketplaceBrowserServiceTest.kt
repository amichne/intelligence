package intelligence.cli.marketplace

import intelligence.cli.io.JsonFiles
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

class MarketplaceBrowserServiceTest {
    @Test
    fun `copilot browse provider alias resolves to github projection`() {
        assertEquals(MarketplaceBrowseProvider.GitHub, MarketplaceBrowseProvider.parse("copilot"))
    }

    @Test
    fun `source browse exposes only standalone primitives listed by marketplace`() {
        val repo = Files.createTempDirectory("intelligence-browse-source-")
        writeJson(
            repo.resolve("source").resolve("adaptable.marketplace.json"),
            """
            {
              "type": "MARKETPLACE",
              "schemaVersion": 1,
              "name": "fixture-marketplace",
              "owner": {
                "name": "Fixture"
              },
              "description": "Fixture marketplace.",
              "plugins": [
                {
                  "type": "PLUGIN_ENTRY",
                  "name": "core-plugin",
                  "plugin": {
                    "type": "PLUGIN_REFERENCE",
                    "name": "core-plugin",
                    "source": {
                      "type": "LOCAL_SOURCE",
                      "path": "./plugins/core-plugin"
                    },
                    "version": "0.1.0"
                  },
                  "description": "Core plugin."
                }
              ],
              "skills": [
                {
                  "type": "SKILL",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "skills/public-skill",
                  "name": "public-skill"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repo.resolve("source").resolve("plugins").resolve("core-plugin").resolve("plugin.json"),
            """
            {
              "type": "PLUGIN",
              "schemaVersion": 1,
              "name": "core-plugin",
              "skills": [
                {
                  "type": "SKILL",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "skills/hidden-skill",
                  "name": "hidden-skill"
                }
              ],
              "agents": [],
              "instructions": [],
              "hooks": []
            }
            """.trimIndent(),
        )

        val result = MarketplaceBrowserService().browse(repo.toString(), MarketplaceBrowseProvider.Source)

        assertEquals(MarketplaceBrowseProvider.Source, result.summary.provider)
        assertEquals(listOf("core-plugin"), result.plugins.map { it.name })
        assertEquals(listOf("public-skill"), result.standalonePrimitives.map { it.name })
        assertFalse(result.renderText().contains("hidden-skill"))
        assertTrue(result.renderText().contains("public-skill"))
    }

    @Test
    fun `auto browse prefers generated codex marketplace when present`() {
        val repo = Files.createTempDirectory("intelligence-browse-codex-")
        writeJson(
            repo.resolve(".agents").resolve("plugins").resolve("marketplace.json"),
            """
            {
              "name": "fixture-marketplace",
              "plugins": [
                {
                  "name": "core-plugin",
                  "source": {
                    "source": "local",
                    "path": "./plugins/core-plugin"
                  },
                  "category": "Engineering"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repo.resolve("plugins").resolve("core-plugin").resolve(".codex-plugin").resolve("plugin.json"),
            """
            {
              "name": "core-plugin",
              "version": "0.1.0",
              "description": "Generated Codex plugin.",
              "keywords": [
                "core-plugin",
                "engineering"
              ]
            }
            """.trimIndent(),
        )

        val result = MarketplaceBrowserService().browse(repo.toString(), MarketplaceBrowseProvider.Auto)
        val json = JsonFiles.json.parseToJsonElement(result.renderJson()).jsonObject

        assertEquals(MarketplaceBrowseProvider.Codex, result.summary.provider)
        assertEquals("Generated Codex plugin.", result.plugins.single().description)
        assertTrue(result.standalonePrimitives.isEmpty())
        assertEquals("codex", json["marketplace"]!!.jsonObject["provider"]!!.toString().trim('"'))
    }

    @Test
    fun `auto browse reads codex marketplace relative plugin payloads`() {
        val repo = Files.createTempDirectory("intelligence-browse-codex-relative-")
        writeJson(
            repo.resolve(".agents").resolve("plugins").resolve("marketplace.json"),
            """
            {
              "name": "fixture-marketplace",
              "plugins": [
                {
                  "name": "core-plugin",
                  "source": {
                    "source": "local",
                    "path": "./plugins/core-plugin"
                  },
                  "category": "Engineering"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repo.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("core-plugin")
                .resolve(".codex-plugin")
                .resolve("plugin.json"),
            """
            {
              "name": "core-plugin",
              "version": "0.1.0",
              "description": "Marketplace-relative Codex plugin.",
              "keywords": [
                "core-plugin",
                "engineering"
              ]
            }
            """.trimIndent(),
        )

        val result = MarketplaceBrowserService().browse(repo.toString(), MarketplaceBrowseProvider.Auto)

        assertEquals(MarketplaceBrowseProvider.Codex, result.summary.provider)
        assertEquals("Marketplace-relative Codex plugin.", result.plugins.single().description)
    }

    private fun writeJson(path: Path, content: String) {
        path.parent.createDirectories()
        path.writeText(content.trimIndent() + "\n")
    }
}
