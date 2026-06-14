package intelligence.cli.validation

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.arrayValue
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringList
import intelligence.cli.io.stringValue
import intelligence.cli.marketplace.PrimitiveKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal class ValidationService(
    private val output: (String) -> Unit = ::println,
) {
    fun validate(options: ValidationOptions): Int {
        val repository = options.repo.toAbsolutePath().normalize()
        if (!repository.isDirectory()) {
            throw ValidationFailure("repository does not exist: ${options.repo}")
        }

        val issues = mutableListOf<String>()
        validateRetiredRootPaths(repository, issues)
        validateOptionalJsonSyntax(repository.resolve(SOURCE_ROOT), issues)
        validateOptionalJsonSyntax(repository.resolve(SCHEMAS_ROOT), issues)
        validateOptionalJsonSyntax(repository.resolve(INTELLIGENCE_ROOT), issues)
        validateMarketplaceSource(repository, issues)

        options.hydrated?.let { hydrated ->
            validateHydratedOutput(hydrated.toAbsolutePath().normalize(), issues)
        }

        return if (issues.isEmpty()) {
            output("OK adaptable marketplace")
            options.hydrated?.let { output("OK hydrated marketplace ${it.toAbsolutePath().normalize()}") }
            0
        } else {
            issues.forEach { issue -> output("FAIL $issue") }
            1
        }
    }

    private fun validateRetiredRootPaths(repo: Path, issues: MutableList<String>) {
        RETIRED_ROOT_PATHS
            .map(repo::resolve)
            .filter(Path::exists)
            .forEach { path -> issues += "retired root path remains: ${path.relativeToUnix(repo)}" }
    }

    private fun validateJsonSyntax(root: Path, issues: MutableList<String>) {
        if (!root.exists()) {
            issues += "missing source root: ${root.name}"
            return
        }

        walkRegularFiles(root)
            .filter { it.name.endsWith(".json") }
            .forEach { path ->
                try {
                    JsonFiles.json.parseToJsonElement(path.readText(Charsets.UTF_8))
                } catch (error: Exception) {
                    issues += "invalid JSON ${path.relativeToUnix(root.parent)}: ${error.message}"
                }
            }
    }

    private fun validateOptionalJsonSyntax(root: Path, issues: MutableList<String>) {
        if (root.exists()) {
            validateJsonSyntax(root, issues)
        }
    }

    private fun validateMarketplaceSource(repo: Path, issues: MutableList<String>) {
        val marketplacePath = existingMarketplacePath(repo)
            ?: run {
                issues += "missing adaptable marketplace: $ADAPTABLE_MARKETPLACE_PATH or $INSTALLED_MARKETPLACE_PATH"
                return
            }
        val sourceRoot = marketplaceContentRoot(repo, marketplacePath)
        val marketplace = readObject(marketplacePath, repo, issues) ?: return

        requireValue(marketplace.stringValue("type") == "MARKETPLACE", marketplacePath, repo, "type must be MARKETPLACE", issues)
        requireValue(marketplace["schemaVersion"]?.primitiveContent() == "1", marketplacePath, repo, "schemaVersion must be 1", issues)
        requireString(marketplace, "name", marketplacePath, repo, issues)
        requireValue(marketplace.objectValue("owner") != null, marketplacePath, repo, "owner must be an object", issues)
        val externalMarketplaces = validateExternalMarketplaces(repo, marketplacePath, marketplace, issues)
        val allowedExternalMarketplaces = marketplace.objectValue("management")
            ?.stringList("allowExternalMarketplaces")
            .orEmpty()
            .toSet()
        val lock = readLock(repo, issues)
        val lockedImportedMarketplaces = linkedSetOf<String>()

        val pluginEntries = marketplace.arrayValue("plugins")
        requireValue(pluginEntries.isNotEmpty(), marketplacePath, repo, "plugins must not be empty", issues)

        val referencedPlugins = linkedSetOf<String>()
        pluginEntries.forEachObject { entry ->
            val entryName = requireString(entry, "name", marketplacePath, repo, issues) ?: return@forEachObject
            if (!referencedPlugins.add(entryName)) {
                issues += "${marketplacePath.relativeToUnix(repo)}: duplicate plugin entry `$entryName`"
            }

            val pluginRef = entry.objectValue("plugin")
            if (pluginRef == null) {
                issues += "${marketplacePath.relativeToUnix(repo)}: plugin entry `$entryName` is missing plugin reference"
                return@forEachObject
            }

            when (val source = pluginRef.objectValue("source")) {
                null -> issues += "${marketplacePath.relativeToUnix(repo)}: plugin `$entryName` is missing source"
                else -> when (source.stringValue("type")) {
                    "LOCAL_SOURCE" -> {
                        val sourcePath = source.stringValue("path")
                        if (sourcePath.isNullOrBlank()) {
                            issues += "${marketplacePath.relativeToUnix(repo)}: plugin `$entryName` must use a local source path"
                            return@forEachObject
                        }
                        val manifestPath = resolveSourcePath(sourceRoot, sourcePath, issues) ?: return@forEachObject
                        val manifestFile = manifestPath.resolve("plugin.json")
                        val manifest = readObject(manifestFile, repo, issues) ?: return@forEachObject
                        validatePluginManifest(
                            repo = repo,
                            sourceRoot = sourceRoot,
                            path = manifestFile,
                            manifest = manifest,
                            expectedName = entryName,
                            issues = issues,
                        )
                    }
                    "MARKETPLACE_SOURCE" -> {
                        val imported = validateMarketplacePluginReference(
                            repo = repo,
                            marketplacePath = marketplacePath,
                            entryName = entryName,
                            pluginRef = pluginRef,
                            source = source,
                            externalMarketplaces = externalMarketplaces,
                            allowedExternalMarketplaces = allowedExternalMarketplaces,
                            lock = lock,
                            issues = issues,
                        )
                        if (imported?.hasLock == true) {
                            lockedImportedMarketplaces += imported.marketplace
                        }
                    }
                    else -> issues += "${marketplacePath.relativeToUnix(repo)}: plugin `$entryName` has unsupported source type `${source.stringValue("type")}`"
                }
            }
        }
        validateExternalMarketplaceAvailability(
            repo = repo,
            marketplacePath = marketplacePath,
            externalMarketplaces = externalMarketplaces,
            lockedImportedMarketplaces = lockedImportedMarketplaces,
            issues = issues,
        )

        val pluginRoot = sourceRoot.resolve("plugins")
        if (pluginRoot.exists()) {
            Files.list(pluginRoot).use { stream ->
                stream
                    .filter { it.isDirectory() }
                    .map { it.name }
                    .filter { it !in referencedPlugins }
                    .forEach { name -> issues += "source/plugins/$name is not listed in source/adaptable.marketplace.json" }
            }
        }
    }

    private fun validateExternalMarketplaces(
        repo: Path,
        marketplacePath: Path,
        marketplace: JsonObject,
        issues: MutableList<String>,
    ): Map<String, ValidatedExternalMarketplace> {
        val byName = linkedMapOf<String, ValidatedExternalMarketplace>()
        marketplace.arrayValue("externalMarketplaces").forEachObject { entry ->
            val name = requireString(entry, "name", marketplacePath, repo, issues) ?: return@forEachObject
            if (byName.containsKey(name)) {
                issues += "${marketplacePath.relativeToUnix(repo)}: duplicate external marketplace `$name`"
            }
            val source = entry.objectValue("source")
            if (source == null) {
                issues += "${marketplacePath.relativeToUnix(repo)}: external marketplace `$name` is missing source"
            } else {
                byName[name] = ValidatedExternalMarketplace(
                    missingLocalPath = validateExternalMarketplaceSource(repo, marketplacePath, name, source, issues),
                )
            }
        }
        marketplace.objectValue("management")
            ?.stringList("allowExternalMarketplaces")
            .orEmpty()
            .filter { it !in byName }
            .forEach { name ->
                issues += "${marketplacePath.relativeToUnix(repo)}: management allows unknown external marketplace `$name`"
            }
        return byName
    }

    private fun validateExternalMarketplaceSource(
        repo: Path,
        marketplacePath: Path,
        name: String,
        source: JsonObject,
        issues: MutableList<String>,
    ): Boolean =
        when (source.stringValue("type")) {
            "LOCAL_SOURCE" -> {
                val path = requireString(source, "path", marketplacePath, repo, issues) ?: return false
                val root = resolveRelative(repo, path, issues) ?: return false
                !root.isDirectory()
            }
            "GITHUB_SOURCE" -> {
                requireString(source, "repo", marketplacePath, repo, issues)
                requireString(source, "ref", marketplacePath, repo, issues)
                false
            }
            "GIT_SOURCE" -> {
                requireString(source, "url", marketplacePath, repo, issues)
                requireString(source, "ref", marketplacePath, repo, issues)
                false
            }
            "GIT_SUBDIR_SOURCE" -> {
                requireString(source, "url", marketplacePath, repo, issues)
                requireString(source, "path", marketplacePath, repo, issues)
                requireString(source, "ref", marketplacePath, repo, issues)
                false
            }
            else -> {
                issues += "${marketplacePath.relativeToUnix(repo)}: external marketplace `$name` has unsupported source type `${source.stringValue("type")}`"
                false
            }
        }

    private fun validateExternalMarketplaceAvailability(
        repo: Path,
        marketplacePath: Path,
        externalMarketplaces: Map<String, ValidatedExternalMarketplace>,
        lockedImportedMarketplaces: Set<String>,
        issues: MutableList<String>,
    ) {
        externalMarketplaces
            .filterValues { it.missingLocalPath }
            .filterKeys { name -> name !in lockedImportedMarketplaces }
            .forEach { (name, _) ->
                issues += "${marketplacePath.relativeToUnix(repo)}: external marketplace `$name` path is missing"
            }
    }

    private fun validateMarketplacePluginReference(
        repo: Path,
        marketplacePath: Path,
        entryName: String,
        pluginRef: JsonObject,
        source: JsonObject,
        externalMarketplaces: Map<String, ValidatedExternalMarketplace>,
        allowedExternalMarketplaces: Set<String>,
        lock: JsonObject?,
        issues: MutableList<String>,
    ): ImportedPluginValidation? {
        val marketplace = requireString(source, "marketplace", marketplacePath, repo, issues) ?: return null
        val plugin = requireString(source, "plugin", marketplacePath, repo, issues) ?: return null
        val version = requireString(source, "version", marketplacePath, repo, issues) ?: return null
        if (plugin != entryName || pluginRef.stringValue("name") != entryName) {
            issues += "${marketplacePath.relativeToUnix(repo)}: imported plugin `$entryName` must reference the same plugin name"
        }
        if (pluginRef.stringValue("version") != version) {
            issues += "${marketplacePath.relativeToUnix(repo)}: imported plugin `$entryName` must repeat the exact version on plugin.version"
        }
        if (marketplace !in externalMarketplaces) {
            issues += "${marketplacePath.relativeToUnix(repo)}: imported plugin `$entryName` references unknown marketplace `$marketplace`"
        }
        if (marketplace !in allowedExternalMarketplaces) {
            issues += "${marketplacePath.relativeToUnix(repo)}: imported plugin `$entryName` references disallowed marketplace `$marketplace`"
        }

        val lockEntry = lock?.arrayValue("entries")
            ?.objects()
            ?.firstOrNull { entry ->
                val target = entry.objectValue("target")
                val targetSource = target?.objectValue("source")
                target?.stringValue("name") == entryName &&
                    targetSource?.stringValue("type") == "MARKETPLACE_SOURCE" &&
                    targetSource.stringValue("marketplace") == marketplace &&
                    targetSource.stringValue("plugin") == plugin &&
                    targetSource.stringValue("version") == version
            }
        if (lockEntry == null) {
            issues += "${MARKETPLACE_LOCK_PATH.relativeToUnix(repo)}: missing lock entry for imported plugin `$entryName`"
            return ImportedPluginValidation(marketplace = marketplace, hasLock = false)
        }
        if (lockEntry.objectValue("resolvedSource") == null) {
            issues += "${MARKETPLACE_LOCK_PATH.relativeToUnix(repo)}: imported plugin `$entryName` is missing resolvedSource"
        }
        if (!lockEntry.stringValue("integrity").orEmpty().startsWith("sha256:")) {
            issues += "${MARKETPLACE_LOCK_PATH.relativeToUnix(repo)}: imported plugin `$entryName` is missing sha256 integrity"
        }
        return ImportedPluginValidation(marketplace = marketplace, hasLock = true)
    }

    private fun readLock(repo: Path, issues: MutableList<String>): JsonObject? {
        val lockPath = repo.resolve(MARKETPLACE_LOCK_PATH)
        if (!lockPath.exists()) {
            return null
        }
        val lock = readObject(lockPath, repo, issues) ?: return null
        requireValue(lock.stringValue("type") == "LOCKFILE", lockPath, repo, "type must be LOCKFILE", issues)
        requireValue(lock["schemaVersion"]?.primitiveContent() == "1", lockPath, repo, "schemaVersion must be 1", issues)
        return lock
    }

    private fun validatePluginManifest(
        repo: Path,
        sourceRoot: Path,
        path: Path,
        manifest: JsonObject,
        expectedName: String,
        issues: MutableList<String>,
    ) {
        requireValue(manifest.stringValue("type") == "PLUGIN", path, repo, "type must be PLUGIN", issues)
        requireValue(manifest["schemaVersion"]?.primitiveContent() == "1", path, repo, "schemaVersion must be 1", issues)
        val name = requireString(manifest, "name", path, repo, issues)
        if (name != null && name != expectedName) {
            issues += "${path.relativeToUnix(repo)}: plugin name `$name` does not match marketplace entry `$expectedName`"
        }

        PrimitiveKind.entries.forEach { kind ->
            manifest.arrayValue(kind.collectionName).forEachObject { primitive ->
                validatePrimitiveReference(repo, sourceRoot, path, primitive, kind, issues)
                primitive.arrayValue("dependsOn").forEachObject { dependency ->
                    val dependencyKind = dependency.stringValue("type")?.let(PrimitiveKind::fromSourceName)
                    if (dependencyKind == null) {
                        issues += "${path.relativeToUnix(repo)}: unsupported dependency type `${dependency.stringValue("type")}`"
                    } else {
                        validatePrimitiveReference(repo, sourceRoot, path, dependency, dependencyKind, issues)
                    }
                }
            }
        }
    }

    private fun validatePrimitiveReference(
        repo: Path,
        sourceRoot: Path,
        owner: Path,
        primitive: JsonObject,
        kind: PrimitiveKind,
        issues: MutableList<String>,
    ) {
        val primitiveName = requireString(primitive, "name", owner, repo, issues) ?: "<unnamed>"
        val source = primitive.objectValue("source")
        if (source?.stringValue("type") != "LOCAL_SOURCE" || source.stringValue("path") != "./") {
            issues += "${owner.relativeToUnix(repo)}: `$primitiveName` must use local source `./`"
            return
        }

        val sourcePathValue = requireString(primitive, "path", owner, repo, issues) ?: return
        val sourcePath = resolveSourcePath(sourceRoot, sourcePathValue, issues) ?: return
        if (!sourcePath.exists()) {
            issues += "${owner.relativeToUnix(repo)}: missing ${kind.sourceName.lowercase()} `$primitiveName` at ${sourcePath.relativeToUnix(repo)}"
            return
        }

        if (kind == PrimitiveKind.Hook) {
            val metadata = readObject(sourcePath, repo, issues) ?: return
            val adapterPathValue = metadata.stringValue("path") ?: sourcePathValue
            val adapterPath = resolveSourcePath(sourceRoot, adapterPathValue, issues) ?: return
            if (!adapterPath.exists()) {
                issues += "${sourcePath.relativeToUnix(repo)}: missing hook adapter ${adapterPath.relativeToUnix(repo)}"
            }
        }
    }

    private fun validateHydratedOutput(root: Path, issues: MutableList<String>) {
        if (!root.isDirectory()) {
            issues += "hydrated output does not exist: $root"
            return
        }

        var foundProvider = false
        val branchCodexMarketplace = root.resolve(".agents").resolve("plugins").resolve("marketplace.json")
        if (branchCodexMarketplace.exists()) {
            foundProvider = true
            validateCodexMarketplace(root, branchCodexMarketplace, issues)
        }

        val nestedCodexMarketplace = root.resolve("codex").resolve("marketplace.json")
        if (nestedCodexMarketplace.exists()) {
            foundProvider = true
            validateCodexMarketplace(root.resolve("codex"), nestedCodexMarketplace, issues)
        }

        val githubMarketplace = root.resolve(".github").resolve("plugin").resolve("marketplace.json")
        if (githubMarketplace.exists()) {
            foundProvider = true
            validateGithubMarketplace(root, githubMarketplace, issues)
        }

        if (!foundProvider) {
            issues += "hydrated output has no supported marketplace entrypoint: $root"
        }
    }

    private fun validateCodexMarketplace(root: Path, marketplacePath: Path, issues: MutableList<String>) {
        val marketplace = readObject(marketplacePath, root, issues) ?: return
        marketplace.arrayValue("plugins").forEachObject { entry ->
            val name = requireString(entry, "name", marketplacePath, root, issues) ?: return@forEachObject
            val sourcePath = entry.objectValue("source")?.stringValue("path")
            if (sourcePath.isNullOrBlank()) {
                issues += "${marketplacePath.relativeToUnix(root)}: Codex plugin `$name` has no source.path"
                return@forEachObject
            }

            val pluginRoot = resolveCodexPluginRoot(root, sourcePath, issues) ?: return@forEachObject
            if (!pluginRoot.isDirectory()) {
                issues += "${marketplacePath.relativeToUnix(root)}: missing Codex plugin payload ${pluginRoot.relativeToUnix(root)}"
                return@forEachObject
            }

            val manifest = pluginRoot.resolve(".codex-plugin").resolve("plugin.json")
            if (!manifest.isRegularFile()) {
                issues += "${pluginRoot.relativeToUnix(root)}: missing .codex-plugin/plugin.json"
            }
        }
    }

    private fun resolveCodexPluginRoot(
        root: Path,
        sourcePath: String,
        issues: MutableList<String>,
    ): Path? {
        return resolveRelative(root, sourcePath, issues)
    }

    private fun validateGithubMarketplace(root: Path, marketplacePath: Path, issues: MutableList<String>) {
        val marketplace = readObject(marketplacePath, root, issues) ?: return
        val pluginRoot = marketplace.objectValue("metadata")
            ?.stringValue("pluginRoot")
            ?.let { resolveRelative(root, it, issues) }
            ?: run {
                issues += "${marketplacePath.relativeToUnix(root)}: missing metadata.pluginRoot"
                return
            }

        marketplace.arrayValue("plugins").forEachObject { entry ->
            val name = requireString(entry, "name", marketplacePath, root, issues) ?: return@forEachObject
            val source = requireString(entry, "source", marketplacePath, root, issues) ?: return@forEachObject
            val payload = pluginRoot.resolve(source).normalize()
            if (!payload.startsWith(pluginRoot) || !payload.isDirectory()) {
                issues += "${marketplacePath.relativeToUnix(root)}: missing GitHub plugin payload for `$name`"
            }
        }
    }

    private fun readObject(path: Path, displayRoot: Path, issues: MutableList<String>): JsonObject? {
        if (!path.isRegularFile()) {
            issues += "missing JSON object: ${path.relativeToUnix(displayRoot)}"
            return null
        }

        return try {
            JsonFiles.readObject(path)
        } catch (error: Exception) {
            issues += "invalid JSON object ${path.relativeToUnix(displayRoot)}: ${error.message}"
            null
        }
    }

    private fun requireString(
        payload: JsonObject,
        key: String,
        path: Path,
        displayRoot: Path,
        issues: MutableList<String>,
    ): String? =
        payload.stringValue(key)?.takeIf { it.isNotBlank() }
            ?: run {
                issues += "${path.relativeToUnix(displayRoot)}: missing string field `$key`"
                null
            }

    private fun requireValue(
        condition: Boolean,
        path: Path,
        displayRoot: Path,
        message: String,
        issues: MutableList<String>,
    ) {
        if (!condition) {
            issues += "${path.relativeToUnix(displayRoot)}: $message"
        }
    }

    private fun resolveSourcePath(sourceRoot: Path, pathValue: String, issues: MutableList<String>): Path? =
        resolveRelative(sourceRoot, pathValue.removePrefix("source/"), issues)

    private fun existingMarketplacePath(repo: Path): Path? =
        listOf(
            repo.resolve(ADAPTABLE_MARKETPLACE_PATH),
            repo.resolve(INSTALLED_MARKETPLACE_PATH),
        ).firstOrNull { it.isRegularFile() }

    private fun marketplaceContentRoot(repo: Path, marketplacePath: Path): Path =
        if (marketplacePath == repo.resolve(ADAPTABLE_MARKETPLACE_PATH)) {
            repo.resolve(SOURCE_ROOT)
        } else {
            marketplacePath.parent ?: repo
        }

    private fun resolveRelative(base: Path, pathValue: String, issues: MutableList<String>): Path? {
        val relativeValue = pathValue.removePrefix("./")
        val path = Path.of(relativeValue).normalize()
        if (path.isAbsolute || path.startsWith("..")) {
            issues += "path escapes its root: $pathValue"
            return null
        }
        return base.resolve(path).normalize()
    }

    private fun walkRegularFiles(root: Path): Sequence<Path> =
        Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() }
                .filter { path -> !path.anySegmentIn(SKIPPED_DIRECTORIES) }
                .toList()
                .asSequence()
        }

    private fun Path.anySegmentIn(names: Set<String>): Boolean =
        iterator().asSequence().any { it.toString() in names }

    private fun JsonElement.primitiveContent(): String? =
        (this as? JsonPrimitive)?.content

    private fun JsonArray.forEachObject(action: (JsonObject) -> Unit) {
        forEach { element ->
            runCatching { element.jsonObject }.getOrNull()?.let(action)
        }
    }

    private fun JsonArray.objects(): List<JsonObject> =
        mapNotNull { it as? JsonObject }

    private fun Path.relativeToUnix(base: Path): String =
        runCatching { base.relativize(this).toString().replace('\\', '/') }
            .getOrElse { toString().replace('\\', '/') }

    private data class ValidatedExternalMarketplace(
        val missingLocalPath: Boolean,
    )

    private data class ImportedPluginValidation(
        val marketplace: String,
        val hasLock: Boolean,
    )

    private companion object {
        val SOURCE_ROOT: Path = Path.of("source")
        val ADAPTABLE_MARKETPLACE_PATH: Path = SOURCE_ROOT.resolve("adaptable.marketplace.json")
        val SCHEMAS_ROOT: Path = Path.of("schemas")
        val INTELLIGENCE_ROOT: Path = Path.of(".intelligence")
        val INSTALLED_MARKETPLACE_PATH: Path = INTELLIGENCE_ROOT.resolve("adaptable.marketplace.json")
        val MARKETPLACE_LOCK_PATH: Path = INTELLIGENCE_ROOT.resolve("marketplace-lock.json")
        val RETIRED_ROOT_PATHS: List<Path> = listOf(
            Path.of("packages"),
            Path.of("scripts"),
        )
        val SKIPPED_DIRECTORIES: Set<String> = setOf(
            ".git",
            ".gradle",
            ".idea",
            ".kotlin",
            ".local",
            ".cache",
            "build",
            "site",
        )
    }
}

internal data class ValidationOptions(
    val repo: Path,
    val portable: Boolean,
    val hydrated: Path?,
)

internal class ValidationFailure(message: String) : RuntimeException(message)
