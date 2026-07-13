package intelligence.cli.portable

import java.nio.file.Path

internal sealed interface PackageSelectionRequest {
    class Explicit internal constructor(packages: List<PackageName>) : PackageSelectionRequest {
        val packages: List<PackageName> = packages.toList()
    }

    data object All : PackageSelectionRequest

    companion object {
        fun explicit(packages: List<PackageName>): PackageSelectionRequestCreation {
            if (packages.isEmpty()) {
                return PackageSelectionRequestCreation.Rejected(PackageSelectionRequestRejection.NoPackages)
            }
            val ordered = packages.sortedBy(PackageName::render)
            val duplicate = ordered.zipWithNext().firstOrNull { (left, right) -> left == right }?.first
            if (duplicate != null) {
                return PackageSelectionRequestCreation.Rejected(
                    PackageSelectionRequestRejection.DuplicatePackage(duplicate),
                )
            }
            return PackageSelectionRequestCreation.Created(Explicit(ordered))
        }
    }
}

internal sealed interface PackageSelectionRequestCreation {
    data class Created(val request: PackageSelectionRequest.Explicit) : PackageSelectionRequestCreation

    data class Rejected(val reason: PackageSelectionRequestRejection) : PackageSelectionRequestCreation
}

internal sealed interface PackageSelectionRequestRejection {
    data object NoPackages : PackageSelectionRequestRejection

    data class DuplicatePackage(val packageName: PackageName) : PackageSelectionRequestRejection
}

internal object LocalConsumerOperations {
    fun planSelect(
        repository: Path,
        marketplaceId: MarketplaceId,
        source: MarketplaceIntentSource.LocalSnapshot,
        request: PackageSelectionRequest,
        cache: DigestAddressedCache,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): ConsumerOperationPlanning {
        val context =
            when (val loaded = loadSelectionContext(repository, allowUninitialized = true)) {
                is SelectionContextLoading.Loaded -> loaded.context
                is SelectionContextLoading.Rejected -> return ConsumerOperationPlanning.Rejected(loaded.reason)
            }
        val existing = context.selections.singleOrNull { selection -> selection.marketplaceId == marketplaceId }
        if (existing != null && existing.source != source) {
            return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.SourceChangeRequiresUpdate(marketplaceId),
            )
        }
        val requestedPackages =
            when (request) {
                is PackageSelectionRequest.Explicit -> request.packages
                PackageSelectionRequest.All -> {
                    when (val inspected = LocalSnapshotResolver.inspect(repository, marketplaceId, source)) {
                        is LocalSnapshotInspection.Inspected ->
                            inspected.index.packages.map(SnapshotPackageRecord::name)
                        is LocalSnapshotInspection.Rejected -> {
                            return ConsumerOperationPlanning.Rejected(
                                ConsumerOperationRejection.ResolutionRejected(inspected.reason),
                            )
                        }
                    }
                }
            }
        val packages =
            ((existing?.packages ?: emptyList()) + requestedPackages)
                .distinct()
                .sortedBy(PackageName::render)
        val selection =
            when (val created = MarketplaceIntentSelection.create(marketplaceId, source, packages)) {
                is MarketplaceIntentSelectionCreation.Created -> created.selection
                is MarketplaceIntentSelectionCreation.Rejected -> {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.SelectionRejected(created.reason),
                    )
                }
            }
        val resolved =
            when (val resolution = LocalSnapshotResolver.resolve(repository, selection, cache, cacheWritePolicy)) {
                is MarketplaceResolution.Resolved -> resolution.marketplace
                is MarketplaceResolution.Rejected -> {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.ResolutionRejected(resolution.reason),
                    )
                }
            }
        val selections = replaceSelection(context.selections, selection)
        val entries = replaceEntry(context.entries, resolved.lockEntry)
        return planPairMutation(
            repository,
            ConsumerOperation.SELECT,
            marketplaceId,
            packages,
            selections,
            entries,
        )
    }

    fun planRemove(
        repository: Path,
        marketplaceId: MarketplaceId,
        packages: List<PackageName>,
    ): ConsumerOperationPlanning {
        if (packages.isEmpty()) {
            return ConsumerOperationPlanning.Rejected(ConsumerOperationRejection.NoPackages)
        }
        val duplicate = packages.sortedBy(PackageName::render).zipWithNext()
            .firstOrNull { (left, right) -> left == right }?.first
        if (duplicate != null) {
            return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.DuplicatePackage(duplicate),
            )
        }
        val context =
            when (val loaded = loadSelectionContext(repository, allowUninitialized = false)) {
                is SelectionContextLoading.Loaded -> loaded.context
                is SelectionContextLoading.Rejected -> return ConsumerOperationPlanning.Rejected(loaded.reason)
            }
        val selection = context.selections.singleOrNull { candidate -> candidate.marketplaceId == marketplaceId }
            ?: return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.MarketplaceNotSelected(marketplaceId),
            )
        packages.firstOrNull { packageName -> packageName !in selection.packages }?.let { missing ->
            return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.PackageNotSelected(marketplaceId, missing),
            )
        }
        val remaining = selection.packages.filterNot(packages.toSet()::contains)
        val selections = context.selections.filterNot { candidate -> candidate.marketplaceId == marketplaceId }.toMutableList()
        val entries = context.entries.filterNot { candidate -> candidate.marketplaceId == marketplaceId }.toMutableList()
        if (remaining.isNotEmpty()) {
            val retainedSelection =
                when (val created = MarketplaceIntentSelection.create(marketplaceId, selection.source, remaining)) {
                    is MarketplaceIntentSelectionCreation.Created -> created.selection
                    is MarketplaceIntentSelectionCreation.Rejected -> {
                        return ConsumerOperationPlanning.Rejected(
                            ConsumerOperationRejection.SelectionRejected(created.reason),
                        )
                    }
                }
            val currentEntry = context.entries.single { entry -> entry.marketplaceId == marketplaceId }
            val retainedEntry =
                when (
                    val created =
                        MarketplaceLockEntry.create(
                            marketplaceId,
                            currentEntry.source,
                            currentEntry.index,
                            currentEntry.checksum,
                            currentEntry.packages.filter { locked -> locked.name in remaining },
                        )
                ) {
                    is MarketplaceLockEntryCreation.Created -> created.entry
                    is MarketplaceLockEntryCreation.Rejected -> {
                        return ConsumerOperationPlanning.Rejected(
                            ConsumerOperationRejection.LockEntryRejected(created.reason),
                        )
                    }
                }
            selections += retainedSelection
            entries += retainedEntry
        }
        if (selections.isEmpty()) {
            return when (val planned = ConsumerStateRepository.planDeletion(repository)) {
                is ConsumerDeletionPlanning.Planned ->
                    ConsumerOperationPlanning.Planned(
                        ConsumerMutationPlan.Delete(planned.plan),
                        ConsumerOperation.REMOVE,
                        marketplaceId,
                        emptyList(),
                    )
                is ConsumerDeletionPlanning.Rejected ->
                    ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.DeletionRejected(planned.reason),
                    )
            }
        }
        return planPairMutation(
            repository,
            ConsumerOperation.REMOVE,
            marketplaceId,
            remaining,
            selections,
            entries,
        )
    }

    fun planUpdate(
        repository: Path,
        marketplaceId: MarketplaceId,
        source: MarketplaceIntentSource.LocalSnapshot,
        cache: DigestAddressedCache,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): ConsumerOperationPlanning {
        val context =
            when (val loaded = loadSelectionContext(repository, allowUninitialized = false)) {
                is SelectionContextLoading.Loaded -> loaded.context
                is SelectionContextLoading.Rejected -> return ConsumerOperationPlanning.Rejected(loaded.reason)
            }
        val existing = context.selections.singleOrNull { selection -> selection.marketplaceId == marketplaceId }
            ?: return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.MarketplaceNotSelected(marketplaceId),
            )
        val selection =
            when (val created = MarketplaceIntentSelection.create(marketplaceId, source, existing.packages)) {
                is MarketplaceIntentSelectionCreation.Created -> created.selection
                is MarketplaceIntentSelectionCreation.Rejected -> {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.SelectionRejected(created.reason),
                    )
                }
            }
        val resolved =
            when (val resolution = LocalSnapshotResolver.resolve(repository, selection, cache, cacheWritePolicy)) {
                is MarketplaceResolution.Resolved -> resolution.marketplace
                is MarketplaceResolution.Rejected -> {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.ResolutionRejected(resolution.reason),
                    )
                }
            }
        return planPairMutation(
            repository,
            ConsumerOperation.UPDATE,
            marketplaceId,
            existing.packages,
            replaceSelection(context.selections, selection),
            replaceEntry(context.entries, resolved.lockEntry),
        )
    }

    fun planResolve(
        repository: Path,
        marketplaceId: MarketplaceId?,
        cache: DigestAddressedCache,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): ConsumerOperationPlanning {
        val state =
            when (val read = ConsumerStateRepository.read(repository)) {
                is ConsumerStateReading.Read -> read.state
                is ConsumerStateReading.Rejected -> {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.StateReadRejected(read.reason),
                    )
                }
            }
        val intent: MarketplaceIntent
        val existingEntries: List<MarketplaceLockEntry>
        when (state) {
            is ConsumerState.Resolved -> {
                intent = state.intent
                existingEntries = state.lock.entries
            }
            is ConsumerState.Stale -> {
                intent = state.intent
                existingEntries = state.lock.entries
            }
            is ConsumerState.Unresolved -> {
                intent = state.intent
                existingEntries = emptyList()
            }
            ConsumerState.Uninitialized -> {
                return ConsumerOperationPlanning.Rejected(ConsumerOperationRejection.Uninitialized)
            }
            is ConsumerState.Recovering -> {
                return ConsumerOperationPlanning.Rejected(
                    ConsumerOperationRejection.RecoveryRequired(state.journal.transactionId),
                )
            }
            is ConsumerState.Invalid -> {
                return ConsumerOperationPlanning.Rejected(
                    ConsumerOperationRejection.InvalidState(state.reason),
                )
            }
            is ConsumerState.Orphaned -> {
                return ConsumerOperationPlanning.Rejected(ConsumerOperationRejection.OrphanedState)
            }
        }
        if (marketplaceId != null && intent.selections.none { selection -> selection.marketplaceId == marketplaceId }) {
            return ConsumerOperationPlanning.Rejected(
                ConsumerOperationRejection.MarketplaceNotSelected(marketplaceId),
            )
        }
        val entries = mutableListOf<MarketplaceLockEntry>()
        val affectedPackages = mutableListOf<PackageName>()
        intent.selections.forEach { selection ->
            val targeted = marketplaceId == null || selection.marketplaceId == marketplaceId
            if (targeted) {
                val source =
                    when (val candidate = selection.source) {
                        is MarketplaceIntentSource.LocalSnapshot -> candidate
                        is MarketplaceIntentSource.GitHubRelease -> {
                            return ConsumerOperationPlanning.Rejected(
                                ConsumerOperationRejection.GitHubResolutionRequired(selection.marketplaceId),
                            )
                        }
                    }
                when (val resolution = LocalSnapshotResolver.resolve(repository, selection, cache, cacheWritePolicy)) {
                    is MarketplaceResolution.Resolved -> {
                        entries += resolution.marketplace.lockEntry
                        affectedPackages += selection.packages
                    }
                    is MarketplaceResolution.Rejected -> {
                        return ConsumerOperationPlanning.Rejected(
                            ConsumerOperationRejection.ResolutionRejected(resolution.reason),
                        )
                    }
                }
            } else {
                val existing = existingEntries.singleOrNull { entry -> entry.marketplaceId == selection.marketplaceId }
                if (existing == null || !entryMatches(selection, existing)) {
                    return ConsumerOperationPlanning.Rejected(
                        ConsumerOperationRejection.MarketplaceRequiresResolution(selection.marketplaceId),
                    )
                }
                entries += existing
            }
        }
        return planPairMutation(
            repository,
            ConsumerOperation.RESOLVE,
            marketplaceId,
            affectedPackages.sortedBy(PackageName::render),
            intent.selections,
            entries,
        )
    }

    fun reconstruct(
        repository: Path,
        marketplaceId: MarketplaceId?,
        cache: DigestAddressedCache,
        offline: Boolean,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): ConsumerReconstruction {
        val state =
            when (val read = ConsumerStateRepository.read(repository)) {
                is ConsumerStateReading.Read -> read.state
                is ConsumerStateReading.Rejected -> {
                    return ConsumerReconstruction.Rejected(
                        ConsumerOperationRejection.StateReadRejected(read.reason),
                    )
                }
            }
        val resolved =
            when (state) {
                is ConsumerState.Resolved -> state
                ConsumerState.Uninitialized -> {
                    return ConsumerReconstruction.Rejected(ConsumerOperationRejection.Uninitialized)
                }
                is ConsumerState.Recovering -> {
                    return ConsumerReconstruction.Rejected(
                        ConsumerOperationRejection.RecoveryRequired(state.journal.transactionId),
                    )
                }
                is ConsumerState.Invalid -> {
                    return ConsumerReconstruction.Rejected(
                        ConsumerOperationRejection.InvalidState(state.reason),
                    )
                }
                is ConsumerState.Orphaned -> {
                    return ConsumerReconstruction.Rejected(ConsumerOperationRejection.OrphanedState)
                }
                is ConsumerState.Unresolved,
                is ConsumerState.Stale,
                -> return ConsumerReconstruction.Rejected(ConsumerOperationRejection.StateMustBeResolved)
            }
        val entries =
            if (marketplaceId == null) {
                resolved.lock.entries
            } else {
                listOfNotNull(resolved.lock.entries.singleOrNull { entry -> entry.marketplaceId == marketplaceId })
                    .ifEmpty {
                        return ConsumerReconstruction.Rejected(
                            ConsumerOperationRejection.MarketplaceNotSelected(marketplaceId),
                        )
                    }
            }
        var requiredObjects = 0
        var cacheHits = 0
        val reconstructed = mutableListOf<ResolvedMarketplace>()
        entries.forEach { entry ->
            val assets = listOf(entry.index, entry.checksum) + entry.packages.map(LockedPackage::archive)
            requiredObjects += assets.size
            assets.forEach { asset ->
                when (val read = cache.read(CacheBlobExpectation.from(asset))) {
                    is DigestCacheRead.Hit -> cacheHits += 1
                    DigestCacheRead.Miss -> Unit
                    is DigestCacheRead.Rejected -> {
                        return ConsumerReconstruction.Rejected(
                            ConsumerOperationRejection.ResolutionRejected(
                                MarketplaceResolutionRejection.CacheRejected(asset.name, read.reason),
                            ),
                        )
                    }
                }
            }
            val result =
                if (offline) {
                    OfflineMarketplaceReconstructor.reconstruct(entry, cache)
                } else {
                    when (entry.source) {
                        is MarketplaceLockSource.LocalSnapshot ->
                            LocalSnapshotResolver.reconstruct(repository, entry, cache, cacheWritePolicy)
                        is MarketplaceLockSource.GitHubRelease -> {
                            return ConsumerReconstruction.Rejected(
                                ConsumerOperationRejection.GitHubResolutionRequired(entry.marketplaceId),
                            )
                        }
                    }
                }
            when (result) {
                is MarketplaceResolution.Resolved -> reconstructed += result.marketplace
                is MarketplaceResolution.Rejected -> {
                    return ConsumerReconstruction.Rejected(
                        ConsumerOperationRejection.ResolutionRejected(result.reason),
                    )
                }
            }
        }
        return ConsumerReconstruction.Reconstructed(
            marketplaces = reconstructed,
            requiredObjects = requiredObjects,
            cacheHits = cacheHits,
            fetchedObjects = if (offline) 0 else requiredObjects - cacheHits,
        )
    }

    fun execute(mutation: ConsumerMutationPlan): ConsumerOperationExecution =
        when (mutation) {
            is ConsumerMutationPlan.Commit ->
                when (val executed = ConsumerStateRepository.execute(mutation.plan)) {
                    is ConsumerCommitExecution.Committed -> ConsumerOperationExecution.Applied(executed.state)
                    is ConsumerCommitExecution.Unchanged -> ConsumerOperationExecution.Unchanged(executed.state)
                    is ConsumerCommitExecution.Rejected ->
                        ConsumerOperationExecution.Rejected(
                            ConsumerOperationRejection.CommitRejected(executed.reason),
                        )
                }
            is ConsumerMutationPlan.Delete ->
                when (val executed = ConsumerStateRepository.execute(mutation.plan)) {
                    ConsumerDeletionExecution.Deleted -> ConsumerOperationExecution.Applied(ConsumerState.Uninitialized)
                    ConsumerDeletionExecution.Unchanged ->
                        ConsumerOperationExecution.Unchanged(ConsumerState.Uninitialized)
                    is ConsumerDeletionExecution.Rejected ->
                        ConsumerOperationExecution.Rejected(
                            ConsumerOperationRejection.DeletionRejected(executed.reason),
                        )
                }
        }
}

internal sealed interface ConsumerMutationPlan {
    val before: ConsumerState
    val after: ConsumerState
    val changed: Boolean

    class Commit internal constructor(val plan: ConsumerCommitPlan) : ConsumerMutationPlan {
        override val before: ConsumerState = plan.before
        override val after: ConsumerState = plan.after
        override val changed: Boolean = plan.changed
    }

    class Delete internal constructor(val plan: ConsumerDeletionPlan) : ConsumerMutationPlan {
        override val before: ConsumerState = plan.before
        override val after: ConsumerState = plan.after
        override val changed: Boolean = plan.changed
    }
}

internal sealed interface ConsumerOperationPlanning {
    data class Planned(
        val mutation: ConsumerMutationPlan,
        val operation: ConsumerOperation,
        val marketplaceId: MarketplaceId?,
        val packages: List<PackageName>,
    ) : ConsumerOperationPlanning

    data class Rejected(val reason: ConsumerOperationRejection) : ConsumerOperationPlanning
}

internal sealed interface ConsumerOperationExecution {
    data class Applied(val state: ConsumerState) : ConsumerOperationExecution

    data class Unchanged(val state: ConsumerState) : ConsumerOperationExecution

    data class Rejected(val reason: ConsumerOperationRejection) : ConsumerOperationExecution
}

internal sealed interface ConsumerReconstruction {
    data class Reconstructed(
        val marketplaces: List<ResolvedMarketplace>,
        val requiredObjects: Int,
        val cacheHits: Int,
        val fetchedObjects: Int,
    ) : ConsumerReconstruction

    data class Rejected(val reason: ConsumerOperationRejection) : ConsumerReconstruction
}

internal enum class ConsumerOperation {
    SELECT,
    REMOVE,
    UPDATE,
    RESOLVE,
}

internal sealed interface ConsumerOperationRejection {
    data class StateReadRejected(val reason: ConsumerStateReadRejection) : ConsumerOperationRejection
    data class RecoveryRequired(val transactionId: MarketplaceTransactionId) : ConsumerOperationRejection
    data class InvalidState(val reason: ConsumerStateInvalidity) : ConsumerOperationRejection
    data object OrphanedState : ConsumerOperationRejection
    data object Uninitialized : ConsumerOperationRejection
    data object StateMustBeResolved : ConsumerOperationRejection
    data object NoPackages : ConsumerOperationRejection
    data class DuplicatePackage(val packageName: PackageName) : ConsumerOperationRejection
    data class MarketplaceNotSelected(val marketplaceId: MarketplaceId) : ConsumerOperationRejection
    data class PackageNotSelected(
        val marketplaceId: MarketplaceId,
        val packageName: PackageName,
    ) : ConsumerOperationRejection
    data class SourceChangeRequiresUpdate(val marketplaceId: MarketplaceId) : ConsumerOperationRejection
    data class MarketplaceRequiresResolution(val marketplaceId: MarketplaceId) : ConsumerOperationRejection
    data class GitHubResolutionRequired(val marketplaceId: MarketplaceId) : ConsumerOperationRejection
    data class SelectionRejected(val reason: MarketplaceIntentSelectionRejection) : ConsumerOperationRejection
    data class ResolutionRejected(val reason: MarketplaceResolutionRejection) : ConsumerOperationRejection
    data class LockEntryRejected(val reason: MarketplaceLockEntryRejection) : ConsumerOperationRejection
    data class IntentRejected(val reason: MarketplaceIntentRejection) : ConsumerOperationRejection
    data class LockRejected(val reason: MarketplaceLockRejection) : ConsumerOperationRejection
    data class CommitRejected(val reason: ConsumerCommitRejection) : ConsumerOperationRejection
    data class DeletionRejected(val reason: ConsumerDeletionRejection) : ConsumerOperationRejection
}

private data class SelectionContext(
    val selections: List<MarketplaceIntentSelection>,
    val entries: List<MarketplaceLockEntry>,
)

private sealed interface SelectionContextLoading {
    data class Loaded(val context: SelectionContext) : SelectionContextLoading

    data class Rejected(val reason: ConsumerOperationRejection) : SelectionContextLoading
}

private fun loadSelectionContext(
    repository: Path,
    allowUninitialized: Boolean,
): SelectionContextLoading {
    val state =
        when (val read = ConsumerStateRepository.read(repository)) {
            is ConsumerStateReading.Read -> read.state
            is ConsumerStateReading.Rejected -> {
                return SelectionContextLoading.Rejected(
                    ConsumerOperationRejection.StateReadRejected(read.reason),
                )
            }
        }
    return when (state) {
        is ConsumerState.Resolved -> SelectionContextLoading.Loaded(
            SelectionContext(state.intent.selections, state.lock.entries),
        )
        ConsumerState.Uninitialized ->
            if (allowUninitialized) {
                SelectionContextLoading.Loaded(SelectionContext(emptyList(), emptyList()))
            } else {
                SelectionContextLoading.Rejected(ConsumerOperationRejection.Uninitialized)
            }
        is ConsumerState.Recovering ->
            SelectionContextLoading.Rejected(
                ConsumerOperationRejection.RecoveryRequired(state.journal.transactionId),
            )
        is ConsumerState.Invalid ->
            SelectionContextLoading.Rejected(ConsumerOperationRejection.InvalidState(state.reason))
        is ConsumerState.Orphaned ->
            SelectionContextLoading.Rejected(ConsumerOperationRejection.OrphanedState)
        is ConsumerState.Unresolved,
        is ConsumerState.Stale,
        -> SelectionContextLoading.Rejected(ConsumerOperationRejection.StateMustBeResolved)
    }
}

private fun replaceSelection(
    selections: List<MarketplaceIntentSelection>,
    replacement: MarketplaceIntentSelection,
): List<MarketplaceIntentSelection> =
    (selections.filterNot { selection -> selection.marketplaceId == replacement.marketplaceId } + replacement)
        .sortedBy { selection -> selection.marketplaceId.render() }

private fun replaceEntry(
    entries: List<MarketplaceLockEntry>,
    replacement: MarketplaceLockEntry,
): List<MarketplaceLockEntry> =
    (entries.filterNot { entry -> entry.marketplaceId == replacement.marketplaceId } + replacement)
        .sortedBy { entry -> entry.marketplaceId.render() }

private fun planPairMutation(
    repository: Path,
    operation: ConsumerOperation,
    marketplaceId: MarketplaceId?,
    packages: List<PackageName>,
    selections: List<MarketplaceIntentSelection>,
    entries: List<MarketplaceLockEntry>,
): ConsumerOperationPlanning {
    val intent =
        when (val materialized = MarketplaceIntent.materialize(selections)) {
            is MarketplaceIntentMaterialization.Materialized -> materialized.intent
            is MarketplaceIntentMaterialization.Rejected -> {
                return ConsumerOperationPlanning.Rejected(
                    ConsumerOperationRejection.IntentRejected(materialized.reason),
                )
            }
        }
    val lock =
        when (val materialized = MarketplaceLock.materialize(entries)) {
            is MarketplaceLockMaterialization.Materialized -> materialized.lock
            is MarketplaceLockMaterialization.Rejected -> {
                return ConsumerOperationPlanning.Rejected(
                    ConsumerOperationRejection.LockRejected(materialized.reason),
                )
            }
        }
    return when (val planned = ConsumerStateRepository.planCommit(repository, intent, lock)) {
        is ConsumerCommitPlanning.Planned ->
            ConsumerOperationPlanning.Planned(
                ConsumerMutationPlan.Commit(planned.plan),
                operation,
                marketplaceId,
                packages.toList(),
            )
        is ConsumerCommitPlanning.Rejected ->
            ConsumerOperationPlanning.Rejected(ConsumerOperationRejection.CommitRejected(planned.reason))
    }
}

private fun entryMatches(
    selection: MarketplaceIntentSelection,
    entry: MarketplaceLockEntry,
): Boolean {
    if (selection.marketplaceId != entry.marketplaceId ||
        selection.packages != entry.packages.map(LockedPackage::name)
    ) {
        return false
    }
    return when (val source = selection.source) {
        is MarketplaceIntentSource.LocalSnapshot ->
            entry.source is MarketplaceLockSource.LocalSnapshot &&
                source.directory == entry.source.directory &&
                source.indexSha256 == entry.index.sha256
        is MarketplaceIntentSource.GitHubRelease ->
            entry.source is MarketplaceLockSource.GitHubRelease &&
                source.repository == entry.source.repository &&
                source.tag == entry.source.tag
    }
}
