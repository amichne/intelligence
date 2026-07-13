package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.io.TempDir

class AuthoredMarketplaceSourceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `closed authored source produces canonical whole package evidence`() {
        val source = authoredSource()

        val inspected = assertIs<AuthoredMarketplaceInspection.Inspected>(AuthoredMarketplace.inspect(source))

        assertEquals("example-marketplace", inspected.marketplace.marketplaceId.render())
        assertEquals("alpha-tools", inspected.marketplace.defaultPackage.render())
        assertEquals(listOf("alpha-tools"), inspected.marketplace.packages.map { it.packageName.render() })
    }

    @Test
    fun `undeclared root or package content fails closed`() {
        val rootExtra = authoredSource(temporaryDirectory.resolve("root-extra"))
        Files.writeString(rootExtra.resolve("notes.txt"), "undeclared\n")
        assertIs<AuthoredMarketplaceRejection.RootClosureMismatch>(
            assertIs<AuthoredMarketplaceInspection.Rejected>(AuthoredMarketplace.inspect(rootExtra)).reason,
        )

        val packageExtra = authoredSource(temporaryDirectory.resolve("package-extra"))
        Files.writeString(packageExtra.resolve("packages/alpha-tools/skills/alpha/private.txt"), "undeclared\n")
        assertIs<AuthoredMarketplaceRejection.PackageClosureMismatch>(
            assertIs<AuthoredMarketplaceInspection.Rejected>(AuthoredMarketplace.inspect(packageExtra)).reason,
        )
    }

    @Test
    fun `changed source bytes and missing default package are rejected`() {
        val changed = authoredSource(temporaryDirectory.resolve("changed"))
        Files.writeString(changed.resolve("packages/alpha-tools/skills/alpha/SKILL.md"), "changed\n")
        assertIs<AuthoredMarketplaceRejection.PackageRejected>(
            assertIs<AuthoredMarketplaceInspection.Rejected>(AuthoredMarketplace.inspect(changed)).reason,
        )

        val missingDefault = authoredSource(temporaryDirectory.resolve("missing-default"))
        Files.writeString(missingDefault.resolve("default-package"), "missing-tools\n")
        assertIs<AuthoredMarketplaceRejection.DefaultPackageMissing>(
            assertIs<AuthoredMarketplaceInspection.Rejected>(AuthoredMarketplace.inspect(missingDefault)).reason,
        )
    }

    private fun authoredSource(root: Path = temporaryDirectory.resolve("source")): Path {
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
        return root
    }
}
