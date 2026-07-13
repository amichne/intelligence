package intelligence.cli.portable

@JvmInline
internal value class PackageName private constructor(private val text: String) {
    fun render(): String = text

    companion object {
        fun parse(raw: String): IdentifierParse<PackageName> = parseIdentifier(raw, ::PackageName)
    }
}
