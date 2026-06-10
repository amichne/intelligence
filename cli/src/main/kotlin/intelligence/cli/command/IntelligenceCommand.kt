package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import intelligence.cli.rpc.RpcDispatcher
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands

internal class IntelligenceCommand(
    processRunner: ProcessRunner = ProcessRunner.system(),
) : CliktCommand(
    name = "intelligence",
) {
    init {
        context {
            helpFormatter = { IntelligenceHelpFormatter(it) }
        }
        val dispatcher = RpcDispatcher(processRunner = processRunner)
        subcommands(
            ValidateCommand(dispatcher),
            MarketplaceCommand(dispatcher),
            RpcCommand(dispatcher),
        )
    }

    override fun help(context: Context): String =
        "Operate portable plugin marketplaces across harness projections."

    override fun run() = Unit
}
