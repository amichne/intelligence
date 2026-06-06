package intelligence.cli.marketplace

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class MarketplaceServiceTest {
    @Test
    fun `materialize all serializes codex and github marketplace roots`() {
        val output = Files.createTempDirectory("intelligence-marketplace-test-")
        val service = MarketplaceService(output = {})

        service.materialize(
            repoRoot = repoRoot(),
            outRoot = output,
            provider = MarketplaceProvider.All,
        )

        assertTrue(output.resolve("codex").resolve("marketplace.json").exists())
        assertTrue(output.resolve("codex").resolve("plugins").resolve("kotlin-engineering").exists())
        assertTrue(output.resolve(".github").resolve("plugin").resolve("marketplace.json").exists())
        assertTrue(
            output.resolve(".github")
                .resolve("plugin")
                .resolve("plugins")
                .resolve("kotlin-engineering")
                .resolve("AGENTS.md")
                .exists()
        )
    }

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { it.resolve("source").resolve("adaptable.marketplace.json").toFile().isFile }
}
