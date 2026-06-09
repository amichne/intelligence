package intelligence.cli.command

import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceBrowserService
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceService
import java.nio.file.Path
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt

internal class MarketplaceTerminalUi(
    private val marketplaceService: MarketplaceService,
    private val browserService: MarketplaceBrowserService,
    private val terminal: Terminal = Terminal(),
) {
    fun run(repoRoot: Path, ref: String?) {
        if (System.console() == null) {
            throw MarketplaceFailure.InvalidSource(
                "marketplace ui requires an interactive terminal; use `intelligence marketplace browse <repository>` " +
                    "or `intelligence marketplace import <repository>/<plugin>` in scripts"
            )
        }

        val repository = terminal.prompt("Repository", default = "amichne/intelligence")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw MarketplaceFailure.InvalidSource("repository is required")
        val catalog = browserService.browse(repository, MarketplaceBrowseProvider.Auto)
        terminal.println(catalog.renderText())

        when (terminal.prompt("Action", choices = listOf("import", "publish", "exit"), default = "import")) {
            "import" -> importPlugin(repoRoot, repository, ref, catalog.plugins.map { it.name })
            "publish" -> publish(repoRoot)
            else -> terminal.println("No changes made.")
        }
    }

    private fun importPlugin(repoRoot: Path, repository: String, ref: String?, plugins: List<String>) {
        if (plugins.isEmpty()) {
            throw MarketplaceFailure.InvalidSource("marketplace has no plugins to import")
        }
        val plugin = terminal.prompt("Plugin", choices = plugins, default = plugins.first())
            ?: throw MarketplaceFailure.InvalidSource("plugin selection is required")
        val version = terminal.prompt("Version (or remote)", default = "remote")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != "remote" }
        marketplaceService.importPlugin(
            repoRoot = repoRoot,
            target = "${repository.trimEnd('/')}/$plugin",
            version = version,
            ref = ref,
        )
    }

    private fun publish(repoRoot: Path) {
        when (
            terminal.prompt(
                "Publish",
                choices = listOf("default", "codex-preview", "github-preview", "exit"),
                default = "default",
            )
        ) {
            "default" -> marketplaceService.publishDefault(repoRoot)
            "codex-preview" -> marketplaceService.publishBranch(
                repoRoot = repoRoot,
                provider = intelligence.cli.marketplace.MarketplaceProvider.Codex,
                branch = null,
                noPush = true,
            )
            "github-preview" -> marketplaceService.publishBranch(
                repoRoot = repoRoot,
                provider = intelligence.cli.marketplace.MarketplaceProvider.GitHub,
                branch = null,
                noPush = true,
            )
            else -> terminal.println("No changes made.")
        }
    }
}
