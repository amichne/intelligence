package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import intelligence.cli.marketplace.MarketplaceBrowserService
import intelligence.cli.marketplace.MarketplaceService
import intelligence.cli.validation.ValidationService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

internal class IntelligenceCommand(
    processRunner: ProcessRunner = ProcessRunner.system(),
) : CliktCommand(
    name = "intelligence",
) {
    init {
        val validationService = ValidationService(output = { echo(it) })
        val marketplaceService = MarketplaceService(processRunner = processRunner, output = { echo(it) })
        val browserService = MarketplaceBrowserService()
        subcommands(
            ValidateCommand(validationService),
            MarketplaceCommand(marketplaceService, browserService),
        )
    }

    override fun help(context: Context): String =
        "Browse, validate, materialize, and publish Intelligence marketplaces."

    override fun run() = Unit
}
