package id.rahmat.newsin.domain.model

data class MarketAsset(
    val name: String,
    val symbol: String,
    val price: String,
    val changeValue: String,
    val changePercent: Double,
    val updatedAt: String,
    val sparkline: List<Float>,
    val category: String = "Kripto",
    val source: String = "",
    val unit: String = "",
    val chartId: String? = null,
    val bid: String = price,
    val ask: String = price,
    val dayRange: String = price,
    val yearRange: String = "-",
    val previousClose: String = "-",
    val open: String = "-"
) {
    val isPositive: Boolean = changePercent >= 0.0
}

data class MarketDetailStats(
    val bid: String,
    val ask: String,
    val dayRange: String,
    val yearRange: String,
    val previousClose: String,
    val open: String,
    val volume24h: String,
    val marketCap: String,
    val rank: String,
    val circulatingSupply: String,
    val change7d: Double?,
    val change30d: Double?,
    val change1y: Double?
) {
    val bidAsk: String = if (bid.isBlank() || ask.isBlank()) "-" else "$bid/$ask"
}
