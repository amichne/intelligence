package intelligence.cli.marketplace

import intelligence.cli.io.arrayValue
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import intelligence.cli.validation.ProjectionValidationOptions
import intelligence.cli.validation.ProjectionValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

class ProjectionPipelineTest {
    @Test
    fun `copilot provider alias resolves to github projection`() {
        assertEquals(MarketplaceProvider.GitHub, MarketplaceProvider.parse("copilot"))
    }

    @Test
    fun `materialize all serializes codex and github marketplace roots`() {
        val output = Files.createTempDirectory("intelligence-marketplace-test-")
        val service = MarketplaceProjector(output = {})

        service.materialize(
            repoRoot = repoRoot(),
            outRoot = output,
            provider = MarketplaceProvider.All,
        )

        assertTrue(output.resolve(".agents").resolve("plugins").resolve("marketplace.json").exists())
        val codexMarketplace =
            JsonFiles.readObject(output.resolve(".agents").resolve("plugins").resolve("marketplace.json"))
        val kotlinEngineeringEntry =
            codexMarketplace.arrayValue("plugins").single {
                it.jsonObject.stringValue("name") == "kotlin-engineering"
            }.jsonObject
        assertEquals(
            "./.agents/plugins/kotlin-engineering",
            kotlinEngineeringEntry.objectValue("source")!!.stringValue("path"),
        )
        assertTrue(
            output.resolve(".agents")
                .resolve("plugins")
                .resolve("kotlin-engineering")
                .exists()
        )
        assertTrue(output.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
        val githubMarketplace =
            JsonFiles.readObject(output.resolve(".github").resolve("plugin").resolve("marketplace.json"))
        assertEquals(".github/plugin", githubMarketplace.objectValue("metadata")!!.stringValue("pluginRoot"))
        val githubKotlinEngineeringEntry =
            githubMarketplace.arrayValue("plugins").single {
                it.jsonObject.stringValue("name") == "kotlin-engineering"
            }.jsonObject
        assertEquals("kotlin-engineering", githubKotlinEngineeringEntry.stringValue("source"))
        assertTrue(
            output.resolve(".github")
                .resolve("plugin")
                .resolve("kotlin-engineering")
                .resolve("AGENTS.md")
                .exists()
        )

        assertEquals(
            0,
            ProjectionValidator(output = {}).validate(
                ProjectionValidationOptions(
                    repo = repoRoot(),
                    hydrated = output,
                )
            )
        )
    }

    @Test
    fun `github hook metadata rewrites primitive dependencies to hydrated package paths`() {
        val repository = minimalMarketplaceRepository()
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
              "description": "Core plugin.",
              "instructions": [
                {
                  "type": "INSTRUCTION",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "concepts/type-safety/core.md",
                  "name": "type-safety"
                }
              ],
              "hooks": [
                {
                  "type": "HOOK",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "hooks/layout-check.hook.json",
                  "name": "layout-check"
                }
              ]
            }
            """.trimIndent(),
        )
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
                      "path": "./plugins/core-plugin"
                    },
                    "version": "0.1.0"
                  },
                  "tags": [
                    "kotlin"
                  ]
                }
              ],
              "instructions": [
                {
                  "type": "INSTRUCTION",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "concepts/type-safety/core.md",
                  "name": "type-safety"
                }
              ],
              "hooks": [
                {
                  "type": "HOOK",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "hooks/layout-check.hook.json",
                  "name": "layout-check"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repository.resolve("source")
                .resolve("concepts")
                .resolve("type-safety")
                .resolve("core.md"),
            """
            # Type Safety
            """.trimIndent(),
        )
        writeJson(
            repository.resolve("source")
                .resolve("hooks")
                .resolve("layout-check.hook.json"),
            """
            {
              "type": "HOOK",
              "source": {
                "type": "LOCAL_SOURCE",
                "path": "./"
              },
              "path": "hooks/codex/layout-check.hooks.json",
              "name": "layout-check",
              "dependsOn": [
                {
                  "type": "INSTRUCTION",
                  "source": {
                    "type": "LOCAL_SOURCE",
                    "path": "./"
                  },
                  "path": "concepts/type-safety/core.md",
                  "name": "type-safety"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repository.resolve("source")
                .resolve("hooks")
                .resolve("codex")
                .resolve("layout-check.hooks.json"),
            """
            {
              "hooks": {
                "Stop": [
                  {
                    "hooks": [
                      {
                        "type": "command",
                        "command": "bash hooks/layout-check.sh"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        repository.resolve("source")
            .resolve("hooks")
            .resolve("layout-check.sh")
            .also {
                it.parent.createDirectories()
                it.writeText("#!/usr/bin/env bash\n", Charsets.UTF_8)
            }
        val output = Files.createTempDirectory("intelligence-marketplace-github-hook-metadata-")

        MarketplaceProjector(output = {}).materialize(
            repoRoot = repository,
            outRoot = output,
            provider = MarketplaceProvider.GitHub,
        )

        val hookMetadata = JsonFiles.readObject(
            output.resolve(".github")
                .resolve("plugin")
                .resolve("core-plugin")
                .resolve("hooks")
                .resolve("metadata")
                .resolve("layout-check.hook.json")
        )
        val dependency = hookMetadata.arrayValue("dependsOn").single().jsonObject

        assertEquals("hooks/layout-check.hooks.json", hookMetadata.stringValue("path"))
        assertEquals("instructions/type-safety.md", dependency.stringValue("path"))
    }

    @Test
    fun `source validation rejects non https plugin interface URLs`() {
        val repository = Files.createTempDirectory("intelligence-marketplace-interface-validation-")
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
                  "name": "core-plugin",
                  "plugin": {
                    "source": {
                      "type": "LOCAL_SOURCE",
                      "path": "plugins/core-plugin"
                    },
                    "version": "0.1.0"
                  }
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
              "description": "Core plugin.",
              "interface": {
                "websiteURL": "http://example.invalid"
              }
            }
            """.trimIndent(),
        )

        val result = ProjectionValidator(output = {}).validate(
            ProjectionValidationOptions(
                repo = repository,
                hydrated = null,
            ),
        )

        assertTrue(result != 0)
    }

    @Test
    fun `hydrated codex validation rejects nested plugin source paths`() {
        val repository = Files.createTempDirectory("intelligence-marketplace-stale-codex-path-")
        writeJson(
            repository.resolve(".agents").resolve("plugins").resolve("marketplace.json"),
            """
            {
              "name": "fixture-marketplace",
              "plugins": [
                {
                  "name": "core-plugin",
                  "source": {
                    "source": "local",
                    "path": "./.agents/plugins/plugins/core-plugin"
                  },
                  "policy": {
                    "installation": "AVAILABLE",
                    "authentication": "ON_INSTALL"
                  },
                  "category": "Engineering"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repository.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("core-plugin")
                .resolve(".codex-plugin")
                .resolve("plugin.json"),
            """
            {
              "name": "core-plugin",
              "version": "0.1.0",
              "description": "Nested generated Codex plugin."
            }
            """.trimIndent(),
        )

        val result = ProjectionValidator(output = {}).validate(
            ProjectionValidationOptions(
                repo = repository,
                hydrated = repository,
            ),
        )

        assertTrue(result != 0)
    }

    @Test
    fun `hydrated github validation rejects nested plugin root`() {
        val repository = Files.createTempDirectory("intelligence-marketplace-stale-github-path-")
        writeJson(
            repository.resolve(".github").resolve("plugin").resolve("marketplace.json"),
            """
            {
              "name": "fixture-marketplace",
              "owner": {
                "name": "Fixture Owner"
              },
              "metadata": {
                "pluginRoot": ".github/plugin/plugins"
              },
              "plugins": [
                {
                  "name": "core-plugin",
                  "source": "core-plugin"
                }
              ]
            }
            """.trimIndent(),
        )
        writeJson(
            repository.resolve(".github")
                .resolve("plugin")
                .resolve("plugins")
                .resolve("core-plugin")
                .resolve("AGENTS.md"),
            "# Core Plugin\n",
        )

        val result = ProjectionValidator(output = {}).validate(
            ProjectionValidationOptions(
                repo = repository,
                hydrated = repository,
            ),
        )

        assertTrue(result != 0)
    }

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { it.resolve("source").resolve("adaptable.marketplace.json").toFile().isFile }

    private fun writeJson(path: Path, content: String) {
        path.parent.createDirectories()
        path.writeText(content.trimIndent() + "\n")
    }

    private fun minimalMarketplaceRepository(): Path {
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
              "plugins": []
            }
            """.trimIndent(),
        )
        return repository
    }
}
