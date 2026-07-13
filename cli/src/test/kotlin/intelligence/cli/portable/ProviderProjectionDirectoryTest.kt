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

class ProviderProjectionDirectoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `selected packages materialize one exact package directory each and repeat unchanged`() {
        val output = temporaryDirectory.resolve("codex-output")
        val alpha = packageArchive("alpha-tools", "alpha")
        val beta = packageArchive("beta-tools", "beta")
        val written = assertIs<ProviderProjectionDirectoryMaterialization.Written>(
            materialize(output, listOf(beta, alpha), PortableProvider.CODEX),
        )

        assertEquals(listOf("alpha-tools", "beta-tools"), written.packages.map(PackageName::render))
        assertEquals(
            listOf("alpha-tools", "beta-tools"),
            Files.list(output).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() },
        )
        assertTrue(Files.exists(output.resolve("alpha-tools/.codex-plugin/plugin.json")))
        assertTrue(Files.exists(output.resolve("alpha-tools/.intelligence/projection.json")))
        assertTrue(Files.exists(output.resolve("alpha-tools/.intelligence/checksums.sha256")))
        assertFalse(Files.exists(output.resolve("marketplace.json")))

        val repeated = assertIs<ProviderProjectionDirectoryMaterialization.Unchanged>(
            materialize(output, listOf(alpha, beta), PortableProvider.CODEX),
        )
        assertEquals(written.treeDigest, repeated.treeDigest)
    }

    @Test
    fun `provider outputs are deterministic and provider specific`() {
        val archive = packageArchive("alpha-tools", "alpha")
        val codex = assertIs<ProviderProjectionDirectoryMaterialization.Written>(
            materialize(temporaryDirectory.resolve("codex"), listOf(archive), PortableProvider.CODEX),
        )
        val copilot = assertIs<ProviderProjectionDirectoryMaterialization.Written>(
            materialize(temporaryDirectory.resolve("copilot"), listOf(archive), PortableProvider.GITHUB_COPILOT),
        )

        assertTrue(Files.exists(codex.output.resolve("alpha-tools/.codex-plugin/plugin.json")))
        assertTrue(Files.exists(copilot.output.resolve("alpha-tools/plugin.json")))
        assertFalse(codex.treeDigest == copilot.treeDigest)
    }

    @Test
    fun `changed existing output is rejected and preserved`() {
        val output = temporaryDirectory.resolve("changed")
        val archive = packageArchive("alpha-tools", "alpha")
        assertIs<ProviderProjectionDirectoryMaterialization.Written>(
            materialize(output, listOf(archive), PortableProvider.CODEX),
        )
        val changed = output.resolve("alpha-tools/.codex-plugin/plugin.json")
        val changedBytes = changed.readBytes() + ' '.code.toByte()
        changed.writeBytes(changedBytes)

        assertEquals(
            ProviderProjectionDirectoryMaterialization.Rejected(
                ProviderProjectionDirectoryRejection.OutputExists(output.toAbsolutePath().normalize()),
            ),
            materialize(output, listOf(archive), PortableProvider.CODEX),
        )
        assertContentEquals(changedBytes, changed.readBytes())
    }

    @Test
    fun `symbolic output content is rejected without being followed`() {
        val output = temporaryDirectory.resolve("linked")
        val archive = packageArchive("alpha-tools", "alpha")
        assertIs<ProviderProjectionDirectoryMaterialization.Written>(
            materialize(output, listOf(archive), PortableProvider.CODEX),
        )
        val target = output.resolve("alpha-tools/.codex-plugin/plugin.json")
        val external = temporaryDirectory.resolve("external.json")
        Files.move(target, external)
        Files.createSymbolicLink(target, external)

        assertIs<ProviderProjectionDirectoryMaterialization.Rejected>(
            materialize(output, listOf(archive), PortableProvider.CODEX),
        )
        assertTrue(Files.isSymbolicLink(target))
        assertTrue(Files.exists(external))
    }

    private fun materialize(
        output: Path,
        packages: List<PackageArchive>,
        provider: PortableProvider,
    ): ProviderProjectionDirectoryMaterialization =
        ProviderProjectionDirectory.materialize(
            output,
            snapshotId("snapshot-one"),
            packages,
            provider,
        )

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

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun packagePath(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value
}
