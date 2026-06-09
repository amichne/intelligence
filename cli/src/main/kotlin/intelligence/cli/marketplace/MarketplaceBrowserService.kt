package intelligence.cli.marketplace

import intelligence.cli.io.FileSystem
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.arrayValue
import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringList
import intelligence.cli.io.stringValue
import intelligence.cli.io.toCliPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class MarketplaceBrowserService {
    fun browse(repository: String, provider: MarketplaceBrowseProvider): MarketplaceBrowseResult {
        val ref = MarketplaceRepositoryRef.parse(repository)
        return when (ref) {
            is MarketplaceRepositoryRef.Local -> browseLocal(ref, provider)
            is MarketplaceRepositoryRef.GitHub -> browseGitHub(ref, provider)
        }
    }

    private fun browseLocal(
        ref: MarketplaceRepositoryRef.Local,
        provider: MarketplaceBrowseProvider,
    ): MarketplaceBrowseResult =
        discover(ref.path, provider, ref.displayName)
            ?: throw MarketplaceFailure.InvalidSource(
                "no ${provider.failureName} marketplace found in ${ref.path}"
            )

    private fun browseGitHub(
        ref: MarketplaceRepositoryRef.GitHub,
        provider: MarketplaceBrowseProvider,
    ): MarketplaceBrowseResult {
        val tempRoot = Files.createTempDirectory("intelligence-marketplace-browse-")
        try {
            remoteCandidates(provider).forEach { candidate ->
                val checkout = tempRoot.resolve(candidate.directoryName)
                if (cloneRepository(ref.cloneUrl, candidate.branch, checkout)) {
                    discover(checkout, candidate.provider, ref.displayName)?.let { return it }
                }
            }
        } finally {
            FileSystem.deleteRecursively(tempRoot)
        }

        throw MarketplaceFailure.InvalidSource(
            "no ${provider.failureName} marketplace found for ${ref.displayName}"
        )
    }

    private fun discover(
        root: Path,
        provider: MarketplaceBrowseProvider,
        repositoryDisplay: String,
    ): MarketplaceBrowseResult? =
        localCandidates(root, provider)
            .firstNotNullOfOrNull { candidate ->
                candidate.path.takeIf { it.isRegularFile() }?.let {
                    when (candidate.provider) {
                        MarketplaceBrowseProvider.Codex -> readCodexMarketplace(candidate, repositoryDisplay)
                        MarketplaceBrowseProvider.GitHub -> readGitHubMarketplace(candidate, repositoryDisplay)
                        MarketplaceBrowseProvider.Source -> readSourceMarketplace(candidate, repositoryDisplay)
                        MarketplaceBrowseProvider.Auto -> null
                    }
                }
            }

    private fun readSourceMarketplace(
        candidate: LocalMarketplaceCandidate,
        repositoryDisplay: String,
    ): MarketplaceBrowseResult {
        val marketplace = JsonFiles.readObject(candidate.path)
        return MarketplaceBrowseResult(
            summary = MarketplaceBrowseSummary(
                name = marketplace.requiredString("name"),
                description = marketplace.stringValue("description"),
                provider = MarketplaceBrowseProvider.Source,
                repository = repositoryDisplay,
                entrypoint = candidate.entrypoint,
            ),
            plugins = marketplace.arrayValue("plugins")
                .objects()
                .map { entry ->
                    MarketplacePluginOffering(
                        name = entry.requiredString("name"),
                        description = entry.stringValue("description"),
                        category = null,
                        tags = entry.stringList("tags"),
                    )
                },
            standalonePrimitives = PrimitiveKind.entries.flatMap { kind ->
                marketplace.arrayValue(kind.collectionName)
                    .objects()
                    .map { primitive ->
                        MarketplacePrimitiveOffering(
                            kind = kind,
                            name = primitive.requiredString("name"),
                            description = primitive.stringValue("description"),
                        )
                    }
            },
        )
    }

    private fun readCodexMarketplace(
        candidate: LocalMarketplaceCandidate,
        repositoryDisplay: String,
    ): MarketplaceBrowseResult {
        val marketplace = JsonFiles.readObject(candidate.path)
        val name = marketplace.stringValue("name")
            ?: marketplace.objectValue("interface")?.stringValue("displayName")
            ?: "codex-marketplace"
        return MarketplaceBrowseResult(
            summary = MarketplaceBrowseSummary(
                name = name,
                description = marketplace.stringValue("description"),
                provider = MarketplaceBrowseProvider.Codex,
                repository = repositoryDisplay,
                entrypoint = candidate.entrypoint,
            ),
            plugins = marketplace.arrayValue("plugins")
                .objects()
                .map { entry -> readCodexPluginOffering(candidate, entry) },
            standalonePrimitives = emptyList(),
        )
    }

    private fun readCodexPluginOffering(candidate: LocalMarketplaceCandidate, entry: JsonObject): MarketplacePluginOffering {
        val name = entry.requiredString("name")
        val pluginRoot = entry.objectValue("source")
            ?.stringValue("path")
            ?.let { resolveCodexPluginRoot(candidate, it) }
        val manifest = pluginRoot
            ?.resolve(".codex-plugin")
            ?.resolve("plugin.json")
            ?.takeIf { it.isRegularFile() }
            ?.let(JsonFiles::readObject)
        return MarketplacePluginOffering(
            name = name,
            description = manifest?.stringValue("description") ?: entry.stringValue("description"),
            category = entry.stringValue("category")
                ?: manifest?.objectValue("interface")?.stringValue("category"),
            tags = manifest?.stringList("keywords").orEmpty(),
        )
    }

    private fun readGitHubMarketplace(
        candidate: LocalMarketplaceCandidate,
        repositoryDisplay: String,
    ): MarketplaceBrowseResult {
        val marketplace = JsonFiles.readObject(candidate.path)
        return MarketplaceBrowseResult(
            summary = MarketplaceBrowseSummary(
                name = marketplace.requiredString("name"),
                description = marketplace.objectValue("metadata")?.stringValue("description"),
                provider = MarketplaceBrowseProvider.GitHub,
                repository = repositoryDisplay,
                entrypoint = candidate.entrypoint,
            ),
            plugins = marketplace.arrayValue("plugins")
                .objects()
                .map { entry ->
                    MarketplacePluginOffering(
                        name = entry.requiredString("name"),
                        description = entry.stringValue("description"),
                        category = entry.stringValue("category"),
                        tags = entry.stringList("tags"),
                    )
                },
            standalonePrimitives = emptyList(),
        )
    }

    private fun cloneRepository(cloneUrl: String, branch: String?, destination: Path): Boolean {
        val command = buildList {
            add("git")
            add("clone")
            add("--depth")
            add("1")
            add("--single-branch")
            branch?.let {
                add("--branch")
                add(it)
            }
            add(cloneUrl)
            add(destination.toString())
        }
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return process.waitFor() == 0
    }

    private fun localCandidates(root: Path, provider: MarketplaceBrowseProvider): List<LocalMarketplaceCandidate> {
        val codexBranch = LocalMarketplaceCandidate(
            provider = MarketplaceBrowseProvider.Codex,
            baseRoot = root,
            path = root.resolve(CODEX_BRANCH_MARKETPLACE_PATH),
            entrypoint = CODEX_BRANCH_MARKETPLACE_PATH.toUnixString(),
        )
        val codexNestedRoot = root.resolve(CODEX_NESTED_PROVIDER_DIR)
        val codexNested = LocalMarketplaceCandidate(
            provider = MarketplaceBrowseProvider.Codex,
            baseRoot = codexNestedRoot,
            path = codexNestedRoot.resolve(CODEX_NESTED_MARKETPLACE_PATH),
            entrypoint = CODEX_NESTED_PROVIDER_DIR.resolve(CODEX_NESTED_MARKETPLACE_PATH).toUnixString(),
        )
        val github = LocalMarketplaceCandidate(
            provider = MarketplaceBrowseProvider.GitHub,
            baseRoot = root,
            path = root.resolve(GITHUB_BRANCH_MARKETPLACE_PATH),
            entrypoint = GITHUB_BRANCH_MARKETPLACE_PATH.toUnixString(),
        )
        val source = LocalMarketplaceCandidate(
            provider = MarketplaceBrowseProvider.Source,
            baseRoot = root,
            path = root.resolve(SOURCE_MARKETPLACE_PATH),
            entrypoint = SOURCE_MARKETPLACE_PATH.toUnixString(),
        )
        return when (provider) {
            MarketplaceBrowseProvider.Auto -> listOf(codexBranch, codexNested, github, source)
            MarketplaceBrowseProvider.Codex -> listOf(codexBranch, codexNested)
            MarketplaceBrowseProvider.GitHub -> listOf(github)
            MarketplaceBrowseProvider.Source -> listOf(source)
        }
    }

    private fun remoteCandidates(provider: MarketplaceBrowseProvider): List<RemoteMarketplaceCandidate> =
        when (provider) {
            MarketplaceBrowseProvider.Auto -> listOf(
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Codex, branch = null),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.GitHub, branch = null),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Codex, branch = "codex"),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.GitHub, branch = "github"),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Source, branch = null),
            )
            MarketplaceBrowseProvider.Codex -> listOf(
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Codex, branch = null),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Codex, branch = "codex"),
            )
            MarketplaceBrowseProvider.GitHub -> listOf(
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.GitHub, branch = null),
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.GitHub, branch = "github"),
            )
            MarketplaceBrowseProvider.Source -> listOf(
                RemoteMarketplaceCandidate(MarketplaceBrowseProvider.Source, branch = null),
            )
        }

    private companion object {
        val SOURCE_MARKETPLACE_PATH: Path = Path.of("source").resolve("adaptable.marketplace.json")
        val CODEX_BRANCH_MARKETPLACE_PATH: Path = Path.of(".agents").resolve("plugins").resolve("marketplace.json")
        val CODEX_NESTED_PROVIDER_DIR: Path = Path.of("codex")
        val CODEX_NESTED_MARKETPLACE_PATH: Path = Path.of("marketplace.json")
        val GITHUB_BRANCH_MARKETPLACE_PATH: Path = Path.of(".github").resolve("plugin").resolve("marketplace.json")
    }
}

internal enum class MarketplaceBrowseProvider(
    val cliName: String,
    val displayName: String,
    private val aliases: Set<String> = emptySet(),
) {
    Auto("auto", "Auto"),
    Codex("codex", "Codex"),
    GitHub("github", "GitHub Copilot", aliases = setOf("copilot")),
    Source("source", "Source");

    val failureName: String
        get() = if (this == Auto) "supported" else cliName

    val cliNames: Set<String>
        get() = setOf(cliName) + aliases

    companion object {
        fun parse(value: String): MarketplaceBrowseProvider =
            entries.firstOrNull { value.lowercase() in it.cliNames }
                ?: throw IllegalArgumentException(
                    "provider must be one of: ${entries.flatMap { it.cliNames }.joinToString(", ")}"
                )
    }
}

internal enum class MarketplaceBrowseFormat(
    val cliName: String,
) {
    Text("text"),
    Json("json");

    companion object {
        fun parse(value: String): MarketplaceBrowseFormat =
            entries.firstOrNull { it.cliName == value.lowercase() }
                ?: throw IllegalArgumentException(
                    "format must be one of: ${entries.joinToString(", ") { it.cliName }}"
                )
    }
}

internal data class MarketplaceBrowseResult(
    val summary: MarketplaceBrowseSummary,
    val plugins: List<MarketplacePluginOffering>,
    val standalonePrimitives: List<MarketplacePrimitiveOffering>,
) {
    fun renderText(): String {
        val lines = mutableListOf(
            "Marketplace: ${summary.name}",
            "Provider: ${summary.provider.displayName}",
            "Repository: ${summary.repository}",
            "Entrypoint: ${summary.entrypoint}",
        )
        summary.description?.takeIf { it.isNotBlank() }?.let { description ->
            lines += "Description: $description"
        }
        lines += ""
        lines += "Plugins"
        if (plugins.isEmpty()) {
            lines += "- none exposed"
        } else {
            lines += plugins.sortedBy { it.name }.mapIndexed { index, plugin ->
                val suffix = plugin.description?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                "${index + 1}. ${plugin.name}$suffix"
            }
        }

        val primitivesByKind = standalonePrimitives.groupBy { it.kind }
        if (standalonePrimitives.isEmpty()) {
            lines += ""
            lines += "Standalone primitives"
            lines += "- none exposed"
        } else {
            PrimitiveKind.entries.forEach { kind ->
                val primitives = primitivesByKind[kind].orEmpty().sortedBy { it.name }
                if (primitives.isNotEmpty()) {
                    lines += ""
                    lines += standaloneTitle(kind)
                    lines += primitives.mapIndexed { index, primitive ->
                        val suffix = primitive.description?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                        "${index + 1}. ${primitive.name}$suffix"
                    }
                }
            }
        }

        return lines.joinToString("\n")
    }

    fun renderJson(): String =
        JsonFiles.json.encodeToString(JsonElement.serializer(), toJson())

    private fun toJson(): JsonObject =
        buildJsonObject {
            putJsonObject("marketplace") {
                put("name", summary.name)
                put("provider", summary.provider.cliName)
                put("repository", summary.repository)
                put("entrypoint", summary.entrypoint)
                summary.description?.let { put("description", it) }
            }
            putJsonArray("plugins") {
                plugins.sortedBy { it.name }.forEach { plugin ->
                    add(plugin.toJson())
                }
            }
            putJsonObject("standalonePrimitives") {
                PrimitiveKind.entries.forEach { kind ->
                    putJsonArray(kind.collectionName) {
                        standalonePrimitives
                            .filter { it.kind == kind }
                            .sortedBy { it.name }
                            .forEach { primitive -> add(primitive.toJson()) }
                    }
                }
            }
        }
}

internal data class MarketplaceBrowseSummary(
    val name: String,
    val description: String?,
    val provider: MarketplaceBrowseProvider,
    val repository: String,
    val entrypoint: String,
)

internal data class MarketplacePluginOffering(
    val name: String,
    val description: String?,
    val category: String?,
    val tags: List<String>,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            category?.let { put("category", it) }
            putJsonArray("tags") {
                tags.forEach { tag -> add(tag) }
            }
        }
}

internal data class MarketplacePrimitiveOffering(
    val kind: PrimitiveKind,
    val name: String,
    val description: String?,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("name", name)
            put("type", kind.sourceName)
            description?.let { put("description", it) }
        }
}

private sealed interface MarketplaceRepositoryRef {
    val displayName: String

    data class Local(
        val path: Path,
        override val displayName: String,
    ) : MarketplaceRepositoryRef

    data class GitHub(
        val owner: String,
        val repo: String,
    ) : MarketplaceRepositoryRef {
        override val displayName: String = "$owner/$repo"
        val cloneUrl: String = "https://github.com/$owner/$repo.git"
    }

    companion object {
        fun parse(value: String): MarketplaceRepositoryRef {
            val path = value.toCliPath()
            if (path.exists()) {
                return Local(path.normalizedAbsolute(), value)
            }
            if (looksLikeLocalPath(value)) {
                throw MarketplaceFailure.InvalidSource("repository path does not exist: $value")
            }
            parseGitHub(value)?.let { return it }
            throw MarketplaceFailure.InvalidSource(
                "repository must be a local path, GitHub URL, or owner/repo shorthand: $value"
            )
        }

        private fun looksLikeLocalPath(value: String): Boolean =
            value.startsWith(".") || value.startsWith("~") || value.startsWith("/")

        private fun parseGitHub(value: String): GitHub? {
            val trimmed = value.trim()
            val githubPath = when {
                trimmed.startsWith("https://github.com/") -> trimmed.removePrefix("https://github.com/")
                trimmed.startsWith("http://github.com/") -> trimmed.removePrefix("http://github.com/")
                trimmed.startsWith("git@github.com:") -> trimmed.removePrefix("git@github.com:")
                GITHUB_SHORTHAND.matches(trimmed) -> trimmed
                else -> return null
            }
            val segments = githubPath.split("/")
            val owner = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            val repo = segments.getOrNull(1)
                ?.removeSuffix(".git")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return GitHub(owner, repo)
        }

        private val GITHUB_SHORTHAND = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    }
}

private data class LocalMarketplaceCandidate(
    val provider: MarketplaceBrowseProvider,
    val baseRoot: Path,
    val path: Path,
    val entrypoint: String,
)

private data class RemoteMarketplaceCandidate(
    val provider: MarketplaceBrowseProvider,
    val branch: String?,
) {
    val directoryName: String =
        "${branch ?: "default"}-${provider.cliName}"
}

private fun JsonArray.objects(): List<JsonObject> =
    mapNotNull { it as? JsonObject }

private fun JsonObject.requiredString(key: String): String =
    stringValue(key)?.takeIf { it.isNotBlank() }
        ?: throw MarketplaceFailure.InvalidSource("missing string field `$key` in $this")

private fun resolveRelative(root: Path, value: String): Path {
    val relative = value.removePrefix("./")
    return root.resolve(relative).normalize()
}

private fun resolveCodexPluginRoot(candidate: LocalMarketplaceCandidate, value: String): Path {
    val marketplaceRoot = candidate.path.parent ?: candidate.baseRoot
    val marketplaceRelative = resolveRelative(marketplaceRoot, value)
    if (marketplaceRelative.exists()) {
        return marketplaceRelative
    }

    val rootRelative = resolveRelative(candidate.baseRoot, value)
    return if (rootRelative.exists()) rootRelative else marketplaceRelative
}

private fun Path.toUnixString(): String =
    toString().replace('\\', '/')

private fun standaloneTitle(kind: PrimitiveKind): String =
    when (kind) {
        PrimitiveKind.Skill -> "Standalone skills"
        PrimitiveKind.Agent -> "Standalone agents"
        PrimitiveKind.Hook -> "Standalone hooks"
        PrimitiveKind.Instruction -> "Standalone instructions"
    }
