package intelligence.cli.marketplace

import intelligence.cli.io.DigestAlgorithm
import intelligence.cli.io.Digests
import intelligence.cli.io.FileSystem
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.ProcessRunner
import intelligence.cli.io.arrayValue
import intelligence.cli.io.jsonArrayOfStrings
import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.objectValue
import intelligence.cli.io.putIfNotNull
import intelligence.cli.io.putStringArray
import intelligence.cli.io.stringList
import intelligence.cli.io.stringValue
import intelligence.cli.io.toCliPath
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class MarketplaceService(
    private val processRunner: ProcessRunner = ProcessRunner.system(),
    private val output: (String) -> Unit = ::println,
) {
    fun materialize(
        repoRoot: Path,
        outRoot: Path,
        provider: MarketplaceProvider,
        generatedAt: String? = null,
        sourceSha: String? = null,
    ) {
        val repository = repoRoot.normalizedAbsolute()
        val outputRoot = outRoot.normalizedAbsolute()
        refuseRepositoryOutput(repository, outputRoot)

        RemoteMarketplaceResolver(repository).use { resolver ->
            when (provider) {
                MarketplaceProvider.Codex -> materializeCodexMarketplace(repository, outputRoot, generatedAt, sourceSha, resolver)
                MarketplaceProvider.GitHub -> materializeGitHubMarketplace(repository, outputRoot, resolver)
                MarketplaceProvider.All -> materializeAllMarketplaces(repository, outputRoot, generatedAt, sourceSha, resolver)
            }
        }
    }

    fun publishDefault(repoRoot: Path) {
        val repository = repoRoot.normalizedAbsolute()
        val tempRoot = Files.createTempDirectory("intelligence-marketplace-default-")

        try {
            RemoteMarketplaceResolver(repository).use { resolver ->
                renderDefaultMarketplaces(repository, tempRoot, generatedAt = null, sourceSha = null, resolver)
                FileSystem.copyPath(
                    tempRoot.resolve(CODEX_MARKETPLACE_ROOT),
                    repository.resolve(CODEX_MARKETPLACE_ROOT),
                )
                FileSystem.copyPath(
                    tempRoot.resolve(GITHUB_COPILOT_PROVIDER_PATH),
                    repository.resolve(GITHUB_COPILOT_PROVIDER_PATH),
                )
            }
        } finally {
            FileSystem.deleteRecursively(tempRoot)
        }

        output(
            "published default marketplaces at " +
                "${repository.resolve(CODEX_BRANCH_MARKETPLACE_PATH)} and " +
                repository.resolve(GITHUB_BRANCH_MARKETPLACE_PATH)
        )
    }

    fun publishBranch(
        repoRoot: Path,
        provider: MarketplaceProvider,
        branch: String?,
        noPush: Boolean,
    ) {
        val repository = repoRoot.normalizedAbsolute()
        val targetBranch = branch ?: provider.defaultBranch
        val tempRoot = Files.createTempDirectory("intelligence-marketplace-")
        val materialized = tempRoot.resolve("materialized")
        val worktree = tempRoot.resolve("worktree")
        val localBranch = "intelligence-marketplace-publish-${ProcessHandle.current().pid()}"

        materialize(repository, materialized, provider)
        runChecked(listOf("git", "worktree", "add", "--detach", worktree.toString(), "HEAD"), repository)
        try {
            runChecked(listOf("git", "checkout", "--orphan", localBranch), worktree)
            FileSystem.clearWorktree(worktree)
            FileSystem.copyTreeContents(materialized, worktree)
            runChecked(listOf("git", "config", "user.name", "github-actions[bot]"), worktree)
            runChecked(
                listOf(
                    "git",
                    "config",
                    "user.email",
                    "41898282+github-actions[bot]@users.noreply.github.com",
                ),
                worktree,
            )
            runChecked(listOf("git", "add", "-A"), worktree)
            runChecked(listOf("git", "commit", "-m", "Publish Intelligence Marketplace"), worktree)
            if (!noPush) {
                runChecked(listOf("git", "push", "--force", "origin", "HEAD:$targetBranch"), worktree)
            }
        } finally {
            runIgnoringFailure(listOf("git", "worktree", "remove", "--force", worktree.toString()), repository)
            runIgnoringFailure(listOf("git", "worktree", "prune"), repository)
            runIgnoringFailure(listOf("git", "branch", "-D", localBranch), repository)
            FileSystem.deleteRecursively(tempRoot)
        }

        if (noPush) {
            output("prepared marketplace branch $targetBranch without pushing")
        } else {
            output("published marketplace branch $targetBranch")
        }
    }

    fun addRemote(
        repoRoot: Path,
        name: String,
        repository: String,
        ref: String?,
    ) {
        val root = repoRoot.normalizedAbsolute()
        val remoteName = MarketplaceName.parse(name)
        val source = externalSource(root, repository, ref)
        val marketplacePath = root.resolve(ADAPTABLE_MARKETPLACE_PATH)
        val marketplace = marketplace(root)
        val remotes = marketplace.externalMarketplaces()
            .filterNot { it.name == remoteName.value } +
            ExternalMarketplace(remoteName.value, source)

        JsonFiles.writeObject(
            marketplacePath,
            marketplace
                .withExternalMarketplaces(remotes.sortedBy { it.name })
                .withAllowedExternalMarketplace(remoteName.value),
        )
        output("added external marketplace ${remoteName.value}")
    }

    fun listRemotes(repoRoot: Path) {
        val remotes = marketplace(repoRoot.normalizedAbsolute()).externalMarketplaces().sortedBy { it.name }
        if (remotes.isEmpty()) {
            output("no external marketplaces configured")
        } else {
            remotes.forEach { remote -> output("${remote.name}\t${sourceDisplay(remote.source)}") }
        }
    }

    fun removeRemote(repoRoot: Path, name: String) {
        val root = repoRoot.normalizedAbsolute()
        val remoteName = MarketplaceName.parse(name)
        val marketplacePath = root.resolve(ADAPTABLE_MARKETPLACE_PATH)
        val marketplace = marketplace(root)
        if (marketplace.importedPluginEntries(remoteName.value).isNotEmpty()) {
            throw MarketplaceFailure.InvalidSource(
                "cannot remove external marketplace ${remoteName.value}; imported plugins still reference it"
            )
        }

        val remotes = marketplace.externalMarketplaces().filterNot { it.name == remoteName.value }
        if (remotes.size == marketplace.externalMarketplaces().size) {
            throw MarketplaceFailure.InvalidSource("unknown external marketplace: ${remoteName.value}")
        }

        JsonFiles.writeObject(
            marketplacePath,
            marketplace
                .withExternalMarketplaces(remotes.sortedBy { it.name })
                .withoutAllowedExternalMarketplace(remoteName.value),
        )
        output("removed external marketplace ${remoteName.value}")
    }

    fun importPlugin(
        repoRoot: Path,
        target: String,
        version: String?,
        ref: String? = null,
    ) {
        val root = repoRoot.normalizedAbsolute()
        val importTarget = MarketplaceImportTarget.parse(
            value = target,
            ref = ref,
            remoteNameForRepository = ::remoteNameForRepository,
        )
        val importRef = importTarget.importRef
        val marketplacePath = root.resolve(ADAPTABLE_MARKETPLACE_PATH)
        val marketplace = when (importTarget) {
            is MarketplaceImportTarget.Named -> marketplace(root)
            is MarketplaceImportTarget.Direct -> marketplace(root).withDirectRemote(root, importTarget)
        }
        val remote = marketplace.externalMarketplaces()
            .firstOrNull { it.name == importRef.marketplace.value }
            ?: throw MarketplaceFailure.InvalidSource("unknown external marketplace: ${importRef.marketplace.value}")

        if (importRef.marketplace.value !in marketplace.allowedExternalMarketplaceNames()) {
            throw MarketplaceFailure.InvalidSource(
                "external marketplace ${importRef.marketplace.value} is not allowed by management policy"
            )
        }
        if (marketplace.arrayValue("plugins").objects().any { it.stringValue("name") == importRef.plugin.value }) {
            throw MarketplaceFailure.InvalidSource("plugin already exists in marketplace: ${importRef.plugin.value}")
        }

        var importedVersion = ""
        RemoteMarketplaceResolver(root).use { resolver ->
            val resolvedMarketplace = resolver.resolve(remote)
            val remoteMarketplace = marketplaceAt(resolvedMarketplace.root)
            val remoteEntry = remoteMarketplace.arrayValue("plugins")
                .objects()
                .firstOrNull { it.stringValue("name") == importRef.plugin.value }
                ?: throw MarketplaceFailure.InvalidSource(
                    "plugin ${importRef.plugin.value} was not found in ${importRef.marketplace.value}"
                )
            val exactVersion = ExactVersion.parse(version ?: remotePluginVersion(resolvedMarketplace.root, remoteEntry))
            importedVersion = exactVersion.value
            requireRemoteVersion(
                remoteRoot = resolvedMarketplace.root,
                remoteEntry = remoteEntry,
                marketplace = importRef.marketplace.value,
                plugin = importRef.plugin.value,
                version = exactVersion.value,
            )
            val pluginReference = importedPluginReference(importRef, exactVersion)
            val importedEntry = importedPluginEntry(importRef, pluginReference, remoteEntry)
            JsonFiles.writeObject(
                marketplacePath,
                marketplace.withPluginEntry(importedEntry),
            )
            writeMarketplaceLock(
                repoRoot = root,
                rootMarketplace = marketplace,
                target = pluginReference,
                resolvedSource = resolvedMarketplace.resolvedSource,
                version = exactVersion,
                integrity = resolvedPluginIntegrity(resolvedMarketplace.root, remoteEntry),
            )
        }

        output("imported ${importRef.marketplace.value}/${importRef.plugin.value} at $importedVersion")
        output("provider payloads are unchanged until you run marketplace materialize or marketplace publish")
    }

    private fun JsonObject.withDirectRemote(
        repoRoot: Path,
        target: MarketplaceImportTarget.Direct,
    ): JsonObject {
        val source = externalSource(
            repoRoot = repoRoot,
            repository = target.repository,
            ref = target.ref,
            allowExternalLocalGit = true,
        )
        val marketplaceName = target.importRef.marketplace.value
        val remotes = externalMarketplaces()
        val existing = remotes.firstOrNull { it.name == marketplaceName }
        if (existing != null && existing.source != source) {
            throw MarketplaceFailure.InvalidSource(
                "direct import marketplace name `$marketplaceName` already points to ${sourceDisplay(existing.source)}"
            )
        }
        return withExternalMarketplaces(
            (remotes.filterNot { it.name == marketplaceName } + ExternalMarketplace(marketplaceName, source))
                .sortedBy { it.name }
        ).withAllowedExternalMarketplace(marketplaceName)
    }

    private fun materializeAllMarketplaces(
        repoRoot: Path,
        outRoot: Path,
        generatedAt: String?,
        sourceSha: String?,
        resolver: RemoteMarketplaceResolver,
    ) {
        FileSystem.replaceDirectory(outRoot)
        renderDefaultMarketplaces(repoRoot, outRoot, generatedAt, sourceSha, resolver)
        outRoot.resolve("README.md").writeText(
            """
            # Intelligence Marketplace

            This branch is generated from the referential source graph on `main`.

            Provider-native marketplace projections are scoped under their expected entrypoints:

            - `.agents/plugins/marketplace.json`
            - `.github/plugin/marketplace.json`

            Each provider entrypoint owns its own `plugins/` payloads so marketplace-relative paths stay provider-native.
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
        output("materialized marketplace at $outRoot")
    }

    private fun materializeCodexMarketplace(
        repoRoot: Path,
        outRoot: Path,
        generatedAt: String?,
        sourceSha: String?,
        resolver: RemoteMarketplaceResolver,
    ) {
        FileSystem.replaceDirectory(outRoot)
        renderCodexMarketplace(repoRoot, outRoot, generatedAt, sourceSha, resolver)
        outRoot.resolve("README.md").writeText(
            """
            # Intelligence Codex Marketplace

            This branch is generated from the referential source graph on `main`.

            Codex expects the marketplace manifest at `.agents/plugins/marketplace.json` and plugin payloads under `.agents/plugins/plugins/`. Each plugin payload is fully hydrated from the provider-neutral primitives and contains its own `.codex-plugin/plugin.json`.

            Install with:

            ```sh
            codex plugin marketplace add amichne/intelligence --ref codex
            ```
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
        output("materialized Codex marketplace at $outRoot")
    }

    private fun materializeGitHubMarketplace(
        repoRoot: Path,
        outRoot: Path,
        resolver: RemoteMarketplaceResolver,
    ) {
        FileSystem.replaceDirectory(outRoot)
        renderGitHubMarketplace(repoRoot, outRoot, resolver)
        outRoot.resolve("README.md").writeText(
            """
            # Intelligence GitHub Marketplace

            This branch is generated from the referential source graph on `main`.

            GitHub Copilot marketplace consumers expect the marketplace manifest at `.github/plugin/marketplace.json` and plugin payloads under `.github/plugin/plugins/`. Each plugin payload is fully hydrated from the provider-neutral primitives.
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
        output("materialized GitHub marketplace at $outRoot")
    }

    private fun renderDefaultMarketplaces(
        repoRoot: Path,
        outRoot: Path,
        generatedAt: String?,
        sourceSha: String?,
        resolver: RemoteMarketplaceResolver,
    ) {
        renderCodexMarketplace(repoRoot, outRoot, generatedAt, sourceSha, resolver)
        renderGitHubMarketplace(repoRoot, outRoot, resolver)
    }

    private fun renderCodexMarketplace(
        repoRoot: Path,
        outRoot: Path,
        generatedAt: String?,
        sourceSha: String?,
        resolver: RemoteMarketplaceResolver,
    ) {
        val marketplace = marketplace(repoRoot)
        val owner = marketplace.objectValue("owner") ?: JsonObject(emptyMap())
        val ownerName = owner.stringValue("name") ?: "Local developer"
        val pluginsRoot = outRoot.resolve(CODEX_BRANCH_PLUGINS_PATH)
        val codexPlugins = mutableListOf<JsonObject>()
        val lockPlugins = mutableListOf<JsonObject>()

        marketplace.arrayValue("plugins").forEachObject { entry ->
            val pluginProjection = hydrateProjection(repoRoot, entry, owner, ownerName, resolver)
            val pluginOut = pluginsRoot.resolve(pluginProjection.name)
            pluginOut.createDirectories()

            val hydrated = hydratePlugin(pluginProjection.repoRoot, pluginOut, pluginProjection.manifest)
            renderAgentsMdAdapter(pluginOut, pluginProjection.name, pluginProjection.description, hydrated)
            codexPlugins.add(codexMarketplaceEntry(pluginProjection.name, pluginProjection.category))
            val codexManifest = codexPluginManifest(pluginProjection, ownerName, hydrated)
            JsonFiles.writeObject(pluginOut.resolve(CODEX_PLUGIN_DIR).resolve("plugin.json"), codexManifest)
            lockPlugins.add(lockEntry(pluginProjection, codexManifest, hydrated))
        }

        JsonFiles.writeObject(outRoot.resolve(CODEX_BRANCH_MARKETPLACE_PATH), codexMarketplace(marketplace, codexPlugins))
        JsonFiles.writeObject(outRoot.resolve(CODEX_BRANCH_LOCK_PATH), codexLock(repoRoot, generatedAt, sourceSha, lockPlugins))
    }

    private fun renderGitHubMarketplace(
        repoRoot: Path,
        outRoot: Path,
        resolver: RemoteMarketplaceResolver,
    ) {
        val marketplace = marketplace(repoRoot)
        val owner = marketplace.objectValue("owner") ?: JsonObject(emptyMap())
        val ownerName = owner.stringValue("name") ?: "Local developer"
        val pluginsRoot = outRoot.resolve(GITHUB_BRANCH_PLUGINS_PATH)
        val githubPlugins = mutableListOf<JsonObject>()

        marketplace.arrayValue("plugins").forEachObject { entry ->
            val pluginProjection = hydrateProjection(repoRoot, entry, owner, ownerName, resolver)
            val pluginOut = pluginsRoot.resolve(pluginProjection.name)
            pluginOut.createDirectories()

            val hydrated = hydratePlugin(pluginProjection.repoRoot, pluginOut, pluginProjection.manifest)
            renderAgentsMdAdapter(pluginOut, pluginProjection.name, pluginProjection.description, hydrated)
            githubPlugins.add(githubCopilotPluginEntry(pluginProjection, hydrated))
        }

        JsonFiles.writeObject(
            outRoot.resolve(GITHUB_BRANCH_MARKETPLACE_PATH),
            githubMarketplace(marketplace, owner, ownerName, githubPlugins),
        )
    }

    private fun hydrateProjection(
        repoRoot: Path,
        entry: JsonObject,
        owner: JsonObject,
        ownerName: String,
        resolver: RemoteMarketplaceResolver,
    ): PluginProjection {
        val sourceEntry = sourceEntryFor(repoRoot, entry, resolver)
        val pluginRef = sourceEntry.entry.requiredObject("plugin")
        val sourcePath = pluginRef.requiredObject("source").requiredString("path")
        val manifestPath = resolveSourcePath(sourceEntry.repoRoot, sourcePath).resolve("plugin.json")
        val manifest = JsonFiles.readObject(manifestPath)
        val name = manifest.requiredString("name")
        val description = manifest.stringValue("description")
            ?: entry.stringValue("description")
            ?: "$name plugin."
        val tags = entry.stringList("tags")

        return PluginProjection(
            name = name,
            repoRoot = sourceEntry.repoRoot,
            sourcePath = Path.of(sourcePath),
            manifest = manifest,
            pluginRef = entry.requiredObject("plugin"),
            description = description,
            tags = tags,
            category = categoryFor(tags),
            owner = personFor(owner, ownerName),
        )
    }

    private fun hydratePlugin(repoRoot: Path, pluginOut: Path, pluginManifest: JsonObject): HydratedPlugin {
        val queue = ArrayDeque<JsonObject>()
        val seen = linkedSetOf<Pair<String, String>>()
        val hydrated = HydratedPluginBuilder()

        fun enqueue(primitive: JsonObject) {
            val primitiveType = primitive.stringValue("type")
            val path = primitive.stringValue("path")
            if (PrimitiveKind.fromSourceName(primitiveType) == null || path.isNullOrBlank()) {
                return
            }
            val key = primitiveType!! to path
            if (seen.add(key)) {
                queue.add(primitive)
            }
        }

        PrimitiveKind.entries.forEach { kind ->
            pluginManifest.arrayValue(kind.collectionName).forEachObject(::enqueue)
        }

        while (queue.isNotEmpty()) {
            val primitive = queue.removeFirst()
            primitive.arrayValue("dependsOn").forEachObject(::enqueue)
            copyPrimitive(repoRoot, pluginOut, primitive, hydrated)
        }

        return hydrated.build()
    }

    private fun copyPrimitive(
        repoRoot: Path,
        pluginOut: Path,
        primitive: JsonObject,
        hydrated: HydratedPluginBuilder,
    ) {
        val source = primitive.requiredObject("source")
        if (source.stringValue("type") != "LOCAL_SOURCE" || source.stringValue("path") != "./") {
            throw MarketplaceFailure.InvalidSource("unsupported non-local primitive reference: $primitive")
        }

        val primitiveType = primitive.requiredString("type")
        val kind = PrimitiveKind.fromSourceName(primitiveType)
            ?: throw MarketplaceFailure.InvalidSource("unsupported primitive type: $primitiveType")
        val sourcePathValue = primitive.requiredString("path")
        val sourcePath = resolveSourcePath(repoRoot, sourcePathValue)
        if (!sourcePath.exists()) {
            throw MarketplaceFailure.InvalidSource("missing primitive path: $sourcePathValue")
        }

        if (kind == PrimitiveKind.Hook) {
            copyHook(repoRoot, pluginOut, primitive, sourcePath, hydrated)
            return
        }

        val targetPath = targetForPrimitive(pluginOut, kind, primitive, sourcePath)
        FileSystem.copyPath(sourcePath, targetPath)
        val relativeTarget = targetPath.relativeToUnix(pluginOut)
        hydrated.addPath(kind, relativeTarget)
        hydrated.addReference(
            HydratedReference(
                type = primitiveType,
                name = primitive.stringValue("name"),
                sourcePath = sourceDisplayPath(sourcePathValue),
                targetPath = relativeTarget,
                sha256 = Digests.digestPath(targetPath, DigestAlgorithm.Sha256),
            )
        )

        if (kind == PrimitiveKind.Agent && sourcePath.name.endsWith(".md")) {
            renderAgentToml(targetPath)
        }
    }

    private fun copyHook(
        repoRoot: Path,
        pluginOut: Path,
        primitive: JsonObject,
        sourcePath: Path,
        hydrated: HydratedPluginBuilder,
    ) {
        val metadata = JsonFiles.readObject(sourcePath)
        val adapterPathValue = metadata.stringValue("path") ?: primitive.requiredString("path")
        val adapterSource = resolveSourcePath(repoRoot, adapterPathValue)
        if (!adapterSource.exists()) {
            throw MarketplaceFailure.InvalidSource("missing hook adapter path: $adapterPathValue")
        }

        val metadataTarget = pluginOut.resolve("hooks").resolve("metadata").resolve(sourcePath.name)
        val adapterTarget = pluginOut.resolve("hooks").resolve(adapterSource.name)
        FileSystem.copyPath(sourcePath, metadataTarget)
        FileSystem.copyPath(adapterSource, adapterTarget)

        val relativeAdapter = adapterTarget.relativeToUnix(pluginOut)
        hydrated.addPath(PrimitiveKind.Hook, relativeAdapter)
        hydrated.addReference(
            HydratedReference(
                type = PrimitiveKind.Hook.sourceName,
                name = primitive.stringValue("name"),
                sourcePath = sourceDisplayPath(primitive.requiredString("path")),
                targetPath = relativeAdapter,
                sha256 = Digests.digestPath(adapterTarget, DigestAlgorithm.Sha256),
            )
        )

        val adapter = JsonFiles.readObject(adapterSource)
        hookCommandPaths(adapter).sorted().forEach { commandPath ->
            val commandSource = resolveSourcePath(repoRoot, commandPath)
            if (commandSource.exists()) {
                FileSystem.copyPath(commandSource, pluginOut.resolve(commandPath))
                FileSystem.copyHookSidecars(pluginOut, commandSource)
            }
        }
    }

    private fun renderAgentsMdAdapter(
        pluginOut: Path,
        pluginName: String,
        description: String,
        hydrated: HydratedPlugin,
    ) {
        if (hydrated.agents.isEmpty() && hydrated.instructions.isEmpty()) {
            return
        }

        val referencesByType = hydrated.references.groupBy { it.type }
        val lines = mutableListOf(
            "# ${titleCase(pluginName)} Plugin Instructions",
            "",
            "## Scope",
            "",
            "This generated adapter applies to the `$pluginName` plugin payload. Do not edit it directly; update the provider-neutral primitives or plugin manifest, then regenerate the marketplace output.",
            "",
            "## Runtime Boundary",
            "",
            "The source graph keeps skills, agent profiles, instructions, concepts, and hooks as independent primitives. This `AGENTS.md` adapts bundled agent and instruction primitives into a plain instruction file for runtimes that do not expose those primitive kinds directly.",
            "",
            "## Plugin Intent",
            "",
            description,
            "",
            "## Operating Rules",
            "",
            "- Treat this file as an adapter, not a new source of truth.",
            "- Use bundled skills for step-by-step workflows.",
            "- Apply bundled instructions as normative guidance when their scope matches the task.",
            "- Treat bundled agent profiles as review criteria or focused review passes.",
            "- Keep hook behavior in bundled hook files and runtime adapter configs.",
            "- When guidance conflicts with the target repository's nearest `AGENTS.md`, follow the target repository unless the user explicitly chooses this plugin's rule.",
            "",
        )

        appendReferenceSection(lines, "Instruction Primitives", referencesByType[PrimitiveKind.Instruction.sourceName].orEmpty())
        appendReferenceSection(lines, "Agent Profile Primitives", referencesByType[PrimitiveKind.Agent.sourceName].orEmpty())
        appendReferenceSection(lines, "Skill Primitives", referencesByType[PrimitiveKind.Skill.sourceName].orEmpty())
        appendReferenceSection(lines, "Hook Primitives", referencesByType[PrimitiveKind.Hook.sourceName].orEmpty())
        pluginOut.resolve("AGENTS.md").writeText(lines.joinToString("\n").trimEnd() + "\n", Charsets.UTF_8)
    }

    private fun appendReferenceSection(lines: MutableList<String>, title: String, references: List<HydratedReference>) {
        if (references.isEmpty()) {
            return
        }

        lines += "## $title"
        lines += ""
        references
            .sortedWith(compareBy(HydratedReference::type, HydratedReference::sourcePath, HydratedReference::targetPath))
            .forEach { reference ->
                lines += "- `${reference.name}`: `${reference.targetPath}` (source: `${reference.sourcePath}`)"
            }
        lines += ""
    }

    private fun renderAgentToml(markdown: Path) {
        val lines = markdown.readText(Charsets.UTF_8).lines()
        if (lines.firstOrNull() != "---") {
            return
        }
        val end = lines.drop(1).indexOf("---").takeIf { it >= 0 }?.plus(1) ?: return
        val frontmatter = lines.subList(1, end)
            .mapNotNull { line ->
                val separator = line.indexOf(":")
                if (separator < 0) {
                    null
                } else {
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim().trim('\'', '"')
                }
            }
            .toMap()
        val name = frontmatter["name"]
        val description = frontmatter["description"]
        val instructions = lines.drop(end + 1).joinToString("\n").trim() + "\n"
        if (name.isNullOrBlank() || description.isNullOrBlank() || instructions.isBlank()) {
            return
        }

        markdown.resolveSibling("${markdown.name.substringBeforeLast(".")}.toml").writeText(
            buildString {
                append("name = ")
                appendLine(JsonFiles.json.encodeToString(String.serializer(), name))
                append("description = ")
                appendLine(JsonFiles.json.encodeToString(String.serializer(), description))
                append("developer_instructions = ")
                appendLine(JsonFiles.json.encodeToString(String.serializer(), instructions))
            },
            Charsets.UTF_8,
        )
    }

    private fun codexMarketplace(marketplace: JsonObject, plugins: List<JsonObject>): JsonObject =
        buildJsonObject {
            put("name", marketplace.requiredString("name"))
            putJsonObject("interface") {
                put("displayName", titleCase(marketplace.requiredString("name")))
            }
            put("plugins", JsonArray(plugins))
        }

    private fun githubMarketplace(
        marketplace: JsonObject,
        owner: JsonObject,
        ownerName: String,
        plugins: List<JsonObject>,
    ): JsonObject =
        buildJsonObject {
            put("\$schema", "github-marketplace.schema.json")
            put("name", marketplace.requiredString("name"))
            put("owner", personFor(owner, ownerName))
            putJsonObject("metadata") {
                put("description", marketplace.stringValue("description") ?: "")
                put("pluginRoot", GITHUB_MARKETPLACE_PLUGIN_ROOT)
            }
            put("plugins", JsonArray(plugins))
        }

    private fun codexLock(
        repoRoot: Path,
        generatedAt: String?,
        sourceSha: String?,
        plugins: List<JsonObject>,
    ): JsonObject =
        buildJsonObject {
            put("type", "HYDRATED_MARKETPLACE")
            put("schemaVersion", 1)
            put("generatedAt", generatedAt ?: currentSourceTimestamp(repoRoot))
            put("sourceSha", sourceSha ?: currentSha(repoRoot))
            put("plugins", JsonArray(plugins))
        }

    private fun codexMarketplaceEntry(pluginName: String, category: String): JsonObject =
        buildJsonObject {
            put("name", pluginName)
            putJsonObject("source") {
                put("source", "local")
                put("path", "./plugins/$pluginName")
            }
            putJsonObject("policy") {
                put("installation", "AVAILABLE")
                put("authentication", "ON_INSTALL")
            }
            put("category", category)
        }

    private fun codexPluginManifest(
        plugin: PluginProjection,
        ownerName: String,
        hydrated: HydratedPlugin,
    ): JsonObject =
        buildJsonObject {
            put("name", plugin.name)
            put("version", plugin.version)
            put("description", plugin.description)
            putJsonObject("author") {
                put("name", ownerName)
            }
            put("license", "UNLICENSED")
            putStringArray("keywords", (listOf(plugin.name) + plugin.tags).toSortedSet())
            putJsonObject("interface") {
                put("displayName", titleCase(plugin.name))
                put("shortDescription", shortDescription(plugin.description))
                put("longDescription", plugin.description)
                put("developerName", ownerName)
                put("category", plugin.category)
                putStringArray("capabilities", capabilitiesFor(hydrated))
                put("brandColor", brandColorFor(plugin.category))
                put("defaultPrompt", defaultPrompts(plugin.name))
            }
            if (hydrated.skills.isNotEmpty()) {
                put("skills", "./skills/")
            }
        }

    private fun githubCopilotPluginEntry(plugin: PluginProjection, hydrated: HydratedPlugin): JsonObject =
        buildJsonObject {
            put("name", plugin.name)
            put("source", plugin.name)
            put("description", plugin.description)
            put("version", plugin.version)
            put("author", plugin.owner)
            put("license", "UNLICENSED")
            putStringArray("keywords", (listOf(plugin.name) + plugin.tags).toSortedSet())
            put("category", plugin.category)
            putStringArray("tags", plugin.tags)
            put("strict", true)
            if (hydrated.agents.isNotEmpty()) {
                put("agents", "./agents")
            }
            if (hydrated.skills.isNotEmpty()) {
                put("skills", "./skills")
            }
            if (hydrated.hooks.isNotEmpty()) {
                put("hooks", "./hooks")
            }
        }

    private fun lockEntry(plugin: PluginProjection, codexManifest: JsonObject, hydrated: HydratedPlugin): JsonObject =
        buildJsonObject {
            put("name", plugin.name)
            put("version", codexManifest.requiredString("version"))
            put("sourceManifest", sourceDisplayPath(plugin.sourcePath.resolve("plugin.json")))
            put("references", hydrated.toReferenceJson())
        }

    private fun personFor(owner: JsonObject, fallbackName: String): JsonObject =
        buildJsonObject {
            put("name", owner.stringValue("name") ?: fallbackName)
            putIfNotNull("email", owner.stringValue("email"))
            putIfNotNull("url", owner.stringValue("url"))
        }

    private fun targetForPrimitive(
        pluginOut: Path,
        kind: PrimitiveKind,
        primitive: JsonObject,
        sourcePath: Path,
    ): Path =
        when (kind) {
            PrimitiveKind.Skill -> pluginOut.resolve("skills").resolve(primitive.requiredString("name"))
            PrimitiveKind.Agent -> pluginOut.resolve("agents").resolve(sourcePath.name)
            PrimitiveKind.Instruction -> {
                val suffix = sourcePath.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".md"
                pluginOut.resolve("instructions").resolve("${primitive.requiredString("name")}$suffix")
            }
            PrimitiveKind.Hook -> throw MarketplaceFailure.InvalidSource("hooks use hook-specific projection")
        }

    private fun hookCommandPaths(payload: JsonElement): Set<String> =
        when (payload) {
            is JsonObject -> payload.entries.flatMapTo(linkedSetOf()) { (key, value) ->
                if (key == "command" && value is JsonPrimitive && value.isString) {
                    HOOK_COMMAND_PATH_RE.findAll(value.content).map { it.value }.toSet()
                } else {
                    hookCommandPaths(value)
                }
            }
            is JsonArray -> payload.flatMapTo(linkedSetOf(), ::hookCommandPaths)
            else -> emptySet()
        }

    private fun marketplace(repoRoot: Path): JsonObject =
        JsonFiles.readObject(repoRoot.resolve(ADAPTABLE_MARKETPLACE_PATH))

    private fun currentSha(repoRoot: Path): String =
        Digests.digestPath(repoRoot.resolve(SOURCE_ROOT), DigestAlgorithm.Sha1)

    private fun currentSourceTimestamp(repoRoot: Path): String {
        val process = ProcessBuilder("git", "show", "-s", "--format=%cI", "HEAD")
            .directory(repoRoot.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val value = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        return if (process.waitFor() == 0 && value.isNotBlank()) {
            value
        } else {
            "1970-01-01T00:00:00+00:00"
        }
    }

    private fun runChecked(command: List<String>, cwd: Path) {
        output("+ " + command.joinToString(" "))
        val exit = processRunner.run(command, cwd)
        if (exit != 0) {
            throw MarketplaceFailure.InvalidSource("command failed with exit code $exit: ${command.joinToString(" ")}")
        }
    }

    private fun runIgnoringFailure(command: List<String>, cwd: Path) {
        processRunner.run(command, cwd)
    }

    private fun refuseRepositoryOutput(repoRoot: Path, outRoot: Path) {
        if (outRoot == repoRoot || outRoot.startsWith(repoRoot)) {
            throw MarketplaceFailure.InvalidSource("refusing to materialize inside repository root: $outRoot")
        }
    }

    private fun resolveSourcePath(repoRoot: Path, pathValue: String): Path {
        val path = Path.of(pathValue).normalize()
        if (path.isAbsolute) {
            throw MarketplaceFailure.InvalidSource("source paths must be repository-relative: $pathValue")
        }
        return if (path.nameCount > 0 && path.getName(0).toString() == SOURCE_ROOT.toString()) {
            repoRoot.resolve(path).normalize()
        } else {
            repoRoot.resolve(SOURCE_ROOT).resolve(path).normalize()
        }
    }

    private fun sourceDisplayPath(pathValue: Path): String =
        sourceDisplayPath(pathValue.toString().replace('\\', '/'))

    private fun sourceDisplayPath(pathValue: String): String {
        val trimmed = pathValue.removePrefix("./")
        if (trimmed == ".") {
            return SOURCE_ROOT.toString()
        }
        return if (trimmed.startsWith("${SOURCE_ROOT}/")) {
            trimmed
        } else {
            "$SOURCE_ROOT/$trimmed"
        }
    }

    private fun sourceEntryFor(
        repoRoot: Path,
        entry: JsonObject,
        resolver: RemoteMarketplaceResolver,
    ): SourceEntry {
        val pluginRef = entry.requiredObject("plugin")
        val source = pluginRef.requiredObject("source")
        return when (val sourceType = source.stringValue("type")) {
            "LOCAL_SOURCE" -> SourceEntry(repoRoot, entry)
            "MARKETPLACE_SOURCE" -> {
                val importRef = MarketplaceImportRef.parse(
                    "${source.requiredString("marketplace")}/${source.requiredString("plugin")}"
                )
                val remote = marketplace(repoRoot).externalMarketplaces()
                    .firstOrNull { it.name == importRef.marketplace.value }
                    ?: throw MarketplaceFailure.InvalidSource(
                        "unknown external marketplace: ${importRef.marketplace.value}"
                    )
                val resolved = resolver.resolve(remote)
                val remoteMarketplace = marketplaceAt(resolved.root)
                val remoteEntry = remoteMarketplace.arrayValue("plugins")
                    .objects()
                    .firstOrNull { it.stringValue("name") == importRef.plugin.value }
                    ?: throw MarketplaceFailure.InvalidSource(
                        "plugin ${importRef.plugin.value} was not found in ${importRef.marketplace.value}"
                    )
                requireRemoteVersion(
                    remoteRoot = resolved.root,
                    remoteEntry = remoteEntry,
                    marketplace = importRef.marketplace.value,
                    plugin = importRef.plugin.value,
                    version = source.requiredString("version"),
                )
                SourceEntry(resolved.root, remoteEntry)
            }
            else -> throw MarketplaceFailure.InvalidSource("unsupported plugin source type: $sourceType")
        }
    }

    private fun externalSource(
        repoRoot: Path,
        repository: String,
        ref: String?,
        allowExternalLocalGit: Boolean = false,
    ): JsonObject {
        val localPath = repository.toCliPath()
        if (localPath.exists()) {
            val absolute = localPath.normalizedAbsolute()
            if (!absolute.startsWith(repoRoot)) {
                if (allowExternalLocalGit && looksLikeGitRepository(absolute)) {
                    return buildJsonObject {
                        put("type", "GIT_SOURCE")
                        put("url", absolute.toString())
                        put("ref", ExactRef.parse(ref).value)
                    }
                }
                throw MarketplaceFailure.InvalidSource("local marketplace path must be inside the target repository")
            }
            return buildJsonObject {
                put("type", "LOCAL_SOURCE")
                put("path", absolute.relativeToUnix(repoRoot))
            }
        }

        parseGitHubRepository(repository)?.let { repo ->
            return buildJsonObject {
                put("type", "GITHUB_SOURCE")
                put("repo", repo)
                put("ref", ExactRef.parse(ref).value)
            }
        }

        if (repository.startsWith("git@") || repository.startsWith("https://") || repository.startsWith("ssh://") ||
            repository.endsWith(".git")
        ) {
            return buildJsonObject {
                put("type", "GIT_SOURCE")
                put("url", repository)
                put("ref", ExactRef.parse(ref).value)
            }
        }

        throw MarketplaceFailure.InvalidSource(
            "repository must be an existing local path, GitHub owner/repo, GitHub URL, or git URL"
        )
    }

    private fun looksLikeGitRepository(path: Path): Boolean =
        path.name.endsWith(".git") || path.resolve(".git").exists()

    private fun remoteNameForRepository(repository: String): MarketplaceName {
        val rawName = parseGitHubRepository(repository)
            ?.substringAfter("/")
            ?: repository
                .trim()
                .trimEnd('/')
                .removeSuffix(".git")
                .substringAfterLast("/")
                .substringAfterLast(":")
        return MarketplaceName.fromRepositoryName(rawName)
    }

    private fun importedPluginReference(importRef: MarketplaceImportRef, version: ExactVersion): JsonObject =
        buildJsonObject {
            put("type", "PLUGIN_REFERENCE")
            put("name", importRef.plugin.value)
            putJsonObject("source") {
                put("type", "MARKETPLACE_SOURCE")
                put("marketplace", importRef.marketplace.value)
                put("plugin", importRef.plugin.value)
                put("version", version.value)
            }
            put("version", version.value)
        }

    private fun importedPluginEntry(
        importRef: MarketplaceImportRef,
        pluginReference: JsonObject,
        remoteEntry: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("type", "PLUGIN_ENTRY")
            put("name", importRef.plugin.value)
            put("plugin", pluginReference)
            putIfNotNull("description", remoteEntry.stringValue("description"))
            putStringArray("tags", remoteEntry.stringList("tags"))
        }

    private fun writeMarketplaceLock(
        repoRoot: Path,
        rootMarketplace: JsonObject,
        target: JsonObject,
        resolvedSource: JsonObject,
        version: ExactVersion,
        integrity: String,
    ) {
        val lockPath = repoRoot.resolve(MARKETPLACE_LOCK_PATH)
        val currentEntries = if (lockPath.exists()) {
            JsonFiles.readObject(lockPath).arrayValue("entries").objects()
        } else {
            emptyList()
        }
        val targetName = target.requiredString("name")
        val nextEntries = currentEntries
            .filterNot { it.objectValue("target")?.stringValue("name") == targetName } +
            buildJsonObject {
                put("type", "LOCKED_PLUGIN")
                put("target", target)
                put("resolvedSource", resolvedSource)
                put("version", version.value)
                put("integrity", integrity)
                putJsonArray("dependsOn") {}
            }

        JsonFiles.writeObject(
            lockPath,
            buildJsonObject {
                put("type", "LOCKFILE")
                put("schemaVersion", 1)
                put("generatedAt", Instant.now().toString())
                putJsonObject("root") {
                    put("type", "MARKETPLACE_LOCK_ROOT")
                    put("name", rootMarketplace.requiredString("name"))
                    putJsonObject("source") {
                        put("type", "LOCAL_SOURCE")
                        put("path", "source/adaptable.marketplace.json")
                    }
                }
                put("entries", JsonArray(nextEntries.sortedBy { it.objectValue("target")?.stringValue("name") }))
            }
        )
    }

    private fun resolvedPluginIntegrity(remoteRoot: Path, remoteEntry: JsonObject): String {
        val pluginRef = remoteEntry.requiredObject("plugin")
        val source = pluginRef.requiredObject("source")
        if (source.stringValue("type") != "LOCAL_SOURCE") {
            throw MarketplaceFailure.InvalidSource("imported plugin must resolve to a local source in its marketplace")
        }
        val sourcePath = resolveSourcePath(remoteRoot, source.requiredString("path"))
        if (!sourcePath.exists()) {
            throw MarketplaceFailure.InvalidSource("imported plugin source path is missing: ${source.requiredString("path")}")
        }
        return Digests.digestPath(sourcePath, DigestAlgorithm.Sha256)
    }

    private fun requireRemoteVersion(
        remoteRoot: Path,
        remoteEntry: JsonObject,
        marketplace: String,
        plugin: String,
        version: String,
    ) {
        val remoteVersion = remotePluginVersion(remoteRoot, remoteEntry)
        if (remoteVersion != version) {
            throw MarketplaceFailure.InvalidSource(
                "plugin $marketplace/$plugin is version $remoteVersion, not requested version $version"
            )
        }
    }

    private fun remotePluginVersion(remoteRoot: Path, remoteEntry: JsonObject): String {
        val pluginRef = remoteEntry.requiredObject("plugin")
        pluginRef.stringValue("version")?.takeIf { it.isNotBlank() }?.let { return it }

        val source = pluginRef.requiredObject("source")
        if (source.stringValue("type") != "LOCAL_SOURCE") {
            throw MarketplaceFailure.InvalidSource("imported plugin must resolve to a local source in its marketplace")
        }
        val manifestPath = resolveSourcePath(remoteRoot, source.requiredString("path")).resolve("plugin.json")
        val manifest = JsonFiles.readObject(manifestPath)
        return manifest.stringValue("version")
            ?.takeIf { it.isNotBlank() }
            ?: throw MarketplaceFailure.InvalidSource(
                "imported plugin ${pluginRef.requiredString("name")} is missing an exact version"
            )
    }

    private fun marketplaceAt(root: Path): JsonObject {
        val sourceMarketplace = root.resolve(ADAPTABLE_MARKETPLACE_PATH)
        val rootMarketplace = root.resolve("adaptable.marketplace.json")
        return when {
            sourceMarketplace.exists() -> JsonFiles.readObject(sourceMarketplace)
            rootMarketplace.exists() -> JsonFiles.readObject(rootMarketplace)
            else -> throw MarketplaceFailure.InvalidSource("missing marketplace source in $root")
        }
    }

    private fun parseGitHubRepository(value: String): String? {
        val trimmed = value.trim()
        val githubPath = when {
            trimmed.startsWith("https://github.com/") -> trimmed.removePrefix("https://github.com/")
            trimmed.startsWith("http://github.com/") -> trimmed.removePrefix("http://github.com/")
            trimmed.startsWith("git@github.com:") -> trimmed.removePrefix("git@github.com:")
            GITHUB_SHORTHAND.matches(trimmed) -> trimmed
            else -> return null
        }
        val owner = githubPath.split("/").getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val repo = githubPath.split("/").getOrNull(1)
            ?.removeSuffix(".git")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "$owner/$repo"
    }

    private fun sourceDisplay(source: JsonObject): String =
        when (source.stringValue("type")) {
            "LOCAL_SOURCE" -> source.requiredString("path")
            "GITHUB_SOURCE" -> "${source.requiredString("repo")}@${source.stringValue("ref") ?: "<unresolved>"}"
            "GIT_SOURCE" -> "${source.requiredString("url")}@${source.stringValue("ref") ?: "<unresolved>"}"
            "GIT_SUBDIR_SOURCE" -> "${source.requiredString("url")}/${source.requiredString("path")}@" +
                (source.stringValue("ref") ?: "<unresolved>")
            else -> source.toString()
        }

    private fun JsonObject.externalMarketplaces(): List<ExternalMarketplace> =
        arrayValue("externalMarketplaces")
            .objects()
            .map { remote ->
                ExternalMarketplace(
                    name = remote.requiredString("name"),
                    source = remote.requiredObject("source"),
                )
            }

    private fun JsonObject.allowedExternalMarketplaceNames(): Set<String> =
        objectValue("management")
            ?.stringList("allowExternalMarketplaces")
            .orEmpty()
            .toSet()

    private fun JsonObject.importedPluginEntries(marketplaceName: String): List<JsonObject> =
        arrayValue("plugins")
            .objects()
            .filter { entry ->
                entry.objectValue("plugin")
                    ?.objectValue("source")
                    ?.takeIf { it.stringValue("type") == "MARKETPLACE_SOURCE" }
                    ?.stringValue("marketplace") == marketplaceName
            }

    private fun JsonObject.withExternalMarketplaces(remotes: List<ExternalMarketplace>): JsonObject =
        withReplacedFields(
            "externalMarketplaces" to JsonArray(
                remotes.map { remote ->
                    buildJsonObject {
                        put("type", "EXTERNAL_MARKETPLACE")
                        put("name", remote.name)
                        put("source", remote.source)
                    }
                }
            )
        )

    private fun JsonObject.withAllowedExternalMarketplace(name: String): JsonObject {
        val management = objectValue("management") ?: buildJsonObject {
            put("type", "MANAGEMENT_POLICY")
            put("mode", "CURATED")
        }
        val allowed = (management.stringList("allowExternalMarketplaces") + name).toSortedSet()
        return withReplacedFields(
            "management" to management.withReplacedFields(
                "allowExternalMarketplaces" to jsonArrayOfStrings(allowed)
            )
        )
    }

    private fun JsonObject.withoutAllowedExternalMarketplace(name: String): JsonObject {
        val management = objectValue("management") ?: return this
        val allowed = management.stringList("allowExternalMarketplaces")
            .filterNot { it == name }
            .toSortedSet()
        return withReplacedFields(
            "management" to management.withReplacedFields(
                "allowExternalMarketplaces" to jsonArrayOfStrings(allowed)
            )
        )
    }

    private fun JsonObject.withPluginEntry(entry: JsonObject): JsonObject =
        withReplacedFields(
            "plugins" to JsonArray(arrayValue("plugins").objects() + entry)
        )

    private fun JsonObject.withReplacedFields(vararg replacements: Pair<String, JsonElement>): JsonObject =
        buildJsonObject {
            val replacementKeys = replacements.map { it.first }.toSet()
            this@withReplacedFields.forEach { (key, value) ->
                if (key !in replacementKeys) {
                    put(key, value)
                }
            }
            replacements.forEach { (key, value) -> put(key, value) }
        }

    private inner class RemoteMarketplaceResolver(
        private val repoRoot: Path,
    ) : AutoCloseable {
        private val tempRoot: Path = Files.createTempDirectory("intelligence-marketplace-resolve-")
        private val resolvedByName = mutableMapOf<String, ResolvedMarketplace>()

        fun resolve(remote: ExternalMarketplace): ResolvedMarketplace =
            resolvedByName.getOrPut(remote.name) {
                val source = remote.source
                when (val sourceType = source.stringValue("type")) {
                    "LOCAL_SOURCE" -> {
                        val root = resolveExternalLocalRoot(source.requiredString("path"))
                        ResolvedMarketplace(root = root, resolvedSource = source)
                    }
                    "GITHUB_SOURCE" -> {
                        val repo = source.requiredString("repo")
                        val ref = source.requiredString("ref")
                        val root = cloneRepository(
                            cloneUrl = "https://github.com/$repo.git",
                            ref = ref,
                            destination = tempRoot.resolve(remote.name),
                        )
                        ResolvedMarketplace(root = root, resolvedSource = source.withSha(root))
                    }
                    "GIT_SOURCE" -> {
                        val root = cloneRepository(
                            cloneUrl = source.requiredString("url"),
                            ref = source.requiredString("ref"),
                            destination = tempRoot.resolve(remote.name),
                        )
                        ResolvedMarketplace(root = root, resolvedSource = source.withSha(root))
                    }
                    "GIT_SUBDIR_SOURCE" -> {
                        val checkout = cloneRepository(
                            cloneUrl = source.requiredString("url"),
                            ref = source.requiredString("ref"),
                            destination = tempRoot.resolve(remote.name),
                        )
                        val root = resolveRelative(checkout, source.requiredString("path"))
                        ResolvedMarketplace(root = root, resolvedSource = source.withSha(checkout))
                    }
                    else -> throw MarketplaceFailure.InvalidSource("unsupported external marketplace source type: $sourceType")
                }
            }

        override fun close() {
            FileSystem.deleteRecursively(tempRoot)
        }

        private fun resolveExternalLocalRoot(pathValue: String): Path {
            val root = resolveRelative(repoRoot, pathValue)
            if (!root.exists()) {
                throw MarketplaceFailure.InvalidSource("external marketplace path does not exist: $pathValue")
            }
            return root
        }

        private fun cloneRepository(cloneUrl: String, ref: String, destination: Path): Path {
            destination.parent.createDirectories()
            val branchClone = listOf(
                "git",
                "clone",
                "--depth",
                "1",
                "--single-branch",
                "--branch",
                ref,
                cloneUrl,
                destination.toString(),
            )
            if (runProcess(branchClone, repoRoot) != 0) {
                val clone = listOf("git", "clone", "--depth", "1", cloneUrl, destination.toString())
                if (runProcess(clone, repoRoot) != 0 || runProcess(listOf("git", "checkout", ref), destination) != 0) {
                    throw MarketplaceFailure.InvalidSource("failed to resolve external marketplace $cloneUrl at $ref")
                }
            }
            return destination
        }

        private fun JsonObject.withSha(gitRoot: Path): JsonObject =
            withReplacedFields("sha" to JsonPrimitive(gitOutput(listOf("git", "rev-parse", "HEAD"), gitRoot)))
    }

    private fun runProcess(command: List<String>, cwd: Path): Int =
        ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor()

    private fun gitOutput(command: List<String>, cwd: Path): String {
        val process = ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        if (process.waitFor() != 0 || output.isBlank()) {
            throw MarketplaceFailure.InvalidSource("command failed: ${command.joinToString(" ")}")
        }
        return output
    }

    private fun resolveRelative(base: Path, pathValue: String): Path {
        val relative = Path.of(pathValue.removePrefix("./")).normalize()
        if (relative.isAbsolute || relative.startsWith("..")) {
            throw MarketplaceFailure.InvalidSource("path escapes its root: $pathValue")
        }
        return base.resolve(relative).normalize()
    }

    private fun categoryFor(tags: List<String>): String {
        val tagSet = tags.toSet()
        return when {
            tagSet.any { it in setOf("git", "github", "ci", "schemas", "audit", "governance") } -> "Engineering"
            tagSet.any { it in setOf("kotlin", "testing") } -> "Coding"
            else -> "Productivity"
        }
    }

    private fun capabilitiesFor(hydrated: HydratedPlugin): List<String> =
        buildList {
            add("Interactive")
            if (hydrated.skills.isNotEmpty() || hydrated.hooks.isNotEmpty()) {
                add("Write")
            }
        }

    private fun brandColorFor(category: String): String =
        when (category) {
            "Coding" -> "#2563EB"
            "Engineering" -> "#0F766E"
            "Productivity" -> "#7C3AED"
            else -> "#475569"
        }

    private fun defaultPrompts(pluginName: String): JsonArray {
        val display = titleCase(pluginName)
        return buildJsonArray {
            add("Use $display for this task.")
            add("Review this repo with $display.")
            add("Show the $display workflow.")
        }
    }

    private fun shortDescription(description: String): String =
        if (description.length <= 72) description else description.take(69).trimEnd() + "..."

    private fun titleCase(value: String): String {
        val acronyms = mapOf(
            "api" to "API",
            "ci" to "CI",
            "cli" to "CLI",
            "github" to "GitHub",
            "json" to "JSON",
            "pr" to "PR",
            "prs" to "PRs",
            "tdd" to "TDD",
            "yaml" to "YAML",
        )
        return value.replace("_", "-")
            .split("-")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> acronyms[part.lowercase()] ?: part.replaceFirstChar { it.uppercase() } }
    }

    private data class PluginProjection(
        val name: String,
        val repoRoot: Path,
        val sourcePath: Path,
        val manifest: JsonObject,
        val pluginRef: JsonObject,
        val description: String,
        val tags: List<String>,
        val category: String,
        val owner: JsonObject,
    ) {
        val version: String =
            manifest.stringValue("version") ?: pluginRef.stringValue("version") ?: "0.1.0"
    }

    private companion object {
        const val CODEX_PLUGIN_DIR = ".codex-plugin"
        val SOURCE_ROOT: Path = Path.of("source")
        val ADAPTABLE_MARKETPLACE_PATH: Path = SOURCE_ROOT.resolve("adaptable.marketplace.json")
        val CODEX_MARKETPLACE_ROOT: Path = Path.of(".agents").resolve("plugins")
        val CODEX_BRANCH_MARKETPLACE_PATH: Path = CODEX_MARKETPLACE_ROOT.resolve("marketplace.json")
        val CODEX_BRANCH_PLUGINS_PATH: Path = CODEX_MARKETPLACE_ROOT.resolve("plugins")
        val CODEX_BRANCH_LOCK_PATH: Path = CODEX_MARKETPLACE_ROOT.resolve("marketplace-lock.json")
        val GITHUB_COPILOT_PROVIDER_PATH: Path = Path.of(".github").resolve("plugin")
        val GITHUB_BRANCH_MARKETPLACE_PATH: Path = GITHUB_COPILOT_PROVIDER_PATH.resolve("marketplace.json")
        val GITHUB_BRANCH_PLUGINS_PATH: Path = GITHUB_COPILOT_PROVIDER_PATH.resolve("plugins")
        const val GITHUB_MARKETPLACE_PLUGIN_ROOT = ".github/plugin/plugins"
        val HOOK_COMMAND_PATH_RE = Regex("\\bhooks/[A-Za-z0-9_.-]+")
        val MARKETPLACE_LOCK_PATH: Path = Path.of(".intelligence").resolve("marketplace-lock.json")
        val GITHUB_SHORTHAND = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    }
}

private class MarketplaceName private constructor(
    val value: String,
) {
    companion object {
        private val NAME = Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$")
        private val NON_NAME_CHARACTER = Regex("[^a-z0-9]+")

        fun parse(value: String): MarketplaceName {
            if (!NAME.matches(value)) {
                throw MarketplaceFailure.InvalidSource("marketplace names must be kebab-case: $value")
            }
            return MarketplaceName(value)
        }

        fun fromRepositoryName(value: String): MarketplaceName {
            val normalized = value
                .lowercase()
                .replace(NON_NAME_CHARACTER, "-")
                .trim('-')
            if (normalized.isBlank()) {
                throw MarketplaceFailure.InvalidSource("cannot derive marketplace name from repository: $value")
            }
            return parse(normalized)
        }
    }
}

private class ExactRef private constructor(
    val value: String,
) {
    companion object {
        fun parse(value: String?): ExactRef {
            val ref = value?.takeIf { it.isNotBlank() } ?: DEFAULT_REF
            return ExactRef(ref)
        }

        const val DEFAULT_REF = "main"
    }
}

private class ExactVersion private constructor(
    val value: String,
) {
    companion object {
        fun parse(value: String): ExactVersion {
            if (value.isBlank() || value.any(Char::isWhitespace) || value.any { it in setOf('^', '~', '*', '<', '>', '=') }) {
                throw MarketplaceFailure.InvalidSource("imports require an exact version")
            }
            return ExactVersion(value)
        }
    }
}

private data class MarketplaceImportRef(
    val marketplace: MarketplaceName,
    val plugin: MarketplaceName,
) {
    companion object {
        fun parse(value: String): MarketplaceImportRef {
            val parts = value.split("/")
            if (parts.size != 2) {
                throw MarketplaceFailure.InvalidSource("import must use marketplace/plugin syntax")
            }
            return MarketplaceImportRef(
                marketplace = MarketplaceName.parse(parts[0]),
                plugin = MarketplaceName.parse(parts[1]),
            )
        }

        fun parseOrNull(value: String): MarketplaceImportRef? {
            val parts = value.split("/")
            if (parts.size != 2) {
                return null
            }
            return MarketplaceImportRef(
                marketplace = MarketplaceName.parse(parts[0]),
                plugin = MarketplaceName.parse(parts[1]),
            )
        }
    }
}

private sealed interface MarketplaceImportTarget {
    val importRef: MarketplaceImportRef

    data class Named(
        override val importRef: MarketplaceImportRef,
    ) : MarketplaceImportTarget

    data class Direct(
        val repository: String,
        val ref: String?,
        override val importRef: MarketplaceImportRef,
    ) : MarketplaceImportTarget

    companion object {
        fun parse(
            value: String,
            ref: String?,
            remoteNameForRepository: (String) -> MarketplaceName,
        ): MarketplaceImportTarget {
            MarketplaceImportRef.parseOrNull(value)?.let { named ->
                if (!ref.isNullOrBlank()) {
                    throw MarketplaceFailure.InvalidSource("--ref only applies to direct repository imports")
                }
                return Named(named)
            }

            val separator = value.lastIndexOf('/')
            if (separator <= 0 || separator == value.lastIndex) {
                throw MarketplaceFailure.InvalidSource("import must use marketplace/plugin or repository/plugin syntax")
            }
            val repository = value.substring(0, separator).trimEnd('/')
            val plugin = MarketplaceName.parse(value.substring(separator + 1))
            val marketplace = remoteNameForRepository(repository)
            return Direct(
                repository = repository,
                ref = ref,
                importRef = MarketplaceImportRef(marketplace = marketplace, plugin = plugin),
            )
        }
    }
}

private data class ExternalMarketplace(
    val name: String,
    val source: JsonObject,
)

private data class ResolvedMarketplace(
    val root: Path,
    val resolvedSource: JsonObject,
)

private data class SourceEntry(
    val repoRoot: Path,
    val entry: JsonObject,
)

private fun JsonArray.forEachObject(action: (JsonObject) -> Unit) {
    forEach { element ->
        action(element.jsonObject)
    }
}

private fun JsonArray.objects(): List<JsonObject> =
    mapNotNull { it as? JsonObject }

private fun JsonObject.requiredObject(key: String): JsonObject =
    objectValue(key) ?: throw MarketplaceFailure.InvalidSource("missing object field `$key` in $this")

private fun JsonObject.requiredString(key: String): String =
    stringValue(key)?.takeIf { it.isNotBlank() }
        ?: throw MarketplaceFailure.InvalidSource("missing string field `$key` in $this")

private fun Path.relativeToUnix(base: Path): String =
    base.relativize(this).toString().replace('\\', '/')
