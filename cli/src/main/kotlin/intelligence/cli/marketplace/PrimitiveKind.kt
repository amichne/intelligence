package intelligence.cli.marketplace

internal enum class PrimitiveKind(
    val sourceName: String,
    val collectionName: String,
) {
    Skill("SKILL", "skills"),
    Agent("AGENT", "agents"),
    Hook("HOOK", "hooks"),
    Instruction("INSTRUCTION", "instructions");

    companion object {
        fun fromSourceName(value: String?): PrimitiveKind? =
            entries.firstOrNull { it.sourceName == value }
    }
}
