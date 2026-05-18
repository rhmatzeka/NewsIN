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
    val bid: String = price,
    val ask: String = price,
    val dayRange: String = price,
    val yearRange: String = "-",
    val previousClose: String = "-",
    val open: String = "-"
) {
    val isPositive: Boolean = changePercent >= 0.0
}
