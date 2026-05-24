package id.rahmat.marketedge.domain.model

data class ChatMessage(
    val id: String,
    val text: String,
    val timestamp: String,
    val fromUser: Boolean,
    val recommendations: List<MarketAsset> = emptyList(),
    val relatedNews: List<NewsArticle> = emptyList()
)
