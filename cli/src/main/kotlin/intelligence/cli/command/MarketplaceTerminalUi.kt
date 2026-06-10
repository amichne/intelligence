package intelligence.cli.command

import intelligence.cli.io.arrayValue
import intelligence.cli.io.stringValue
import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceBrowseText
import intelligence.cli.rpc.RpcDispatcher
import intelligence.cli.rpc.RpcMethod
import java.nio.file.Path
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class MarketplaceTerminalUi(
    private val dispatcher: RpcDispatcher,
    private val terminal: Terminal = Terminal(),
) {
    fun run(repoRoot: Path, ref: String?) {
        if (System.console() == null) {
            throw MarketplaceFailure.InvalidSource(
                "marketplace ui requires an interactive terminal; use `intelligence marketplace browse <repository>` " +
                    "or `intelligence marketplace import <repository>/<plugin>` in scripts"
            )
        }

        val repository = terminal.prompt("Repository", default = "amichne/intelligence")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw MarketplaceFailure.InvalidSource("repository is required")
        val catalog = executeRpcCall(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceBrowse,
            params = buildJsonObject {
                put("repository", repository)
                put("provider", MarketplaceBrowseProvider.Auto.cliName)
            },
            failureMessage = "marketplace browse failed",
        )
        terminal.println(MarketplaceBrowseText.render(catalog))

        when (terminal.prompt("Action", choices = listOf("import", "publish", "exit"), default = "import")) {
            "import" -> importPlugin(repoRoot, repository, ref, pluginNames(catalog))
            "publish" -> publish(repoRoot)
            else -> terminal.println("No changes made.")
        }
    }

    private fun importPlugin(repoRoot: Path, repository: String, ref: String?, plugins: List<String>) {
        if (plugins.isEmpty()) {
            throw MarketplaceFailure.InvalidSource("marketplace has no plugins to import")
        }
        val plugin = terminal.prompt("Plugin", choices = plugins, default = plugins.first())
            ?: throw MarketplaceFailure.InvalidSource("plugin selection is required")
        val version = terminal.prompt("Version (or remote)", default = "remote")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != "remote" }
        val result = executeRpcCall(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceImport,
            params = buildJsonObject {
                put("repoRoot", repoRoot.toString())
                put("target", "${repository.trimEnd('/')}/$plugin")
                version?.let { put("version", it) }
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace import failed",
        )
        result.messages().forEach(terminal::println)
    }

    private fun publish(repoRoot: Path) {
        when (
            terminal.prompt(
                "Publish",
                choices = listOf("default", "codex-preview", "github-preview", "exit"),
                default = "default",
            )
        ) {
            "default" -> publishDefault(repoRoot)
            "codex-preview" -> publishBranch(
                repoRoot = repoRoot,
                provider = intelligence.cli.marketplace.MarketplaceProvider.Codex.cliName,
            )
            "github-preview" -> publishBranch(
                repoRoot = repoRoot,
                provider = intelligence.cli.marketplace.MarketplaceProvider.GitHub.cliName,
            )
            else -> terminal.println("No changes made.")
        }
    }

    private fun publishDefault(repoRoot: Path) {
        val result = executeRpcCall(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplacePublishDefault,
            params = buildJsonObject {
                put("repoRoot", repoRoot.toString())
            },
            failureMessage = "marketplace publication failed",
        )
        result.messages().forEach(terminal::println)
    }

    private fun publishBranch(repoRoot: Path, provider: String) {
        val result = executeRpcCall(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplacePublishBranch,
            params = buildJsonObject {
                put("repoRoot", repoRoot.toString())
                put("provider", provider)
                put("noPush", true)
            },
            failureMessage = "marketplace publication failed",
        )
        result.messages().forEach(terminal::println)
    }
}

private fun pluginNames(catalog: JsonObject): List<String> =
    catalog.arrayValue("plugins")
        .mapNotNull { it as? JsonObject }
        .mapNotNull { it.stringValue("name") }

private fun JsonObject.messages(): List<String> =
    arrayValue("messages").mapNotNull { (it as? JsonPrimitive)?.content }
