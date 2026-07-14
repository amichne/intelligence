package intelligence.cli.command

import intelligence.cli.BuildInfo
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

internal class IntelligenceCommand : CliktCommand(
    name = "intelligence",
) {
    override val invokeWithoutSubcommand: Boolean = true

    init {
        context {
            helpFormatter = { IntelligenceHelpFormatter(it) }
        }
        versionOption(BuildInfo.VERSION)
        subcommands(
            ProjectCommand(),
        )
    }

    override fun help(context: Context): String =
        "Project provider-neutral agent tooling into harness-native material."

    override fun run() {
        if (currentContext.invokedSubcommand != null) {
            return
        }
        echoFormattedHelp()
    }
}
