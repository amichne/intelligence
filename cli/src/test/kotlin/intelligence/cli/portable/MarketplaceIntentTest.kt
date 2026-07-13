package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarketplaceIntentTest {
    @Test
    fun `shuffled exact selections materialize one canonical intent`() {
        val localDigest = Sha256Digest.compute("local-index\n".encodeToByteArray())
        val github =
            selection(
                marketplace = "zeta-marketplace",
                source = githubSource("https://github.com/example/zeta", "snapshot-two"),
                packages = listOf("zeta-tools", "alpha-tools"),
            )
        val local =
            selection(
                marketplace = "alpha-marketplace",
                source = localSource("fixtures/snapshot-one", localDigest),
                packages = listOf("review-tools"),
            )

        val forward = materialize(listOf(github, local))
        val reversed = materialize(listOf(local, github))
        assertContentEquals(forward.canonicalBytes(), reversed.canonicalBytes())
        assertEquals(
            "{\"schemaVersion\":1,\"selections\":[" +
                "{\"marketplaceId\":\"alpha-marketplace\",\"packages\":[\"review-tools\"]," +
                "\"source\":{\"indexSha256\":\"${localDigest.render()}\",\"path\":\"fixtures/snapshot-one\"," +
                "\"type\":\"LOCAL_SNAPSHOT\"}}," +
                "{\"marketplaceId\":\"zeta-marketplace\",\"packages\":[\"alpha-tools\",\"zeta-tools\"]," +
                "\"source\":{\"repository\":\"https://github.com/example/zeta\",\"tag\":\"snapshot-two\"," +
                "\"type\":\"GITHUB_RELEASE\"}}],\"type\":\"MARKETPLACE_INTENT\"}\n",
            forward.canonicalBytes().decodeToString(),
        )
        assertEquals(listOf("alpha-marketplace", "zeta-marketplace"), forward.selections.map {
            it.marketplaceId.render()
        })
        assertEquals(listOf("alpha-tools", "zeta-tools"), forward.selections[1].packages.map(PackageName::render))

        val reparsed = assertIs<MarketplaceIntentParsing.Parsed>(
            MarketplaceIntent.parse(forward.canonicalBytes()),
        ).intent
        assertContentEquals(forward.canonicalBytes(), reparsed.canonicalBytes())
        assertEquals(forward.sha256(), reparsed.sha256())
    }

    @Test
    fun `intent construction rejects empty duplicate and oversized selections`() {
        assertEquals(
            MarketplaceIntentMaterialization.Rejected(MarketplaceIntentRejection.NoSelections),
            MarketplaceIntent.materialize(emptyList()),
        )
        val alpha =
            selection(
                "alpha-marketplace",
                githubSource("https://github.com/example/alpha", "snapshot-one"),
                listOf("alpha-tools"),
            )
        assertEquals(
            MarketplaceIntentMaterialization.Rejected(
                MarketplaceIntentRejection.DuplicateMarketplace(alpha.marketplaceId),
            ),
            MarketplaceIntent.materialize(listOf(alpha, alpha)),
        )
        assertEquals(
            MarketplaceIntentSelectionCreation.Rejected(MarketplaceIntentSelectionRejection.NoPackages),
            MarketplaceIntentSelection.create(
                marketplaceId("alpha-marketplace"),
                githubSource("https://github.com/example/alpha", "snapshot-one"),
                emptyList(),
            ),
        )
        val selectedPackage = packageName("alpha-tools")
        assertEquals(
            MarketplaceIntentSelectionCreation.Rejected(
                MarketplaceIntentSelectionRejection.DuplicatePackage(selectedPackage),
            ),
            MarketplaceIntentSelection.create(
                marketplaceId("alpha-marketplace"),
                githubSource("https://github.com/example/alpha", "snapshot-one"),
                listOf(selectedPackage, selectedPackage),
            ),
        )
    }

    @Test
    fun `source boundaries reject noncanonical GitHub and nonportable local locators`() {
        assertEquals(
            GitHubRepositoryUrlParsing.Rejected(GitHubRepositoryUrlRejection.NonCanonical),
            GitHubRepositoryUrl.parse("https://github.com/Example/repo"),
        )
        assertEquals(
            GitHubRepositoryUrlParsing.Rejected(GitHubRepositoryUrlRejection.NonCanonical),
            GitHubRepositoryUrl.parse("https://github.com/example/repo.git"),
        )
        assertEquals(
            ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.Absolute),
            ConsumerRelativeDirectory.parse("/tmp/snapshot"),
        )
        assertEquals(
            ConsumerRelativeDirectoryParsing.Rejected(ConsumerRelativeDirectoryRejection.DotSegment),
            ConsumerRelativeDirectory.parse("fixtures/../snapshot"),
        )
    }

    @Test
    fun `intent parser rejects unknown noncanonical and invalid source documents`() {
        val valid =
            materialize(
                listOf(
                    selection(
                        "alpha-marketplace",
                        githubSource("https://github.com/example/alpha", "snapshot-one"),
                        listOf("alpha-tools"),
                    ),
                ),
            ).canonicalBytes().decodeToString()
        assertEquals(
            MarketplaceIntentRejection.NonCanonicalJson,
            rejected(valid.replace("{\"schemaVersion\"", "{ \"schemaVersion\"")),
        )
        assertEquals(
            MarketplaceIntentRejection.UnknownField("$", "latest"),
            rejected(valid.replaceFirst("{", "{\"latest\":true,")),
        )
        assertEquals(
            MarketplaceIntentRejection.UnsupportedSchemaVersion(2),
            rejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")),
        )
        assertEquals(
            MarketplaceIntentRejection.MalformedJson,
            rejected(valid.replace("\"type\":\"MARKETPLACE_INTENT\"", "\"type\":\"MARKETPLACE_INTENT\",\"type\":\"MARKETPLACE_INTENT\"")),
        )
        assertEquals(
            MarketplaceIntentRejection.InvalidGitHubRepository(
                selectionIndex = 0,
                reason = GitHubRepositoryUrlRejection.NonCanonical,
            ),
            rejected(valid.replace("https://github.com/example/alpha", "https://github.com/Example/alpha")),
        )
        val local =
            "{\"schemaVersion\":1,\"selections\":[{\"marketplaceId\":\"alpha-marketplace\"," +
                "\"packages\":[\"alpha-tools\"],\"source\":{\"indexSha256\":" +
                "\"${Sha256Digest.compute("index".encodeToByteArray()).render()}\",\"path\":\"/tmp/snapshot\"," +
                "\"type\":\"LOCAL_SNAPSHOT\"}}],\"type\":\"MARKETPLACE_INTENT\"}\n"
        assertEquals(
            MarketplaceIntentRejection.InvalidLocalDirectory(
                selectionIndex = 0,
                reason = ConsumerRelativeDirectoryRejection.Absolute,
            ),
            rejected(local),
        )
    }

    @Test
    fun `intent owns immutable canonical byte copies`() {
        val intent =
            materialize(
                listOf(
                    selection(
                        "alpha-marketplace",
                        githubSource("https://github.com/example/alpha", "snapshot-one"),
                        listOf("alpha-tools"),
                    ),
                ),
            )
        val bytes = intent.canonicalBytes()
        bytes.fill(0)
        assertEquals('{', intent.canonicalBytes().decodeToString().first())
    }

    private fun rejected(document: String): MarketplaceIntentRejection =
        assertIs<MarketplaceIntentParsing.Rejected>(
            MarketplaceIntent.parse(document.encodeToByteArray()),
        ).reason

    private fun materialize(selections: List<MarketplaceIntentSelection>): MarketplaceIntent =
        assertIs<MarketplaceIntentMaterialization.Materialized>(
            MarketplaceIntent.materialize(selections),
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

    private fun githubSource(
        repository: String,
        tag: String,
    ): MarketplaceIntentSource.GitHubRelease =
        MarketplaceIntentSource.GitHubRelease(
            assertIs<GitHubRepositoryUrlParsing.Parsed>(GitHubRepositoryUrl.parse(repository)).url,
            snapshotId(tag),
        )

    private fun localSource(
        path: String,
        digest: Sha256Digest,
    ): MarketplaceIntentSource.LocalSnapshot =
        MarketplaceIntentSource.LocalSnapshot(
            assertIs<ConsumerRelativeDirectoryParsing.Parsed>(ConsumerRelativeDirectory.parse(path)).directory,
            digest,
        )

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value
}
