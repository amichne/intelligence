package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarketplaceLockTest {
    @Test
    fun `local and immutable GitHub evidence materialize one canonical lock`() {
        val local = localEntry("alpha-marketplace", "fixtures/alpha", listOf("alpha-tools"))
        val github = githubEntry("zeta-marketplace", "zeta", listOf("zeta-tools", "alpha-tools"))

        val forward = materialize(listOf(github, local))
        val reversed = materialize(listOf(local, github))
        assertContentEquals(forward.canonicalBytes(), reversed.canonicalBytes())
        assertEquals(listOf("alpha-marketplace", "zeta-marketplace"), forward.entries.map {
            it.marketplaceId.render()
        })
        assertEquals(listOf("alpha-tools", "zeta-tools"), forward.entries[1].packages.map {
            it.name.render()
        })

        val reparsed = assertIs<MarketplaceLockParsing.Parsed>(
            MarketplaceLock.parse(forward.canonicalBytes()),
        ).lock
        assertContentEquals(forward.canonicalBytes(), reparsed.canonicalBytes())
        assertEquals(forward.sha256(), reparsed.sha256())
        assertEquals(MarketplaceLockAgreement.Matched, forward.agreement(intentFor(forward)))
    }

    @Test
    fun `lock agreement reports package source and selection drift`() {
        val lock = materialize(listOf(localEntry("alpha-marketplace", "fixtures/alpha", listOf("alpha-tools"))))
        val sourceChanged =
            intent(
                selection(
                    "alpha-marketplace",
                    MarketplaceIntentSource.LocalSnapshot(
                        relativeDirectory("fixtures/beta"),
                        lock.entries.single().index.sha256,
                    ),
                    listOf("alpha-tools"),
                ),
            )
        assertEquals(
            MarketplaceLockAgreement.Stale(
                MarketplaceLockStaleness.SourceMismatch(marketplaceId("alpha-marketplace")),
            ),
            lock.agreement(sourceChanged),
        )

        val packageChanged =
            intent(
                selection(
                    "alpha-marketplace",
                    localIntentSource(lock.entries.single()),
                    listOf("beta-tools"),
                ),
            )
        assertEquals(
            MarketplaceLockAgreement.Stale(
                MarketplaceLockStaleness.PackageSetMismatch(
                    marketplaceId = marketplaceId("alpha-marketplace"),
                    expected = listOf(packageName("beta-tools")),
                    actual = listOf(packageName("alpha-tools")),
                ),
            ),
            lock.agreement(packageChanged),
        )

        val additional =
            intent(
                selection(
                    "alpha-marketplace",
                    localIntentSource(lock.entries.single()),
                    listOf("alpha-tools"),
                ),
                selection(
                    "beta-marketplace",
                    MarketplaceIntentSource.LocalSnapshot(
                        relativeDirectory("fixtures/beta"),
                        digest("beta-index"),
                    ),
                    listOf("beta-tools"),
                ),
            )
        assertEquals(
            MarketplaceLockAgreement.Stale(
                MarketplaceLockStaleness.MarketplaceSetMismatch(
                    expected = listOf(marketplaceId("alpha-marketplace"), marketplaceId("beta-marketplace")),
                    actual = listOf(marketplaceId("alpha-marketplace")),
                ),
            ),
            lock.agreement(additional),
        )
    }

    @Test
    fun `lock entry rejects mixed evidence duplicate packages and wrong archive identity`() {
        val source = MarketplaceLockSource.LocalSnapshot(relativeDirectory("fixtures/alpha"))
        val index = localAsset("marketplace.json", "index")
        val checksum = localAsset("SHA256SUMS", "checksums")
        val alpha = lockedPackage("alpha-tools", localAsset("package-alpha-tools.zip", "alpha"))
        val githubAlpha = lockedPackage("alpha-tools", githubAsset(30, "package-alpha-tools.zip", "alpha"))

        assertEquals(
            MarketplaceLockEntryCreation.Rejected(
                MarketplaceLockEntryRejection.MixedAssetEvidence(ReleaseAssetName.snapshotIndex()),
            ),
            MarketplaceLockEntry.create(
                marketplaceId("alpha-marketplace"),
                source,
                githubAsset(10, "marketplace.json", "index"),
                checksum,
                listOf(alpha),
            ),
        )
        assertEquals(
            MarketplaceLockEntryCreation.Rejected(
                MarketplaceLockEntryRejection.DuplicatePackage(packageName("alpha-tools")),
            ),
            MarketplaceLockEntry.create(
                marketplaceId("alpha-marketplace"),
                source,
                index,
                checksum,
                listOf(alpha, alpha),
            ),
        )
        assertEquals(
            MarketplaceLockEntryCreation.Rejected(
                MarketplaceLockEntryRejection.MixedAssetEvidence(githubAlpha.archive.name),
            ),
            MarketplaceLockEntry.create(
                marketplaceId("alpha-marketplace"),
                source,
                index,
                checksum,
                listOf(githubAlpha),
            ),
        )
        val wrong = lockedPackage("alpha-tools", localAsset("package-beta-tools.zip", "alpha"))
        assertEquals(
            MarketplaceLockEntryCreation.Rejected(
                MarketplaceLockEntryRejection.UnexpectedPackageArchiveName(
                    packageName("alpha-tools"),
                    ReleaseAssetName.packageArchive(packageName("alpha-tools")),
                    wrong.archive.name,
                ),
            ),
            MarketplaceLockEntry.create(
                marketplaceId("alpha-marketplace"),
                source,
                index,
                checksum,
                listOf(wrong),
            ),
        )
    }

    @Test
    fun `lock parser rejects false immutability unknown fields and noncanonical arrays`() {
        val lock = materialize(listOf(githubEntry("alpha-marketplace", "alpha", listOf("alpha-tools"))))
        val valid = lock.canonicalBytes().decodeToString()
        assertEquals(
            MarketplaceLockRejection.ImmutableReleaseRequired(0),
            rejected(valid.replace("\"immutable\":true", "\"immutable\":false")),
        )
        assertEquals(
            MarketplaceLockRejection.UnknownField("$", "cachePath"),
            rejected(valid.replaceFirst("{", "{\"cachePath\":\"/tmp\",")),
        )
        assertEquals(
            MarketplaceLockRejection.NonCanonicalJson,
            rejected(valid.replace("{\"entries\"", "{ \"entries\"")),
        )
        assertEquals(
            MarketplaceLockRejection.MalformedJson,
            rejected(valid.replace("\"type\":\"MARKETPLACE_LOCK\"", "\"type\":\"MARKETPLACE_LOCK\",\"type\":\"MARKETPLACE_LOCK\"")),
        )
    }

    @Test
    fun `lock owns immutable canonical bytes`() {
        val lock = materialize(listOf(localEntry("alpha-marketplace", "fixtures/alpha", listOf("alpha-tools"))))
        val bytes = lock.canonicalBytes()
        bytes.fill(0)
        assertEquals('{', lock.canonicalBytes().decodeToString().first())
    }

    private fun githubEntry(
        marketplace: String,
        repository: String,
        packages: List<String>,
    ): MarketplaceLockEntry {
        val source =
            MarketplaceLockSource.GitHubRelease(
                githubUrl("https://github.com/example/$repository"),
                snapshotId("snapshot-one"),
                releaseId(100),
                commitSha("0123456789abcdef0123456789abcdef01234567"),
            )
        return entry(
            marketplace,
            source,
            githubAsset(10, "marketplace.json", "$marketplace-index"),
            githubAsset(20, "SHA256SUMS", "$marketplace-checksum"),
            packages.mapIndexed { index, name ->
                lockedPackage(name, githubAsset(30L + index, "package-$name.zip", "$marketplace-$name"))
            },
        )
    }

    private fun localEntry(
        marketplace: String,
        path: String,
        packages: List<String>,
    ): MarketplaceLockEntry =
        entry(
            marketplace,
            MarketplaceLockSource.LocalSnapshot(relativeDirectory(path)),
            localAsset("marketplace.json", "$marketplace-index"),
            localAsset("SHA256SUMS", "$marketplace-checksum"),
            packages.map { name -> lockedPackage(name, localAsset("package-$name.zip", "$marketplace-$name")) },
        )

    private fun entry(
        marketplace: String,
        source: MarketplaceLockSource,
        index: LockedAsset,
        checksum: LockedAsset,
        packages: List<LockedPackage>,
    ): MarketplaceLockEntry =
        assertIs<MarketplaceLockEntryCreation.Created>(
            MarketplaceLockEntry.create(marketplaceId(marketplace), source, index, checksum, packages),
        ).entry

    private fun lockedPackage(
        name: String,
        archive: LockedAsset,
    ): LockedPackage = LockedPackage(packageName(name), archive)

    private fun localAsset(
        name: String,
        content: String,
    ): LockedAsset.Local =
        LockedAsset.Local(releaseAsset(name), content.length, digest(content))

    private fun githubAsset(
        id: Long,
        name: String,
        content: String,
    ): LockedAsset.GitHub =
        LockedAsset.GitHub(assetId(id), releaseAsset(name), content.length, digest(content))

    private fun materialize(entries: List<MarketplaceLockEntry>): MarketplaceLock =
        assertIs<MarketplaceLockMaterialization.Materialized>(MarketplaceLock.materialize(entries)).lock

    private fun rejected(document: String): MarketplaceLockRejection =
        assertIs<MarketplaceLockParsing.Rejected>(MarketplaceLock.parse(document.encodeToByteArray())).reason

    private fun intentFor(lock: MarketplaceLock): MarketplaceIntent =
        intent(*lock.entries.map { entry ->
            selection(
                entry.marketplaceId.render(),
                when (val source = entry.source) {
                    is MarketplaceLockSource.GitHubRelease ->
                        MarketplaceIntentSource.GitHubRelease(source.repository, source.tag)
                    is MarketplaceLockSource.LocalSnapshot -> localIntentSource(entry)
                },
                entry.packages.map { it.name.render() },
            )
        }.toTypedArray())

    private fun localIntentSource(entry: MarketplaceLockEntry): MarketplaceIntentSource.LocalSnapshot =
        MarketplaceIntentSource.LocalSnapshot(
            (entry.source as MarketplaceLockSource.LocalSnapshot).directory,
            entry.index.sha256,
        )

    private fun intent(vararg selections: MarketplaceIntentSelection): MarketplaceIntent =
        assertIs<MarketplaceIntentMaterialization.Materialized>(
            MarketplaceIntent.materialize(selections.toList()),
        ).intent

    private fun selection(
        marketplace: String,
        source: MarketplaceIntentSource,
        packages: List<String>,
    ): MarketplaceIntentSelection =
        assertIs<MarketplaceIntentSelectionCreation.Created>(
            MarketplaceIntentSelection.create(
                marketplaceId(marketplace),
                source,
                packages.map(::packageName),
            ),
        ).selection

    private fun digest(text: String): Sha256Digest = Sha256Digest.compute(text.encodeToByteArray())

    private fun githubUrl(raw: String): GitHubRepositoryUrl =
        assertIs<GitHubRepositoryUrlParsing.Parsed>(GitHubRepositoryUrl.parse(raw)).url

    private fun relativeDirectory(raw: String): ConsumerRelativeDirectory =
        assertIs<ConsumerRelativeDirectoryParsing.Parsed>(ConsumerRelativeDirectory.parse(raw)).directory

    private fun releaseAsset(raw: String): ReleaseAssetName =
        assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse(raw)).name

    private fun releaseId(value: Long): GitHubReleaseId =
        assertIs<GitHubReleaseIdParsing.Parsed>(GitHubReleaseId.parse(value)).id

    private fun assetId(value: Long): GitHubAssetId =
        assertIs<GitHubAssetIdParsing.Parsed>(GitHubAssetId.parse(value)).id

    private fun commitSha(raw: String): GitCommitSha =
        assertIs<GitCommitShaParsing.Parsed>(GitCommitSha.parse(raw)).sha

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value
}
