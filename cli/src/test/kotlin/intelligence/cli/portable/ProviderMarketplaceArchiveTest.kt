package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProviderMarketplaceArchiveTest {
    @Test
    fun `two packages assemble into exact deterministic provider marketplaces`() {
        val packages = listOf(packageArchive("zeta-tools", "zeta"), packageArchive("alpha-tools", "alpha"))

        PortableProvider.entries.forEach { provider ->
            val first = materialize(provider, packages)
            val reordered = materialize(provider, packages.reversed())

            assertContentEquals(first.bytes(), reordered.bytes())
            assertEquals(ProviderMarketplaceArchiveVerification.Verified, first.verify(first.bytes()))
            assertEquals(expectedPaths(provider), zipFiles(first).keys.toList())
            assertEquals(expectedCatalog(provider, packages), first.catalogBytes().decodeToString())
            assertEquals(expectedReceipt(provider, packages), first.receiptBytes().decodeToString())
            assertEquals(expectedChecksums(first), first.checksumBytes().decodeToString())
            assertEquals(ReleaseAssetName.providerArchive(provider), first.assetName)
            assertEquals(
                when (provider) {
                    PortableProvider.CODEX -> "10c8ff7ec4d751dc2f479b223658ab20b3257b80724b63a4d299abc0b2211ea4"
                    PortableProvider.GITHUB_COPILOT ->
                        "ba4438a6ff072d4d441e8a4027bf2e7719e5dbd3f99690c446cbe4eab50d2392"
                },
                first.sha256.render(),
            )
        }
    }

    @Test
    fun `materialization rejects empty duplicate and foreign package sets`() {
        val marketplaceId = marketplaceId("example-marketplace")
        val snapshotId = snapshotId("snapshot-one")
        val alpha = packageArchive("alpha-tools", "alpha")
        assertEquals(
            ProviderMarketplaceArchiveMaterialization.Rejected(
                ProviderMarketplaceArchiveRejection.NoPackages,
            ),
            ProviderMarketplaceArchive.materialize(
                marketplaceId,
                snapshotId,
                emptyList(),
                PortableProvider.CODEX,
            ),
        )
        assertEquals(
            ProviderMarketplaceArchiveMaterialization.Rejected(
                ProviderMarketplaceArchiveRejection.DuplicatePackage(alpha.packageName),
            ),
            ProviderMarketplaceArchive.materialize(
                marketplaceId,
                snapshotId,
                listOf(alpha, alpha),
                PortableProvider.CODEX,
            ),
        )

        val foreign = packageArchive("foreign-tools", "foreign", "foreign-marketplace")
        assertEquals(
            ProviderMarketplaceArchiveMaterialization.Rejected(
                ProviderMarketplaceArchiveRejection.PackageMarketplaceMismatch(
                    packageName = foreign.packageName,
                    expected = marketplaceId,
                    actual = foreign.marketplaceId,
                ),
            ),
            ProviderMarketplaceArchive.materialize(
                marketplaceId,
                snapshotId,
                listOf(foreign),
                PortableProvider.GITHUB_COPILOT,
            ),
        )
    }

    @Test
    fun `archive and returned bytes are immutable`() {
        val archive = materialize(PortableProvider.CODEX, listOf(packageArchive("alpha-tools", "alpha")))
        val bytes = archive.bytes()
        val receipt = archive.receiptBytes()
        val secondArchiveBytes = archive.bytes()
        bytes.fill(0)
        receipt.fill(0)
        secondArchiveBytes.fill(0)

        assertEquals('P', archive.bytes().decodeToString().first())
        assertEquals('{', archive.receiptBytes().decodeToString().first())
        assertEquals('P', archive.bytes().decodeToString().first())
    }

    @Test
    fun `verification rejects a canonical archive with an extra file`() {
        val archive = materialize(PortableProvider.CODEX, listOf(packageArchive("alpha-tools", "alpha")))
        val parsed = assertIs<CanonicalZipParsing.Parsed>(CanonicalZipArchive.parse(archive.bytes()))
        val extraPath = path("notes.txt")
        val extra =
            assertIs<CanonicalZipEntryCreation.Accepted>(
                CanonicalZipEntry.create(
                    extraPath,
                    "notes\n".encodeToByteArray(),
                    CanonicalZipEntryMode.REGULAR,
                ),
            ).entry
        val changed =
            assertIs<CanonicalZipCreation.Created>(
                CanonicalZipArchive.create(parsed.entries + extra),
            ).archive

        assertEquals(
            ProviderMarketplaceArchiveVerification.Rejected(
                ProviderMarketplaceArchiveVerificationRejection.InvalidTree(
                    ProviderProjectionRejection.UnexpectedFile(extraPath),
                ),
            ),
            archive.verify(changed.bytes()),
        )
    }

    private fun materialize(
        provider: PortableProvider,
        packages: List<PackageArchive>,
    ): ProviderMarketplaceArchive =
        assertIs<ProviderMarketplaceArchiveMaterialization.Materialized>(
            ProviderMarketplaceArchive.materialize(
                marketplaceId = marketplaceId("example-marketplace"),
                snapshotId = snapshotId("snapshot-one"),
                packages = packages,
                provider = provider,
            ),
        ).archive

    private fun expectedPaths(provider: PortableProvider): List<String> {
        val catalog =
            when (provider) {
                PortableProvider.CODEX -> ".agents/plugins/marketplace.json"
                PortableProvider.GITHUB_COPILOT -> ".github/plugin/marketplace.json"
            }
        val manifest =
            when (provider) {
                PortableProvider.CODEX -> ".codex-plugin/plugin.json"
                PortableProvider.GITHUB_COPILOT -> "plugin.json"
            }
        return (
            listOf(catalog, ".intelligence/checksums.sha256", ".intelligence/projection.json") +
                listOf("alpha", "zeta").flatMap { skill ->
                    val packageName = "$skill-tools"
                    listOf(
                        "plugins/$packageName/.intelligence/checksums.sha256",
                        "plugins/$packageName/.intelligence/projection.json",
                        "plugins/$packageName/$manifest",
                        "plugins/$packageName/skills/$skill/SKILL.md",
                    )
                }
        ).sorted()
    }

    private fun expectedCatalog(
        provider: PortableProvider,
        packages: List<PackageArchive>,
    ): String {
        val entries =
            packages.sortedBy { it.packageName.render() }.joinToString(",") { archive ->
                val name = archive.packageName.render()
                when (provider) {
                    PortableProvider.CODEX ->
                        "{\"category\":\"Productivity\",\"name\":\"$name\"," +
                            "\"policy\":{\"authentication\":\"ON_INSTALL\",\"installation\":\"AVAILABLE\"}," +
                            "\"source\":{\"path\":\"./plugins/$name\",\"source\":\"local\"}}"
                    PortableProvider.GITHUB_COPILOT ->
                        "{\"name\":\"$name\",\"source\":\"./plugins/$name\",\"strict\":true," +
                            "\"version\":\"${ProviderAdapterVersion.fromPackageDigest(archive.sha256).render()}\"}"
                }
            }
        return when (provider) {
            PortableProvider.CODEX ->
                "{\"interface\":{\"displayName\":\"example-marketplace\"}," +
                    "\"name\":\"example-marketplace\",\"plugins\":[$entries]}\n"
            PortableProvider.GITHUB_COPILOT ->
                "{\"name\":\"example-marketplace\",\"owner\":{\"name\":\"example-marketplace\"}," +
                    "\"plugins\":[$entries]}\n"
        }
    }

    private fun expectedReceipt(
        provider: PortableProvider,
        packages: List<PackageArchive>,
    ): String {
        val records =
            packages.sortedBy { it.packageName.render() }.joinToString(",") { archive ->
                val name = archive.packageName.render()
                val projection = ProviderPackageProjection.project(snapshotId("snapshot-one"), archive, provider)
                "{\"adapterVersion\":\"${projection.adapterVersion.render()}\"," +
                    "\"archive\":{\"name\":\"${archive.assetName.render()}\",\"sha256\":\"${archive.sha256.render()}\"," +
                    "\"size\":${archive.byteSize}},\"name\":\"$name\",\"pluginPath\":\"plugins/$name\"," +
                    "\"projectionSha256\":\"${projection.treeDigest().render()}\"}"
            }
        return "{\"generator\":\"intelligence-kotlin-v1\",\"marketplaceId\":\"example-marketplace\"," +
            "\"packages\":[$records],\"provider\":\"${provider.render()}\",\"schemaVersion\":1," +
            "\"snapshotId\":\"snapshot-one\",\"type\":\"INTELLIGENCE_PROVIDER_MARKETPLACE_PROJECTION\"}\n"
    }

    private fun expectedChecksums(archive: ProviderMarketplaceArchive): String =
        zipFiles(archive)
            .filterKeys { path -> path != ".intelligence/checksums.sha256" }
            .entries
            .joinToString(separator = "") { (path, bytes) ->
                "${Sha256Digest.compute(bytes).render()}  $path\n"
            }

    private fun zipFiles(archive: ProviderMarketplaceArchive): Map<String, ByteArray> {
        val parsed = assertIs<CanonicalZipParsing.Parsed>(CanonicalZipArchive.parse(archive.bytes()))
        return parsed.entries.associate { entry -> entry.path.render() to entry.contentCopy() }
    }

    private fun packageArchive(
        packageName: String,
        skillName: String,
        marketplaceName: String = "example-marketplace",
    ): PackageArchive {
        val skillBytes =
            "---\nname: $skillName\ndescription: \"$skillName skill\"\n---\n\nUse $skillName.\n".encodeToByteArray()
        val skillPath = "skills/$skillName/SKILL.md"
        val manifestText =
            "{\"description\":\"$packageName package\",\"marketplaceId\":\"$marketplaceName\"," +
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

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value
}
