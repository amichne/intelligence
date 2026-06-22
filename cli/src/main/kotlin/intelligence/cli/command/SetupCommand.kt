package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.stringValue
import intelligence.cli.io.toCliPath
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class SetupCommand(
    private val dispatcher: RpcDispatcher,
    private val github: GitHubCli,
) : CliktCommand(
    name = "setup",
) {
    private val repo by option(
        "--repo",
        help = "Repository root to initialize with locked Intelligence marketplace state.",
    )
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

    private val marketplace by option(
        "--marketplace",
        help = "Marketplace repository to consume. Defaults to amichne/slopsentral.",
    )
        .default(DEFAULT_MARKETPLACE)

    private val plugin by option(
        "--plugin",
        help = "Marketplace plugin to import. Defaults to kotlin-engineering.",
    )
        .default(DEFAULT_PLUGIN)

    private val version by option(
        "--version",
        help = "Exact plugin version to import. Defaults to the remote plugin version.",
    )

    private val ref by option(
        "--ref",
        help = "Branch, tag, or SHA for direct repository setup. Defaults to main.",
    )

    private val host by option(
        "--host",
        help = "GitHub host for owner/repo marketplace shorthand. Defaults to the active gh host.",
    )

    private val noValidate by option(
        "--no-validate",
        help = "Skip portable validation after setup writes marketplace state.",
    )
        .flag(default = false)

    override fun help(context: Context): String =
        "Set up a repository to consume the default locked Intelligence marketplace plugin."

    override fun run() {
        val target = SetupTarget(marketplace = marketplace, plugin = plugin)
        val importTarget = target.importTarget(github = github, host = host)
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceImport,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("target", importTarget)
                version?.let { put("version", it) }
                ref?.let { put("ref", it) }
            },
            failureMessage = "setup failed",
        )
        echoRpcMessages(result)
        if (!noValidate) {
            validateSetup(repo)
        }
        echo("setup complete: ${repo} consumes $importTarget")
    }

    private fun validateSetup(repo: Path) {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.ValidationRun,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("portable", true)
            },
            failureMessage = "post-setup validation failed",
        )
        echoRpcMessages(result)
        val exitCode = result.stringValue("exitCode")?.toIntOrNull() ?: 0
        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }

    private data class SetupTarget(
        val marketplace: String,
        val plugin: String,
    ) {
        init {
            if (marketplace.isBlank()) {
                throw CliktError("--marketplace must not be blank")
            }
            if (plugin.isBlank()) {
                throw CliktError("--plugin must not be blank")
            }
        }

        fun importTarget(github: GitHubCli, host: String?): String {
            val repository = github.normalizeRepository(marketplace.trim(), host).trimEnd('/')
            return "$repository/${plugin.trim()}"
        }
    }

    private companion object {
        const val DEFAULT_MARKETPLACE = "amichne/slopsentral"
        const val DEFAULT_PLUGIN = "kotlin-engineering"
    }
}
