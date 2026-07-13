package intelligence.cli.command

import intelligence.cli.BuildInfo
import intelligence.cli.github.GitHubCli
import intelligence.cli.io.ProcessRunner
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

internal class IntelligenceCommand(
    @Suppress("UNUSED_PARAMETER") processRunner: ProcessRunner = ProcessRunner.system(),
    github: GitHubCli = GitHubCli(),
    portableCacheRoot: Path? = null,
    portableEnvironmentOverride: PortableCommandEnvironment? = null,
) : CliktCommand(
    name = "intelligence",
) {
    override val invokeWithoutSubcommand: Boolean = true

    init {
        context {
            helpFormatter = { IntelligenceHelpFormatter(it) }
        }
        versionOption(BuildInfo.VERSION)
        val portableEnvironment = portableEnvironmentOverride ?: PortableCommandEnvironment(portableCacheRoot)
        subcommands(
            DoctorCommand(github, portableEnvironment),
            SetupCommand(portableEnvironment),
            ValidateCommand(),
            MarketplaceCommand(portableEnvironment),
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
