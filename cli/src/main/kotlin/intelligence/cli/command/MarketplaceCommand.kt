package intelligence.cli.command

import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.toCliPath
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceProvider
import intelligence.cli.marketplace.MarketplaceService
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

internal class MarketplaceCommand(
    marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "marketplace",
) {
    init {
        subcommands(
            MaterializeMarketplaceCommand(marketplaceService),
            PublishMarketplaceBranchCommand(marketplaceService),
        )
    }

    override fun help(context: Context): String =
        "Serialize and publish provider-specific marketplace projections."

    override fun run() = Unit
}

private class MaterializeMarketplaceCommand(
    private val marketplaceService: MarketplaceService,
) : CliktCommand(
    name = "materialize",
) {
    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.Codex)

    private val out by option("--out", help = "Output directory to replace.")
        .convert("PATH") { it.toCliPath() }
        .required()

    override fun help(context: Context): String =
        "Materialize a Codex, GitHub Copilot, or combined marketplace root."

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

    private val branch by option("--branch", help = "Remote branch to update. Defaults from the provider.")

    private val noPush by option("--no-push", help = "Prepare the generated branch locally without pushing.")
        .flag(default = false)

    override fun help(context: Context): String =
        "Publish a generated orphan marketplace branch."

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
