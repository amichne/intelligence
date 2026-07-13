package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarketplaceReleaseTest {
    @Test
    fun `shuffled packages produce one exact all-provider release`() {
        val alpha = packageArchive("alpha-tools", "alpha")
        val zeta = packageArchive("zeta-tools", "zeta")
        val forward = materialize(listOf(alpha, zeta))
        val reversed = materialize(listOf(zeta, alpha))

        assertReleaseFilesEqual(forward.files(), reversed.files())
        assertEquals(
            listOf(
                "SHA256SUMS",
                "codex-marketplace.zip",
                "github-copilot-marketplace.zip",
                "marketplace.json",
                "package-alpha-tools.zip",
                "package-zeta-tools.zip",
            ),
            forward.files().map { file -> file.name.render() },
        )
        assertEquals(MarketplaceReleaseVerification.Verified, forward.verify(forward.files()))
        assertEquals(expectedChecksums(forward), forward.checksumBytes().decodeToString())
        assertContentEquals(alpha.bytes(), forward.file(alpha.assetName).bytes())
        assertContentEquals(zeta.bytes(), forward.file(zeta.assetName).bytes())
        assertContentEquals(forward.index.canonicalBytes(), forward.file(releaseAsset("marketplace.json")).bytes())
        assertEquals(
            "550d5268c887548fa6d4c6f0eb3e4b0d9238c020e0b19c859b235d35d068ba35",
            Sha256Digest.compute(forward.checksumBytes()).render(),
        )
    }

    @Test
    fun `release verification rejects missing extra and changed assets`() {
        val release = materialize(listOf(packageArchive("alpha-tools", "alpha")))
        val files = release.files()
        val checksum = files.single { file -> file.name.render() == "SHA256SUMS" }
        assertEquals(
            MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.MissingAsset(checksum.name),
            ),
            release.verify(files.filterNot { file -> file.name == checksum.name }),
        )

        val extra = releaseFile("notes.txt", "notes\n".encodeToByteArray())
        assertEquals(
            MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.UnexpectedAsset(extra.name),
            ),
            release.verify(files + extra),
        )

        val index = files.single { file -> file.name.render() == "marketplace.json" }
        val changed = releaseFile("marketplace.json", index.bytes() + ' '.code.toByte())
        assertEquals(
            MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.SizeMismatch(
                    name = index.name,
                    expectedBytes = index.byteSize,
                    actualBytes = changed.byteSize,
                ),
            ),
            release.verify(files.map { file -> if (file.name == index.name) changed else file }),
        )
    }

    @Test
    fun `release bytes and file lists are immutable copies`() {
        val release = materialize(listOf(packageArchive("alpha-tools", "alpha")))
        val expected = release.checksumBytes()
        val mutable = release.checksumBytes()
        mutable.fill(0)
        val returned = release.files().toMutableList()
        returned.clear()

        assertContentEquals(expected, release.checksumBytes())
        assertEquals(5, release.files().size)
    }

    @Test
    fun `release reports invalid defaults through the snapshot boundary`() {
        val alpha = packageArchive("alpha-tools", "alpha")
        assertEquals(
            MarketplaceReleaseMaterialization.Rejected(
                MarketplaceReleaseRejection.SnapshotIndexRejected(
                    MarketplaceSnapshotIndexRejection.DefaultPackageMissing(packageName("missing")),
                ),
            ),
            MarketplaceRelease.materialize(
                marketplaceId("example-marketplace"),
                snapshotId("snapshot-one"),
                packageName("missing"),
                listOf(alpha),
            ),
        )
    }

    private fun materialize(packages: List<PackageArchive>): MarketplaceRelease =
        assertIs<MarketplaceReleaseMaterialization.Materialized>(
            MarketplaceRelease.materialize(
                marketplaceId("example-marketplace"),
                snapshotId("snapshot-one"),
                packageName("alpha-tools"),
                packages,
            ),
        ).release

    private fun expectedChecksums(release: MarketplaceRelease): String =
        release.files()
            .filterNot { file -> file.name.render() == "SHA256SUMS" }
            .joinToString(separator = "") { file ->
                "${file.sha256.render()}  ${file.name.render()}\n"
            }

    private fun assertReleaseFilesEqual(
        expected: List<ReleaseFile>,
        actual: List<ReleaseFile>,
    ) {
        assertEquals(expected.map(ReleaseFile::name), actual.map(ReleaseFile::name))
        expected.zip(actual).forEach { (left, right) ->
            assertContentEquals(left.bytes(), right.bytes())
        }
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
        val source =
            assertIs<PackageSourceFileCreation.Created>(
                PackageSourceFile.create(path(skillPath), skillBytes, executable = false),
            ).file
        return assertIs<PackageArchiveMaterialization.Materialized>(
            PackageArchive.materialize(manifest, listOf(source)),
        ).archive
    }

    private fun releaseFile(
        name: String,
        bytes: ByteArray,
    ): ReleaseFile =
        assertIs<ReleaseFileCreation.Created>(
            ReleaseFile.create(releaseAsset(name), bytes),
        ).file

    private fun releaseAsset(raw: String): ReleaseAssetName =
        assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse(raw)).name

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value
}
