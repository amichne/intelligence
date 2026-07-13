package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import intelligence.cli.portable.GitCommitSha
import intelligence.cli.portable.GitCommitShaParsing
import intelligence.cli.portable.GitHubAssetDownload
import intelligence.cli.portable.GitHubAssetContentType
import intelligence.cli.portable.GitHubAssetId
import intelligence.cli.portable.GitHubAssetIdParsing
import intelligence.cli.portable.GitHubExactRelease
import intelligence.cli.portable.GitHubDraftCleanup
import intelligence.cli.portable.GitHubDraftRelease
import intelligence.cli.portable.GitHubMutationComplete
import intelligence.cli.portable.GitHubPublicationLookup
import intelligence.cli.portable.GitHubPublicationPreflight
import intelligence.cli.portable.GitHubPublicationRead
import intelligence.cli.portable.GitHubPublicationRequest
import intelligence.cli.portable.GitHubPublicationTransport
import intelligence.cli.portable.GitHubPublishedRelease
import intelligence.cli.portable.GitHubPublishedTimestamp
import intelligence.cli.portable.GitHubPublishedTimestampParsing
import intelligence.cli.portable.GitHubReadTransport
import intelligence.cli.portable.GitHubReleaseAsset
import intelligence.cli.portable.GitHubReleaseId
import intelligence.cli.portable.GitHubReleaseIdParsing
import intelligence.cli.portable.GitHubReleaseListing
import intelligence.cli.portable.GitHubReleaseResolution
import intelligence.cli.portable.GitHubRepository
import intelligence.cli.portable.GitHubRepositoryId
import intelligence.cli.portable.GitHubRepositoryIdParsing
import intelligence.cli.portable.GitHubRepositoryParsing
import intelligence.cli.portable.GitHubRepositoryUrl
import intelligence.cli.portable.GitHubRepositoryUrlParsing
import intelligence.cli.portable.GitHubReleaseUrl
import intelligence.cli.portable.GitHubReleaseUrlParsing
import intelligence.cli.portable.GitHubRemoteMutation
import intelligence.cli.portable.IdentifierParse
import intelligence.cli.portable.MarketplaceRelease
import intelligence.cli.portable.MarketplaceReleaseDirectory
import intelligence.cli.portable.MarketplaceReleaseDirectoryInspection
import intelligence.cli.portable.MarketplaceReleaseDirectoryMaterialization
import intelligence.cli.portable.MarketplaceReleaseMaterialization
import intelligence.cli.portable.PackageArchive
import intelligence.cli.portable.PackageArchiveMaterialization
import intelligence.cli.portable.PackageEntryPath
import intelligence.cli.portable.PackageEntryPathParse
import intelligence.cli.portable.PackageManifest
import intelligence.cli.portable.PackageManifestParsing
import intelligence.cli.portable.PackageName
import intelligence.cli.portable.PackageSourceFile
import intelligence.cli.portable.PackageSourceFileCreation
import intelligence.cli.portable.ReleaseFile
import intelligence.cli.portable.Sha256Digest
import intelligence.cli.portable.SnapshotId
import intelligence.cli.portable.publicationContentType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.io.TempDir

class GitHubConsumerCommandTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `packaged default setup verifies pinned index and selects only its default package`() {
        val repository = githubRepository("acme/tools")
        val fixture = fixture(repository, "snapshot-one", 1)
        val transport = RecordedGitHubReadTransport(mapOf(fixture.release.snapshotId to fixture))
        val consumer = temporaryDirectory.resolve("default-consumer")
        val cache = temporaryDirectory.resolve("default-cache")
        Files.createDirectories(consumer)
        val setup =
            SetupCommand(
                PortableCommandEnvironment(cacheRootOverride = cache, githubReadTransport = transport),
                PackagedMarketplaceDefault(
                    repository,
                    repositoryUrl(repository),
                    fixture.release.snapshotId,
                    fixture.marketplace.index.sha256(),
                ),
            )

        val dryRun = setup.test("--repository $consumer --dry-run --format json")
        assertEquals(0, dryRun.statusCode)
        assertFalse(Files.exists(consumer.resolve(".intelligence")))
        assertFalse(Files.exists(cache))

        val applied = setup.test("--repository $consumer --format json")
        assertEquals(0, applied.statusCode)
        assertTrue(applied.stdout.contains("alpha-tools"))
        assertFalse(applied.stdout.contains("skills/alpha"))
    }

    @Test
    fun `publication commands wire preflight and full immutable reread without implicit retry`() {
        val repository = githubRepository("acme/tools")
        val fixture = fixture(repository, "snapshot-one", 1)
        val releaseDirectory = temporaryDirectory.resolve("publication-release")
        assertTrue(
            MarketplaceReleaseDirectory.materialize(
                releaseDirectory,
                fixture.marketplace.marketplaceId,
                fixture.marketplace.snapshotId,
                fixture.marketplace.defaultPackage,
                listOf(fixture.packageArchive),
            ) is MarketplaceReleaseDirectoryMaterialization.Written,
        )
        val transport = RecordedPublicationTransport(fixture)
        val command =
            IntelligenceCommand(
                processRunner = ProcessRunner { _, _ -> 127 },
                portableEnvironmentOverride =
                    PortableCommandEnvironment(
                        cacheRootOverride = temporaryDirectory.resolve("publication-cache"),
                        githubPublicationTransport = transport,
                    ),
            )

        val dryRun = command.test(
            "marketplace publish --release-dir $releaseDirectory --github acme/tools " +
                "--commit ${fixture.release.tagCommitSha.render()} --dry-run --format json",
        )
        assertEquals(0, dryRun.statusCode)
        assertTrue(dryRun.stdout.contains("\"dryRun\":true"))
        assertEquals(1, transport.preflightCalls)
        assertEquals(0, transport.mutationCalls)

        val verified = command.test(
            "marketplace verify-publication --github acme/tools --snapshot snapshot-one --format json",
        )
        assertEquals(0, verified.statusCode)
        assertTrue(verified.stdout.contains("\"immutable\":true"))
        assertTrue(verified.stdout.contains("REMOTE_REDOWNLOAD"))
        assertEquals(0, transport.mutationCalls)

        val uncertainTransport = RecordedPublicationTransport(fixture, unknownOnCreate = true)
        val uncertainCommand =
            IntelligenceCommand(
                processRunner = ProcessRunner { _, _ -> 127 },
                portableEnvironmentOverride =
                    PortableCommandEnvironment(
                        cacheRootOverride = temporaryDirectory.resolve("uncertain-cache"),
                        githubPublicationTransport = uncertainTransport,
                    ),
            )
        val uncertain = uncertainCommand.test(
            "marketplace publish --release-dir $releaseDirectory --github acme/tools " +
                "--commit ${fixture.release.tagCommitSha.render()} --format json",
        )
        assertEquals(6, uncertain.statusCode)
        assertTrue(uncertain.stdout.contains("\"code\":\"REMOTE_STATE_UNKNOWN\""))
        assertEquals(1, uncertainTransport.mutationCalls)
    }

    @Test
    fun `materialize and inspect preserve canonical release closure without source reconstruction`() {
        val repository = githubRepository("acme/tools")
        val fixture = fixture(repository, "snapshot-one", 1)
        val source = temporaryDirectory.resolve("authored-source")
        val output = temporaryDirectory.resolve("rebuilt-release")
        writeAuthoredSource(source)
        val command = IntelligenceCommand(processRunner = ProcessRunner { _, _ -> 127 })

        val dryRun = command.test(
            "marketplace materialize --source $source --snapshot snapshot-two --out $output --dry-run --format json",
        )
        assertEquals(0, dryRun.statusCode)
        assertFalse(Files.exists(output))

        val materialized = command.test(
            "marketplace materialize --source $source --snapshot snapshot-two --out $output --format json",
        )
        assertEquals(0, materialized.statusCode)
        val rebuilt = MarketplaceReleaseDirectory.inspect(output) as MarketplaceReleaseDirectoryInspection.Inspected
        assertEquals("snapshot-two", rebuilt.release.snapshotId.render())
        val indexDigest = rebuilt.release.index.sha256().render()

        val inspected = command.test(
            "marketplace inspect --local-snapshot $output --index-sha256 $indexDigest --format json",
        )
        assertEquals(0, inspected.statusCode)
        assertTrue(inspected.stdout.contains("\"defaultPackage\":\"alpha-tools\""))
        assertFalse(inspected.stdout.contains("Use alpha"))

        val repeated = command.test(
            "marketplace materialize --source $source --snapshot snapshot-two --out $output --format json",
        )
        assertEquals(0, repeated.statusCode)
    }

    private fun writeAuthoredSource(root: Path) {
        val skillBytes =
            "---\nname: alpha\ndescription: \"Alpha skill\"\n---\n\nUse alpha.\n".encodeToByteArray()
        val skillPath = "skills/alpha/SKILL.md"
        val packageRoot = root.resolve("packages/alpha-tools")
        Files.createDirectories(packageRoot.resolve("skills/alpha"))
        root.resolve("default-package").toFile().writeText("alpha-tools\n")
        packageRoot.resolve(skillPath).toFile().writeBytes(skillBytes)
        packageRoot.resolve("package.json").toFile().writeText(
            "{\"description\":\"Alpha package\",\"marketplaceId\":\"example-marketplace\"," +
                "\"name\":\"alpha-tools\",\"schemaVersion\":1,\"skills\":[{\"assets\":[]," +
                "\"description\":\"Alpha skill\",\"name\":\"alpha\"," +
                "\"primary\":{\"executable\":false,\"path\":\"$skillPath\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[],\"type\":\"INTELLIGENCE_PACKAGE\"}\n",
        )
    }

    @Test
    fun `exact GitHub source selects resolves updates and reconstructs without moving discovery`() {
        val repository = githubRepository("acme/tools")
        val first = fixture(repository, "snapshot-one", 1)
        val second = fixture(repository, "snapshot-two", 2)
        val transport = RecordedGitHubReadTransport(mapOf(first.release.snapshotId to first, second.release.snapshotId to second))
        val consumer = temporaryDirectory.resolve("consumer")
        val cache = temporaryDirectory.resolve("cache")
        Files.createDirectories(consumer)
        val command =
            IntelligenceCommand(
                processRunner = ProcessRunner { _, _ -> 127 },
                portableEnvironmentOverride =
                    PortableCommandEnvironment(
                        cacheRootOverride = cache,
                        githubReadTransport = transport,
                    ),
            )

        val selected = command.test(
            "marketplace select example-marketplace --repository $consumer " +
                "--github acme/tools --snapshot snapshot-one --all --format json",
        )
        assertEquals(0, selected.statusCode)
        assertTrue(selected.stdout.contains("\"command\":\"marketplace.select\""))
        assertTrue(selected.stdout.contains("alpha-tools"))
        assertFalse(selected.stdout.contains("skills/alpha"))

        val updated = command.test(
            "marketplace update example-marketplace --repository $consumer " +
                "--github acme/tools --snapshot snapshot-two --format json",
        )
        assertEquals(0, updated.statusCode)

        Files.delete(consumer.resolve(".intelligence/marketplace-lock.json"))
        val resolved = command.test("marketplace resolve --repository $consumer --format json")
        assertEquals(0, resolved.statusCode)

        val cacheObject = cache.resolve(first.packageArchive.sha256.render().take(2))
            .resolve(first.packageArchive.sha256.render().drop(2))
        if (Files.exists(cacheObject)) Files.delete(cacheObject)
        val reconstructed = command.test("marketplace reconstruct --repository $consumer --format json")
        assertEquals(0, reconstructed.statusCode)
        assertTrue(reconstructed.stdout.contains("\"fetched\":"))
        assertTrue(transport.listCalls == 0)
        assertTrue(transport.resolvedSnapshots.all { it == "snapshot-one" || it == "snapshot-two" })
    }

    private fun fixture(
        repository: GitHubRepository,
        snapshot: String,
        releaseNumber: Long,
    ): RemoteFixture {
        val packageArchive = packageArchive()
        val materialized =
            MarketplaceRelease.materialize(
                marketplaceId("example-marketplace"),
                snapshotId(snapshot),
                packageName("alpha-tools"),
                listOf(packageArchive),
            ) as MarketplaceReleaseMaterialization.Materialized
        val release = materialized.release
        val assets =
            release.files().mapIndexed { index, file ->
                GitHubReleaseAsset(
                    assetId((releaseNumber * 100) + index + 1),
                    file.name,
                    file.byteSize,
                    file.sha256,
                    publicationContentType(file.name),
                )
            }
        return RemoteFixture(
            GitHubExactRelease(
                repository,
                releaseId(releaseNumber),
                release.snapshotId,
                commitSha(releaseNumber),
                immutable = true,
                draft = false,
                assets = assets,
            ),
            release,
            packageArchive,
        )
    }

    private fun packageArchive(): PackageArchive {
        val skillBytes =
            "---\nname: alpha\ndescription: \"Alpha skill\"\n---\n\nUse alpha.\n".encodeToByteArray()
        val skillPath = "skills/alpha/SKILL.md"
        val manifest =
            "{\"description\":\"Alpha package\",\"marketplaceId\":\"example-marketplace\"," +
                "\"name\":\"alpha-tools\",\"schemaVersion\":1,\"skills\":[{\"assets\":[]," +
                "\"description\":\"Alpha skill\",\"name\":\"alpha\"," +
                "\"primary\":{\"executable\":false,\"path\":\"$skillPath\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"
        val parsedManifest = (PackageManifest.parse(manifest.encodeToByteArray()) as PackageManifestParsing.Parsed).manifest
        val source =
            (PackageSourceFile.create(packagePath(skillPath), skillBytes, false) as PackageSourceFileCreation.Created).file
        return (PackageArchive.materialize(parsedManifest, listOf(source)) as PackageArchiveMaterialization.Materialized).archive
    }

    private class RecordedGitHubReadTransport(
        private val fixtures: Map<SnapshotId, RemoteFixture>,
    ) : GitHubReadTransport {
        var listCalls: Int = 0
        val resolvedSnapshots = mutableListOf<String>()

        override fun list(repository: GitHubRepository): GitHubReleaseListing {
            listCalls += 1
            return GitHubReleaseListing.Listed(emptyList())
        }

        override fun resolve(
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubReleaseResolution {
            resolvedSnapshots += snapshotId.render()
            return GitHubReleaseResolution.Resolved(checkNotNull(fixtures[snapshotId]).release)
        }

        override fun download(
            repository: GitHubRepository,
            release: GitHubExactRelease,
            asset: GitHubReleaseAsset,
        ): GitHubAssetDownload {
            val fixture = checkNotNull(fixtures[release.snapshotId])
            return GitHubAssetDownload.Downloaded(fixture.marketplace.file(asset.name).bytes())
        }
    }

    private class RecordedPublicationTransport(
        private val fixture: RemoteFixture,
        private val unknownOnCreate: Boolean = false,
    ) : GitHubPublicationTransport {
        var preflightCalls: Int = 0
        var mutationCalls: Int = 0

        override fun preflight(request: GitHubPublicationRequest): GitHubPublicationPreflight {
            preflightCalls += 1
            return GitHubPublicationPreflight.Ready(repositoryId(1))
        }

        override fun createDraft(request: GitHubPublicationRequest): GitHubRemoteMutation<GitHubDraftRelease> {
            mutationCalls += 1
            return if (unknownOnCreate) {
                GitHubRemoteMutation.Unknown(null)
            } else {
                error("dry-run must not create a draft")
            }
        }

        override fun upload(
            draft: GitHubDraftRelease,
            file: ReleaseFile,
            contentType: GitHubAssetContentType,
        ) = error("dry-run must not upload")

        override fun listDraftAssets(draft: GitHubDraftRelease) = error("dry-run must not list draft assets")

        override fun downloadDraftAsset(
            draft: GitHubDraftRelease,
            asset: GitHubReleaseAsset,
        ) = error("dry-run must not read draft assets")

        override fun publish(draft: GitHubDraftRelease): GitHubRemoteMutation<GitHubMutationComplete> {
            mutationCalls += 1
            error("dry-run must not publish")
        }

        override fun lookup(
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubPublicationLookup =
            GitHubPublicationLookup.Published(
                GitHubPublishedRelease(
                    repositoryId(1),
                    fixture.release,
                    releaseUrl(repository, snapshotId),
                    publishedTimestamp(),
                ),
            )

        override fun downloadPublishedAsset(
            release: GitHubPublishedRelease,
            asset: GitHubReleaseAsset,
        ): GitHubPublicationRead<ByteArray> =
            GitHubPublicationRead.Read(fixture.marketplace.file(asset.name).bytes())

        override fun cleanup(draft: GitHubDraftRelease): GitHubDraftCleanup {
            mutationCalls += 1
            error("dry-run must not clean up")
        }

        private fun repositoryId(raw: Long): GitHubRepositoryId =
            (GitHubRepositoryId.parse(raw) as GitHubRepositoryIdParsing.Parsed).id

        private fun releaseUrl(
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubReleaseUrl =
            (
                GitHubReleaseUrl.parse(
                    "https://github.com/${repository.render()}/releases/tag/${snapshotId.render()}",
                    repository,
                    snapshotId,
                ) as GitHubReleaseUrlParsing.Parsed
            ).url

        private fun publishedTimestamp(): GitHubPublishedTimestamp =
            (GitHubPublishedTimestamp.parse("2026-07-13T00:00:00Z") as GitHubPublishedTimestampParsing.Parsed)
                .timestamp
    }

    private data class RemoteFixture(
        val release: GitHubExactRelease,
        val marketplace: MarketplaceRelease,
        val packageArchive: PackageArchive,
    )

    private fun githubRepository(raw: String): GitHubRepository =
        (GitHubRepository.parse(raw) as GitHubRepositoryParsing.Parsed).repository

    private fun repositoryUrl(repository: GitHubRepository): GitHubRepositoryUrl =
        (
            GitHubRepositoryUrl.parse("https://github.com/${repository.render()}") as
                GitHubRepositoryUrlParsing.Parsed
        ).url

    private fun marketplaceId(raw: String) =
        (intelligence.cli.portable.MarketplaceId.parse(raw) as IdentifierParse.Accepted).value

    private fun packageName(raw: String): PackageName =
        (PackageName.parse(raw) as IdentifierParse.Accepted).value

    private fun snapshotId(raw: String): SnapshotId =
        (SnapshotId.parse(raw) as IdentifierParse.Accepted).value

    private fun packagePath(raw: String): PackageEntryPath =
        (PackageEntryPath.parse(raw) as PackageEntryPathParse.Accepted).value

    private fun releaseId(raw: Long): GitHubReleaseId =
        (GitHubReleaseId.parse(raw) as GitHubReleaseIdParsing.Parsed).id

    private fun assetId(raw: Long): GitHubAssetId =
        (GitHubAssetId.parse(raw) as GitHubAssetIdParsing.Parsed).id

    private fun commitSha(seed: Long): GitCommitSha =
        (GitCommitSha.parse(seed.toString(16).padStart(40, '0')) as GitCommitShaParsing.Parsed).sha
}
