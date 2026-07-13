package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarketplaceSnapshotIndexTest {
    @Test
    fun `shuffled package and projection inputs produce one canonical snapshot index`() {
        val alpha = packageArchive("alpha", "Alpha package")
        val zeta = packageArchive("zeta", "Zeta package")
        val codex = providerArchive(PortableProvider.CODEX)
        val copilot = providerArchive(PortableProvider.GITHUB_COPILOT)
        val marketplace = marketplaceId("example-marketplace")
        val snapshot = snapshotId("snapshot-one")
        val defaultPackage = packageName("alpha")

        val forward = materializedIndex(
            marketplace,
            snapshot,
            defaultPackage,
            packages = listOf(alpha, zeta),
            projections = listOf(codex, copilot),
        )
        val reversed = materializedIndex(
            marketplace,
            snapshot,
            defaultPackage,
            packages = listOf(zeta, alpha),
            projections = listOf(copilot, codex),
        )

        assertContentEquals(forward.canonicalBytes(), reversed.canonicalBytes())
        assertEquals(forward.sha256(), reversed.sha256())
        assertEquals("74f4738c2a61ee977980b8ae2f55778fa649e70d7c6ea6bd83e3a39832bb8760", forward.sha256().render())
        assertEquals(expectedIndex(forward), forward.canonicalBytes().decodeToString())
        assertEquals(listOf("alpha", "zeta"), forward.packages.map { it.name.render() })
        assertEquals(
            listOf("codex", "github-copilot"),
            forward.projections.map { it.provider.render() },
        )

        val reparsed = assertIs<MarketplaceSnapshotIndexParsing.Parsed>(
            MarketplaceSnapshotIndex.parse(forward.canonicalBytes()),
        ).index
        assertContentEquals(forward.canonicalBytes(), reparsed.canonicalBytes())
        assertEquals(forward.sha256(), reparsed.sha256())
    }

    @Test
    fun `snapshot index requires packages and an existing default package`() {
        val marketplace = marketplaceId("example-marketplace")
        val snapshot = snapshotId("snapshot-one")
        val alpha = packageArchive("alpha", "Alpha package")
        val projections = allProviderArchives()

        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(MarketplaceSnapshotIndexRejection.NoPackages),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                packageName("alpha"),
                packages = emptyList(),
                projections = projections,
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.DefaultPackageMissing(packageName("missing")),
            ),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                packageName("missing"),
                packages = listOf(alpha),
                projections = projections,
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.DuplicatePackage(packageName("alpha")),
            ),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                packageName("alpha"),
                packages = listOf(alpha, alpha),
                projections = projections,
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.TooManyPackages(
                    actual = MAX_PACKAGES_PER_SNAPSHOT + 1,
                    maximum = MAX_PACKAGES_PER_SNAPSHOT,
                ),
            ),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                packageName("alpha"),
                packages = List(MAX_PACKAGES_PER_SNAPSHOT + 1) { alpha },
                projections = projections,
            ),
        )
    }

    @Test
    fun `snapshot index rejects a package from another marketplace`() {
        val expectedMarketplace = marketplaceId("expected-marketplace")
        val actualMarketplace = marketplaceId("other-marketplace")
        val foreign = packageArchive("alpha", "Alpha package", actualMarketplace)

        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.PackageMarketplaceMismatch(
                    packageName = packageName("alpha"),
                    expected = expectedMarketplace,
                    actual = actualMarketplace,
                ),
            ),
            MarketplaceSnapshotIndex.materialize(
                expectedMarketplace,
                snapshotId("snapshot-one"),
                packageName("alpha"),
                packages = listOf(foreign),
                projections = allProviderArchives(),
            ),
        )
    }

    @Test
    fun `snapshot index requires exactly one archive for each portable provider`() {
        val marketplace = marketplaceId("example-marketplace")
        val snapshot = snapshotId("snapshot-one")
        val defaultPackage = packageName("alpha")
        val packages = listOf(packageArchive("alpha", "Alpha package"))
        val codex = providerArchive(PortableProvider.CODEX)

        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.MissingProvider(PortableProvider.GITHUB_COPILOT),
            ),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                defaultPackage,
                packages,
                projections = listOf(codex),
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexMaterialization.Rejected(
                MarketplaceSnapshotIndexRejection.DuplicateProvider(PortableProvider.CODEX),
            ),
            MarketplaceSnapshotIndex.materialize(
                marketplace,
                snapshot,
                defaultPackage,
                packages,
                projections = listOf(codex, codex, providerArchive(PortableProvider.GITHUB_COPILOT)),
            ),
        )
    }

    @Test
    fun `snapshot parser rejects non-canonical unknown and unsupported documents`() {
        val valid = canonicalIndexText()

        assertEquals(
            MarketplaceSnapshotIndexRejection.NonCanonicalJson,
            rejected(valid.replace("{\"checksumAsset\"", "{ \"checksumAsset\"")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.UnknownField(path = "$", field = "branch"),
            rejected(valid.replaceFirst("{", "{\"branch\":\"main\",")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.UnsupportedType("LEGACY_MARKETPLACE"),
            rejected(valid.replace("INTELLIGENCE_MARKETPLACE_SNAPSHOT", "LEGACY_MARKETPLACE")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.UnsupportedSchemaVersion(2),
            rejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")),
        )
    }

    @Test
    fun `snapshot parser enforces exact package projection and asset evidence`() {
        val index = canonicalIndex()
        val valid = index.canonicalBytes().decodeToString()
        val packageRecord = index.packages.single()
        val codex = index.projections[0]
        val copilot = index.projections[1]

        assertEquals(
            MarketplaceSnapshotIndexRejection.DefaultPackageMissing(packageName("missing")),
            rejected(valid.replace("\"defaultPackage\":\"alpha\"", "\"defaultPackage\":\"missing\"")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.UnexpectedChecksumAsset(
                expected = index.checksumAsset,
                actual = assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse("CHECKSUMS")).name,
            ),
            rejected(valid.replace("SHA256SUMS", "CHECKSUMS")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.UnexpectedAssetName(
                path = "$.packages[0].archive.name",
                expected = packageRecord.archive.name,
                actual = assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse("package-other.zip")).name,
            ),
            rejected(valid.replace("package-alpha.zip", "package-other.zip")),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.InvalidAssetSize(
                path = "$.packages[0].archive.size",
                actualBytes = 0,
                maximumBytes = MAX_RELEASE_ARTIFACT_BYTES,
            ),
            rejected(
                valid.replaceFirst(
                    "\"size\":${packageRecord.archive.byteSize}",
                    "\"size\":0",
                ),
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.InvalidAssetSize(
                path = "$.packages[0].archive.size",
                actualBytes = MAX_RELEASE_ARTIFACT_BYTES.toLong() + 1,
                maximumBytes = MAX_RELEASE_ARTIFACT_BYTES,
            ),
            rejected(
                valid.replaceFirst(
                    "\"size\":${packageRecord.archive.byteSize}",
                    "\"size\":${MAX_RELEASE_ARTIFACT_BYTES.toLong() + 1}",
                ),
            ),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.InvalidDigest(
                path = "$.packages[0].archive.sha256",
                reason = Sha256DigestRejection.InvalidCharacter(index = 0, character = 'B'),
            ),
            rejected(
                valid.replace(
                    packageRecord.archive.sha256.render(),
                    "B${packageRecord.archive.sha256.render().drop(1)}",
                ),
            ),
        )

        val withoutCopilot = valid.replace(",${projectionJson(copilot)}", "")
        assertEquals(
            MarketplaceSnapshotIndexRejection.MissingProvider(PortableProvider.GITHUB_COPILOT),
            rejected(withoutCopilot),
        )
        val reversedProjections =
            valid.replace(
                "[${projectionJson(codex)},${projectionJson(copilot)}]",
                "[${projectionJson(copilot)},${projectionJson(codex)}]",
            )
        assertEquals(
            MarketplaceSnapshotIndexRejection.ProjectionsNotCanonical,
            rejected(reversedProjections),
        )
    }

    @Test
    fun `snapshot parser rejects non-canonical package order and duplicate asset digests`() {
        val alpha = packageArchive("alpha", "Alpha package")
        val zeta = packageArchive("zeta", "Zeta package")
        val index =
            materializedIndex(
                marketplaceId("example-marketplace"),
                snapshotId("snapshot-one"),
                packageName("alpha"),
                packages = listOf(alpha, zeta),
                projections = allProviderArchives(),
            )
        val valid = index.canonicalBytes().decodeToString()
        val first = index.packages[0]
        val second = index.packages[1]
        val reversedPackages =
            valid.replace(
                "[${packageJson(first)},${packageJson(second)}]",
                "[${packageJson(second)},${packageJson(first)}]",
            )
        assertEquals(
            MarketplaceSnapshotIndexRejection.PackagesNotCanonical,
            rejected(reversedPackages),
        )

        val projection = index.projections.first()
        assertEquals(
            MarketplaceSnapshotIndexRejection.DuplicateAssetDigest(first.archive.sha256),
            rejected(valid.replace(projection.archive.sha256.render(), first.archive.sha256.render())),
        )
    }

    @Test
    fun `snapshot parser bounds document bytes and requires UTF-8 object roots`() {
        assertEquals(
            MarketplaceSnapshotIndexRejection.InvalidUtf8,
            assertIs<MarketplaceSnapshotIndexParsing.Rejected>(
                MarketplaceSnapshotIndex.parse(byteArrayOf(0xc3.toByte())),
            ).reason,
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.RootMustBeObject,
            rejected("[]\n"),
        )
        assertEquals(
            MarketplaceSnapshotIndexRejection.JsonDocumentTooLarge(
                actualBytes = MAX_MARKETPLACE_SNAPSHOT_JSON_BYTES.toLong() + 1,
                maximumBytes = MAX_MARKETPLACE_SNAPSHOT_JSON_BYTES,
            ),
            assertIs<MarketplaceSnapshotIndexParsing.Rejected>(
                MarketplaceSnapshotIndex.parse(ByteArray(MAX_MARKETPLACE_SNAPSHOT_JSON_BYTES + 1)),
            ).reason,
        )
    }

    @Test
    fun `snapshot index and provider archive return immutable byte copies`() {
        val index = canonicalIndex()
        val expectedIndexBytes = index.canonicalBytes()
        val mutableIndexBytes = index.canonicalBytes()
        mutableIndexBytes.fill(0)
        assertContentEquals(expectedIndexBytes, index.canonicalBytes())

        val artifact = providerArchive(PortableProvider.CODEX)
        val expectedArchiveBytes = artifact.bytes()
        val mutableArchiveBytes = artifact.bytes()
        mutableArchiveBytes.fill(0)
        assertContentEquals(expectedArchiveBytes, artifact.bytes())
    }

    private fun canonicalIndex(): MarketplaceSnapshotIndex =
        materializedIndex(
            marketplaceId("example-marketplace"),
            snapshotId("snapshot-one"),
            packageName("alpha"),
            packages = listOf(packageArchive("alpha", "Alpha package")),
            projections = allProviderArchives(),
        )

    private fun canonicalIndexText(): String = canonicalIndex().canonicalBytes().decodeToString()

    private fun rejected(document: String): MarketplaceSnapshotIndexRejection =
        assertIs<MarketplaceSnapshotIndexParsing.Rejected>(
            MarketplaceSnapshotIndex.parse(document.encodeToByteArray()),
        ).reason

    private fun expectedIndex(index: MarketplaceSnapshotIndex): String {
        val packages =
            index.packages.joinToString(separator = ",", transform = ::packageJson)
        val projections =
            index.projections.joinToString(separator = ",", transform = ::projectionJson)
        return "{\"checksumAsset\":\"SHA256SUMS\",\"defaultPackage\":\"${index.defaultPackage.render()}\"," +
            "\"marketplaceId\":\"${index.marketplaceId.render()}\",\"packages\":[$packages],\"projections\":[$projections]," +
            "\"schemaVersion\":1,\"snapshotId\":\"${index.snapshotId.render()}\"," +
            "\"type\":\"INTELLIGENCE_MARKETPLACE_SNAPSHOT\"}\n"
    }

    private fun assetJson(asset: SnapshotAssetEvidence): String =
        "{\"name\":\"${asset.name.render()}\",\"sha256\":\"${asset.sha256.render()}\",\"size\":${asset.byteSize}}"

    private fun packageJson(record: SnapshotPackageRecord): String =
        "{\"archive\":${assetJson(record.archive)},\"description\":\"${record.description.render()}\"," +
            "\"name\":\"${record.name.render()}\",\"tags\":[${record.tags.joinToString(",") { tag -> "\"${tag.render()}\"" }}]}"

    private fun projectionJson(record: SnapshotProjectionRecord): String =
        "{\"archive\":${assetJson(record.archive)},\"provider\":\"${record.provider.render()}\"}"

    private fun packageArchive(
        rawName: String,
        description: String,
        marketplace: MarketplaceId = marketplaceId("example-marketplace"),
    ): PackageArchive {
        val skillBytes =
            "---\nname: review\ndescription: \"Review code\"\n---\n\nReview the code.\n".encodeToByteArray()
        val scriptBytes = "#!/bin/sh\nexit 0\n".encodeToByteArray()
        val manifestText =
            "{\"description\":\"$description\",\"marketplaceId\":\"${marketplace.render()}\",\"name\":\"$rawName\"," +
                "\"schemaVersion\":1,\"skills\":[{\"assets\":[{\"executable\":true," +
                "\"path\":\"skills/review/scripts/check.sh\",\"sha256\":\"${Sha256Digest.compute(scriptBytes).render()}\"," +
                "\"size\":${scriptBytes.size}}],\"description\":\"Review code\",\"name\":\"review\"," +
                "\"primary\":{\"executable\":false,\"path\":\"skills/review/SKILL.md\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[\"kotlin\"],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"
        val manifest = assertIs<PackageManifestParsing.Parsed>(
            PackageManifest.parse(manifestText.encodeToByteArray()),
        ).manifest
        val files =
            listOf(
                sourceFile("skills/review/SKILL.md", skillBytes, executable = false),
                sourceFile("skills/review/scripts/check.sh", scriptBytes, executable = true),
            )
        return assertIs<PackageArchiveMaterialization.Materialized>(
            PackageArchive.materialize(manifest, files),
        ).archive
    }

    private fun providerArchive(provider: PortableProvider): ProviderArchiveArtifact {
        val path = path("${provider.render()}.txt")
        val entry = assertIs<CanonicalZipEntryCreation.Accepted>(
            CanonicalZipEntry.create(path, provider.render().encodeToByteArray(), CanonicalZipEntryMode.REGULAR),
        ).entry
        val archive = assertIs<CanonicalZipCreation.Created>(
            CanonicalZipArchive.create(listOf(entry)),
        ).archive
        return ProviderArchiveArtifact.fromCanonicalArchive(provider, archive)
    }

    private fun allProviderArchives(): List<ProviderArchiveArtifact> =
        listOf(
            providerArchive(PortableProvider.CODEX),
            providerArchive(PortableProvider.GITHUB_COPILOT),
        )

    private fun sourceFile(
        rawPath: String,
        bytes: ByteArray,
        executable: Boolean,
    ): PackageSourceFile =
        assertIs<PackageSourceFileCreation.Created>(
            PackageSourceFile.create(path(rawPath), bytes, executable),
        ).file

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value

    private fun materializedIndex(
        marketplaceId: MarketplaceId,
        snapshotId: SnapshotId,
        defaultPackage: PackageName,
        packages: List<PackageArchive>,
        projections: List<ProviderArchiveArtifact>,
    ): MarketplaceSnapshotIndex =
        assertIs<MarketplaceSnapshotIndexMaterialization.Materialized>(
            MarketplaceSnapshotIndex.materialize(
                marketplaceId,
                snapshotId,
                defaultPackage,
                packages,
                projections,
            ),
        ).index
}
