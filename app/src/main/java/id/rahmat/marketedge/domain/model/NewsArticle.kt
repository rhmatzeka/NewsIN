package id.rahmat.marketedge.domain.model

data class NewsArticle(
    val id: String,
    val title: String,
    val source: String,
    val timeAgo: String,
    val category: String,
    val summary: String,
    val content: String,
    val imageColor: Int,
    val tags: List<AssetTag>,
    val isPro: Boolean = false,
    val sourceUrl: String? = null,
    val imageUrl: String? = null
)

data class AssetTag(
    val symbol: String,
    val changePercent: Double
) {
    val isPositive: Boolean = changePercent >= 0.0
}
