package intelligence.cli.command

import intelligence.cli.BuildInfo
import intelligence.cli.github.GitHubCli
import intelligence.cli.io.ProcessRunner
import intelligence.cli.rpc.RpcDispatcher
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

internal class IntelligenceCommand(
    processRunner: ProcessRunner = ProcessRunner.system(),
    github: GitHubCli = GitHubCli(),
    portableCacheRoot: Path? = null,
) : CliktCommand(
    name = "intelligence",
) {
    override val invokeWithoutSubcommand: Boolean = true

    init {
        context {
            helpFormatter = { IntelligenceHelpFormatter(it) }
        }
        versionOption(BuildInfo.VERSION)
        val dispatcher = RpcDispatcher(processRunner = processRunner)
        subcommands(
            DoctorCommand(github),
            SetupCommand(dispatcher, github),
            ValidateCommand(dispatcher),
            MarketplaceCommand(dispatcher, github, PortableCommandEnvironment(portableCacheRoot)),
            RpcCommand(dispatcher),
        )
    }

    override fun help(context: Context): String =
        "Operate portable plugin marketplaces across harness projections."

    override fun run() {
        if (currentContext.invokedSubcommand != null) {
            return
        }
        echoFormattedHelp()
    }
}
