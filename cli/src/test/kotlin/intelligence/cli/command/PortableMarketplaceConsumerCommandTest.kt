package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import intelligence.cli.portable.ConsumerPersistedFile
import intelligence.cli.portable.ConsumerRelativeDirectory
import intelligence.cli.portable.ConsumerRelativeDirectoryParsing
import intelligence.cli.portable.ConsumerState
import intelligence.cli.portable.ConsumerStateReading
import intelligence.cli.portable.ConsumerStateRepository
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.MarketplaceId
import intelligence.cli.portable.MarketplaceIntentSource
import intelligence.cli.portable.MarketplaceReleaseDirectory
import intelligence.cli.portable.MarketplaceReleaseDirectoryMaterialization
import intelligence.cli.portable.PackageArchive
import intelligence.cli.portable.PackageArchiveMaterialization
import intelligence.cli.portable.PackageEntryPath
import intelligence.cli.portable.PackageEntryPathParse
import intelligence.cli.portable.PackageManifest
import intelligence.cli.portable.PackageManifestParsing
import intelligence.cli.portable.PackageName
import intelligence.cli.portable.PackageSourceFile
import intelligence.cli.portable.PackageSourceFileCreation
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.SnapshotId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class PortableMarketplaceConsumerCommandTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `local commands preserve dry run parity and package only state`() {
        val fixture = fixture()
        val dryRun = command(fixture).test(
            selectArguments(fixture, fixture.firstSource) + " --dry-run --format json",
        )

        assertEquals(0, dryRun.statusCode)
        assertEquals(1, dryRun.stdout.lineSequence().count { line -> line.isNotBlank() })
        assertEnvelope(dryRun.stdout, "marketplace.select", expectedOk = true)
        assertFalse(Files.exists(fixture.repository.resolve(".intelligence")))
        assertFalse(Files.exists(fixture.cache))

        val selected = command(fixture).test(selectArguments(fixture, fixture.firstSource) + " --format json")
        assertEquals(0, selected.statusCode)
        assertEnvelope(selected.stdout, "marketplace.select", expectedOk = true)
        assertTrue(Files.exists(fixture.repository.resolve(ConsumerPersistedFile.INTENT.targetPath)))
        assertTrue(Files.exists(fixture.repository.resolve(ConsumerPersistedFile.LOCK.targetPath)))
        assertTrue(Files.exists(fixture.cache))

        val update = command(fixture).test(
            "marketplace update example-marketplace --repository ${fixture.repository} " +
                sourceArguments(fixture.secondSource) + " --format json",
        )
        assertEquals(0, update.statusCode)
        assertEnvelope(update.stdout, "marketplace.update", expectedOk = true)
        val updatedState = resolvedState(fixture.repository)
        assertEquals(fixture.secondSource, updatedState.intent.selections.single().source)
        assertEquals(listOf("alpha-tools"), updatedState.intent.selections.single().packages.map(PackageName::render))

        Files.delete(fixture.repository.resolve(ConsumerPersistedFile.LOCK.targetPath))
        val resolve = command(fixture).test(
            "marketplace resolve --repository ${fixture.repository} --format json",
        )
        assertEquals(0, resolve.statusCode)
        assertEnvelope(resolve.stdout, "marketplace.resolve", expectedOk = true)
        resolvedState(fixture.repository)

        val recover = command(fixture).test(
            "marketplace recover --repository ${fixture.repository} --dry-run --format json",
        )
        assertEquals(0, recover.statusCode)
        assertEnvelope(recover.stdout, "marketplace.recover", expectedOk = true)

        val remove = command(fixture).test(
            "marketplace remove example-marketplace --repository ${fixture.repository} " +
                "--package alpha-tools --format json",
        )
        assertEquals(0, remove.statusCode)
        assertEnvelope(remove.stdout, "marketplace.remove", expectedOk = true)
        assertEquals(
            ConsumerState.Uninitialized,
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(fixture.repository)).state,
        )
    }

    @Test
    fun `reconstruct and project expose exact cache and output behavior`() {
        val fixture = fixture()
        assertEquals(0, command(fixture).test(selectArguments(fixture, fixture.firstSource)).statusCode)
        val state = resolvedState(fixture.repository)
        val packageAsset = state.lock.entries.single().packages.single().archive
        val cacheObject =
            fixture.cache.resolve(packageAsset.sha256.render().take(2))
                .resolve(packageAsset.sha256.render().drop(2))
        Files.delete(cacheObject)

        val output = temporaryDirectory.resolve("codex-dry-run")
        val dryProject = command(fixture).test(
            "marketplace project example-marketplace --repository ${fixture.repository} " +
                "--provider codex --out $output --dry-run --format json",
        )
        assertEquals(0, dryProject.statusCode)
        assertEnvelope(dryProject.stdout, "marketplace.project", expectedOk = true)
        assertFalse(Files.exists(cacheObject))
        assertFalse(Files.exists(output))

        val projected = command(fixture).test(
            "marketplace project example-marketplace --repository ${fixture.repository} " +
                "--provider codex --out $output --format json",
        )
        assertEquals(0, projected.statusCode)
        assertEnvelope(projected.stdout, "marketplace.project", expectedOk = true)
        assertTrue(Files.exists(cacheObject))
        assertTrue(Files.exists(output.resolve("alpha-tools/.codex-plugin/plugin.json")))
        assertFalse(Files.exists(output.resolve("marketplace.json")))

        val reconstructed = command(fixture).test(
            "marketplace reconstruct --repository ${fixture.repository} " +
                "--offline --dry-run --format json",
        )
        assertEquals(0, reconstructed.statusCode)
        assertEnvelope(reconstructed.stdout, "marketplace.reconstruct", expectedOk = true)

        Files.delete(cacheObject)
        val rejectedOutput = temporaryDirectory.resolve("offline-miss")
        val offlineMiss = command(fixture).test(
            "marketplace project example-marketplace --repository ${fixture.repository} " +
                "--provider github-copilot --out $rejectedOutput --offline --format json",
        )
        assertEquals(4, offlineMiss.statusCode)
        val failure = assertEnvelope(offlineMiss.stdout, "marketplace.project", expectedOk = false)
        assertEquals("OFFLINE_CACHE_MISS", failure["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertFalse(Files.exists(rejectedOutput))
    }

    private fun command(fixture: Fixture): IntelligenceCommand =
        IntelligenceCommand(
            processRunner = ProcessRunner { _, _ -> 127 },
            portableCacheRoot = fixture.cache,
        )

    private fun selectArguments(
        fixture: Fixture,
        source: MarketplaceIntentSource.LocalSnapshot,
    ): String =
        "marketplace select example-marketplace --repository ${fixture.repository} " +
            sourceArguments(source) + " --package alpha-tools"

    private fun sourceArguments(source: MarketplaceIntentSource.LocalSnapshot): String =
        "--local-snapshot ${source.directory.render()} --index-sha256 ${source.indexSha256.render()}"

    private fun assertEnvelope(
        stdout: String,
        command: String,
        expectedOk: Boolean,
    ): JsonObject {
        val envelope = Json.parseToJsonElement(stdout.trim()).jsonObject
        assertEquals(1, envelope["schemaVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals(command, envelope["command"]!!.jsonPrimitive.content)
        assertEquals(expectedOk, envelope["ok"]!!.jsonPrimitive.boolean)
        return envelope
    }

    private fun fixture(): Fixture {
        val repository = temporaryDirectory.resolve("consumer")
        Files.createDirectories(repository)
        val marketplaceId = marketplaceId("example-marketplace")
        val packages = listOf(packageArchive("alpha-tools", "alpha"), packageArchive("beta-tools", "beta"))
        val first = localSource(repository, "snapshots/one", "snapshot-one", marketplaceId, packages)
        val second = localSource(repository, "snapshots/two", "snapshot-two", marketplaceId, packages)
        return Fixture(repository, temporaryDirectory.resolve("cache"), first, second)
    }

    private fun localSource(
        repository: Path,
        relativeSnapshot: String,
        snapshotId: String,
        marketplaceId: MarketplaceId,
        packages: List<PackageArchive>,
    ): MarketplaceIntentSource.LocalSnapshot {
        val relative = relativeDirectory(relativeSnapshot)
        val output = repository.resolve(relative.render())
        Files.createDirectories(output.parent)
        val release = assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(
            MarketplaceReleaseDirectory.materialize(
                output,
                marketplaceId,
                snapshotId(snapshotId),
                packageName("alpha-tools"),
                packages,
            ),
        ).release
        return MarketplaceIntentSource.LocalSnapshot(relative, release.index.sha256())
    }

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

    private fun resolvedState(repository: Path): ConsumerState.Resolved =
        assertIs<ConsumerState.Resolved>(
            assertIs<ConsumerStateReading.Read>(ConsumerStateRepository.read(repository)).state,
        )

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
        val cache: Path,
        val firstSource: MarketplaceIntentSource.LocalSnapshot,
        val secondSource: MarketplaceIntentSource.LocalSnapshot,
    )

}
