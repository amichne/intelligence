package intelligence.cli.github

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.ProcessCapture
import intelligence.cli.io.ProcessCaptureRunner
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class GitHubCli(
    private val runner: ProcessCaptureRunner = ProcessCaptureRunner.system(),
    private val cwd: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    private var cachedStatus: GitHubCliStatus? = null

    fun status(): GitHubCliStatus =
        cachedStatus ?: readStatus().also { cachedStatus = it }

    fun host(explicitHost: String?): GitHubHost {
        explicitHost?.let { return GitHubHost.parse(it) }
        return status().defaultHost ?: GitHubHost.Public
    }

    fun normalizeRepository(value: String, explicitHost: String?): String {
        val shorthand = GitHubRepositoryShorthand.parseOrNull(value) ?: return value
        return shorthand.url(host(explicitHost))
    }

    fun normalizeImportTarget(value: String, explicitHost: String?): String {
        val parts = value.split("/")
        if (parts.size != 3) {
            return value
        }
        val repository = "${parts[0]}/${parts[1]}"
        val plugin = parts[2]
        if (GitHubRepositoryShorthand.parseOrNull(repository) == null) {
            return value
        }
        return "${normalizeRepository(repository, explicitHost)}/$plugin"
    }

    fun searchRepositories(query: String, explicitHost: String?, limit: Int): GitHubRepositorySearch {
        val host = host(explicitHost)
        val capture = runGh(
            args = listOf(
                "search",
                "repos",
                query,
                "--limit",
                limit.toString(),
                "--json",
                "fullName,url,description,visibility,updatedAt,pushedAt",
            ),
            host = host,
        )
        if (capture.exitCode != 0) {
            throw GitHubCliFailure("gh search repos failed: ${capture.errorMessage()}")
        }
        val repositories = JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonArray
            ?: throw GitHubCliFailure("gh search repos returned non-array JSON")
        return GitHubRepositorySearch(
            query = query,
            host = host,
            repositories = repositories.mapNotNull { element ->
                (element as? JsonObject)?.let(::repositorySummary)
            },
        )
    }

    fun inspectRepository(repository: String, explicitHost: String?): GitHubRepositorySummary? {
        val shorthand = GitHubRepositoryShorthand.parseOrNull(repository) ?: return null
        val host = host(explicitHost)
        val capture = runGh(
            args = listOf(
                "repo",
                "view",
                shorthand.url(host),
                "--json",
                "nameWithOwner,url,description,visibility,updatedAt,pushedAt,defaultBranchRef",
            ),
            host = host,
        )
        if (capture.exitCode != 0) {
            return null
        }
        val payload = JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonObject ?: return null
        return repositorySummary(payload)
    }

    private fun readStatus(): GitHubCliStatus {
        val capture = runner.run(listOf("gh", "auth", "status", "--json", "hosts"), cwd, emptyMap())
        if (capture.exitCode == ProcessCaptureRunner.COMMAND_NOT_FOUND) {
            return GitHubCliStatus.Unavailable(capture.errorMessage())
        }
        val hosts = runCatching {
            val payload = JsonFiles.compactJson.parseToJsonElement(capture.stdout) as? JsonObject
            payload?.objectValue("hosts")?.flatMap { (host, accounts) ->
                (accounts as? JsonArray).orEmpty().mapNotNull { account ->
                    (account as? JsonObject)?.let { hostAccount(host, it) }
                }
            }.orEmpty()
        }.getOrElse { emptyList() }
        return GitHubCliStatus(
            available = true,
            hosts = hosts,
            message = capture.stderr.takeIf { it.isNotBlank() },
        )
    }

    private fun runGh(
        args: List<String>,
        host: GitHubHost,
    ) = runner.run(
        command = listOf("gh") + args,
        cwd = cwd,
        environment = if (host == GitHubHost.Public) emptyMap() else mapOf("GH_HOST" to host.value),
    )

    private fun hostAccount(host: String, payload: JsonObject): GitHubHostAccount =
        GitHubHostAccount(
            host = GitHubHost.parse(payload.stringValue("host") ?: host),
            login = payload.stringValue("login"),
            active = payload.stringValue("active") == "true",
            authenticated = payload.stringValue("state") == "success",
            tokenSource = payload.stringValue("tokenSource"),
            gitProtocol = payload.stringValue("gitProtocol"),
        )

    private fun repositorySummary(payload: JsonObject): GitHubRepositorySummary =
        GitHubRepositorySummary(
            nameWithOwner = payload.stringValue("nameWithOwner") ?: payload.stringValue("fullName").orEmpty(),
            url = payload.stringValue("url").orEmpty(),
            description = payload.stringValue("description"),
            visibility = payload.stringValue("visibility"),
            updatedAt = payload.stringValue("updatedAt"),
            pushedAt = payload.stringValue("pushedAt"),
            defaultBranch = payload.objectValue("defaultBranchRef")?.stringValue("name"),
        )
}

internal data class GitHubCliStatus(
    val available: Boolean,
    val hosts: List<GitHubHostAccount>,
    val message: String?,
) {
    val defaultHost: GitHubHost?
        get() = hosts.firstOrNull { it.active && it.authenticated }?.host
            ?: hosts.firstOrNull { it.active }?.host
            ?: hosts.firstOrNull { it.authenticated }?.host

    fun toJson(): JsonObject =
        buildJsonObject {
            put("available", available)
            defaultHost?.let { put("defaultHost", it.value) }
            message?.let { put("message", it) }
            putJsonArray("hosts") {
                hosts.forEach { account -> add(account.toJson()) }
            }
        }

    companion object {
        fun Unavailable(message: String): GitHubCliStatus =
            GitHubCliStatus(
                available = false,
                hosts = emptyList(),
                message = message,
            )
    }
}

internal data class GitHubHostAccount(
    val host: GitHubHost,
    val login: String?,
    val active: Boolean,
    val authenticated: Boolean,
    val tokenSource: String?,
    val gitProtocol: String?,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("host", host.value)
            login?.let { put("login", it) }
            put("active", active)
            put("authenticated", authenticated)
            tokenSource?.let { put("tokenSource", it) }
            gitProtocol?.let { put("gitProtocol", it) }
        }
}

internal class GitHubHost private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean =
        other is GitHubHost && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    override fun toString(): String =
        value

    companion object {
        val Public: GitHubHost = GitHubHost("github.com")

        fun parse(value: String): GitHubHost {
            val normalized = value
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trim('/')
                .substringBefore("/")
                .lowercase()
            require(normalized.isNotBlank()) { "GitHub host must not be blank" }
            return GitHubHost(normalized)
        }
    }
}

internal data class GitHubRepositorySearch(
    val query: String,
    val host: GitHubHost,
    val repositories: List<GitHubRepositorySummary>,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("query", query)
            put("host", host.value)
            putJsonArray("repositories") {
                repositories.forEach { repository -> add(repository.toJson()) }
            }
        }
}

internal data class GitHubRepositorySummary(
    val nameWithOwner: String,
    val url: String,
    val description: String?,
    val visibility: String?,
    val updatedAt: String?,
    val pushedAt: String?,
    val defaultBranch: String?,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("nameWithOwner", nameWithOwner)
            put("url", url)
            description?.let { put("description", it) }
            visibility?.let { put("visibility", it) }
            updatedAt?.let { put("updatedAt", it) }
            pushedAt?.let { put("pushedAt", it) }
            defaultBranch?.let { put("defaultBranch", it) }
        }
}

internal class GitHubCliFailure(message: String) : RuntimeException(message)

private data class GitHubRepositoryShorthand(
    val owner: String,
    val repo: String,
) {
    fun url(host: GitHubHost): String =
        "https://${host.value}/$owner/$repo"

    companion object {
        private val Shorthand = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

        fun parseOrNull(value: String): GitHubRepositoryShorthand? {
            if (!Shorthand.matches(value)) {
                return null
            }
            val (owner, repo) = value.split("/")
            return GitHubRepositoryShorthand(owner, repo)
        }
    }
}

private fun ProcessCapture.errorMessage(): String =
    stderr.takeIf { it.isNotBlank() } ?: stdout.takeIf { it.isNotBlank() } ?: "unknown error"
