package intelligence.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

internal class MarketplaceCommand(
    portableEnvironment: PortableCommandEnvironment = PortableCommandEnvironment(),
) : CliktCommand(name = "marketplace") {
    init {
        subcommands(
            *portableMarketplaceAuthorCommands(portableEnvironment).toTypedArray(),
            *localMarketplaceConsumerCommands(portableEnvironment).toTypedArray(),
        )
    }

    override fun help(context: Context): String =
        "Operate exact package-level marketplace snapshots without version or workflow inference."

    override fun run() = Unit
}
