package intelligence.cli.portable

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GitHubSnapshotResolverTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `exact immutable release resolves selected packages through verified bytes`() {
        val fixture = fixture(immutable = true)
        val selection = selection(fixture, "alpha-tools")
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))

        val resolution = assertIs<MarketplaceResolution.Resolved>(
            GitHubSnapshotResolver.resolve(
                selection,
                cache,
                fixture.transport,
                DigestCacheWritePolicy.STORE,
            ),
        ).marketplace

        assertEquals(listOf("alpha-tools"), resolution.packages.map { archive -> archive.packageName.render() })
        assertIs<MarketplaceLockSource.GitHubRelease>(resolution.lockEntry.source)
        assertEquals(
            listOf("marketplace.json", "SHA256SUMS", "package-alpha-tools.zip"),
            fixture.transport.downloaded,
        )
        assertTrue(fixture.transport.listed.isEmpty())
        assertTrue(cache.root.toFile().walkTopDown().any { file -> file.isFile })

        val packageEvidence = resolution.lockEntry.packages.single().archive
        java.nio.file.Files.delete(cache.pathFor(packageEvidence.sha256))
        fixture.transport.downloaded.clear()
        val reconstructed = assertIs<MarketplaceResolution.Resolved>(
            GitHubSnapshotResolver.reconstruct(
                resolution.lockEntry,
                cache,
                fixture.transport,
                DigestCacheWritePolicy.STORE,
            ),
        ).marketplace
        assertEquals(listOf("alpha-tools"), reconstructed.packages.map { archive -> archive.packageName.render() })
        assertEquals(
            listOf("marketplace.json", "SHA256SUMS", "package-alpha-tools.zip"),
            fixture.transport.downloaded,
        )
    }

    @Test
    fun `inspection validates index and checksum without exposing package bytes`() {
        val fixture = fixture(immutable = true)

        val inspection = assertIs<GitHubSnapshotInspection.Inspected>(
            GitHubSnapshotResolver.inspect(fixture.repository, fixture.snapshotId, fixture.transport),
        )

        assertEquals("example-marketplace", inspection.index.marketplaceId.render())
        assertEquals(listOf("alpha-tools", "beta-tools"), inspection.index.packages.map { it.name.render() })
        assertEquals(listOf("marketplace.json", "SHA256SUMS"), fixture.transport.downloaded)
    }

    @Test
    fun `mutable release and corrupt asset evidence fail closed`() {
        val mutable = fixture(immutable = false)
        assertEquals(
            GitHubSnapshotRejection.ReleaseNotImmutable(mutable.snapshotId),
            assertIs<GitHubSnapshotInspection.Rejected>(
                GitHubSnapshotResolver.inspect(mutable.repository, mutable.snapshotId, mutable.transport),
            ).reason,
        )
        assertTrue(mutable.transport.downloaded.isEmpty())

        val corrupt = fixture(immutable = true, corruptIndexDownload = true)
        assertIs<GitHubSnapshotRejection.DownloadRejected>(
            assertIs<GitHubSnapshotInspection.Rejected>(
                GitHubSnapshotResolver.inspect(corrupt.repository, corrupt.snapshotId, corrupt.transport),
            ).reason,
        )
        assertEquals(listOf("marketplace.json"), corrupt.transport.downloaded)
    }

    @Test
    fun `discovery is read only explicit and deterministically filtered`() {
        val fixture = fixture(immutable = true)
        fixture.transport.candidates += GitHubReleaseCandidate(fixture.snapshotId, "Primary tools")
        fixture.transport.candidates += GitHubReleaseCandidate(snapshotId("snapshot-two"), "Secondary tools")

        val discovered = assertIs<GitHubMarketplaceDiscovery.Discovered>(
            GitHubMarketplaceDiscovery.discover(fixture.repository, "secondary", fixture.transport),
        )

        assertEquals(listOf("snapshot-two"), discovered.candidates.map { candidate -> candidate.snapshotId.render() })
        assertEquals(listOf(fixture.repository), fixture.transport.listed)
        assertTrue(fixture.transport.downloaded.isEmpty())
        assertFalse(fixture.transport.resolved)
    }

    private fun fixture(
        immutable: Boolean,
        corruptIndexDownload: Boolean = false,
    ): Fixture {
        val marketplaceId = marketplaceId("example-marketplace")
        val snapshotId = snapshotId("snapshot-one")
        val alpha = packageArchive("alpha-tools", "alpha")
        val beta = packageArchive("beta-tools", "beta")
        val release = assertIs<MarketplaceReleaseMaterialization.Materialized>(
            MarketplaceRelease.materialize(
                marketplaceId,
                snapshotId,
                packageName("alpha-tools"),
                listOf(alpha, beta),
            ),
        ).release
        val repository = repository("amichne/example-marketplace")
        val remoteAssets = release.files().mapIndexed { index, file ->
            GitHubReleaseAsset(
                assetId((index + 101).toLong()),
                file.name,
                file.byteSize,
                file.sha256,
                contentType(file.name),
            )
        }
        val remoteRelease =
            GitHubExactRelease(
                repository,
                releaseId(42),
                snapshotId,
                commitSha("0123456789abcdef0123456789abcdef01234567"),
                immutable,
                draft = false,
                remoteAssets,
            )
        val bytes = release.files().associate { file -> file.name to file.bytes() }.toMutableMap()
        if (corruptIndexDownload) {
            bytes[ReleaseAssetName.snapshotIndex()] = "corrupt".encodeToByteArray()
        }
        return Fixture(repository, snapshotId, FakeTransport(remoteRelease, bytes))
    }

    private fun selection(
        fixture: Fixture,
        packageName: String,
    ): MarketplaceIntentSelection =
        assertIs<MarketplaceIntentSelectionCreation.Created>(
            MarketplaceIntentSelection.create(
                marketplaceId("example-marketplace"),
                MarketplaceIntentSource.GitHubRelease(repositoryUrl(fixture.repository), fixture.snapshotId),
                listOf(packageName(packageName)),
            ),
        ).selection

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

    private fun repository(raw: String): GitHubRepository =
        assertIs<GitHubRepositoryParsing.Parsed>(GitHubRepository.parse(raw)).repository

    private fun repositoryUrl(repository: GitHubRepository): GitHubRepositoryUrl =
        assertIs<GitHubRepositoryUrlParsing.Parsed>(
            GitHubRepositoryUrl.parse("https://github.com/${repository.render().lowercase()}"),
        ).url

    private fun releaseId(raw: Long): GitHubReleaseId =
        assertIs<GitHubReleaseIdParsing.Parsed>(GitHubReleaseId.parse(raw)).id

    private fun assetId(raw: Long): GitHubAssetId =
        assertIs<GitHubAssetIdParsing.Parsed>(GitHubAssetId.parse(raw)).id

    private fun commitSha(raw: String): GitCommitSha =
        assertIs<GitCommitShaParsing.Parsed>(GitCommitSha.parse(raw)).sha

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value

    private fun packagePath(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun contentType(name: ReleaseAssetName): GitHubAssetContentType =
        when {
            name.render().endsWith(".json") -> GitHubAssetContentType.JSON
            name.render().endsWith(".zip") -> GitHubAssetContentType.ZIP
            else -> GitHubAssetContentType.PLAIN_TEXT
        }

    private data class Fixture(
        val repository: GitHubRepository,
        val snapshotId: SnapshotId,
        val transport: FakeTransport,
    )

    private class FakeTransport(
        private val release: GitHubExactRelease,
        private val bytes: Map<ReleaseAssetName, ByteArray>,
    ) : GitHubReadTransport {
        val listed = mutableListOf<GitHubRepository>()
        val downloaded = mutableListOf<String>()
        val candidates = mutableListOf<GitHubReleaseCandidate>()
        var resolved: Boolean = false

        override fun list(repository: GitHubRepository): GitHubReleaseListing {
            listed += repository
            return GitHubReleaseListing.Listed(candidates.toList())
        }

        override fun resolve(
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubReleaseResolution {
            resolved = true
            return GitHubReleaseResolution.Resolved(release)
        }

        override fun download(
            repository: GitHubRepository,
            release: GitHubExactRelease,
            asset: GitHubReleaseAsset,
        ): GitHubAssetDownload {
            downloaded += asset.name.render()
            return GitHubAssetDownload.Downloaded(bytes.getValue(asset.name))
        }
    }
}
