package intelligence.cli.portable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class ProviderPackageProjectionTest {
    @Test
    fun `one package projects to exact Codex and Copilot plugin trees`() {
        val fixture = fixture()
        val snapshot = snapshotId("snapshot-one")
        val codex = ProviderPackageProjection.project(snapshot, fixture.archive, PortableProvider.CODEX)
        val copilot = ProviderPackageProjection.project(snapshot, fixture.archive, PortableProvider.GITHUB_COPILOT)
        val version = "0.0.0-intelligence.sha${fixture.archive.sha256.render()}"

        assertEquals(
            listOf(
                ".codex-plugin/plugin.json",
                ".intelligence/checksums.sha256",
                ".intelligence/projection.json",
                "skills/review/SKILL.md",
                "skills/review/scripts/check.sh",
            ),
            codex.files().map { it.path.render() },
        )
        assertEquals(
            listOf(
                ".intelligence/checksums.sha256",
                ".intelligence/projection.json",
                "plugin.json",
                "skills/review/SKILL.md",
                "skills/review/scripts/check.sh",
            ),
            copilot.files().map { it.path.render() },
        )
        assertEquals(version, codex.adapterVersion.render())
        assertEquals(version, copilot.adapterVersion.render())
        assertEquals(
            "{\"description\":\"A review package\",\"name\":\"review-tools\",\"skills\":\"./skills/\",\"version\":\"$version\"}\n",
            codex.file(path(".codex-plugin/plugin.json")).bytes().decodeToString(),
        )
        assertEquals(
            "{\"description\":\"A review package\",\"name\":\"review-tools\",\"skills\":\"skills/\",\"version\":\"$version\"}\n",
            copilot.file(path("plugin.json")).bytes().decodeToString(),
        )
        assertContentEquals(
            fixture.skillBytes,
            codex.file(path("skills/review/SKILL.md")).bytes(),
        )
        assertContentEquals(
            fixture.scriptBytes,
            copilot.file(path("skills/review/scripts/check.sh")).bytes(),
        )
        assertEquals(
            CanonicalZipEntryMode.EXECUTABLE,
            codex.file(path("skills/review/scripts/check.sh")).mode,
        )
        assertEquals(expectedReceipt(codex, fixture), codex.receiptBytes().decodeToString())
        assertEquals(expectedReceipt(copilot, fixture), copilot.receiptBytes().decodeToString())
        assertEquals(expectedChecksums(codex), codex.checksumBytes().decodeToString())
        assertEquals(expectedChecksums(copilot), copilot.checksumBytes().decodeToString())
        assertEquals("1d1cec966a7763525a85e10bc383b5fac448cd2c28fbe64d670e1155f4bdf8a3", codex.treeDigest().render())
        assertEquals("3cb83a0157f3aa7be1670fcde726ed2f3d72b1b8007b497a6423fb109028a33b", copilot.treeDigest().render())
    }

    @Test
    fun `projection from reparsed package bytes is identical`() {
        val fixture = fixture()
        val reparsed = assertIs<PackageArchiveParsing.Parsed>(
            PackageArchive.parse(fixture.archive.bytes()),
        ).archive

        PortableProvider.entries.forEach { provider ->
            val direct = ProviderPackageProjection.project(snapshotId("snapshot-one"), fixture.archive, provider)
            val fromBytes = ProviderPackageProjection.project(snapshotId("snapshot-one"), reparsed, provider)
            assertProjectionFilesEqual(direct.files(), fromBytes.files())
            assertEquals(direct.treeDigest(), fromBytes.treeDigest())
        }
    }

    @Test
    fun `projection verification rejects missing extra duplicate changed and remoded files`() {
        val projection =
            ProviderPackageProjection.project(
                snapshotId("snapshot-one"),
                fixture().archive,
                PortableProvider.CODEX,
            )
        val files = projection.files()
        val checksumPath = path(".intelligence/checksums.sha256")
        assertEquals(ProviderProjectionVerification.Verified, projection.verify(files))
        assertEquals(
            ProviderProjectionVerification.Rejected(ProviderProjectionRejection.MissingFile(checksumPath)),
            projection.verify(files.filterNot { it.path == checksumPath }),
        )

        val extraPath = path("notes.txt")
        assertEquals(
            ProviderProjectionVerification.Rejected(ProviderProjectionRejection.UnexpectedFile(extraPath)),
            projection.verify(files + projectedFile(extraPath, "notes".encodeToByteArray())),
        )
        assertEquals(
            ProviderProjectionVerification.Rejected(ProviderProjectionRejection.DuplicateFile(files.first().path)),
            projection.verify(files + files.first()),
        )

        val skill = files.single { it.path.render() == "skills/review/SKILL.md" }
        val changedBytes = skill.bytes().also { bytes -> bytes[bytes.lastIndex] = 'x'.code.toByte() }
        assertEquals(
            ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.DigestMismatch(
                    path = skill.path,
                    expected = skill.sha256,
                    actual = Sha256Digest.compute(changedBytes),
                ),
            ),
            projection.verify(files.replace(skill, projectedFile(skill.path, changedBytes, skill.mode))),
        )

        val script = files.single { it.path.render().endsWith("check.sh") }
        assertEquals(
            ProviderProjectionVerification.Rejected(
                ProviderProjectionRejection.ModeMismatch(
                    path = script.path,
                    expected = CanonicalZipEntryMode.EXECUTABLE,
                    actual = CanonicalZipEntryMode.REGULAR,
                ),
            ),
            projection.verify(files.replace(script, projectedFile(script.path, script.bytes()))),
        )
    }

    @Test
    fun `snapshot identity changes only generated evidence`() {
        val fixture = fixture()
        val first =
            ProviderPackageProjection.project(snapshotId("snapshot-one"), fixture.archive, PortableProvider.CODEX)
        val second =
            ProviderPackageProjection.project(snapshotId("snapshot-two"), fixture.archive, PortableProvider.CODEX)

        assertNotEquals(first.treeDigest(), second.treeDigest())
        assertContentEquals(
            first.file(path(".codex-plugin/plugin.json")).bytes(),
            second.file(path(".codex-plugin/plugin.json")).bytes(),
        )
        assertContentEquals(
            first.file(path("skills/review/SKILL.md")).bytes(),
            second.file(path("skills/review/SKILL.md")).bytes(),
        )
        assertNotEquals(first.receiptBytes().decodeToString(), second.receiptBytes().decodeToString())
    }

    @Test
    fun `projection and projected files own immutable byte copies`() {
        val projection =
            ProviderPackageProjection.project(
                snapshotId("snapshot-one"),
                fixture().archive,
                PortableProvider.CODEX,
            )
        val receipt = projection.receiptBytes()
        val skill = projection.file(path("skills/review/SKILL.md"))
        val skillBytes = skill.bytes()
        receipt.fill(0)
        skillBytes.fill(0)

        assertEquals('{', projection.receiptBytes().decodeToString().first())
        assertEquals('-', skill.bytes().decodeToString().first())
    }

    private fun expectedReceipt(
        projection: ProviderPackageProjection,
        fixture: ProjectionFixture,
    ): String {
        val sourceFiles = fixture.archive.sourceFiles().sortedBy { it.path.render() }
        val files =
            sourceFiles.joinToString(separator = ",") { file ->
                "{\"executable\":${file.executable},\"generatedPath\":\"${file.path.render()}\"," +
                    "\"sha256\":\"${file.sha256().render()}\",\"size\":${file.byteSize}," +
                    "\"sourcePath\":\"${file.path.render()}\"}"
            }
        return "{\"adapterVersion\":\"${projection.adapterVersion.render()}\",\"files\":[$files]," +
            "\"generator\":\"intelligence-kotlin-v1\",\"marketplaceId\":\"example-marketplace\"," +
            "\"packageArchive\":{\"name\":\"package-review-tools.zip\",\"sha256\":\"${fixture.archive.sha256.render()}\"," +
            "\"size\":${fixture.archive.byteSize}},\"packageName\":\"review-tools\"," +
            "\"provider\":\"${projection.provider.render()}\",\"schemaVersion\":1," +
            "\"skills\":[{\"generatedPath\":\"skills/review/SKILL.md\",\"name\":\"review\"," +
            "\"sourcePath\":\"skills/review/SKILL.md\"}],\"snapshotId\":\"snapshot-one\"," +
            "\"type\":\"INTELLIGENCE_PACKAGE_PROJECTION\"}\n"
    }

    private fun expectedChecksums(projection: ProviderPackageProjection): String =
        projection.files()
            .filterNot { file -> file.path.render() == ".intelligence/checksums.sha256" }
            .joinToString(separator = "", postfix = "") { file ->
                "${file.sha256.render()}  ${file.path.render()}\n"
            }

    private fun fixture(): ProjectionFixture {
        val skillBytes =
            "---\nname: review\ndescription: \"Review code\"\n---\n\nReview the code.\n".encodeToByteArray()
        val scriptBytes = "#!/bin/sh\nexit 0\n".encodeToByteArray()
        val manifestText =
            "{\"description\":\"A review package\",\"marketplaceId\":\"example-marketplace\",\"name\":\"review-tools\"," +
                "\"schemaVersion\":1,\"skills\":[{\"assets\":[{\"executable\":true," +
                "\"path\":\"skills/review/scripts/check.sh\",\"sha256\":\"${Sha256Digest.compute(scriptBytes).render()}\"," +
                "\"size\":${scriptBytes.size}}],\"description\":\"Review code\",\"name\":\"review\"," +
                "\"primary\":{\"executable\":false,\"path\":\"skills/review/SKILL.md\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[\"kotlin\"],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"
        val manifest = assertIs<PackageManifestParsing.Parsed>(
            PackageManifest.parse(manifestText.encodeToByteArray()),
        ).manifest
        val sourceFiles =
            listOf(
                sourceFile("skills/review/SKILL.md", skillBytes, executable = false),
                sourceFile("skills/review/scripts/check.sh", scriptBytes, executable = true),
            )
        val archive = assertIs<PackageArchiveMaterialization.Materialized>(
            PackageArchive.materialize(manifest, sourceFiles),
        ).archive
        return ProjectionFixture(archive, skillBytes, scriptBytes)
    }

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

    private fun snapshotId(raw: String): SnapshotId =
        assertIs<IdentifierParse.Accepted<SnapshotId>>(SnapshotId.parse(raw)).value

    private fun projectedFile(
        path: PackageEntryPath,
        bytes: ByteArray,
        mode: CanonicalZipEntryMode = CanonicalZipEntryMode.REGULAR,
    ): ProjectedFile =
        assertIs<ProjectedFileCreation.Created>(ProjectedFile.create(path, bytes, mode)).file

    private fun assertProjectionFilesEqual(
        expected: List<ProjectedFile>,
        actual: List<ProjectedFile>,
    ) {
        assertEquals(expected.map { it.path }, actual.map { it.path })
        expected.zip(actual).forEach { (left, right) ->
            assertEquals(left.mode, right.mode)
            assertContentEquals(left.bytes(), right.bytes())
        }
    }
}

private fun List<ProjectedFile>.replace(
    old: ProjectedFile,
    new: ProjectedFile,
): List<ProjectedFile> = map { file -> if (file === old) new else file }

private data class ProjectionFixture(
    val archive: PackageArchive,
    val skillBytes: ByteArray,
    val scriptBytes: ByteArray,
)
