package intelligence.cli.command

import intelligence.cli.rpc.RpcDispatcher
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

internal class RpcCommand(
    private val dispatcher: RpcDispatcher,
    private val inputLines: () -> Sequence<String> = { generateSequence { readlnOrNull() } },
) : CliktCommand(
    name = "rpc",
) {
    override fun help(context: Context): String =
        "Serve the stable JSON-RPC stdio contract for CLI and TUI clients."

    override fun run() {
        inputLines()
            .filter { it.isNotBlank() }
            .forEach { line -> echo(dispatcher.handleLine(line)) }
    }
}
