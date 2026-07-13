package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class MarketplaceReleaseDirectoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `materialization writes exact staged assets and repeats as an unchanged no-op`() {
        val output = temporaryDirectory.resolve("release")
        val packages = listOf(packageArchive("zeta-tools", "zeta"), packageArchive("alpha-tools", "alpha"))
        val written = assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(
            materialize(output, packages),
        )

        assertEquals(output.toAbsolutePath().normalize(), written.output)
        assertEquals(written.release.files().map { it.name.render() }, Files.list(output).use { stream ->
            stream.map { path -> path.fileName.toString() }.sorted().toList()
        })
        written.release.files().forEach { file ->
            assertContentEquals(file.bytes(), output.resolve(file.name.render()).readBytes())
        }
        assertFalse(hasStagingSibling(output))

        val unchanged = assertIs<MarketplaceReleaseDirectoryMaterialization.Unchanged>(
            materialize(output, packages.reversed()),
        )
        assertEquals(output.toAbsolutePath().normalize(), unchanged.output)
        assertFalse(hasStagingSibling(output))
    }

    @Test
    fun `existing changed missing and additional content is never repaired`() {
        val packages = listOf(packageArchive("alpha-tools", "alpha"))

        val changedOutput = temporaryDirectory.resolve("changed")
        assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(materialize(changedOutput, packages))
        val changedIndex = changedOutput.resolve("marketplace.json")
        val changedBytes = changedIndex.readBytes() + ' '.code.toByte()
        changedIndex.writeBytes(changedBytes)
        assertOutputExists(materialize(changedOutput, packages), changedOutput)
        assertContentEquals(changedBytes, changedIndex.readBytes())

        val missingOutput = temporaryDirectory.resolve("missing")
        assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(materialize(missingOutput, packages))
        Files.delete(missingOutput.resolve("SHA256SUMS"))
        assertOutputExists(materialize(missingOutput, packages), missingOutput)
        assertFalse(Files.exists(missingOutput.resolve("SHA256SUMS")))

        val additionalOutput = temporaryDirectory.resolve("additional")
        assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(materialize(additionalOutput, packages))
        val extra = additionalOutput.resolve("notes.txt")
        extra.writeBytes("notes\n".encodeToByteArray())
        assertOutputExists(materialize(additionalOutput, packages), additionalOutput)
        assertTrue(Files.exists(extra))

        assertFalse(hasStagingSibling(changedOutput))
        assertFalse(hasStagingSibling(missingOutput))
        assertFalse(hasStagingSibling(additionalOutput))
    }

    @Test
    fun `non-directory output and unavailable parent fail without mutation`() {
        val packages = listOf(packageArchive("alpha-tools", "alpha"))
        val occupied = temporaryDirectory.resolve("occupied")
        occupied.writeBytes("keep\n".encodeToByteArray())
        assertOutputExists(materialize(occupied, packages), occupied)
        assertEquals("keep\n", occupied.readBytes().decodeToString())

        val missingParentOutput = temporaryDirectory.resolve("missing-parent").resolve("release")
        assertEquals(
            MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.ParentUnavailable(
                    missingParentOutput.toAbsolutePath().normalize().parent,
                ),
            ),
            materialize(missingParentOutput, packages),
        )
        assertFalse(Files.exists(missingParentOutput))
    }

    @Test
    fun `existing symbolic links are rejected without being followed`() {
        val packages = listOf(packageArchive("alpha-tools", "alpha"))
        val output = temporaryDirectory.resolve("symlinked")
        assertIs<MarketplaceReleaseDirectoryMaterialization.Written>(materialize(output, packages))
        val external = temporaryDirectory.resolve("external-index.json")
        external.writeBytes(output.resolve("marketplace.json").readBytes())
        val linked = output.resolve("marketplace.json")
        Files.delete(linked)
        Files.createSymbolicLink(linked, external)

        assertOutputExists(materialize(output, packages), output)
        assertTrue(Files.isSymbolicLink(linked))
        assertTrue(Files.exists(external))
        assertFalse(hasStagingSibling(output))
    }

    @Test
    fun `failed release build never creates an output or staging directory`() {
        val output = temporaryDirectory.resolve("invalid-release")
        val alpha = packageArchive("alpha-tools", "alpha")
        assertEquals(
            MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.BuildRejected(
                    pass = MarketplaceReleaseBuildPass.FIRST,
                    reason =
                        MarketplaceReleaseRejection.SnapshotIndexRejected(
                            MarketplaceSnapshotIndexRejection.DefaultPackageMissing(packageName("missing")),
                        ),
                ),
            ),
            MarketplaceReleaseDirectory.materialize(
                output,
                marketplaceId("example-marketplace"),
                snapshotId("snapshot-one"),
                packageName("missing"),
                listOf(alpha),
            ),
        )
        assertFalse(Files.exists(output))
        assertFalse(hasStagingSibling(output))
    }

    private fun materialize(
        output: Path,
        packages: List<PackageArchive>,
    ): MarketplaceReleaseDirectoryMaterialization =
        MarketplaceReleaseDirectory.materialize(
            output,
            marketplaceId("example-marketplace"),
            snapshotId("snapshot-one"),
            packageName("alpha-tools"),
            packages,
        )

    private fun assertOutputExists(
        actual: MarketplaceReleaseDirectoryMaterialization,
        output: Path,
    ) {
        assertEquals(
            MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.OutputExists(output.toAbsolutePath().normalize()),
            ),
            actual,
        )
    }

    private fun hasStagingSibling(output: Path): Boolean {
        val prefix = ".${output.fileName}.intelligence-staging-"
        return Files.list(output.toAbsolutePath().normalize().parent).use { stream ->
            stream.anyMatch { path -> path.fileName.toString().startsWith(prefix) }
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

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun marketplaceId(raw: String): MarketplaceId =
        assertIs<IdentifierParse.Accepted<MarketplaceId>>(MarketplaceId.parse(raw)).value

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packageName(raw: String): PackageName =
        assertIs<IdentifierParse.Accepted<PackageName>>(PackageName.parse(raw)).value
}
