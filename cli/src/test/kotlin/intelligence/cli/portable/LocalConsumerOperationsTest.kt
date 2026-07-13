package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class LocalConsumerOperationsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `dry-run select validates everything without cache or repository mutation`() {
        val fixture = fixture("consumer-dry-run", "snapshots/one", "snapshot-one")
        val request = explicit("alpha-tools")
        val planned = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planSelect(
                fixture.repository,
                fixture.marketplaceId,
                fixture.source,
                request,
                fixture.cache,
                DigestCacheWritePolicy.VERIFY_ONLY,
            ),
        )

        assertEquals(listOf("alpha-tools"), planned.packages.map(PackageName::render))
        assertTrue(planned.mutation.changed)
        assertFalse(Files.exists(fixture.cache.root))
        assertFalse(Files.exists(fixture.repository.resolve(".intelligence")))

        val writable = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planSelect(
                fixture.repository,
                fixture.marketplaceId,
                fixture.source,
                request,
                fixture.cache,
                DigestCacheWritePolicy.STORE,
            ),
        )
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(writable.mutation))
        assertTrue(Files.exists(fixture.cache.root))
        assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(fixture.repository)).state,
        )
    }

    @Test
    fun `all selects every package while repeated explicit selection merges canonically`() {
        val allFixture = fixture("consumer-all", "snapshots/one", "snapshot-one")
        val allPlan = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planSelect(
                allFixture.repository,
                allFixture.marketplaceId,
                allFixture.source,
                PackageSelectionRequest.All,
                allFixture.cache,
                DigestCacheWritePolicy.STORE,
            ),
        )
        assertEquals(listOf("alpha-tools", "beta-tools"), allPlan.packages.map(PackageName::render))
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(allPlan.mutation))

        val mergeFixture = fixture("consumer-merge", "snapshots/one", "snapshot-one")
        executeSelect(mergeFixture, explicit("beta-tools"))
        val merged = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planSelect(
                mergeFixture.repository,
                mergeFixture.marketplaceId,
                mergeFixture.source,
                explicit("alpha-tools"),
                mergeFixture.cache,
                DigestCacheWritePolicy.STORE,
            ),
        )
        assertEquals(listOf("alpha-tools", "beta-tools"), merged.packages.map(PackageName::render))
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(merged.mutation))
    }

    @Test
    fun `select refuses source changes and update preserves selected package names`() {
        val first = fixture("consumer-update", "snapshots/one", "snapshot-one")
        executeSelect(first, explicit("alpha-tools"))
        val replacement = addSnapshot(first, "snapshots/two", "snapshot-two")

        assertEquals(
            ConsumerOperationRejection.SourceChangeRequiresUpdate(first.marketplaceId),
            assertIs<ConsumerOperationPlanning.Rejected>(
                LocalConsumerOperations.planSelect(
                    first.repository,
                    first.marketplaceId,
                    replacement,
                    explicit("alpha-tools"),
                    first.cache,
                    DigestCacheWritePolicy.STORE,
                ),
            ).reason,
        )

        val update = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planUpdate(
                first.repository,
                first.marketplaceId,
                replacement,
                first.cache,
                DigestCacheWritePolicy.STORE,
            ),
        )
        assertEquals(listOf("alpha-tools"), update.packages.map(PackageName::render))
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(update.mutation))
        val state = assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(first.repository)).state,
        )
        assertEquals(replacement, state.intent.selections.single().source)
    }

    @Test
    fun `removing the final selected package deletes the consumer pair`() {
        val fixture = fixture("consumer-remove", "snapshots/one", "snapshot-one")
        executeSelect(fixture, explicit("alpha-tools"))

        val removal = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planRemove(
                fixture.repository,
                fixture.marketplaceId,
                listOf(packageName("alpha-tools")),
            ),
        )
        assertEquals(emptyList(), removal.packages)
        assertIs<ConsumerMutationPlan.Delete>(removal.mutation)
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(removal.mutation))
        assertEquals(
            ConsumerState.Uninitialized,
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(fixture.repository)).state,
        )
    }

    @Test
    fun `resolve builds a complete lock without changing unresolved intent`() {
        val fixture = fixture("consumer-resolve", "snapshots/one", "snapshot-one")
        val selection = assertIs<MarketplaceIntentSelectionCreation.Created>(
            MarketplaceIntentSelection.create(
                fixture.marketplaceId,
                fixture.source,
                listOf(packageName("alpha-tools")),
            ),
        ).selection
        val intent = assertIs<MarketplaceIntentMaterialization.Materialized>(
            MarketplaceIntent.materialize(listOf(selection)),
        ).intent
        val intentPath = fixture.repository.resolve(ConsumerPersistedFile.INTENT.targetPath)
        Files.createDirectories(intentPath.parent)
        Files.write(intentPath, intent.canonicalBytes())

        val resolution = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planResolve(
                fixture.repository,
                marketplaceId = null,
                cache = fixture.cache,
                cacheWritePolicy = DigestCacheWritePolicy.STORE,
            ),
        )
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(resolution.mutation))
        val resolved = assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(fixture.repository)).state,
        )
        assertEquals(intent.sha256(), resolved.intent.sha256())
        assertEquals(MarketplaceLockAgreement.Matched, resolved.lock.agreement(resolved.intent))
    }

    @Test
    fun `reconstruction reports exact cache work and verify-only leaves misses absent`() {
        val fixture = fixture("consumer-reconstruct", "snapshots/one", "snapshot-one")
        executeSelect(fixture, explicit("alpha-tools"))
        val state = assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(fixture.repository)).state,
        )
        val packageAsset = state.lock.entries.single().packages.single().archive
        val missing = fixture.cache.pathFor(packageAsset.sha256)
        Files.delete(missing)

        val dryRun = assertIs<ConsumerReconstruction.Reconstructed>(
            LocalConsumerOperations.reconstruct(
                fixture.repository,
                fixture.marketplaceId,
                fixture.cache,
                offline = false,
                cacheWritePolicy = DigestCacheWritePolicy.VERIFY_ONLY,
            ),
        )
        assertEquals(3, dryRun.requiredObjects)
        assertEquals(2, dryRun.cacheHits)
        assertEquals(1, dryRun.fetchedObjects)
        assertFalse(Files.exists(missing))

        assertIs<ConsumerReconstruction.Reconstructed>(
            LocalConsumerOperations.reconstruct(
                fixture.repository,
                fixture.marketplaceId,
                fixture.cache,
                offline = false,
                cacheWritePolicy = DigestCacheWritePolicy.STORE,
            ),
        )
        assertTrue(Files.exists(missing))
        assertEquals(
            0,
            assertIs<ConsumerReconstruction.Reconstructed>(
                LocalConsumerOperations.reconstruct(
                    fixture.repository,
                    fixture.marketplaceId,
                    fixture.cache,
                    offline = true,
                    cacheWritePolicy = DigestCacheWritePolicy.VERIFY_ONLY,
                ),
            ).fetchedObjects,
        )
    }

    private fun executeSelect(
        fixture: Fixture,
        request: PackageSelectionRequest,
    ) {
        val planned = assertIs<ConsumerOperationPlanning.Planned>(
            LocalConsumerOperations.planSelect(
                fixture.repository,
                fixture.marketplaceId,
                fixture.source,
                request,
                fixture.cache,
                DigestCacheWritePolicy.STORE,
            ),
        )
        assertIs<ConsumerOperationExecution.Applied>(LocalConsumerOperations.execute(planned.mutation))
    }

    private fun fixture(
        repositoryName: String,
        relativeSnapshot: String,
        snapshotId: String,
    ): Fixture {
        val repository = temporaryDirectory.resolve(repositoryName)
        Files.createDirectories(repository)
        val marketplaceId = marketplaceId("example-marketplace")
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache-$repositoryName"))
        val fixture = Fixture(
            repository,
            marketplaceId,
            source = localSource(repository, relativeSnapshot, snapshotId, marketplaceId),
            cache,
        )
        return fixture
    }

    private fun addSnapshot(
        fixture: Fixture,
        relativeSnapshot: String,
        snapshotId: String,
    ): MarketplaceIntentSource.LocalSnapshot =
        localSource(fixture.repository, relativeSnapshot, snapshotId, fixture.marketplaceId)

    private fun localSource(
        repository: Path,
        relativeSnapshot: String,
        snapshotId: String,
        marketplaceId: MarketplaceId,
    ): MarketplaceIntentSource.LocalSnapshot {
        val alpha = packageArchive("alpha-tools", "alpha")
        val beta = packageArchive("beta-tools", "beta")
        val relative = relativeDirectory(relativeSnapshot)
        val output = repository.resolve(relative.render())
        Files.createDirectories(output.parent)
        val release = assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(
            MarketplaceReleaseDirectory.materialize(
                output,
                marketplaceId,
                snapshotId(snapshotId),
                packageName("alpha-tools"),
                listOf(alpha, beta),
            ),
        ).release
        return MarketplaceIntentSource.LocalSnapshot(relative, release.index.sha256())
    }

    private fun explicit(vararg names: String): PackageSelectionRequest =
        assertIs<PackageSelectionRequestCreation.Created>(
            PackageSelectionRequest.explicit(names.map(::packageName)),
        ).request

    private fun packageArchive(
        packageName: String,
        skillName: String,
    ): PackageArchive {
        val skillBytes =
            "---\nname: $skillName\ndescription: \"$skillName skill\"\n---\n\nUse $skillName.\n".encodeToByteArray()
        val skillPath = "skills/$skillName/SKILL.md"
        val manifestText =
            "{\"description\":\"$packageName package\",\"marketplaceId\":\"example-marketplace\"," +
                "\"name\":\"$packageName\",\"schemaVersion\":1,\"skills\":[{\"assets\":[]," +
                "\"description\":\"$skillName skill\",\"name\":\"$skillName\"," +
                "\"primary\":{\"executable\":false,\"path\":\"$skillPath\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"
        val manifest = assertIs<PackageManifestParsing.Parsed>(
            PackageManifest.parse(manifestText.encodeToByteArray()),
        ).manifest
        val source = assertIs<PackageSourceFileCreation.Created>(
            PackageSourceFile.create(packagePath(skillPath), skillBytes, executable = false),
        ).file
        return assertIs<PackageArchiveMaterialization.Materialized>(
            PackageArchive.materialize(manifest, listOf(source)),
        ).archive
    }

    private fun relativeDirectory(raw: String): ConsumerRelativeDirectory =
        assertIs<ConsumerRelativeDirectoryParsing.Parsed>(ConsumerRelativeDirectory.parse(raw)).directory

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value

    private fun packagePath(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private data class Fixture(
        val repository: Path,
        val marketplaceId: MarketplaceId,
        val source: MarketplaceIntentSource.LocalSnapshot,
        val cache: DigestAddressedCache,
    )
}
