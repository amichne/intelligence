package intelligence.cli.command

import intelligence.cli.portable.Sha256Digest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class InstalledCliBlackBoxTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `installed Kotlin distribution completes one hermetic V1 lifecycle`() {
        val launcher =
            Path.of(checkNotNull(System.getProperty("intelligence.installDir")))
                .resolve("bin/intelligence")
        assertTrue(Files.isExecutable(launcher), "installed launcher must be executable")
        val consumer = temporaryDirectory.resolve("consumer")
        val authored = temporaryDirectory.resolve("authored")
        val snapshot = consumer.resolve("snapshots/one")
        val cacheHome = temporaryDirectory.resolve("cache-home")
        Files.createDirectories(consumer.resolve("snapshots"))
        writeAuthoredSource(authored)

        val sourceValidation = run(
            launcher,
            "validate",
            "--repository",
            authored.toString(),
            "--portable",
            "--format",
            "json",
        )
        assertEquals(0, sourceValidation.exitCode, sourceValidation.stdout)

        assertEquals(0, run(launcher, "--version").exitCode)
        val doctor = run(
            launcher,
            "doctor",
            "--repository",
            consumer.toString(),
            "--format",
            "json",
        )
        assertEquals(0, doctor.exitCode)
        assertTrue(doctor.stdout.contains("\"command\":\"doctor\""))
        assertFalse(doctor.stdout.contains("token", ignoreCase = true))

        val materialized = run(
            launcher,
            "marketplace",
            "materialize",
            "--source",
            authored.toString(),
            "--snapshot",
            "snapshot-one",
            "--out",
            snapshot.toString(),
            "--format",
            "json",
        )
        assertEquals(0, materialized.exitCode, materialized.stderr)
        val indexDigest = Sha256Digest.compute(Files.readAllBytes(snapshot.resolve("marketplace.json"))).render()
        val releaseValidation = run(
            launcher,
            "validate",
            "--repository",
            snapshot.toString(),
            "--portable",
            "--format",
            "json",
        )
        assertEquals(0, releaseValidation.exitCode, releaseValidation.stdout)

        val inspection = run(
            launcher,
            "marketplace",
            "inspect",
            "--local-snapshot",
            snapshot.toString(),
            "--index-sha256",
            indexDigest,
            "--format",
            "json",
        )
        assertEquals(0, inspection.exitCode, inspection.stderr)
        assertTrue(inspection.stdout.contains("\"defaultPackage\":\"alpha-tools\""))
        assertFalse(inspection.stdout.contains("Use alpha"))

        val setupArguments =
            arrayOf(
                "setup",
                "--repository",
                consumer.toString(),
                "--local-snapshot",
                "snapshots/one",
                "--index-sha256",
                indexDigest,
                "--format",
                "json",
            )
        val dryRun = run(launcher, *setupArguments, "--dry-run", cacheHome = cacheHome)
        assertEquals(0, dryRun.exitCode, dryRun.stderr)
        assertFalse(consumer.resolve(".intelligence").exists())
        assertFalse(cacheHome.exists())

        val setup = run(launcher, *setupArguments, cacheHome = cacheHome)
        assertEquals(0, setup.exitCode, setup.stderr)
        val intent = consumer.resolve(".intelligence/adaptable.marketplace.json")
        val lock = consumer.resolve(".intelligence/marketplace-lock.json")
        val intentBefore = Files.readAllBytes(intent)
        val lockBefore = Files.readAllBytes(lock)
        val setupEnvelope = Json.parseToJsonElement(setup.stdout.trim()).jsonObject
        assertEquals("setup", setupEnvelope["command"]!!.jsonPrimitive.content)
        assertTrue(setup.stdout.contains("alpha-tools"))
        assertFalse(setup.stdout.contains("skills/alpha"))

        val repeated = run(launcher, *setupArguments, cacheHome = cacheHome)
        assertEquals(0, repeated.exitCode, repeated.stderr)
        assertTrue(intentBefore.contentEquals(Files.readAllBytes(intent)))
        assertTrue(lockBefore.contentEquals(Files.readAllBytes(lock)))

        listOf("codex", "github-copilot").forEach { provider ->
            val output = temporaryDirectory.resolve("projection-$provider")
            val projected = run(
                launcher,
                "marketplace",
                "project",
                "example-marketplace",
                "--repository",
                consumer.toString(),
                "--provider",
                provider,
                "--out",
                output.toString(),
                "--offline",
                "--format",
                "json",
                cacheHome = cacheHome,
            )
            assertEquals(0, projected.exitCode, projected.stderr)
            assertTrue(output.resolve("alpha-tools").exists())
        }

        val validation = run(
            launcher,
            "validate",
            "--repository",
            consumer.toString(),
            "--portable",
            "--format",
            "json",
        )
        assertEquals(0, validation.exitCode, "${validation.stderr}\n${validation.stdout}")
        assertTrue(validation.stdout.contains("\"outcome\":\"PASS\""))

        Files.writeString(consumer.resolve("unknown.json"), "{}\n")
        val unknownJson = run(
            launcher,
            "validate",
            "--repository",
            consumer.toString(),
            "--portable",
            "--format",
            "json",
        )
        assertEquals(3, unknownJson.exitCode)
        assertTrue(unknownJson.stdout.contains("no typed or schema-owned boundary"))
    }

    private fun run(
        launcher: Path,
        vararg arguments: String,
        cacheHome: Path? = null,
    ): ProcessResult {
        val builder = ProcessBuilder(listOf(launcher.toString()) + arguments)
            .directory(temporaryDirectory.toFile())
        cacheHome?.let { builder.environment()["XDG_CACHE_HOME"] = it.toString() }
        val process = builder.start()
        val stdout = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val stderr = process.errorStream.readAllBytes().toString(StandardCharsets.UTF_8)
        return ProcessResult(process.waitFor(), stdout, stderr)
    }

    private fun writeAuthoredSource(root: Path) {
        val skillBytes =
            "---\nname: alpha\ndescription: \"Alpha skill\"\n---\n\nUse alpha.\n".encodeToByteArray()
        val skillPath = "skills/alpha/SKILL.md"
        val packageRoot = root.resolve("packages/alpha-tools")
        Files.createDirectories(packageRoot.resolve("skills/alpha"))
        Files.writeString(root.resolve("default-package"), "alpha-tools\n")
        Files.write(packageRoot.resolve(skillPath), skillBytes)
        Files.writeString(
            packageRoot.resolve("package.json"),
            "{\"description\":\"Alpha package\",\"marketplaceId\":\"example-marketplace\"," +
                "\"name\":\"alpha-tools\",\"schemaVersion\":1,\"skills\":[{\"assets\":[]," +
                "\"description\":\"Alpha skill\",\"name\":\"alpha\"," +
                "\"primary\":{\"executable\":false,\"path\":\"$skillPath\"," +
                "\"sha256\":\"${Sha256Digest.compute(skillBytes).render()}\",\"size\":${skillBytes.size}}}]," +
                "\"tags\":[],\"type\":\"INTELLIGENCE_PACKAGE\"}\n",
        )
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
