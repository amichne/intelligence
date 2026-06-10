package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import intelligence.cli.rpc.RpcDispatcher
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands

internal class IntelligenceCommand(
    processRunner: ProcessRunner = ProcessRunner.system(),
    private val terminalUiLauncher: TerminalUiLauncher = TerminalUiLauncher(processRunner),
    private val isInteractiveTerminal: () -> Boolean = { System.console() != null },
) : CliktCommand(
    name = "intelligence",
) {
    override val invokeWithoutSubcommand: Boolean = true

    init {
        context {
            helpFormatter = { IntelligenceHelpFormatter(it) }
        }
        val dispatcher = RpcDispatcher(processRunner = processRunner)
        subcommands(
            ValidateCommand(dispatcher),
            MarketplaceCommand(dispatcher, terminalUiLauncher),
            RpcCommand(dispatcher),
        )
    }

    override fun help(context: Context): String =
        "Operate portable plugin marketplaces across harness projections."

    override fun run() {
        if (currentContext.invokedSubcommand != null) {
            return
        }
        if (!isInteractiveTerminal()) {
            echoFormattedHelp()
            return
        }
        val exitCode = terminalUiLauncher.launch(repoRoot = Path.of(".").toAbsolutePath().normalize())
        if (exitCode != 0) {
            throw com.github.ajalt.clikt.core.CliktError(
                "terminal UI exited with status $exitCode",
                statusCode = exitCode,
            )
        }
    }
}
