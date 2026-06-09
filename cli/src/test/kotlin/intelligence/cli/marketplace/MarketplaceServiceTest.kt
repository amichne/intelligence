package intelligence.cli.marketplace

import intelligence.cli.io.arrayValue
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import intelligence.cli.validation.ValidationOptions
import intelligence.cli.validation.ValidationService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

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

    @Test
    fun `remote add list and remove manage repo-local marketplace registry`() {
        val repository = minimalMarketplaceRepository()
        val external = repository.resolve("external").resolve("shared-tools")
        external.createDirectories()
        val lines = mutableListOf<String>()
        val service = MarketplaceService(output = lines::add)

        service.addRemote(repository, "shared-tools", external.toString(), ref = null)
        service.listRemotes(repository)

        val marketplace = repository.resolve("source").resolve("adaptable.marketplace.json").readText()
        assertTrue(marketplace.contains("\"externalMarketplaces\""))
        assertTrue(marketplace.contains("\"allowExternalMarketplaces\""))
        assertTrue(lines.any { it.contains("shared-tools") && it.contains("external/shared-tools") })

        service.removeRemote(repository, "shared-tools")

        val removed = repository.resolve("source").resolve("adaptable.marketplace.json").readText()
        assertTrue(!removed.contains("\"externalMarketplaces\"") || !removed.contains("\"shared-tools\""))
    }

    @Test
    fun `import writes marketplace source reference lock and materializes imported plugin`() {
        val repository = minimalMarketplaceRepository()
        val external = repository.resolve("external").resolve("shared-tools")
        writeExternalMarketplace(external)
        val output = Files.createTempDirectory("intelligence-imported-marketplace-")
        val service = MarketplaceService(output = {})

        service.addRemote(repository, "shared-tools", external.toString(), ref = null)
        service.importPlugin(repository, "shared-tools/review-stack", "1.2.0")
        service.materialize(repository, output, MarketplaceProvider.Codex)

        val marketplace = JsonFiles.readObject(repository.resolve("source").resolve("adaptable.marketplace.json"))
        val imported = marketplace.arrayValue("plugins").single { it.jsonObject.stringValue("name") == "review-stack" }
        val source = imported.jsonObject.objectValue("plugin")!!.objectValue("source")!!
        assertEquals("MARKETPLACE_SOURCE", source.stringValue("type"))
        assertEquals("shared-tools", source.stringValue("marketplace"))
        assertTrue(repository.resolve(".intelligence").resolve("marketplace-lock.json").exists())
        assertTrue(
            output.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("review-stack")
                .resolve(".codex-plugin")
                .resolve("plugin.json")
                .exists()
        )
        assertEquals(
            0,
            ValidationService(output = {}).validate(
                ValidationOptions(
                    repo = repository,
                    portable = true,
                    hydrated = output,
                )
            )
        )
    }

    @Test
    fun `direct remote import defaults to main and writes reconstructable lock`() {
        val repository = minimalMarketplaceRepository()
        val remote = bareGitMarketplace("shared-tools")
        val output = Files.createTempDirectory("intelligence-direct-imported-marketplace-")
        val service = MarketplaceService(output = {})

        service.importPlugin(repository, "${remote}/review-stack", version = null)
        service.materialize(repository, output, MarketplaceProvider.Codex)

        val marketplace = JsonFiles.readObject(repository.resolve("source").resolve("adaptable.marketplace.json"))
        val external = marketplace.arrayValue("externalMarketplaces").single().jsonObject
        val externalSource = external.objectValue("source")!!
        val imported = marketplace.arrayValue("plugins").single { it.jsonObject.stringValue("name") == "review-stack" }
        val source = imported.jsonObject.objectValue("plugin")!!.objectValue("source")!!
        val lockPath = repository.resolve(".intelligence").resolve("marketplace-lock.json")
        val lock = JsonFiles.readObject(lockPath)
        val lockEntry = lock.arrayValue("entries").single().jsonObject

        assertEquals("shared-tools", external.stringValue("name"))
        assertEquals("GIT_SOURCE", externalSource.stringValue("type"))
        assertEquals("main", externalSource.stringValue("ref"))
        assertEquals("MARKETPLACE_SOURCE", source.stringValue("type"))
        assertEquals("shared-tools", source.stringValue("marketplace"))
        assertEquals("1.2.0", source.stringValue("version"))
        assertTrue(lockPath.exists())
        assertEquals("GIT_SOURCE", lockEntry.objectValue("resolvedSource")!!.stringValue("type"))
        assertTrue(lockEntry.objectValue("resolvedSource")!!.stringValue("sha").orEmpty().isNotBlank())
        assertTrue(
            output.resolve(".agents")
                .resolve("plugins")
                .resolve("plugins")
                .resolve("review-stack")
                .resolve(".codex-plugin")
                .resolve("plugin.json")
                .exists()
        )
        assertEquals(
            0,
            ValidationService(output = {}).validate(
                ValidationOptions(
                    repo = repository,
                    portable = true,
                    hydrated = output,
                )
            )
        )
    }

    @Test
    fun `import rejects requested version that does not match remote plugin`() {
        val repository = minimalMarketplaceRepository()
        val external = repository.resolve("external").resolve("shared-tools")
        writeExternalMarketplace(external)
        val service = MarketplaceService(output = {})

        service.addRemote(repository, "shared-tools", external.toString(), ref = null)

        val failure = assertFailsWith<MarketplaceFailure.InvalidSource> {
            service.importPlugin(repository, "shared-tools/review-stack", "9.9.9")
        }
        assertTrue(failure.message!!.contains("not requested version 9.9.9"))
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

    private fun writeExternalMarketplace(root: Path) {
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
                    "version": "1.2.0"
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
              "version": "1.2.0",
              "description": "Review stack.",
              "skills": []
            }
            """.trimIndent(),
        )
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
