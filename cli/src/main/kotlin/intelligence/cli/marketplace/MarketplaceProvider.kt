package intelligence.cli.marketplace

internal enum class MarketplaceProvider(
    val cliName: String,
    val defaultBranch: String,
) {
    All("all", "marketplace"),
    Codex("codex", "codex"),
    GitHub("github", "github");

    companion object {
        fun parse(value: String): MarketplaceProvider =
            entries.firstOrNull { it.cliName == value.lowercase() }
                ?: throw IllegalArgumentException(
                    "provider must be one of: ${entries.joinToString(", ") { it.cliName }}"
                )
    }
}
