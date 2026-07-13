package intelligence.cli.portable

@JvmInline
internal value class SkillName private constructor(private val text: String) {
    fun render(): String = text

    companion object {
        fun parse(raw: String): IdentifierParse<SkillName> = parseIdentifier(raw, ::SkillName)
    }
}
