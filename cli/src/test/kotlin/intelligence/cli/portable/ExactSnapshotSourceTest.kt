package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExactSnapshotSourceTest {
    @Test
    fun `github source requires one repository coordinate and exact snapshot`() {
        val parsed = ExactSnapshotSource.parseGitHub("amichne/intelligence", "initial-snapshot")
        val source = assertIs<ExactSnapshotSourceParsing.Parsed>(parsed).source
        val github = assertIs<ExactSnapshotSource.GitHubRelease>(source)

        assertEquals("amichne/intelligence", github.repository.render())
        assertEquals("initial-snapshot", github.snapshotId.render())
    }

    @Test
    fun `github source rejects URL moving ref and malformed coordinates`() {
        assertIs<ExactSnapshotSourceParsing.Rejected>(
            ExactSnapshotSource.parseGitHub("https://github.com/amichne/intelligence", "initial-snapshot"),
        )
        assertEquals(
            ExactSnapshotSourceRejection.MovingSelectorNotAllowed("latest"),
            assertIs<ExactSnapshotSourceParsing.Rejected>(
                ExactSnapshotSource.parseGitHub("amichne/intelligence", "latest"),
            ).reason,
        )
        assertEquals(
            ExactSnapshotSourceRejection.InvalidRepository(GitHubRepositoryRejection.INVALID_OWNER),
            assertIs<ExactSnapshotSourceParsing.Rejected>(
                ExactSnapshotSource.parseGitHub("-amichne/intelligence", "initial-snapshot"),
            ).reason,
        )
    }

    @Test
    fun `local source carries a portable directory and exact index digest`() {
        val digest = "a".repeat(64)
        val parsed = ExactSnapshotSource.parseLocal(".fixtures/snapshot", digest)
        val source = assertIs<ExactSnapshotSourceParsing.Parsed>(parsed).source
        val local = assertIs<ExactSnapshotSource.LocalSnapshot>(source)

        assertEquals(".fixtures/snapshot", local.directory.render())
        assertEquals(digest, local.indexSha256.render())
    }

    @Test
    fun `local source rejects absolute traversal and noncanonical digest evidence`() {
        val digest = "a".repeat(64)
        assertEquals(
            ExactSnapshotSourceRejection.InvalidLocalDirectory(PackageEntryPathRejection.ABSOLUTE),
            assertIs<ExactSnapshotSourceParsing.Rejected>(
                ExactSnapshotSource.parseLocal("/tmp/snapshot", digest),
            ).reason,
        )
        assertEquals(
            ExactSnapshotSourceRejection.InvalidLocalDirectory(PackageEntryPathRejection.DOT_SEGMENT),
            assertIs<ExactSnapshotSourceParsing.Rejected>(
                ExactSnapshotSource.parseLocal("fixtures/../snapshot", digest),
            ).reason,
        )
        assertEquals(
            ExactSnapshotSourceRejection.InvalidIndexDigest(
                Sha256DigestRejection.InvalidCharacter(index = 0, character = 'A'),
            ),
            assertIs<ExactSnapshotSourceParsing.Rejected>(
                ExactSnapshotSource.parseLocal("fixtures/snapshot", "A" + "a".repeat(63)),
            ).reason,
        )
    }
}
