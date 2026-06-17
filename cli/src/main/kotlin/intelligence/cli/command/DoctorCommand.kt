package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import intelligence.cli.io.JsonFiles
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import intelligence.cli.io.toCliPath
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DoctorCommand(
    private val github: GitHubCli,
) : CliktCommand(
    name = "doctor",
) {
    private val repo by option("--repo", help = "Repository root to inspect.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").toAbsolutePath().normalize())

    private val format by outputFormatOption()

    override fun help(context: Context): String =
        "Check CLI discovery dependencies, GitHub host configuration, and local repository context."

    override fun run() {
        val status = github.status()
        val result = buildJsonObject {
            put("repoRoot", repo.toString())
            put("github", status.toJson())
        }
        echo(
            when (format) {
                CommandOutputFormat.Text -> renderDoctor(result)
                CommandOutputFormat.Json -> JsonFiles.json.encodeToString(JsonElement.serializer(), result)
            }
        )
    }

    private fun renderDoctor(result: JsonObject): String {
        val github = result.objectValue("github") ?: JsonObject(emptyMap())
        val available = github.booleanValue("available")
        val defaultHost = github.stringValue("defaultHost")
        val lines = mutableListOf(
            "Intelligence doctor",
            "Repository: ${result.stringValue("repoRoot").orEmpty()}",
            "GitHub CLI: ${if (available) "available" else "unavailable"}",
        )
        defaultHost?.let { lines += "Default GitHub host: $it" }
        val hosts = github["hosts"] as? JsonArray
        if (hosts.isNullOrEmpty()) {
            lines += "GitHub hosts: none reported"
        } else {
            lines += "GitHub hosts:"
            hosts.forEach { host ->
                val account = host as JsonObject
                val name = account.stringValue("host").orEmpty()
                val login = account.stringValue("login")
                val active = account.booleanValue("active")
                val authenticated = account.booleanValue("authenticated")
                val protocol = account.stringValue("gitProtocol")
                val markers = listOfNotNull(
                    "active".takeIf { active },
                    "authenticated".takeIf { authenticated },
                    protocol,
                    login?.let { "login=$it" },
                )
                lines += "- $name${markers.takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty()}"
            }
        }
        return lines.joinToString("\n")
    }
}

private fun JsonObject.booleanValue(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.content == "true"

internal enum class CommandOutputFormat(
    val cliName: String,
) {
    Text("text"),
    Json("json");

    companion object {
        fun parse(value: String): CommandOutputFormat =
            entries.firstOrNull { it.cliName == value.lowercase() }
                ?: throw IllegalArgumentException(
                    "format must be one of: ${entries.joinToString(", ") { it.cliName }}"
                )
    }
}

internal fun CliktCommand.outputFormatOption(default: CommandOutputFormat = CommandOutputFormat.Text) =
    option("--format", help = "Output format: text or json.")
        .convert("FORMAT") {
            try {
                CommandOutputFormat.parse(it)
            } catch (error: IllegalArgumentException) {
                fail(error.message ?: "invalid format")
            }
        }
        .default(default)
