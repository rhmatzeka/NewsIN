package id.rahmat.newsin.domain.repository

import id.rahmat.newsin.domain.model.AiStockPick
import id.rahmat.newsin.domain.model.ChatMessage
import id.rahmat.newsin.domain.model.MarketAsset
import id.rahmat.newsin.domain.model.NewsArticle
import id.rahmat.newsin.domain.model.PastChampion
import id.rahmat.newsin.domain.model.WatchlistGroup

interface NewsInRepository {
    fun marketAssets(): List<MarketAsset>
    fun topNews(): List<NewsArticle>
    fun aiPick(): AiStockPick
    fun champions(): List<PastChampion>
    fun watchlists(): List<WatchlistGroup>
    fun initialChat(): List<ChatMessage>
    fun answerFor(query: String): ChatMessage
}
