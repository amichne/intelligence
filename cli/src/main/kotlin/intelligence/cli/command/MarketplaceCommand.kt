package intelligence.cli.command

import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.arrayValue
import intelligence.cli.io.toCliPath
import intelligence.cli.marketplace.MarketplaceBrowseFormat
import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceBrowseText
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceProvider
import intelligence.cli.rpc.RpcDispatcher
import intelligence.cli.rpc.RpcMethod
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class MarketplaceCommand(
    dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "marketplace",
) {
    init {
        subcommands(
            BrowseMarketplaceCommand(dispatcher),
            RemoteMarketplaceCommand(dispatcher),
            ImportMarketplaceCommand(dispatcher),
            InstallMarketplaceCommand(dispatcher),
            MarketplaceUiCommand(dispatcher),
            MaterializeMarketplaceCommand(dispatcher),
            PublishMarketplaceCommand(dispatcher),
            PublishMarketplaceBranchCommand(dispatcher),
        )
    }

    override fun help(context: Context): String =
        "Browse, manage, import, project, and publish portable marketplaces."

    override fun run() = Unit
}

private class BrowseMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "browse",
) {
    private val repository by argument(
        name = "repository",
        help = "Local path, GitHub URL, or owner/repo shorthand to inspect.",
    )

    private val provider by option(
        "--provider",
        help = "Provider to browse: auto, codex, github, or source. Auto tries published provider marketplaces before source.",
    )
        .convert("PROVIDER") {
            try {
                MarketplaceBrowseProvider.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid provider")
            }
        }
        .default(MarketplaceBrowseProvider.Auto)

    private val format by option("--format", help = "Output format for the offering catalog: text or json.")
        .convert("FORMAT") {
            try {
                MarketplaceBrowseFormat.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid format")
            }
        }
        .default(MarketplaceBrowseFormat.Text)

    override fun help(context: Context): String =
        "Browse a repository marketplace by name or URL without typing marketplace paths."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceBrowse,
            params = buildJsonObject {
                put("repository", repository)
                put("provider", provider.cliName)
            },
            failureMessage = "marketplace browse failed",
        )
        echo(
            when (format) {
                MarketplaceBrowseFormat.Text -> MarketplaceBrowseText.render(result)
                MarketplaceBrowseFormat.Json -> JsonFiles.json.encodeToString(JsonElement.serializer(), result)
            }
        )
    }
}

private class RemoteMarketplaceCommand(
    dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "remote",
) {
    init {
        subcommands(
            AddRemoteMarketplaceCommand(dispatcher),
            ListRemoteMarketplaceCommand(dispatcher),
            RemoveRemoteMarketplaceCommand(dispatcher),
        )
    }

    override fun help(context: Context): String =
        "Manage repo-local external marketplace names."

    override fun run() = Unit
}

private class AddRemoteMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "add",
) {
    private val repo by repoOption()

    private val name by argument(
        name = "name",
        help = "Repo-local marketplace name used by MARKETPLACE_SOURCE references.",
    )

    private val repository by argument(
        name = "repository",
        help = "Local repository path, GitHub owner/repo, GitHub URL, or git URL.",
    )

    private val ref by option("--ref", help = "Exact branch, tag, or SHA for git-backed marketplaces.")

    override fun help(context: Context): String =
        "Add a named external marketplace to the source graph."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceRemotesAdd,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("name", name)
                put("repository", repository)
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace remote add failed",
        )
        echoRpcMessages(result)
    }
}

private class ListRemoteMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "list",
) {
    private val repo by repoOption()

    override fun help(context: Context): String =
        "List named external marketplaces from the source graph."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceRemotesList,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
            },
            failureMessage = "marketplace remote list failed",
        )
        val remotes = result.arrayValue("remotes").mapNotNull { it as? JsonObject }
        if (remotes.isEmpty()) {
            echoRpcMessages(result)
        } else {
            remotes.forEach { remote ->
                echo("${remote["name"].asString()}\t${remote["source"].asString()}")
            }
        }
    }
}

private class RemoveRemoteMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "remove",
) {
    private val repo by repoOption()

    private val name by argument(
        name = "name",
        help = "Repo-local marketplace name to remove.",
    )

    override fun help(context: Context): String =
        "Remove a named external marketplace from the source graph."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceRemotesRemove,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("name", name)
            },
            failureMessage = "marketplace remote remove failed",
        )
        echoRpcMessages(result)
    }
}

private class ImportMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "import",
) {
    private val repo by repoOption()

    private val plugin by argument(
        name = "marketplace/plugin|repository/plugin",
        help = "Plugin to import by named marketplace or direct repository reference.",
    )

    private val version by option("--version", help = "Exact plugin version to import. Defaults to the remote plugin version.")

    private val ref by option("--ref", help = "Branch, tag, or SHA for direct repository imports. Defaults to main.")

    override fun help(context: Context): String =
        "Import a plugin by portable marketplace reference or direct repository/plugin reference."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceImport,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("target", plugin)
                version?.let { put("version", it) }
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace import failed",
        )
        echoRpcMessages(result)
    }
}

private class InstallMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "install",
) {
    private val repo by repoOption()

    private val repository by argument(
        name = "repository",
        help = "Local path, GitHub owner/repo, GitHub URL, or git URL exposing an adaptable marketplace.",
    )

    private val ref by option("--ref", help = "Branch, tag, or SHA for repository resolution. Defaults to main.")

    override fun help(context: Context): String =
        "Install every plugin exposed by an adaptable marketplace repository."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceInstall,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("repository", repository)
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace install failed",
        )
        echoRpcMessages(result)
    }
}

private class MarketplaceUiCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "ui",
) {
    private val repo by repoOption()

    private val ref by option("--ref", help = "Branch, tag, or SHA for direct imports selected in the UI. Defaults to main.")

    override fun help(context: Context): String =
        "Interactively browse, import, and publish marketplace offerings."

    override fun run() {
        try {
            MarketplaceTerminalUi(dispatcher).run(repoRoot = repo, ref = ref)
        } catch (failure: MarketplaceFailure) {
            throw CliktError(failure.message ?: "marketplace ui failed", statusCode = failure.exitCode)
        }
    }
}

private class MaterializeMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "materialize",
) {
    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.Codex)

    private val out by option("--out", help = "Output directory to replace with generated provider files.")
        .convert("PATH") { it.toCliPath() }
        .required()

    override fun help(context: Context): String =
        "Render provider marketplace files into an output directory."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceMaterialize,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("outRoot", out.toString())
                put("provider", provider.cliName)
            },
            failureMessage = "marketplace materialization failed",
        )
        echoRpcMessages(result)
    }
}

private class PublishMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "publish",
) {
    private val repo by repoOption()

    private val codex by option("--codex", help = "Publish the Codex orphan branch instead of default main payloads.")
        .flag(default = false)

    private val github by option("--github", help = "Publish the GitHub Copilot orphan branch instead of default main payloads.")
        .flag(default = false)

    private val copilot by option("--copilot", help = "Alias for --github.")
        .flag(default = false)

    private val noPush by option("--no-push", help = "Prepare provider branches locally without pushing.")
        .flag(default = false)

    override fun help(context: Context): String =
        "Publish default harness marketplaces or provider orphan branches."

    override fun run() {
        val branchProviders = linkedSetOf<MarketplaceProvider>().apply {
            if (codex) {
                add(MarketplaceProvider.Codex)
            }
            if (github || copilot) {
                add(MarketplaceProvider.GitHub)
            }
        }

        if (branchProviders.isEmpty()) {
            if (noPush) {
                throw CliktError("--no-push only applies when publishing --codex, --github, or --copilot")
            }
            val result = executeRpc(
                dispatcher = dispatcher,
                method = RpcMethod.MarketplacePublishDefault,
                params = buildJsonObject {
                    put("repoRoot", repo.toString())
                },
                failureMessage = "marketplace publication failed",
            )
            echoRpcMessages(result)
        } else {
            branchProviders.forEach { provider ->
                val result = executeRpc(
                    dispatcher = dispatcher,
                    method = RpcMethod.MarketplacePublishBranch,
                    params = buildJsonObject {
                        put("repoRoot", repo.toString())
                        put("provider", provider.cliName)
                        put("noPush", noPush)
                    },
                    failureMessage = "marketplace publication failed",
                )
                echoRpcMessages(result)
            }
        }
    }
}

private class PublishMarketplaceBranchCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "publish-branch",
) {
    override val hiddenFromHelp: Boolean = true

    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.Codex)

    private val branch by option("--branch", help = "Remote branch to update. Defaults to the provider branch.")

    private val noPush by option("--no-push", help = "Prepare the generated branch locally without pushing.")
        .flag(default = false)

    override fun help(context: Context): String =
        "Publish generated provider files to an orphan branch."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplacePublishBranch,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("provider", provider.cliName)
                branch?.let { put("branch", it) }
                put("noPush", noPush)
            },
            failureMessage = "marketplace publication failed",
        )
        echoRpcMessages(result)
    }
}

private fun CliktCommand.repoOption() =
    option("--repo", help = "Repository root containing an adaptable marketplace or install state.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

private fun CliktCommand.providerOption(default: MarketplaceProvider) =
    option("--provider", help = "Marketplace provider: all, codex, or github.")
        .convert("PROVIDER") {
            try {
                MarketplaceProvider.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid provider")
            }
        }
        .default(default)

private fun JsonElement?.asString(): String =
    (this as? JsonPrimitive)?.content.orEmpty()
