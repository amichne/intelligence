package intelligence.cli.portable

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageArchiveTest {
    @Test
    fun `equivalent source orders produce one canonical package archive`() {
        val fixture = fixture()

        val forward = materialized(fixture.manifest, fixture.files)
        val reverse = materialized(fixture.manifest, fixture.files.reversed())

        assertContentEquals(forward.bytes(), reverse.bytes())
        assertEquals(forward.sha256, reverse.sha256)
        assertEquals("8dfedd3cef5467e64b0ce5aea5e73a2fcc480c22edcc3414aaafd6d40ad4f9f6", forward.sha256.render())
        assertEquals("package-review-tools.zip", forward.assetName.render())
        assertEquals(forward.bytes().size, forward.byteSize)
        val entries = zipEntries(forward.bytes())
        assertEquals(
            listOf("package.json", "skills/review/SKILL.md", "skills/review/scripts/check.sh"),
            entries.map { it.first },
        )
        assertContentEquals(fixture.manifest.canonicalBytes(), entries[0].second)
        assertContentEquals(fixture.skillBytes, entries[1].second)
        assertContentEquals(fixture.scriptBytes, entries[2].second)
    }

    @Test
    fun `package source and archive bytes are immutable copies`() {
        val fixture = fixture()
        val mutableSkill = fixture.skillBytes.copyOf()
        val source = sourceFile("skills/review/SKILL.md", mutableSkill, executable = false)
        mutableSkill.fill(0)

        val archive = materialized(
            fixture.manifest,
            listOf(source, fixture.files.single { it.path.render().endsWith("check.sh") }),
        )
        val firstRead = archive.bytes()
        firstRead.fill(0)

        assertContentEquals(fixture.skillBytes, zipEntries(archive.bytes())[1].second)
    }

    @Test
    fun `materialization requires exact declared file closure`() {
        val fixture = fixture()
        val primaryPath = path("skills/review/SKILL.md")
        val unexpectedPath = path("skills/review/notes.txt")

        assertEquals(
            PackageArchiveMaterialization.Rejected(PackageArchiveRejection.MissingFile(primaryPath)),
            PackageArchive.materialize(fixture.manifest, fixture.files.drop(1)),
        )
        assertEquals(
            PackageArchiveMaterialization.Rejected(PackageArchiveRejection.UnexpectedFile(unexpectedPath)),
            PackageArchive.materialize(
                fixture.manifest,
                fixture.files + sourceFile(unexpectedPath.render(), "notes".encodeToByteArray(), false),
            ),
        )
        assertEquals(
            PackageArchiveMaterialization.Rejected(PackageArchiveRejection.DuplicateFile(primaryPath)),
            PackageArchive.materialize(fixture.manifest, fixture.files + fixture.files.first()),
        )
    }

    @Test
    fun `materialization verifies size digest and executable evidence`() {
        val fixture = fixture()
        val scriptPath = path("skills/review/scripts/check.sh")
        val differentScript = "#!/bin/sh\nexit 100\n".encodeToByteArray()

        assertEquals(
            PackageArchiveMaterialization.Rejected(
                PackageArchiveRejection.FileSizeMismatch(
                    path = scriptPath,
                    expectedBytes = fixture.scriptBytes.size,
                    actualBytes = differentScript.size,
                ),
            ),
            PackageArchive.materialize(
                fixture.manifest,
                fixture.files.dropLast(1) + sourceFile(scriptPath.render(), differentScript, true),
            ),
        )

        val sameSizeDifferentContent = fixture.scriptBytes.copyOf().also { it[it.lastIndex] = '1'.code.toByte() }
        assertEquals(
            PackageArchiveMaterialization.Rejected(
                PackageArchiveRejection.FileDigestMismatch(
                    path = scriptPath,
                    expected = Sha256Digest.compute(fixture.scriptBytes),
                    actual = Sha256Digest.compute(sameSizeDifferentContent),
                ),
            ),
            PackageArchive.materialize(
                fixture.manifest,
                fixture.files.dropLast(1) + sourceFile(scriptPath.render(), sameSizeDifferentContent, true),
            ),
        )

        assertEquals(
            PackageArchiveMaterialization.Rejected(
                PackageArchiveRejection.FileModeMismatch(
                    path = scriptPath,
                    expectedExecutable = true,
                    actualExecutable = false,
                ),
            ),
            PackageArchive.materialize(
                fixture.manifest,
                fixture.files.dropLast(1) + sourceFile(scriptPath.render(), fixture.scriptBytes, false),
            ),
        )
    }

    @Test
    fun `skill document must carry exact portable identity and instructions`() {
        val fixture = fixture()
        val primaryPath = "skills/review/SKILL.md"
        val wrongDescription = skillDocument(description = "Different")
        val emptyBody = skillDocument(body = "   \n")

        assertEquals(
            PackageArchiveMaterialization.Rejected(
                PackageArchiveRejection.InvalidSkillDocument(
                    skill = fixture.manifest.skills.single().name,
                    reason = PortableSkillDocumentRejection.DescriptionMismatch(
                        expected = "Review code",
                        actual = "Different",
                    ),
                ),
            ),
            PackageArchive.materialize(
                manifest(skillBytes = wrongDescription, scriptBytes = fixture.scriptBytes),
                listOf(
                    sourceFile(primaryPath, wrongDescription, false),
                    fixture.files.last(),
                ),
            ),
        )
        assertEquals(
            PackageArchiveMaterialization.Rejected(
                PackageArchiveRejection.InvalidSkillDocument(
                    skill = fixture.manifest.skills.single().name,
                    reason = PortableSkillDocumentRejection.EmptyInstructions,
                ),
            ),
            PackageArchive.materialize(
                manifest(skillBytes = emptyBody, scriptBytes = fixture.scriptBytes),
                listOf(
                    sourceFile(primaryPath, emptyBody, false),
                    fixture.files.last(),
                ),
            ),
        )
    }

    @Test
    fun `skill document rejects non-canonical metadata bytes`() {
        val skill = fixture().manifest.skills.single()

        assertEquals(
            PortableSkillDocumentRejection.NameMismatch(expected = "review", actual = "other"),
            rejectedSkillDocument(skillDocument().decodeToString().replace("name: review", "name: other"), skill),
        )
        assertEquals(
            PortableSkillDocumentRejection.MalformedFrontmatter,
            rejectedSkillDocument(
                skillDocument().decodeToString().replace("description:", "license: MIT\ndescription:"),
                skill,
            ),
        )
        assertEquals(
            PortableSkillDocumentRejection.NonCanonicalDescription,
            rejectedSkillDocument(
                skillDocument().decodeToString().replace("\"Review code\"", "\"Review\\u0020code\""),
                skill,
            ),
        )
        assertEquals(
            PortableSkillDocumentRejection.CarriageReturn,
            rejectedSkillDocument(skillDocument().decodeToString().replace("\n", "\r\n"), skill),
        )
        assertEquals(
            PortableSkillDocumentRejection.Nul,
            assertIs<PortableSkillDocumentParsing.Rejected>(
                PortableSkillDocument.parse(
                    bytes = skillDocument() + byteArrayOf(0),
                    expectedName = skill.name,
                    expectedDescription = skill.description,
                ),
            ).reason,
        )
        assertEquals(
            PortableSkillDocumentRejection.InvalidUtf8,
            assertIs<PortableSkillDocumentParsing.Rejected>(
                PortableSkillDocument.parse(
                    bytes = byteArrayOf(0xc3.toByte()),
                    expectedName = skill.name,
                    expectedDescription = skill.description,
                ),
            ).reason,
        )
    }

    @Test
    fun `release asset names reject path and whitespace syntax`() {
        assertEquals(
            "SHA256SUMS",
            assertIs<ReleaseAssetNameParsing.Parsed>(ReleaseAssetName.parse("SHA256SUMS")).name.render(),
        )
        assertEquals(
            ReleaseAssetNameParsing.Rejected(ReleaseAssetNameRejection.INVALID_SYNTAX),
            ReleaseAssetName.parse("package/review.zip"),
        )
        assertEquals(
            ReleaseAssetNameParsing.Rejected(ReleaseAssetNameRejection.INVALID_SYNTAX),
            ReleaseAssetName.parse("package review.zip"),
        )
        assertEquals(
            ReleaseAssetNameParsing.Rejected(ReleaseAssetNameRejection.TOO_LONG),
            ReleaseAssetName.parse("a".repeat(129)),
        )
    }

    @Test
    fun `source file creation rejects content beyond the per-file limit`() {
        val oversized = ByteArray(MAX_PACKAGE_FILE_BYTES + 1)

        assertEquals(
            PackageSourceFileCreation.Rejected(
                PackageSourceFileRejection.TooLarge(
                    actualBytes = oversized.size,
                    maximumBytes = MAX_PACKAGE_FILE_BYTES,
                ),
            ),
            PackageSourceFile.create(path("skills/review/tool.bin"), oversized, executable = false),
        )
    }

    private fun fixture(): Fixture {
        val skillBytes = skillDocument()
        val scriptBytes = "#!/bin/sh\nexit 0\n".encodeToByteArray()
        val manifest = manifest(skillBytes, scriptBytes)
        return Fixture(
            manifest = manifest,
            skillBytes = skillBytes,
            scriptBytes = scriptBytes,
            files =
                listOf(
                    sourceFile("skills/review/SKILL.md", skillBytes, executable = false),
                    sourceFile("skills/review/scripts/check.sh", scriptBytes, executable = true),
                ),
        )
    }

    private fun manifest(
        skillBytes: ByteArray,
        scriptBytes: ByteArray,
    ): PackageManifest {
        val json =
            "{\"description\":\"A review package\",\"marketplaceId\":\"example-marketplace\",\"name\":\"review-tools\",\"schemaVersion\":1," +
                "\"skills\":[{\"assets\":[{\"executable\":true,\"path\":\"skills/review/scripts/check.sh\"," +
                "\"sha256\":\"${Sha256Digest.compute(scriptBytes).render()}\",\"size\":${scriptBytes.size}}]," +
                "\"description\":\"Review code\",\"name\":\"review\",\"primary\":{\"executable\":false," +
                "\"path\":\"skills/review/SKILL.md\",\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\"," +
                "\"size\":${skillBytes.size}}}],\"tags\":[\"kotlin\",\"review\"],\"type\":\"INTELLIGENCE_PACKAGE\"}\n"
        return assertIs<PackageManifestParsing.Parsed>(PackageManifest.parse(json.encodeToByteArray())).manifest
    }

    private fun skillDocument(
        description: String = "Review code",
        body: String = "Review the supplied code and report evidence.\n",
    ): ByteArray =
        "---\nname: review\ndescription: \"$description\"\n---\n\n$body".encodeToByteArray()

    private fun sourceFile(
        rawPath: String,
        bytes: ByteArray,
        executable: Boolean,
    ): PackageSourceFile =
        assertIs<PackageSourceFileCreation.Created>(
            PackageSourceFile.create(path(rawPath), bytes, executable),
        ).file

    private fun rejectedSkillDocument(
        document: String,
        skill: PortableSkillManifest,
    ): PortableSkillDocumentRejection =
        assertIs<PortableSkillDocumentParsing.Rejected>(
            PortableSkillDocument.parse(
                bytes = document.encodeToByteArray(),
                expectedName = skill.name,
                expectedDescription = skill.description,
            ),
        ).reason

    private fun path(raw: String): PackageEntryPath =
        assertIs<PackageEntryPathParse.Accepted>(PackageEntryPath.parse(raw)).value

    private fun materialized(
        manifest: PackageManifest,
        files: List<PackageSourceFile>,
    ): PackageArchive =
        assertIs<PackageArchiveMaterialization.Materialized>(
            PackageArchive.materialize(manifest, files),
        ).archive

    private fun zipEntries(bytes: ByteArray): List<Pair<String, ByteArray>> =
        buildList {
            ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    add(entry.name to archive.readBytes())
                    entry = archive.nextEntry
                }
            }
        }
}

private data class Fixture(
    val manifest: PackageManifest,
    val skillBytes: ByteArray,
    val scriptBytes: ByteArray,
    val files: List<PackageSourceFile>,
)
