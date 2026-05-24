package id.rahmat.marketedge.domain.model

data class WatchlistGroup(
    val name: String,
    val symbolCount: Int,
    val assets: List<MarketAsset>
)
