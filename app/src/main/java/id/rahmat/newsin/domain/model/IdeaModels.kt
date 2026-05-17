package id.rahmat.newsin.domain.model

data class AiStockPick(
    val market: String,
    val totalReturn: String,
    val benchmarkReturn: String,
    val highlight: String
)

data class PastChampion(
    val symbol: String,
    val companyName: String,
    val returnPercent: String,
    val dateAdded: String,
    val dateRemoved: String,
    val addedPrice: String,
    val removedPrice: String,
    val thesis: String
)
