package intelligence.cli.command

import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.toCliPath
import intelligence.cli.marketplace.MarketplaceBrowseFormat
import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceBrowserService
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceProvider
import intelligence.cli.marketplace.MarketplaceService
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

internal class MarketplaceCommand(
    marketplaceService: MarketplaceService,
    browserService: MarketplaceBrowserService,
) : CliktCommand(
    name = "marketplace",
) {
    init {
        subcommands(
            BrowseMarketplaceCommand(browserService),
            RemoteMarketplaceCommand(marketplaceService),
            ImportMarketplaceCommand(marketplaceService),
            MaterializeMarketplaceCommand(marketplaceService),
            PublishMarketplaceCommand(marketplaceService),
            PublishMarketplaceBranchCommand(marketplaceService),
        )
    }

    override fun help(context: Context): String =
        "Browse, manage, import, project, and publish portable marketplaces."

    override fun run() = Unit
}

private class BrowseMarketplaceCommand(
    private val browserService: MarketplaceBrowserService,
) : CliktCommand(
    name = "browse",
) {
    private val repository by argument(
        name = "repository",
        help = "Local path, GitHub URL, or owner/repo shorthand to inspect.",
    )

    private val provider by option(
        "--provider",
        help = "Provider to browse: auto, codex, github, or source. Auto tries published provider marketplaces before source.",
    )
        .convert("PROVIDER") {
            try {
                MarketplaceBrowseProvider.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid provider")
            }
        }
        .default(MarketplaceBrowseProvider.Auto)

    private val format by option("--format", help = "Output format for the offering catalog: text or json.")
        .convert("FORMAT") {
            try {
                MarketplaceBrowseFormat.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid format")
            }
        }
        .default(MarketplaceBrowseFormat.Text)

    override fun help(context: Context): String =
        "Browse a repository marketplace by name or URL without typing marketplace paths."

    override fun run() {
        try {
            val result = browserService.browse(repository, provider)
            echo(
                when (format) {
                    MarketplaceBrowseFormat.Text -> result.renderText()
                    MarketplaceBrowseFormat.Json -> result.renderJson()
                }
            )
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace browse failed", statusCode = failure.exitCode)
        }
    }
}

private class RemoteMarketplaceCommand(
    marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "remote",
) {
    init {
        subcommands(
            AddRemoteMarketplaceCommand(marketplaceService),
            ListRemoteMarketplaceCommand(marketplaceService),
            RemoveRemoteMarketplaceCommand(marketplaceService),
        )
    }

    override fun help(context: Context): String =
        "Manage repo-local external marketplace names."

    override fun run() = Unit
}

private class AddRemoteMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "add",
) {
    private val repo by repoOption()

    private val name by argument(
        name = "name",
        help = "Repo-local marketplace name used by MARKETPLACE_SOURCE references.",
    )

    private val repository by argument(
        name = "repository",
        help = "Local repository path, GitHub owner/repo, GitHub URL, or git URL.",
    )

    private val ref by option("--ref", help = "Exact branch, tag, or SHA for git-backed marketplaces.")

    override fun help(context: Context): String =
        "Add a named external marketplace to the source graph."

    override fun run() {
        try {
            marketplaceService.addRemote(repoRoot = repo, name = name, repository = repository, ref = ref)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace remote add failed", statusCode = failure.exitCode)
        }
    }
}

private class ListRemoteMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "list",
) {
    private val repo by repoOption()

    override fun help(context: Context): String =
        "List named external marketplaces from the source graph."

    override fun run() {
        try {
            marketplaceService.listRemotes(repoRoot = repo)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace remote list failed", statusCode = failure.exitCode)
        }
    }
}

private class RemoveRemoteMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "remove",
) {
    private val repo by repoOption()

    private val name by argument(
        name = "name",
        help = "Repo-local marketplace name to remove.",
    )

    override fun help(context: Context): String =
        "Remove a named external marketplace from the source graph."

    override fun run() {
        try {
            marketplaceService.removeRemote(repoRoot = repo, name = name)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace remote remove failed", statusCode = failure.exitCode)
        }
    }
}

private class ImportMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "import",
) {
    private val repo by repoOption()

    private val plugin by argument(
        name = "marketplace/plugin",
        help = "Plugin to import by repo-local marketplace name and plugin name.",
    )

    private val version by option("--version", help = "Exact plugin version to import.")
        .required()

    override fun help(context: Context): String =
        "Import a plugin by portable marketplace reference."

    override fun run() {
        try {
            marketplaceService.importPlugin(repoRoot = repo, plugin = plugin, version = version)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace import failed", statusCode = failure.exitCode)
        }
    }
}

private class MaterializeMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "materialize",
) {
    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.Codex)

    private val out by option("--out", help = "Output directory to replace with generated provider files.")
        .convert("PATH") { it.toCliPath() }
        .required()

    override fun help(context: Context): String =
        "Render provider marketplace files into an output directory."

    override fun run() {
        try {
            marketplaceService.materialize(repo, out, provider)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace materialization failed", statusCode = failure.exitCode)
        }
    }
}

private class PublishMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "publish",
) {
    private val repo by repoOption()

    private val codex by option("--codex", help = "Publish the Codex orphan branch instead of default main payloads.")
        .flag(default = false)

    private val github by option("--github", help = "Publish the GitHub Copilot orphan branch instead of default main payloads.")
        .flag(default = false)

    private val copilot by option("--copilot", help = "Alias for --github.")
        .flag(default = false)

    private val noPush by option("--no-push", help = "Prepare provider branches locally without pushing.")
        .flag(default = false)

    override fun help(context: Context): String =
        "Publish default harness marketplaces or provider orphan branches."

    override fun run() {
        val branchProviders = linkedSetOf<MarketplaceProvider>().apply {
            if (codex) {
                add(MarketplaceProvider.Codex)
            }
            if (github || copilot) {
                add(MarketplaceProvider.GitHub)
            }
        }

        try {
            if (branchProviders.isEmpty()) {
                if (noPush) {
                    throw CliktError("--no-push only applies when publishing --codex, --github, or --copilot")
                }
                marketplaceService.publishDefault(repoRoot = repo)
            } else {
                branchProviders.forEach { provider ->
                    marketplaceService.publishBranch(
                        repoRoot = repo,
                        provider = provider,
                        branch = null,
                        noPush = noPush,
                    )
                }
            }
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace publication failed", statusCode = failure.exitCode)
        }
    }
}

private class PublishMarketplaceBranchCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "publish-branch",
) {
    override val hiddenFromHelp: Boolean = true

    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.Codex)

    private val branch by option("--branch", help = "Remote branch to update. Defaults to the provider branch.")

    private val noPush by option("--no-push", help = "Prepare the generated branch locally without pushing.")
        .flag(default = false)

    override fun help(context: Context): String =
        "Publish generated provider files to an orphan branch."

    override fun run() {
        try {
            marketplaceService.publishBranch(
                repoRoot = repo,
                provider = provider,
                branch = branch,
                noPush = noPush,
            )
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace publication failed", statusCode = failure.exitCode)
        }
    }
}

private fun CliktCommand.repoOption() =
    option("--repo", help = "Repository root containing source/adaptable.marketplace.json.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

private fun CliktCommand.providerOption(default: MarketplaceProvider) =
    option("--provider", help = "Marketplace provider: all, codex, or github.")
        .convert("PROVIDER") {
            try {
                MarketplaceProvider.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid provider")
            }
        }
        .default(default)
