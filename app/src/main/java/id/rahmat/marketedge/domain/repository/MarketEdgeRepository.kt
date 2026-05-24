package id.rahmat.marketedge.domain.repository

import id.rahmat.marketedge.domain.model.AiStockPick
import id.rahmat.marketedge.domain.model.ChatMessage
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.NewsArticle
import id.rahmat.marketedge.domain.model.PastChampion
import id.rahmat.marketedge.domain.model.WatchlistGroup

interface MarketEdgeRepository {
    fun marketAssets(): List<MarketAsset>
    fun topNews(): List<NewsArticle>
    fun aiPick(): AiStockPick
    fun champions(): List<PastChampion>
    fun watchlists(): List<WatchlistGroup>
    fun initialChat(): List<ChatMessage>
    fun answerFor(query: String): ChatMessage
}
