package intelligence.cli.command

import intelligence.cli.io.JsonFiles
import intelligence.cli.portable.ConsumerOperationExecution
import intelligence.cli.portable.ConsumerOperationPlanning
import intelligence.cli.portable.ConsumerOperationRejection
import intelligence.cli.portable.ConsumerPersistedFile
import intelligence.cli.portable.ConsumerReconstruction
import intelligence.cli.portable.ConsumerRecoveryExecution
import intelligence.cli.portable.ConsumerRecoveryPlanning
import intelligence.cli.portable.ConsumerState
import intelligence.cli.portable.ConsumerStateReading
import intelligence.cli.portable.ConsumerStateRepository
import intelligence.cli.portable.DigestAddressedCache
import intelligence.cli.portable.DigestCacheRoot
import intelligence.cli.portable.DigestCacheRootResolution
import intelligence.cli.portable.DigestCacheWritePolicy
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.LocalConsumerOperations
import intelligence.cli.portable.MarketplaceId
import intelligence.cli.portable.MarketplaceIntentSelection
import intelligence.cli.portable.MarketplaceIntentSource
import intelligence.cli.portable.MarketplaceResolutionRejection
import intelligence.cli.portable.PackageName
import intelligence.cli.portable.PackageSelectionRequest
import intelligence.cli.portable.PackageSelectionRequestCreation
import intelligence.cli.portable.PortableProvider
import intelligence.cli.portable.ProviderProjectionDirectory
import intelligence.cli.portable.ProviderProjectionDirectoryMaterialization
import intelligence.cli.portable.ProviderProjectionDirectoryPreparation
import intelligence.cli.portable.ProviderProjectionDirectoryRejection
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.Sha256DigestParsing
import intelligence.cli.portable.ConsumerRelativeDirectory
import intelligence.cli.portable.ConsumerRelativeDirectoryParsing
import java.nio.file.InvalidPathException
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class PortableCommandEnvironment(
    private val cacheRootOverride: Path? = null,
    private val xdgCacheHome: () -> String? = { System.getenv("XDG_CACHE_HOME") },
    private val userHome: () -> String? = { System.getProperty("user.home") },
) {
    fun cache(): PortableCacheOpening =
        if (cacheRootOverride != null) {
            PortableCacheOpening.Opened(DigestAddressedCache.at(cacheRootOverride))
        } else {
            when (val resolved = DigestCacheRoot.resolve(xdgCacheHome(), userHome())) {
                is DigestCacheRootResolution.Resolved ->
                    PortableCacheOpening.Opened(DigestAddressedCache.at(resolved.path))
                is DigestCacheRootResolution.Rejected ->
                    PortableCacheOpening.Rejected(resolved.reason::class.simpleName ?: "invalid cache root")
            }
        }
}

internal sealed interface PortableCacheOpening {
    data class Opened(val cache: DigestAddressedCache) : PortableCacheOpening

    data class Rejected(val reason: String) : PortableCacheOpening
}

internal fun localMarketplaceConsumerCommands(
    environment: PortableCommandEnvironment,
): List<CliktCommand> =
    listOf(
        SelectPortableMarketplaceCommand(environment),
        RemovePortableMarketplaceCommand(environment),
        UpdatePortableMarketplaceCommand(environment),
        ResolvePortableMarketplaceCommand(environment),
        RecoverPortableMarketplaceCommand(environment),
        ReconstructPortableMarketplaceCommand(environment),
        ProjectPortableMarketplaceCommand(environment),
    )

private enum class PortableOutputFormat {
    HUMAN,
    JSON,
    ;

    companion object {
        fun parse(raw: String): PortableOutputFormat? =
            entries.singleOrNull { format -> format.name.equals(raw, ignoreCase = true) }
    }
}

private enum class PortableErrorCode(
    val exitCode: Int,
) {
    USAGE_ERROR(2),
    CONTRACT_VIOLATION(3),
    EXTERNAL_UNAVAILABLE(4),
    OFFLINE_CACHE_MISS(4),
    STATE_CONFLICT(5),
    INTERNAL_ERROR(70),
}

private data class PortableCommandError(
    val code: PortableErrorCode,
    val message: String,
    val details: JsonObject = JsonObject(emptyMap()),
)

private sealed interface PortableCommandOutcome {
    data class Success(
        val result: JsonObject,
        val human: String,
    ) : PortableCommandOutcome

    data class Failure(val error: PortableCommandError) : PortableCommandOutcome
}

private data class PortableInvocation(
    val repository: Path,
    val format: PortableOutputFormat,
    val dryRun: Boolean,
    val offline: Boolean,
)

private abstract class PortableConsumerCommand(
    name: String,
    private val commandId: String,
) : CliktCommand(name = name) {
    private val repositoryRaw by option(
        "--repository",
        help = "Consumer repository root. Defaults to the current directory.",
    ).default(".")

    private val formatRaw by option(
        "--format",
        help = "Output format: human or json.",
    ).default("human")

    protected val dryRun by option(
        "--dry-run",
        help = "Verify and report the complete operation without writing.",
    ).flag(default = false)

    protected val offline by option(
        "--offline",
        help = "Forbid every network request and require exact cached evidence.",
    ).flag(default = false)

    final override fun run() {
        val format = PortableOutputFormat.parse(formatRaw)
        if (format == null) {
            emit(
                PortableOutputFormat.HUMAN,
                PortableCommandOutcome.Failure(
                    PortableCommandError(
                        PortableErrorCode.USAGE_ERROR,
                        "--format must be human or json",
                    ),
                ),
            )
        }
        val repository =
            try {
                Path.of(repositoryRaw).toAbsolutePath().normalize()
            } catch (_: InvalidPathException) {
                emit(
                    checkNotNull(format),
                    PortableCommandOutcome.Failure(
                        PortableCommandError(
                            PortableErrorCode.USAGE_ERROR,
                            "--repository is not a valid filesystem path",
                        ),
                    ),
                )
            }
        val invocation = PortableInvocation(repository, checkNotNull(format), dryRun, offline)
        val outcome =
            try {
                execute(invocation)
            } catch (_: Exception) {
                PortableCommandOutcome.Failure(
                    PortableCommandError(
                        PortableErrorCode.INTERNAL_ERROR,
                        "Unexpected internal command failure",
                    ),
                )
            }
        emit(invocation.format, outcome)
    }

    protected abstract fun execute(invocation: PortableInvocation): PortableCommandOutcome

    private fun emit(
        format: PortableOutputFormat,
        outcome: PortableCommandOutcome,
    ): Nothing {
        val exitCode =
            when (outcome) {
                is PortableCommandOutcome.Success -> 0
                is PortableCommandOutcome.Failure -> outcome.error.code.exitCode
            }
        when (format) {
            PortableOutputFormat.JSON -> {
                val envelope =
                    buildJsonObject {
                        put("schemaVersion", 1)
                        put("command", commandId)
                        when (outcome) {
                            is PortableCommandOutcome.Success -> {
                                put("ok", true)
                                put("result", outcome.result)
                                put("error", JsonNull)
                            }
                            is PortableCommandOutcome.Failure -> {
                                put("ok", false)
                                put("result", JsonNull)
                                putJsonObject("error") {
                                    put("code", outcome.error.code.name)
                                    put("message", outcome.error.message)
                                    put("details", outcome.error.details)
                                }
                            }
                        }
                        put("diagnostics", JsonArray(emptyList()))
                    }
                echo(JsonFiles.compactJson.encodeToString(JsonElement.serializer(), envelope))
            }
            PortableOutputFormat.HUMAN -> {
                when (outcome) {
                    is PortableCommandOutcome.Success -> echo(outcome.human)
                    is PortableCommandOutcome.Failure ->
                        echo("${outcome.error.code.name}: ${outcome.error.message}", err = true)
                }
            }
        }
        throw ProgramResult(exitCode)
    }
}

private abstract class LocalSourcePortableCommand(
    name: String,
    commandId: String,
    protected val environment: PortableCommandEnvironment,
) : PortableConsumerCommand(name, commandId) {
    private val localSnapshotRaw by option(
        "--local-snapshot",
        help = "Repository-relative exact local snapshot directory.",
    )
    private val indexSha256Raw by option(
        "--index-sha256",
        help = "Expected SHA-256 of the canonical snapshot index.",
    )
    private val githubRaw by option(
        "--github",
        help = "Exact GitHub OWNER/REPOSITORY source.",
    )
    private val snapshotRaw by option(
        "--snapshot",
        help = "Exact immutable GitHub snapshot identifier.",
    )

    protected fun localSource(): BoundaryParsing<MarketplaceIntentSource.LocalSnapshot> {
        val localValues = listOf(localSnapshotRaw, indexSha256Raw)
        val githubValues = listOf(githubRaw, snapshotRaw)
        if (githubValues.any { value -> value != null }) {
            return if (githubValues.all { value -> value != null } && localValues.all { value -> value == null }) {
                BoundaryParsing.Failed(
                    PortableCommandError(
                        PortableErrorCode.EXTERNAL_UNAVAILABLE,
                        "GitHub snapshot resolution is not available in this local-source operation",
                    ),
                )
            } else {
                BoundaryParsing.Failed(sourceShapeError())
            }
        }
        if (localValues.any { value -> value == null }) {
            return BoundaryParsing.Failed(sourceShapeError())
        }
        val directory =
            when (val parsed = ConsumerRelativeDirectory.parse(checkNotNull(localSnapshotRaw))) {
                is ConsumerRelativeDirectoryParsing.Parsed -> parsed.directory
                is ConsumerRelativeDirectoryParsing.Rejected -> {
                    return BoundaryParsing.Failed(
                        contractError("--local-snapshot must be a safe repository-relative directory", parsed.reason),
                    )
                }
            }
        val digest =
            when (val parsed = Sha256Digest.parse(checkNotNull(indexSha256Raw))) {
                is Sha256DigestParsing.Parsed -> parsed.digest
                is Sha256DigestParsing.Rejected -> {
                    return BoundaryParsing.Failed(
                        contractError("--index-sha256 must be a lowercase SHA-256 digest", parsed.reason),
                    )
                }
            }
        return BoundaryParsing.Parsed(MarketplaceIntentSource.LocalSnapshot(directory, digest))
    }
}

private class SelectPortableMarketplaceCommand(
    environment: PortableCommandEnvironment,
) : LocalSourcePortableCommand("select", "marketplace.select", environment) {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()
    private val packageNamesRaw by option("--package", help = "Whole package name to select.").multiple()
    private val all by option("--all", help = "Select every package in the exact snapshot.").flag(default = false)

    override fun help(context: Context): String =
        "Select exact whole packages from one exact marketplace snapshot."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val parsedMarketplace = parseMarketplaceId(marketplaceRaw)
        val marketplaceId = parsedMarketplace.valueOr { failure -> return failure }
        val parsedSource = localSource()
        val source = parsedSource.valueOr { failure -> return failure }
        val parsedRequest = selectionRequest(packageNamesRaw, all)
        val request = parsedRequest.valueOr { failure -> return failure }
        val opened = environment.cache()
        val cache = opened.valueOr { failure -> return failure }
        val planning =
            LocalConsumerOperations.planSelect(
                invocation.repository,
                marketplaceId,
                source,
                request,
                cache,
                invocation.cachePolicy(),
            )
        return completeMutation(invocation, planning)
    }
}

private class RemovePortableMarketplaceCommand(
    @Suppress("unused") environment: PortableCommandEnvironment,
) : PortableConsumerCommand("remove", "marketplace.remove") {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()
    private val packageNamesRaw by option("--package", help = "Whole package name to remove.").multiple()

    override fun help(context: Context): String =
        "Remove exact whole packages and delete an empty marketplace selection."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val marketplace = parseMarketplaceId(marketplaceRaw)
        val marketplaceId = marketplace.valueOr { failure -> return failure }
        val packages = parsePackageNames(packageNamesRaw, requireNonEmpty = true)
        val packageNames = packages.valueOr { failure -> return failure }
        return completeMutation(
            invocation,
            LocalConsumerOperations.planRemove(invocation.repository, marketplaceId, packageNames),
        )
    }
}

private class UpdatePortableMarketplaceCommand(
    environment: PortableCommandEnvironment,
) : LocalSourcePortableCommand("update", "marketplace.update", environment) {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()

    override fun help(context: Context): String =
        "Move one selected marketplace to an explicit snapshot while preserving package names."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val marketplace = parseMarketplaceId(marketplaceRaw)
        val marketplaceId = marketplace.valueOr { failure -> return failure }
        val parsedSource = localSource()
        val source = parsedSource.valueOr { failure -> return failure }
        val opened = environment.cache()
        val cache = opened.valueOr { failure -> return failure }
        return completeMutation(
            invocation,
            LocalConsumerOperations.planUpdate(
                invocation.repository,
                marketplaceId,
                source,
                cache,
                invocation.cachePolicy(),
            ),
        )
    }
}

private class ResolvePortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("resolve", "marketplace.resolve") {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()

    override fun help(context: Context): String =
        "Resolve exact lock evidence without changing package selections."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val parsed = parseOptionalMarketplaceId(marketplaceRaw)
        val marketplaceId = parsed.valueOr { failure -> return failure }
        val opened = environment.cache()
        val cache = opened.valueOr { failure -> return failure }
        return completeMutation(
            invocation,
            LocalConsumerOperations.planResolve(
                invocation.repository,
                marketplaceId,
                cache,
                invocation.cachePolicy(),
            ),
        )
    }
}

private class RecoverPortableMarketplaceCommand(
    @Suppress("unused") environment: PortableCommandEnvironment,
) : PortableConsumerCommand("recover", "marketplace.recover") {
    override fun help(context: Context): String =
        "Explicitly finish or restore one interrupted consumer-state transaction."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val before = readState(invocation.repository)
        val beforeState = before.valueOr { failure -> return failure }
        return when (val planning = ConsumerStateRepository.planRecovery(invocation.repository)) {
            is ConsumerRecoveryPlanning.NotRequired ->
                PortableCommandOutcome.Success(
                    recoveryResult(invocation, beforeState, planning.state, "NOT_REQUIRED"),
                    "marketplace.recover: no recovery required",
                )
            is ConsumerRecoveryPlanning.Rejected -> rejectionFailure(planning.reason)
            is ConsumerRecoveryPlanning.Planned -> {
                if (invocation.dryRun) {
                    PortableCommandOutcome.Success(
                        recoveryResult(invocation, beforeState, beforeState, planning.plan.recovery::class.simpleName ?: "PLANNED"),
                        "marketplace.recover: dry-run verified",
                    )
                } else {
                    when (val executed = ConsumerStateRepository.execute(planning.plan)) {
                        is ConsumerRecoveryExecution.Rejected -> rejectionFailure(executed.reason)
                        is ConsumerRecoveryExecution.NotRequired ->
                            PortableCommandOutcome.Success(
                                recoveryResult(invocation, beforeState, executed.state, "NOT_REQUIRED"),
                                "marketplace.recover: no recovery required",
                            )
                        is ConsumerRecoveryExecution.Recovered -> {
                            val after = readState(invocation.repository)
                            val afterState = after.valueOr { failure -> return failure }
                            PortableCommandOutcome.Success(
                                recoveryResult(invocation, beforeState, afterState, executed.outcome.name),
                                "marketplace.recover: ${executed.outcome.name.lowercase()}",
                            )
                        }
                    }
                }
            }
        }
    }
}

private class ReconstructPortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("reconstruct", "marketplace.reconstruct") {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()

    override fun help(context: Context): String =
        "Reconstruct only exact locked cache objects, or prove them complete offline."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val parsed = parseOptionalMarketplaceId(marketplaceRaw)
        val marketplaceId = parsed.valueOr { failure -> return failure }
        val opened = environment.cache()
        val cache = opened.valueOr { failure -> return failure }
        return when (
            val reconstruction =
                LocalConsumerOperations.reconstruct(
                    invocation.repository,
                    marketplaceId,
                    cache,
                    invocation.offline,
                    invocation.cachePolicy(),
                )
        ) {
            is ConsumerReconstruction.Rejected -> rejectionFailure(reconstruction.reason)
            is ConsumerReconstruction.Reconstructed -> {
                val ids = reconstruction.marketplaces.map { marketplace -> marketplace.lockEntry.marketplaceId.render() }
                PortableCommandOutcome.Success(
                    buildJsonObject {
                        putJsonArray("marketplaceIds") { ids.forEach(::add) }
                        put("requiredObjects", reconstruction.requiredObjects)
                        put("cacheHits", reconstruction.cacheHits)
                        put("fetched", reconstruction.fetchedObjects)
                        put("dryRun", invocation.dryRun)
                    },
                    "marketplace.reconstruct: ${ids.size} marketplace(s), ${reconstruction.fetchedObjects} fetched",
                )
            }
        }
    }
}

private class ProjectPortableMarketplaceCommand(
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("project", "marketplace.project") {
    private val marketplaceRaw by argument("MARKETPLACE_ID").optional()
    private val providerRaw by option("--provider", help = "Provider: codex or github-copilot.")
    private val outputRaw by option("--out", help = "Explicit absent-or-identical output directory.")

    override fun help(context: Context): String =
        "Project selected packages to one explicit provider output root."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val marketplace = parseMarketplaceId(marketplaceRaw)
        val marketplaceId = marketplace.valueOr { failure -> return failure }
        val provider = PortableProvider.parse(providerRaw.orEmpty())
            ?: return PortableCommandOutcome.Failure(
                PortableCommandError(PortableErrorCode.USAGE_ERROR, "--provider must be codex or github-copilot"),
            )
        val output = parseOutputPath(outputRaw)
        val outputPath = output.valueOr { failure -> return failure }
        val opened = environment.cache()
        val cache = opened.valueOr { failure -> return failure }
        val reconstructed =
            LocalConsumerOperations.reconstruct(
                invocation.repository,
                marketplaceId,
                cache,
                invocation.offline,
                invocation.cachePolicy(),
            )
        val resolved =
            when (reconstructed) {
                is ConsumerReconstruction.Reconstructed -> reconstructed.marketplaces.single()
                is ConsumerReconstruction.Rejected -> return rejectionFailure(reconstructed.reason)
            }
        val preparation =
            ProviderProjectionDirectory.prepare(
                outputPath,
                resolved.index.snapshotId,
                resolved.packages,
                provider,
            )
        val evidence =
            when (preparation) {
                is ProviderProjectionDirectoryPreparation.Ready -> preparation.projectionEvidence()
                is ProviderProjectionDirectoryPreparation.Unchanged -> preparation.projectionEvidence()
                is ProviderProjectionDirectoryPreparation.Rejected -> return projectionFailure(preparation.reason)
            }
        if (!invocation.dryRun) {
            when (
                val materialized =
                    ProviderProjectionDirectory.materialize(
                        outputPath,
                        resolved.index.snapshotId,
                        resolved.packages,
                        provider,
                    )
            ) {
                is ProviderProjectionDirectoryMaterialization.Rejected -> return projectionFailure(materialized.reason)
                is ProviderProjectionDirectoryMaterialization.Unchanged -> Unit
                is ProviderProjectionDirectoryMaterialization.Written -> Unit
            }
        }
        return PortableCommandOutcome.Success(
            buildJsonObject {
                put("marketplaceId", marketplaceId.render())
                put("snapshotId", resolved.index.snapshotId.render())
                put("provider", provider.render())
                put("output", evidence.output.toString())
                putJsonArray("packages") { evidence.packages.forEach { name -> add(name.render()) } }
                put("treeSha256", evidence.treeDigest.render())
                put("dryRun", invocation.dryRun)
            },
            "marketplace.project: ${evidence.packages.size} package(s) for ${provider.render()}",
        )
    }
}

private data class ProjectionEvidence(
    val output: Path,
    val packages: List<PackageName>,
    val treeDigest: Sha256Digest,
)

private fun ProviderProjectionDirectoryPreparation.Ready.projectionEvidence(): ProjectionEvidence =
    ProjectionEvidence(output, packages, treeDigest)

private fun ProviderProjectionDirectoryPreparation.Unchanged.projectionEvidence(): ProjectionEvidence =
    ProjectionEvidence(output, packages, treeDigest)

private fun PortableInvocation.cachePolicy(): DigestCacheWritePolicy =
    if (dryRun) DigestCacheWritePolicy.VERIFY_ONLY else DigestCacheWritePolicy.STORE

private fun completeMutation(
    invocation: PortableInvocation,
    planning: ConsumerOperationPlanning,
): PortableCommandOutcome =
    when (planning) {
        is ConsumerOperationPlanning.Rejected -> rejectionFailure(planning.reason)
        is ConsumerOperationPlanning.Planned -> {
            if (invocation.dryRun) {
                PortableCommandOutcome.Success(
                    mutationResult(invocation, planning),
                    "marketplace.${planning.operation.name.lowercase()}: dry-run verified",
                )
            } else {
                when (val execution = LocalConsumerOperations.execute(planning.mutation)) {
                    is ConsumerOperationExecution.Rejected -> rejectionFailure(execution.reason)
                    is ConsumerOperationExecution.Applied,
                    is ConsumerOperationExecution.Unchanged,
                    -> PortableCommandOutcome.Success(
                        mutationResult(invocation, planning),
                        "marketplace.${planning.operation.name.lowercase()}: applied",
                    )
                }
            }
        }
    }

private fun mutationResult(
    invocation: PortableInvocation,
    planning: ConsumerOperationPlanning.Planned,
): JsonObject =
    buildJsonObject {
        put("operation", planning.operation.name)
        put("repository", invocation.repository.toString())
        put("stateBefore", planning.mutation.before.toJson())
        put("stateAfter", planning.mutation.after.toJson())
        put("dryRun", invocation.dryRun)
        put("changes", changes(planning.mutation.before, planning.mutation.after))
        put("selections", selectionJson(planning.mutation.after))
        val lock = planning.mutation.after.lockOrNull()
        put("lockSha256", lock?.sha256()?.render()?.let(::JsonPrimitive) ?: JsonNull)
    }

private fun recoveryResult(
    invocation: PortableInvocation,
    before: ConsumerState,
    after: ConsumerState,
    outcome: String,
): JsonObject =
    buildJsonObject {
        put("operation", "RECOVER")
        put("repository", invocation.repository.toString())
        put("stateBefore", before.toJson())
        put("stateAfter", after.toJson())
        put("dryRun", invocation.dryRun)
        put("changes", changes(before, after))
        put("selections", selectionJson(after))
        put("lockSha256", after.lockOrNull()?.sha256()?.render()?.let(::JsonPrimitive) ?: JsonNull)
        put("recoveryOutcome", outcome)
    }

private fun ConsumerState.toJson(): JsonObject =
    buildJsonObject {
        put(
            "kind",
            when (this@toJson) {
                ConsumerState.Uninitialized -> "UNINITIALIZED"
                is ConsumerState.Resolved -> "RESOLVED"
                is ConsumerState.Unresolved -> "UNRESOLVED"
                is ConsumerState.Stale -> "STALE"
                is ConsumerState.Orphaned -> "ORPHANED"
                is ConsumerState.Recovering -> "RECOVERING"
                is ConsumerState.Invalid -> "INVALID"
            },
        )
        put("selections", selectionJson(this@toJson))
    }

private fun selectionJson(state: ConsumerState): JsonArray =
    buildJsonArray {
        state.selections().forEach { selection ->
            add(
                buildJsonObject {
                    put("marketplaceId", selection.marketplaceId.render())
                    putJsonArray("packages") { selection.packages.forEach { packageName -> add(packageName.render()) } }
                },
            )
        }
    }

private fun ConsumerState.selections(): List<MarketplaceIntentSelection> =
    when (this) {
        is ConsumerState.Resolved -> intent.selections
        is ConsumerState.Unresolved -> intent.selections
        is ConsumerState.Stale -> intent.selections
        ConsumerState.Uninitialized,
        is ConsumerState.Orphaned,
        is ConsumerState.Recovering,
        is ConsumerState.Invalid,
        -> emptyList()
    }

private fun ConsumerState.lockOrNull() =
    when (this) {
        is ConsumerState.Resolved -> lock
        is ConsumerState.Stale -> lock
        is ConsumerState.Orphaned -> lock
        ConsumerState.Uninitialized,
        is ConsumerState.Unresolved,
        is ConsumerState.Recovering,
        is ConsumerState.Invalid,
        -> null
    }

private fun ConsumerState.documentDigests(): Map<String, Sha256Digest> =
    buildMap {
        when (this@documentDigests) {
            is ConsumerState.Resolved -> {
                put(ConsumerPersistedFile.INTENT.targetPath, intent.sha256())
                put(ConsumerPersistedFile.LOCK.targetPath, lock.sha256())
            }
            is ConsumerState.Unresolved -> put(ConsumerPersistedFile.INTENT.targetPath, intent.sha256())
            is ConsumerState.Stale -> {
                put(ConsumerPersistedFile.INTENT.targetPath, intent.sha256())
                put(ConsumerPersistedFile.LOCK.targetPath, lock.sha256())
            }
            is ConsumerState.Orphaned -> put(ConsumerPersistedFile.LOCK.targetPath, lock.sha256())
            ConsumerState.Uninitialized,
            is ConsumerState.Recovering,
            is ConsumerState.Invalid,
            -> Unit
        }
    }

private fun changes(
    before: ConsumerState,
    after: ConsumerState,
): JsonArray {
    val old = before.documentDigests()
    val new = after.documentDigests()
    return buildJsonArray {
        (old.keys + new.keys).toSortedSet().forEach { path ->
            val oldDigest = old[path]
            val newDigest = new[path]
            if (oldDigest != newDigest) {
                add(
                    buildJsonObject {
                        put(
                            "type",
                            when {
                                oldDigest == null -> "CREATE"
                                newDigest == null -> "REMOVE"
                                else -> "REPLACE"
                            },
                        )
                        put("path", path)
                        put("oldSha256", oldDigest?.render()?.let(::JsonPrimitive) ?: JsonNull)
                        put("newSha256", newDigest?.render()?.let(::JsonPrimitive) ?: JsonNull)
                    },
                )
            }
        }
    }
}

private sealed interface BoundaryParsing<out T> {
    data class Parsed<T>(val value: T) : BoundaryParsing<T>

    data class Failed(val error: PortableCommandError) : BoundaryParsing<Nothing>
}

private inline fun <T> BoundaryParsing<T>.valueOr(
    onFailure: (PortableCommandOutcome.Failure) -> Nothing,
): T =
    when (this) {
        is BoundaryParsing.Parsed -> value
        is BoundaryParsing.Failed -> onFailure(PortableCommandOutcome.Failure(error))
    }

private inline fun PortableCacheOpening.valueOr(
    onFailure: (PortableCommandOutcome.Failure) -> Nothing,
): DigestAddressedCache =
    when (this) {
        is PortableCacheOpening.Opened -> cache
        is PortableCacheOpening.Rejected ->
            onFailure(
                PortableCommandOutcome.Failure(
                    PortableCommandError(
                        PortableErrorCode.EXTERNAL_UNAVAILABLE,
                        "Digest cache root is unavailable",
                        reasonDetails(reason),
                    ),
                ),
            )
    }

private fun parseMarketplaceId(raw: String?): BoundaryParsing<MarketplaceId> {
    if (raw == null) return BoundaryParsing.Failed(usageError("missing MARKETPLACE_ID"))
    return when (val parsed = MarketplaceId.parse(raw)) {
        is IdentifierParse.Accepted -> BoundaryParsing.Parsed(parsed.value)
        is IdentifierParse.Rejected -> BoundaryParsing.Failed(contractError("invalid marketplace ID", parsed.reason))
    }
}

private fun parseOptionalMarketplaceId(raw: String?): BoundaryParsing<MarketplaceId?> =
    if (raw == null) BoundaryParsing.Parsed(null) else parseMarketplaceId(raw)

private fun parsePackageNames(
    raw: List<String>,
    requireNonEmpty: Boolean,
): BoundaryParsing<List<PackageName>> {
    if (requireNonEmpty && raw.isEmpty()) {
        return BoundaryParsing.Failed(usageError("at least one --package is required"))
    }
    val parsed = mutableListOf<PackageName>()
    raw.forEach { candidate ->
        when (val name = PackageName.parse(candidate)) {
            is IdentifierParse.Accepted -> parsed += name.value
            is IdentifierParse.Rejected -> {
                return BoundaryParsing.Failed(contractError("invalid package name", name.reason))
            }
        }
    }
    val duplicate = parsed.sortedBy(PackageName::render).zipWithNext().firstOrNull { (left, right) -> left == right }
    if (duplicate != null) {
        return BoundaryParsing.Failed(contractError("duplicate --package is not allowed", duplicate.first.render()))
    }
    return BoundaryParsing.Parsed(parsed.sortedBy(PackageName::render))
}

private fun selectionRequest(
    raw: List<String>,
    all: Boolean,
): BoundaryParsing<PackageSelectionRequest> {
    if (all && raw.isNotEmpty()) {
        return BoundaryParsing.Failed(usageError("--all and --package are mutually exclusive"))
    }
    if (all) return BoundaryParsing.Parsed(PackageSelectionRequest.All)
    val packages = parsePackageNames(raw, requireNonEmpty = true)
    val names =
        when (packages) {
            is BoundaryParsing.Parsed -> packages.value
            is BoundaryParsing.Failed -> return packages
        }
    return when (val request = PackageSelectionRequest.explicit(names)) {
        is PackageSelectionRequestCreation.Created -> BoundaryParsing.Parsed(request.request)
        is PackageSelectionRequestCreation.Rejected ->
            BoundaryParsing.Failed(contractError("invalid package selection", request.reason))
    }
}

private fun parseOutputPath(raw: String?): BoundaryParsing<Path> {
    if (raw == null) return BoundaryParsing.Failed(usageError("missing --out"))
    return try {
        BoundaryParsing.Parsed(Path.of(raw).toAbsolutePath().normalize())
    } catch (_: InvalidPathException) {
        BoundaryParsing.Failed(usageError("--out is not a valid filesystem path"))
    }
}

private fun readState(repository: Path): BoundaryParsing<ConsumerState> =
    when (val read = ConsumerStateRepository.read(repository)) {
        is ConsumerStateReading.Read -> BoundaryParsing.Parsed(read.state)
        is ConsumerStateReading.Rejected ->
            BoundaryParsing.Failed(
                PortableCommandError(
                    PortableErrorCode.CONTRACT_VIOLATION,
                    "Consumer state could not be read",
                    reasonDetails(read.reason),
                ),
            )
    }

private fun rejectionFailure(reason: Any): PortableCommandOutcome.Failure {
    val code =
        when (reason) {
            is ConsumerOperationRejection.ResolutionRejected -> resolutionErrorCode(reason.reason)
            is ConsumerOperationRejection.RecoveryRequired,
            is ConsumerOperationRejection.SourceChangeRequiresUpdate,
            is ConsumerOperationRejection.MarketplaceNotSelected,
            is ConsumerOperationRejection.PackageNotSelected,
            is ConsumerOperationRejection.CommitRejected,
            is ConsumerOperationRejection.DeletionRejected,
            -> PortableErrorCode.STATE_CONFLICT
            else -> PortableErrorCode.CONTRACT_VIOLATION
        }
    return PortableCommandOutcome.Failure(
        PortableCommandError(code, "Operation rejected by the typed consumer contract", reasonDetails(reason)),
    )
}

private fun resolutionErrorCode(reason: MarketplaceResolutionRejection): PortableErrorCode =
    when (reason) {
        is MarketplaceResolutionRejection.OfflineCacheMiss -> PortableErrorCode.OFFLINE_CACHE_MISS
        is MarketplaceResolutionRejection.LocalDirectoryRejected,
        is MarketplaceResolutionRejection.SourceAssetRejected,
        -> PortableErrorCode.EXTERNAL_UNAVAILABLE
        else -> PortableErrorCode.CONTRACT_VIOLATION
    }

private fun projectionFailure(reason: ProviderProjectionDirectoryRejection): PortableCommandOutcome.Failure =
    PortableCommandOutcome.Failure(
        PortableCommandError(
            when (reason) {
                is ProviderProjectionDirectoryRejection.OutputExists -> PortableErrorCode.STATE_CONFLICT
                is ProviderProjectionDirectoryRejection.NoPackages,
                is ProviderProjectionDirectoryRejection.DuplicatePackage,
                -> PortableErrorCode.CONTRACT_VIOLATION
                else -> PortableErrorCode.EXTERNAL_UNAVAILABLE
            },
            "Provider projection was rejected",
            reasonDetails(reason),
        ),
    )

private fun sourceShapeError(): PortableCommandError =
    usageError("provide exactly one complete source: --local-snapshot with --index-sha256, or --github with --snapshot")

private fun usageError(message: String): PortableCommandError =
    PortableCommandError(PortableErrorCode.USAGE_ERROR, message)

private fun contractError(
    message: String,
    reason: Any,
): PortableCommandError =
    PortableCommandError(PortableErrorCode.CONTRACT_VIOLATION, message, reasonDetails(reason))

private fun reasonDetails(reason: Any): JsonObject =
    buildJsonObject {
        put("reason", reason::class.simpleName ?: "Rejected")
    }
