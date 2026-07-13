package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GitHubPublisherTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `publisher preflights uploads reads back and publishes exactly once`() {
        val fixture = fixture()

        val publication = assertIs<GitHubPublication.Published>(
            GitHubPublisher.publish(
                fixture.releaseDirectory,
                fixture.repository,
                fixture.commit,
                fixture.transport,
                dryRun = false,
            ),
        )

        assertTrue(publication.receipt.immutable)
        assertEquals("snapshot-one", publication.receipt.snapshotId.render())
        assertEquals(
            listOf("LOCAL_VALIDATE", "REMOTE_PREFLIGHT", "UPLOAD_READBACK", "IMMUTABLE_VERIFY"),
            publication.receipt.completedGates,
        )
        assertEquals("preflight", fixture.transport.operations.first())
        assertEquals("create", fixture.transport.operations[1])
        assertEquals(1, fixture.transport.operations.count { operation -> operation == "publish" })
        assertEquals("lookup", fixture.transport.operations.last())
        assertEquals(
            fixture.release.files().map { file -> "upload:${file.name.render()}" },
            fixture.transport.operations.filter { operation -> operation.startsWith("upload:") },
        )
    }

    @Test
    fun `publication dry run performs complete preflight without mutation`() {
        val fixture = fixture()

        val prepared = assertIs<GitHubPublication.Prepared>(
            GitHubPublisher.publish(
                fixture.releaseDirectory,
                fixture.repository,
                fixture.commit,
                fixture.transport,
                dryRun = true,
            ),
        )

        assertEquals("snapshot-one", prepared.snapshotId.render())
        assertEquals(listOf("preflight"), fixture.transport.operations)
    }

    @Test
    fun `uncertain publish result is explicit and never cleaned up or retried`() {
        val fixture = fixture()
        fixture.transport.publishUnknown = true

        val unknown = assertIs<GitHubPublication.RemoteStateUnknown>(
            GitHubPublisher.publish(
                fixture.releaseDirectory,
                fixture.repository,
                fixture.commit,
                fixture.transport,
                dryRun = false,
            ),
        )

        assertEquals(fixture.releaseId, unknown.releaseId)
        assertEquals(1, fixture.transport.operations.count { operation -> operation == "publish" })
        assertTrue(fixture.transport.operations.none { operation -> operation == "cleanup" })
    }

    @Test
    fun `remote verifier reopens every published asset and proves canonical release content`() {
        val fixture = fixture()
        assertIs<GitHubPublication.Published>(
            GitHubPublisher.publish(
                fixture.releaseDirectory,
                fixture.repository,
                fixture.commit,
                fixture.transport,
                dryRun = false,
            ),
        )
        fixture.transport.operations.clear()

        val verified = assertIs<GitHubPublicationVerification.Verified>(
            GitHubPublicationVerifier.verify(
                fixture.repository,
                snapshotId("snapshot-one"),
                fixture.transport,
            ),
        )

        assertEquals("example-marketplace", verified.marketplace.marketplaceId.render())
        assertEquals("lookup", fixture.transport.operations.first())
        assertEquals(
            fixture.release.files().map { file -> "verify-download:${file.name.render()}" },
            fixture.transport.operations.drop(1),
        )
    }

    private fun fixture(): Fixture {
        val marketplaceId = marketplaceId("example-marketplace")
        val snapshotId = snapshotId("snapshot-one")
        val archive = packageArchive("alpha-tools", "alpha")
        val output = temporaryDirectory.resolve("release")
        val written = assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(
            MarketplaceReleaseDirectory.materialize(
                output,
                marketplaceId,
                snapshotId,
                packageName("alpha-tools"),
                listOf(archive),
            ),
        )
        val repository = repository("amichne/example")
        val commit = commitSha("0123456789abcdef0123456789abcdef01234567")
        val releaseId = releaseId(42)
        return Fixture(
            output,
            written.release,
            repository,
            commit,
            releaseId,
            FakePublicationTransport(repository, commit, releaseId, written.release),
        )
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

    private fun repository(raw: String): GitHubRepository =
        assertIs<GitHubRepositoryParsing.Parsed>(GitHubRepository.parse(raw)).repository

    private fun releaseId(raw: Long): GitHubReleaseId =
        assertIs<GitHubReleaseIdParsing.Parsed>(GitHubReleaseId.parse(raw)).id

    private fun repositoryId(raw: Long): GitHubRepositoryId =
        assertIs<GitHubRepositoryIdParsing.Parsed>(GitHubRepositoryId.parse(raw)).id

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
        val releaseDirectory: Path,
        val release: MarketplaceRelease,
        val repository: GitHubRepository,
        val commit: GitCommitSha,
        val releaseId: GitHubReleaseId,
        val transport: FakePublicationTransport,
    )

    private inner class FakePublicationTransport(
        private val repository: GitHubRepository,
        private val commit: GitCommitSha,
        private val releaseId: GitHubReleaseId,
        private val release: MarketplaceRelease,
    ) : GitHubPublicationTransport {
        val operations = mutableListOf<String>()
        val assets = mutableListOf<GitHubReleaseAsset>()
        var publishUnknown: Boolean = false

        override fun preflight(request: GitHubPublicationRequest): GitHubPublicationPreflight {
            operations += "preflight"
            return GitHubPublicationPreflight.Ready(repositoryId(9))
        }

        override fun createDraft(request: GitHubPublicationRequest): GitHubRemoteMutation<GitHubDraftRelease> {
            operations += "create"
            return GitHubRemoteMutation.Completed(
                GitHubDraftRelease(repository, releaseId, request.snapshotId, commit),
            )
        }

        override fun upload(
            draft: GitHubDraftRelease,
            file: ReleaseFile,
            contentType: GitHubAssetContentType,
        ): GitHubRemoteMutation<GitHubReleaseAsset> {
            operations += "upload:${file.name.render()}"
            val asset =
                GitHubReleaseAsset(
                    assetId((assets.size + 101).toLong()),
                    file.name,
                    file.byteSize,
                    file.sha256,
                    contentType,
                )
            assets += asset
            return GitHubRemoteMutation.Completed(asset)
        }

        override fun listDraftAssets(draft: GitHubDraftRelease): GitHubPublicationRead<List<GitHubReleaseAsset>> {
            operations += "list"
            return GitHubPublicationRead.Read(assets.toList())
        }

        override fun downloadDraftAsset(
            draft: GitHubDraftRelease,
            asset: GitHubReleaseAsset,
        ): GitHubPublicationRead<ByteArray> {
            operations += "download:${asset.name.render()}"
            return GitHubPublicationRead.Read(release.file(asset.name).bytes())
        }

        override fun publish(draft: GitHubDraftRelease): GitHubRemoteMutation<GitHubMutationComplete> {
            operations += "publish"
            return if (publishUnknown) {
                GitHubRemoteMutation.Unknown(draft.releaseId)
            } else {
                GitHubRemoteMutation.Completed(GitHubMutationComplete)
            }
        }

        override fun lookup(
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubPublicationLookup {
            operations += "lookup"
            val exact =
                GitHubExactRelease(
                    repository,
                    releaseId,
                    snapshotId,
                    commit,
                    immutable = true,
                    draft = false,
                    assets,
                )
            return GitHubPublicationLookup.Published(
                GitHubPublishedRelease(
                    repositoryId(9),
                    exact,
                    releaseUrl(repository, snapshotId),
                    publishedAt("2026-07-13T00:00:00Z"),
                ),
            )
        }

        override fun downloadPublishedAsset(
            release: GitHubPublishedRelease,
            asset: GitHubReleaseAsset,
        ): GitHubPublicationRead<ByteArray> {
            operations += "verify-download:${asset.name.render()}"
            return GitHubPublicationRead.Read(this.release.file(asset.name).bytes())
        }

        override fun cleanup(draft: GitHubDraftRelease): GitHubDraftCleanup {
            operations += "cleanup"
            return GitHubDraftCleanup.Cleared
        }
    }

    private fun releaseUrl(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
    ): GitHubReleaseUrl =
        assertIs<GitHubReleaseUrlParsing.Parsed>(
            GitHubReleaseUrl.parse(
                "https://github.com/${repository.render()}/releases/tag/${snapshotId.render()}",
                repository,
                snapshotId,
            ),
        ).url

    private fun publishedAt(raw: String): GitHubPublishedTimestamp =
        assertIs<GitHubPublishedTimestampParsing.Parsed>(GitHubPublishedTimestamp.parse(raw)).timestamp
}
