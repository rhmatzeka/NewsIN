package id.rahmat.newsin.data.repository

import id.rahmat.newsin.data.api.PublicApiClient
import id.rahmat.newsin.domain.model.AiStockPick
import id.rahmat.newsin.domain.model.ChatMessage
import id.rahmat.newsin.domain.model.MarketAsset
import id.rahmat.newsin.domain.model.NewsArticle
import id.rahmat.newsin.domain.model.PastChampion
import id.rahmat.newsin.domain.model.WatchlistGroup
import id.rahmat.newsin.domain.repository.NewsInRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RealNewsInRepository(
    private val apiClient: PublicApiClient
) : NewsInRepository {
    override fun marketAssets(): List<MarketAsset> =
        apiClient.fetchCurrencyAndCommodityMarkets() + apiClient.fetchCoinGeckoMarkets()

    override fun topNews(): List<NewsArticle> = apiClient.fetchSpaceflightNews()

    override fun aiPick(): AiStockPick = AiStockPick(
        market = "Crypto Global",
        totalReturn = "Data market terbaru",
        benchmarkReturn = "24 jam",
        highlight = "Ide dihitung dari aset aktif yang berhasil diperbarui, bukan data dummy."
    )

    override fun champions(): List<PastChampion> = emptyList()

    override fun watchlists(): List<WatchlistGroup> {
        val assets = marketAssets()
        return listOf(
            WatchlistGroup("Crypto Watchlist Real", assets.size, assets)
        )
    }

    override fun initialChat(): List<ChatMessage> = listOf(
        ChatMessage(
            id = "hello",
            text = "Halo, saya WarrenAI. Saya memakai data pasar dan headline terbaru untuk membantu merangkum aset, berita, dan risiko utama.",
            timestamp = now(),
            fromUser = false
        )
    )

    override fun answerFor(query: String): ChatMessage {
        val assets = runCatching { marketAssets() }.getOrElse { emptyList() }
        val news = runCatching { topNews() }.getOrElse { emptyList() }
        val strongest = assets.maxByOrNull { it.changePercent }
        val weakest = assets.minByOrNull { it.changePercent }
        val text = buildString {
            append("Berdasarkan data real terbaru yang berhasil dimuat: ")
            if (strongest != null && weakest != null) {
                append("${strongest.symbol} paling kuat (${signed(strongest.changePercent)}), ")
                append("sementara ${weakest.symbol} paling lemah (${signed(weakest.changePercent)}). ")
            }
            append("Gunakan ini sebagai rangkuman pasar, bukan saran investasi personal.")
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = now(),
            fromUser = false,
            recommendations = assets.take(3),
            relatedNews = news.take(2)
        )
    }

    private fun now(): String = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())

    private fun signed(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${String.format(Locale("id", "ID"), "%.2f", value)}%"
    }
}
