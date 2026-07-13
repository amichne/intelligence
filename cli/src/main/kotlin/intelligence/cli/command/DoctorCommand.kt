package intelligence.cli.command

import intelligence.cli.github.GitHubCli
import com.github.ajalt.clikt.core.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class DoctorCommand(
    private val github: GitHubCli,
    private val environment: PortableCommandEnvironment,
) : PortableConsumerCommand("doctor", "doctor") {

    override fun help(context: Context): String =
        "Report runtime, repository, cache, and GitHub readiness without mutation."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val status = github.status()
        val githubJson = buildJsonObject {
            put("available", status.available)
            status.defaultHost?.let { put("defaultHost", it.value) }
            putJsonArray("hosts") {
                status.hosts.forEach { account ->
                    add(
                        buildJsonObject {
                            put("host", account.host.value)
                            account.login?.let { put("login", it) }
                            put("active", account.active)
                            put("authenticated", account.authenticated)
                            account.gitProtocol?.let { put("gitProtocol", it) }
                        },
                    )
                }
            }
        }
        val checks = listOf(
            DoctorCheck("runtime", Runtime.version().feature() >= 21, "JDK ${Runtime.version().feature()}"),
            DoctorCheck("repository", java.nio.file.Files.isDirectory(invocation.repository), invocation.repository.toString()),
            DoctorCheck(
                "cache",
                environment.cache() is PortableCacheOpening.Opened,
                "digest-addressed cache root",
            ),
            DoctorCheck("github", githubJson.booleanValue("available"), "GitHub CLI and host authentication"),
        )
        val result = buildJsonObject {
            put("status", if (checks.all(DoctorCheck::ready)) "READY" else "DEGRADED")
            put("repository", invocation.repository.toString())
            put("github", githubJson)
            putJsonArray("checks") {
                checks.forEach { check ->
                    add(
                        buildJsonObject {
                            put("name", check.name)
                            put("outcome", if (check.ready) "READY" else "UNAVAILABLE")
                            put("message", check.message)
                        },
                    )
                }
            }
        }
        return PortableCommandOutcome.Success(
            result,
            buildString {
                appendLine("doctor: ${if (checks.all(DoctorCheck::ready)) "READY" else "DEGRADED"}")
                checks.forEach { check -> appendLine("${check.name}: ${if (check.ready) "READY" else "UNAVAILABLE"}") }
            }.trimEnd(),
        )
    }
}

private data class DoctorCheck(
    val name: String,
    val ready: Boolean,
    val message: String,
)

private fun JsonObject.booleanValue(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.content == "true"
