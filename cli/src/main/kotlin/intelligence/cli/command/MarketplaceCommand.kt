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
            MaterializeMarketplaceCommand(marketplaceService),
            PublishMarketplaceBranchCommand(marketplaceService),
        )
    }

    override fun help(context: Context): String =
        "Browse marketplace offerings and manage provider projections."

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

private class PublishMarketplaceBranchCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "publish-branch",
) {
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
