package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class LocalSnapshotResolverTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `resolution selects packages as the sole unit and caches only exact required assets`() {
        val fixture = fixture()
        val resolved = assertIs<MarketplaceResolution.Resolved>(
            LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
        ).marketplace

        assertEquals(listOf("alpha-tools"), resolved.packages.map { archive -> archive.packageName.render() })
        assertEquals(listOf("alpha-tools"), resolved.lockEntry.packages.map { locked -> locked.name.render() })
        assertEquals(MarketplaceLockSource.LocalSnapshot(fixture.relativeDirectory), resolved.lockEntry.source)
        assertIs<DigestCacheRead.Hit>(fixture.cache.read(expectation(resolved.lockEntry.index)))
        assertIs<DigestCacheRead.Hit>(fixture.cache.read(expectation(resolved.lockEntry.checksum)))
        assertIs<DigestCacheRead.Hit>(
            fixture.cache.read(expectation(resolved.lockEntry.packages.single().archive)),
        )
        assertEquals(
            DigestCacheRead.Miss,
            fixture.cache.read(
                CacheBlobExpectation.fromVerified(fixture.beta.bytes()),
            ),
        )
    }

    @Test
    fun `resolution rejects changed package bytes and never constructs lock evidence`() {
        val fixture = fixture()
        val asset = fixture.snapshotDirectory.resolve(fixture.alpha.assetName.render())
        Files.write(asset, Files.readAllBytes(asset) + 0.toByte())

        val rejected = assertIs<MarketplaceResolution.Rejected>(
            LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
        )
        assertIs<MarketplaceResolutionRejection.SourceAssetRejected>(rejected.reason)
        assertEquals(DigestCacheRead.Miss, fixture.cache.read(CacheBlobExpectation.fromVerified(fixture.alpha.bytes())))
    }

    @Test
    fun `resolution verifies the exact index identity and checksum manifest`() {
        val fixture = fixture()
        val wrongSelection = localSelection(
            fixture.relativeDirectory,
            Sha256Digest.compute("wrong-index".encodeToByteArray()),
        )
        assertIs<MarketplaceResolutionRejection.IndexDigestMismatch>(
            assertIs<MarketplaceResolution.Rejected>(
                LocalSnapshotResolver.resolve(fixture.consumerRoot, wrongSelection, fixture.cache),
            ).reason,
        )

        val checksum = fixture.snapshotDirectory.resolve("SHA256SUMS")
        Files.write(checksum, Files.readAllBytes(checksum) + ' '.code.toByte())
        assertIs<MarketplaceResolutionRejection.ChecksumMismatch>(
            assertIs<MarketplaceResolution.Rejected>(
                LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
            ).reason,
        )
    }

    @Test
    fun `local source traversal rejects symbolic directories`() {
        val fixture = fixture()
        val external = temporaryDirectory.resolve("external")
        Files.move(fixture.snapshotDirectory, external)
        Files.createSymbolicLink(fixture.snapshotDirectory, external)

        val rejected = assertIs<MarketplaceResolution.Rejected>(
            LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
        )
        assertIs<MarketplaceResolutionRejection.LocalDirectoryRejected>(rejected.reason)
        assertTrue(Files.isSymbolicLink(fixture.snapshotDirectory))
    }

    @Test
    fun `offline reconstruction uses only the lock and verified cache`() {
        val fixture = fixture()
        val resolved = assertIs<MarketplaceResolution.Resolved>(
            LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
        ).marketplace
        deleteTree(fixture.snapshotDirectory)

        val reconstructed = assertIs<MarketplaceResolution.Resolved>(
            OfflineMarketplaceReconstructor.reconstruct(resolved.lockEntry, fixture.cache),
        ).marketplace
        assertEquals(listOf("alpha-tools"), reconstructed.packages.map { it.packageName.render() })

        Files.delete(fixture.cache.pathFor(resolved.lockEntry.packages.single().archive.sha256))
        val rejected = assertIs<MarketplaceResolution.Rejected>(
            OfflineMarketplaceReconstructor.reconstruct(resolved.lockEntry, fixture.cache),
        )
        assertEquals(
            MarketplaceResolutionRejection.OfflineCacheMiss(resolved.lockEntry.packages.single().archive.name),
            rejected.reason,
        )
        assertFalse(Files.exists(fixture.snapshotDirectory))
    }

    @Test
    fun `online local reconstruction refetches only missing exact locked content`() {
        val fixture = fixture()
        val resolved = assertIs<MarketplaceResolution.Resolved>(
            LocalSnapshotResolver.resolve(fixture.consumerRoot, fixture.selection, fixture.cache),
        ).marketplace
        val packageEvidence = resolved.lockEntry.packages.single().archive
        val cachePath = fixture.cache.pathFor(packageEvidence.sha256)
        Files.delete(cachePath)

        assertIs<MarketplaceResolution.Resolved>(
            LocalSnapshotResolver.reconstruct(fixture.consumerRoot, resolved.lockEntry, fixture.cache),
        )
        assertContentEquals(fixture.alpha.bytes(), assertIs<DigestCacheRead.Hit>(
            fixture.cache.read(expectation(packageEvidence)),
        ).blob.bytes())

        Files.write(cachePath, ByteArray(packageEvidence.byteSize))
        assertIs<DigestCacheRejection.DigestMismatch>(
            assertIs<MarketplaceResolutionRejection.CacheRejected>(
                assertIs<MarketplaceResolution.Rejected>(
                    LocalSnapshotResolver.reconstruct(fixture.consumerRoot, resolved.lockEntry, fixture.cache),
                ).reason,
            ).reason,
        )
        assertContentEquals(ByteArray(packageEvidence.byteSize), Files.readAllBytes(cachePath))
    }

    private fun fixture(): Fixture {
        val consumerRoot = temporaryDirectory.resolve("consumer")
        Files.createDirectories(consumerRoot)
        val alpha = packageArchive("alpha-tools", "alpha")
        val beta = packageArchive("beta-tools", "beta")
        val relativeDirectory = relativeDirectory("releases/snapshot-one")
        val snapshotDirectory = consumerRoot.resolve(relativeDirectory.render())
        Files.createDirectories(snapshotDirectory.parent)
        val materialized = assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(
            MarketplaceReleaseDirectory.materialize(
                snapshotDirectory,
                marketplaceId("example-marketplace"),
                snapshotId("snapshot-one"),
                packageName("alpha-tools"),
                listOf(alpha, beta),
            ),
        )
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))
        return Fixture(
            consumerRoot = consumerRoot,
            snapshotDirectory = snapshotDirectory,
            relativeDirectory = relativeDirectory,
            selection = localSelection(relativeDirectory, materialized.release.index.sha256()),
            cache = cache,
            alpha = alpha,
            beta = beta,
        )
    }

    private fun localSelection(
        directory: ConsumerRelativeDirectory,
        indexSha256: Sha256Digest,
    ): MarketplaceIntentSelection =
        assertIs<MarketplaceIntentSelectionCreation.Created>(
            MarketplaceIntentSelection.create(
                marketplaceId("example-marketplace"),
                MarketplaceIntentSource.LocalSnapshot(directory, indexSha256),
                listOf(packageName("alpha-tools")),
            ),
        ).selection

    private fun expectation(asset: LockedAsset): CacheBlobExpectation =
        CacheBlobExpectation.from(asset)

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

    private fun deleteTree(root: Path) {
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
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
        val consumerRoot: Path,
        val snapshotDirectory: Path,
        val relativeDirectory: ConsumerRelativeDirectory,
        val selection: MarketplaceIntentSelection,
        val cache: DigestAddressedCache,
        val alpha: PackageArchive,
        val beta: PackageArchive,
    )
}
