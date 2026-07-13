package intelligence.cli.portable

@JvmInline
internal value class MarketplaceId private constructor(private val text: String) {
    fun render(): String = text

    companion object {
        fun parse(raw: String): IdentifierParse<MarketplaceId> = parseIdentifier(raw, ::MarketplaceId)
    }
}
