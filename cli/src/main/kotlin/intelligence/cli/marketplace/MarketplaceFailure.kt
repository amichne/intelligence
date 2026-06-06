package intelligence.cli.marketplace

internal sealed class MarketplaceFailure(
    message: String,
    val exitCode: Int = 1,
) : RuntimeException(message) {
    class InvalidSource(message: String) : MarketplaceFailure(message)
    class StaleMainMarketplaces(val paths: List<String>) :
        MarketplaceFailure("main marketplace projections are out of date", exitCode = 1)
}
