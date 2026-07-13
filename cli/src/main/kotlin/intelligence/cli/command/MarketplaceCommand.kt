package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import intelligence.cli.github.GitHubCliFailure
import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.FileSystem
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.arrayValue
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import intelligence.cli.io.toCliPath
import intelligence.cli.marketplace.MarketplaceBrowseFormat
import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceBrowseText
import intelligence.cli.marketplace.MarketplaceProvider
import intelligence.cli.rpc.RpcDispatcher
import intelligence.cli.rpc.RpcMethod
import java.nio.file.Files
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class MarketplaceCommand(
    dispatcher: RpcDispatcher,
    github: GitHubCli,
    portableEnvironment: PortableCommandEnvironment = PortableCommandEnvironment(),
) : CliktCommand(
    name = "marketplace",
) {
    init {
        subcommands(
            BrowseMarketplaceCommand(dispatcher, github),
            SearchMarketplaceCommand(dispatcher, github),
            InspectMarketplaceCommand(dispatcher, github),
            InstalledMarketplaceCommand(dispatcher),
            VersionsMarketplaceCommand(dispatcher),
            RemoteMarketplaceCommand(dispatcher, github),
            ImportMarketplaceCommand(dispatcher, github),
            InstallMarketplaceCommand(dispatcher, github),
            PinMarketplaceCommand(dispatcher),
            UnpinMarketplaceCommand(dispatcher),
            MaterializeMarketplaceCommand(dispatcher),
            PublishMarketplaceCommand(dispatcher),
            PublishMarketplaceBranchCommand(dispatcher),
            *localMarketplaceConsumerCommands(portableEnvironment).toTypedArray(),
        )
    }

    override fun help(context: Context): String =
        "Browse, manage, import, project, and publish portable marketplaces."

    override fun run() = Unit
}

private class BrowseMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
    private val github: GitHubCli,
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

    private val host by hostOption()

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
                put("repository", github.normalizeRepository(repository, host))
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

private class SearchMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
    private val github: GitHubCli,
) : CliktCommand(
    name = "search",
) {
    private val query by argument(name = "query", help = "Text to search in repositories or marketplace offerings.")

    private val repository by option(
        "--repository",
        help = "Marketplace repository to search. Omit to search GitHub repositories with gh.",
    )

    private val provider by browseProviderOption()

    private val host by hostOption()

    private val limit by limitOption(default = 20)

    private val format by outputFormatOption()

    override fun help(context: Context): String =
        "Search GitHub repositories or a specific marketplace catalog from the shell."

    override fun run() {
        val result = repository
            ?.let { searchMarketplace(github.normalizeRepository(it, host)) }
            ?: searchGitHub()
        echo(renderJsonOrText(result, format, ::renderSearch))
    }

    private fun searchMarketplace(repository: String): JsonObject {
        val catalog = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceBrowse,
            params = buildJsonObject {
                put("repository", repository)
                put("provider", provider.cliName)
            },
            failureMessage = "marketplace search failed",
        )
        return filterCatalog(catalog, query, limit)
    }

    private fun searchGitHub(): JsonObject =
        try {
            github.searchRepositories(query = query, explicitHost = host, limit = limit).toJson()
        } catch (failure: GitHubCliFailure) {
            throw CliktError(failure.message ?: "GitHub search failed")
        }
}

private class InspectMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
    private val github: GitHubCli,
) : CliktCommand(
    name = "inspect",
) {
    private val repository by argument(
        name = "repository",
        help = "Local path, GitHub URL, owner/repo shorthand, or git URL to inspect.",
    )

    private val plugin by option("--plugin", help = "Limit output to one plugin name.")

    private val provider by browseProviderOption()

    private val host by hostOption()

    private val format by outputFormatOption()

    override fun help(context: Context): String =
        "Inspect a marketplace repository, optionally narrowed to one plugin."

    override fun run() {
        val normalized = github.normalizeRepository(repository, host)
        val catalog = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceBrowse,
            params = buildJsonObject {
                put("repository", normalized)
                put("provider", provider.cliName)
            },
            failureMessage = "marketplace inspect failed",
        )
        val inspected = plugin
            ?.let { filterCatalog(catalog, it, limit = Int.MAX_VALUE, exactPlugin = true) }
            ?: catalog
        val withRepositoryInfo = github.inspectRepository(repository, host)?.let { repositoryInfo ->
            buildJsonObject {
                put("repositoryInfo", repositoryInfo.toJson())
                inspected.forEach { (key, value) -> put(key, value) }
            }
        } ?: inspected
        echo(renderJsonOrText(withRepositoryInfo, format, ::renderInspect))
    }
}

private class RemoteMarketplaceCommand(
    dispatcher: RpcDispatcher,
    github: GitHubCli,
) : CliktCommand(
    name = "remote",
) {
    init {
        subcommands(
            AddRemoteMarketplaceCommand(dispatcher, github),
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
    private val github: GitHubCli,
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

    private val host by hostOption()

    override fun help(context: Context): String =
        "Add a named external marketplace to the source graph."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceRemotesAdd,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("name", name)
                put("repository", github.normalizeRepository(repository, host))
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

    private val format by outputFormatOption()

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
        when (format) {
            CommandOutputFormat.Json -> echo(JsonFiles.json.encodeToString(JsonElement.serializer(), result))
            CommandOutputFormat.Text -> {
                if (remotes.isEmpty()) {
                    echoRpcMessages(result)
                } else {
                    remotes.forEach { remote ->
                        echo("${remote["name"].asString()}\t${remote["source"].asString()}")
                    }
                }
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
    private val github: GitHubCli,
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

    private val host by hostOption()

    private val noValidate by noValidateOption()

    override fun help(context: Context): String =
        "Import a plugin by portable marketplace reference or direct repository/plugin reference."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceImport,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("target", github.normalizeImportTarget(plugin, host))
                version?.let { put("version", it) }
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace import failed",
        )
        echoRpcMessages(result)
        validateAfterMutation(dispatcher, repo, enabled = !noValidate)
    }
}

private class InstallMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
    private val github: GitHubCli,
) : CliktCommand(
    name = "install",
) {
    private val repo by repoOption()

    private val repository by argument(
        name = "repository",
        help = "Local path, GitHub owner/repo, GitHub URL, or git URL exposing an adaptable marketplace.",
    )

    private val ref by option("--ref", help = "Branch, tag, or SHA for repository resolution. Defaults to main.")

    private val host by hostOption()

    private val noValidate by noValidateOption()

    override fun help(context: Context): String =
        "Install every plugin exposed by an adaptable marketplace repository."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceInstall,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("repository", github.normalizeRepository(repository, host))
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace install failed",
        )
        echoRpcMessages(result)
        validateAfterMutation(dispatcher, repo, enabled = !noValidate)
    }
}

private class UpdateMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "update",
) {
    private val repo by repoOption()

    private val plugin by argument(
        name = "plugin",
        help = "Imported plugin to update. Omit with --all to update every imported plugin.",
    ).optional()

    private val all by option("--all", help = "Update every imported plugin.")
        .flag(default = false)

    private val noValidate by noValidateOption()

    override fun help(context: Context): String =
        "Update an imported plugin, or every imported plugin with --all."

    override fun run() {
        if (all && plugin != null) {
            throw CliktError("--all cannot be combined with a plugin argument")
        }
        val result = executeRpc(
            dispatcher = dispatcher,
            method = if (all) RpcMethod.MarketplaceUpdateAll else RpcMethod.MarketplaceUpdate,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                if (!all) {
                    put("plugin", plugin ?: throw CliktError("missing argument \"plugin\" or --all"))
                }
            },
            failureMessage = "marketplace update failed",
        )
        echoRpcMessages(result)
        validateAfterMutation(dispatcher, repo, enabled = !noValidate)
    }
}

private class PinMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "pin",
) {
    private val repo by repoOption()

    private val plugin by argument(name = "plugin", help = "Imported plugin to pin.")

    private val version by argument(name = "version", help = "Exact version to pin.")

    private val noValidate by noValidateOption()

    override fun help(context: Context): String =
        "Pin an imported plugin to an exact version."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplacePin,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("plugin", plugin)
                put("version", version)
            },
            failureMessage = "marketplace pin failed",
        )
        echoRpcMessages(result)
        validateAfterMutation(dispatcher, repo, enabled = !noValidate)
    }
}

private class UnpinMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "unpin",
) {
    private val repo by repoOption()

    private val plugin by argument(name = "plugin", help = "Imported plugin to unpin.")

    private val noValidate by noValidateOption()

    override fun help(context: Context): String =
        "Return an imported plugin to the remote current version."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceUnpin,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("plugin", plugin)
            },
            failureMessage = "marketplace unpin failed",
        )
        echoRpcMessages(result)
        validateAfterMutation(dispatcher, repo, enabled = !noValidate)
    }
}

private class InstalledMarketplaceCommand(
    dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "installed",
) {
    init {
        subcommands(ListInstalledMarketplaceCommand(dispatcher))
    }

    override fun help(context: Context): String =
        "Inspect marketplace entries installed in the target repository."

    override fun run() = Unit
}

private class ListInstalledMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "list",
) {
    private val repo by repoOption()

    private val checkUpdates by option("--check-updates", help = "Resolve remote current versions when possible.")
        .flag(default = false)

    private val format by outputFormatOption()

    override fun help(context: Context): String =
        "List installed marketplace plugins and lock state."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceInstalledList,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("checkUpdates", checkUpdates)
            },
            failureMessage = "marketplace installed list failed",
        )
        echo(renderJsonOrText(result, format, ::renderInstalled))
    }
}

private class VersionsMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "versions",
) {
    private val repo by repoOption()

    private val target by argument(
        name = "plugin-or-reference",
        help = "Installed plugin name or marketplace/plugin reference.",
    )

    private val ref by option("--ref", help = "Branch, tag, or SHA for direct repository version checks.")

    private val format by outputFormatOption()

    override fun help(context: Context): String =
        "List installed and remote-current versions for an imported plugin."

    override fun run() {
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceVersionsList,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("target", target)
                ref?.let { put("ref", it) }
            },
            failureMessage = "marketplace versions failed",
        )
        echo(renderJsonOrText(result, format, ::renderVersions))
    }
}

private class MaterializeMarketplaceCommand(
    private val dispatcher: RpcDispatcher,
) : CliktCommand(
    name = "materialize",
) {
    private val repo by repoOption()

    private val provider by providerOption(default = MarketplaceProvider.All)

    private val out by option("--out", help = "Output directory to replace with generated provider files.")
        .convert("PATH") { it.toCliPath() }

    override fun help(context: Context): String =
        "Render provider marketplace files into an output directory."

    override fun run() {
        val outRoot = out ?: repo.resolve("build").resolve("intelligence").resolve("marketplace")
        val result = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceMaterialize,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("outRoot", outRoot.toString())
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

    private val check by option("--check", help = "Validate source and hydrated provider output before publishing.")
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
            if (check) {
                runPublishCheck(dispatcher, repo, MarketplaceProvider.All)
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
                if (check) {
                    runPublishCheck(dispatcher, repo, provider)
                }
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

private fun CliktCommand.browseProviderOption(default: MarketplaceBrowseProvider = MarketplaceBrowseProvider.Auto) =
    option(
        "--provider",
        help = "Provider to inspect: auto, codex, github, or source.",
    )
        .convert("PROVIDER") {
            try {
                MarketplaceBrowseProvider.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid provider")
            }
        }
        .default(default)

private fun CliktCommand.hostOption() =
    option("--host", help = "GitHub host for owner/repo shorthand. Defaults to the active gh host.")

private fun CliktCommand.limitOption(default: Int) =
    option("--limit", help = "Maximum number of results to return.")
        .int()
        .default(default)

private fun CliktCommand.repoOption() =
    option("--repo", help = "Repository root containing an adaptable marketplace or install state.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

private fun CliktCommand.noValidateOption() =
    option("--no-validate", help = "Skip portable validation after the marketplace state changes.")
        .flag(default = false)

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

private fun renderJsonOrText(
    result: JsonObject,
    format: CommandOutputFormat,
    text: (JsonObject) -> String,
): String =
    when (format) {
        CommandOutputFormat.Text -> text(result)
        CommandOutputFormat.Json -> JsonFiles.json.encodeToString(JsonElement.serializer(), result)
    }

private fun filterCatalog(
    catalog: JsonObject,
    query: String,
    limit: Int,
    exactPlugin: Boolean = false,
): JsonObject {
    val plugins = catalog.arrayValue("plugins")
        .jsonObjects()
        .filter { plugin ->
            if (exactPlugin) {
                plugin.stringValue("name") == query
            } else {
                plugin.matchesQuery(query)
            }
        }
        .take(limit)
    val standalone = catalog.objectValue("standalonePrimitives") ?: JsonObject(emptyMap())
    return buildJsonObject {
        put("query", query)
        catalog["marketplace"]?.let { put("marketplace", it) }
        putJsonArray("plugins") {
            plugins.forEach { plugin -> add(plugin) }
        }
        putJsonObject("standalonePrimitives") {
            standalone.forEach { (kind, values) ->
                val matches = (values as? JsonArray).orEmpty()
                    .jsonObjects()
                    .filter { primitive -> primitive.matchesQuery(query) }
                    .take(limit)
                putJsonArray(kind) {
                    matches.forEach { primitive -> add(primitive) }
                }
            }
        }
    }
}

private fun renderSearch(result: JsonObject): String =
    if (result["repositories"] is JsonArray) {
        renderRepositorySearch(result)
    } else {
        renderMarketplaceSearch(result)
    }

private fun renderRepositorySearch(result: JsonObject): String {
    val query = result.stringValue("query").orEmpty()
    val host = result.stringValue("host").orEmpty()
    val repositories = result.arrayValue("repositories").jsonObjects()
    val lines = mutableListOf("GitHub repositories matching `$query` on $host")
    if (repositories.isEmpty()) {
        lines += "- none found"
    } else {
        repositories.forEach { repository ->
            val name = repository.stringValue("nameWithOwner").orEmpty()
            val description = repository.stringValue("description")?.takeIf { it.isNotBlank() }
            lines += "- $name${description?.let { " - $it" }.orEmpty()}"
        }
        repositories.firstOrNull()?.stringValue("nameWithOwner")?.let { first ->
            lines += ""
            lines += "Next: intelligence marketplace inspect $first"
        }
    }
    return lines.joinToString("\n")
}

private fun renderMarketplaceSearch(result: JsonObject): String {
    val query = result.stringValue("query").orEmpty()
    val marketplace = result.objectValue("marketplace") ?: JsonObject(emptyMap())
    val lines = mutableListOf(
        "Marketplace matches for `$query`",
        "Repository: ${marketplace.stringValue("repository").orEmpty()}",
    )
    val plugins = result.arrayValue("plugins").jsonObjects()
    lines += ""
    lines += "Plugins"
    if (plugins.isEmpty()) {
        lines += "- none found"
    } else {
        plugins.forEach { plugin ->
            lines += "- ${plugin.stringValue("name").orEmpty()}${plugin.stringValue("description")?.let { " - $it" }.orEmpty()}"
        }
    }

    val standalone = result.objectValue("standalonePrimitives") ?: JsonObject(emptyMap())
    val primitives = standalone.values.flatMap { (it as? JsonArray).orEmpty().jsonObjects() }
    lines += ""
    lines += "Standalone primitives"
    if (primitives.isEmpty()) {
        lines += "- none found"
    } else {
        primitives.forEach { primitive ->
            lines += "- ${primitive.stringValue("type").orEmpty()}:${primitive.stringValue("name").orEmpty()}"
        }
    }
    return lines.joinToString("\n")
}

private fun renderInspect(result: JsonObject): String {
    val lines = mutableListOf<String>()
    result.objectValue("repositoryInfo")?.let { repository ->
        lines += "Repository: ${repository.stringValue("nameWithOwner").orEmpty()}"
        repository.stringValue("description")?.takeIf { it.isNotBlank() }?.let { description ->
            lines += "Description: $description"
        }
        repository.stringValue("defaultBranch")?.let { branch -> lines += "Default branch: $branch" }
        lines += ""
    }
    lines += MarketplaceBrowseText.render(result)
    val marketplace = result.objectValue("marketplace") ?: JsonObject(emptyMap())
    val repository = marketplace.stringValue("repository").orEmpty()
    val firstPlugin = result.arrayValue("plugins").jsonObjects().firstOrNull()?.stringValue("name")
    if (repository.isNotBlank()) {
        lines += ""
        lines += "Next"
        lines += "- Search: intelligence marketplace search <query> --repository $repository"
        firstPlugin?.let { plugin -> lines += "- Import: intelligence marketplace import $repository/$plugin" }
        lines += "- Install all: intelligence marketplace install $repository"
        lines += "- Validate: intelligence validate --portable"
    }
    return lines.joinToString("\n")
}

private fun renderInstalled(result: JsonObject): String {
    val plugins = result.arrayValue("plugins").jsonObjects()
    val lines = mutableListOf(
        "Installed marketplace plugins",
        "Repository: ${result.stringValue("repoRoot").orEmpty()}",
        "Marketplace state: ${result.stringValue("marketplacePath").orEmpty()}",
        "",
    )
    if (plugins.isEmpty()) {
        lines += "- none installed"
    } else {
        plugins.forEach { plugin ->
            val name = plugin.stringValue("name").orEmpty()
            val version = plugin.stringValue("version")?.let { " version=$it" }.orEmpty()
            val target = plugin.stringValue("target")?.let { " target=$it" }.orEmpty()
            val locked = plugin.stringValue("locked")?.let { " locked=$it" }.orEmpty()
            val remote = plugin.stringValue("remoteVersion")?.let { " remote=$it" }.orEmpty()
            lines += "- $name$version$remote$locked$target"
        }
    }
    return lines.joinToString("\n")
}

private fun renderVersions(result: JsonObject): String {
    val lines = mutableListOf(
        "Versions for ${result.stringValue("target").orEmpty()}",
        "Marketplace: ${result.stringValue("marketplace").orEmpty()}",
        "Plugin: ${result.stringValue("plugin").orEmpty()}",
    )
    result.stringValue("installedVersion")?.let { lines += "Installed: $it" }
    result.stringValue("currentVersion")?.let { lines += "Remote current: $it" }
    val versions = result.arrayValue("versions").jsonObjects()
    if (versions.isNotEmpty()) {
        lines += ""
        lines += "Known versions"
        versions.forEach { version ->
            lines += "- ${version.stringValue("version").orEmpty()} (${version.stringValue("kind").orEmpty()})"
        }
    }
    return lines.joinToString("\n")
}

private fun Iterable<JsonElement>.jsonObjects(): List<JsonObject> =
    mapNotNull { it as? JsonObject }

private fun JsonObject.matchesQuery(query: String): Boolean {
    val normalized = query.lowercase()
    return values.any { element -> element.containsText(normalized) }
}

private fun JsonElement.containsText(query: String): Boolean =
    when (this) {
        is JsonPrimitive -> content.lowercase().contains(query)
        is JsonArray -> any { it.containsText(query) }
        is JsonObject -> values.any { it.containsText(query) }
    }

private fun CliktCommand.validateAfterMutation(dispatcher: RpcDispatcher, repo: Path, enabled: Boolean) {
    if (!enabled) {
        return
    }
    val result = runValidation(
        dispatcher = dispatcher,
        repo = repo,
        hydrated = null,
        failureMessage = "post-change validation failed",
    )
    failOnValidationExit(result)
}

private fun CliktCommand.runPublishCheck(dispatcher: RpcDispatcher, repo: Path, provider: MarketplaceProvider) {
    failOnValidationExit(
        runValidation(
            dispatcher = dispatcher,
            repo = repo,
            hydrated = null,
            failureMessage = "source validation failed",
        )
    )

    val output = Files.createTempDirectory("intelligence-marketplace-check-")
    try {
        val materialized = executeRpc(
            dispatcher = dispatcher,
            method = RpcMethod.MarketplaceMaterialize,
            params = buildJsonObject {
                put("repoRoot", repo.toString())
                put("outRoot", output.toString())
                put("provider", provider.cliName)
            },
            failureMessage = "marketplace check materialization failed",
        )
        echoRpcMessages(materialized)
        failOnValidationExit(
            runValidation(
                dispatcher = dispatcher,
                repo = repo,
                hydrated = output,
                failureMessage = "hydrated validation failed",
            )
        )
    } finally {
        FileSystem.deleteRecursively(output)
    }
}

private fun CliktCommand.runValidation(
    dispatcher: RpcDispatcher,
    repo: Path,
    hydrated: Path?,
    failureMessage: String,
): JsonObject {
    val result = executeRpc(
        dispatcher = dispatcher,
        method = RpcMethod.ValidationRun,
        params = buildJsonObject {
            put("repoRoot", repo.toString())
            put("portable", true)
            hydrated?.let { put("hydrated", it.toString()) }
        },
        failureMessage = failureMessage,
    )
    echoRpcMessages(result)
    return result
}

private fun failOnValidationExit(result: JsonObject) {
    val exitCode = result.stringValue("exitCode")?.toIntOrNull() ?: 0
    if (exitCode != 0) {
        throw ProgramResult(exitCode)
    }
}

private fun JsonElement?.asString(): String =
    (this as? JsonPrimitive)?.content.orEmpty()
