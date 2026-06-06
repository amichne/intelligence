package intelligence.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntelligenceCliTest {
    @Test
    fun `help exits successfully`() {
        val stdout = StringBuilder()
        val exit = IntelligenceCli(stdout = stdout).run(listOf("--help"))

        assertEquals(0, exit)
        assertTrue(stdout.toString().contains("Usage: intelligence"))
    }

    @Test
    fun `validate parses repo portable and hydrated options`() {
        val options = ValidateOptions.parse(
            listOf("--repo", ".", "--portable", "--hydrated", "build/marketplace", "--manifests-only")
        )

        assertEquals(Path.of(".").toAbsolutePath().normalize(), options.repo)
        assertTrue(options.portable)
        assertTrue(options.manifestsOnly)
        assertEquals(Path.of("build/marketplace").toAbsolutePath().normalize(), options.hydrated)
    }
}
