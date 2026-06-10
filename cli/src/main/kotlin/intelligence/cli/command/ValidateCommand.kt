package intelligence.cli.command

import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.toCliPath
import intelligence.cli.io.stringValue
import intelligence.cli.rpc.RpcDispatcher
import intelligence.cli.rpc.RpcMethod
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ValidateCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "validate",
) {
    private val repo by option("--repo", help = "Repository root to validate.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

    private val portable by option("--portable", help = "Skip host-local validation checks.")
        .flag(default = false)

    private val hydrated by option("--hydrated", help = "Hydrated marketplace output to validate.")
        .convert("PATH") { it.toCliPath() }

    override fun help(context: Context): String =
        "Validate marketplace source and hydrated provider outputs."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.ValidationRun,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("portable", portable)
                hydrated?.let { put("hydrated", it.toString()) }
            },
            failureMessage = "validation failed",
        )
        echoRpcMessages(result)
        val exitCode = result.stringValue("exitCode")?.toIntOrNull() ?: 0

        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }
}
