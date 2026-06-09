package intelligence.cli.marketplace

internal enum class MarketplaceProvider(
    val cliName: String,
    val defaultBranch: String,
    private val aliases: Set<String> = emptySet(),
) {
    All("all", "marketplace"),
    Codex("codex", "codex"),
    GitHub("github", "github", aliases = setOf("copilot"));

    val cliNames: Set<String>
        get() = setOf(cliName) + aliases

    companion object {
        fun parse(value: String): MarketplaceProvider =
            entries.firstOrNull { value.lowercase() in it.cliNames }
                ?: throw IllegalArgumentException(
                    "provider must be one of: ${entries.flatMap { it.cliNames }.joinToString(", ")}"
                )
    }
}
