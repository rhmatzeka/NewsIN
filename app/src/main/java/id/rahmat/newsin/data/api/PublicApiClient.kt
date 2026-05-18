package id.rahmat.newsin.data.api

import android.graphics.Color
import id.rahmat.newsin.domain.model.AssetTag
import id.rahmat.newsin.domain.model.MarketAsset
import id.rahmat.newsin.domain.model.NewsArticle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class PublicApiClient {
    private val idLocale = Locale("id", "ID")

    fun fetchCoinGeckoMarkets(): List<MarketAsset> {
        val endpoint = "https://api.coingecko.com/api/v3/coins/markets" +
            "?vs_currency=usd" +
            "&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=24h"
        val json = request(endpoint)
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val change = item.optDouble("price_change_percentage_24h", 0.0)
            val priceChange = item.optDouble("price_change_24h", 0.0)
            val high = item.optDouble("high_24h", 0.0)
            val low = item.optDouble("low_24h", 0.0)
            val price = item.optDouble("current_price", 0.0)
            MarketAsset(
                name = item.optString("name"),
                symbol = item.optString("symbol").uppercase(Locale.US),
                price = formatUsd(price),
                changeValue = formatSignedUsd(priceChange),
                changePercent = change,
                updatedAt = formatUpdatedAt(item.optString("last_updated")),
                sparkline = parseSparkline(item.optJSONObject("sparkline_in_7d")?.optJSONArray("price")),
                category = "Kripto",
                source = "CoinGecko",
                unit = "${item.optString("symbol").uppercase(Locale.US)}/USD",
                bid = formatUsd(price * 0.9995),
                ask = formatUsd(price * 1.0005),
                dayRange = "${formatUsd(low)} - ${formatUsd(high)}",
                yearRange = "${formatUsd(item.optDouble("atl", 0.0))} - ${formatUsd(item.optDouble("ath", 0.0))}",
                previousClose = formatUsd(price - priceChange),
                open = formatUsd(low)
            )
        }
    }

    fun fetchCurrencyAndCommodityMarkets(): List<MarketAsset> {
        val current = JSONObject(request("https://latest.currency-api.pages.dev/v1/currencies/usd.json"))
        val date = current.optString("date")
        val previousDate = previousDate(date)
        val currentRates = current.getJSONObject("usd")
        val previousRates = runCatching {
            JSONObject(request("https://$previousDate.currency-api.pages.dev/v1/currencies/usd.json")).getJSONObject("usd")
        }.getOrNull()
        val specs = listOf(
            CurrencySpec("USD/IDR", "USD/IDR", "idr", false, "Mata Uang"),
            CurrencySpec("EUR/USD", "Euro", "eur", true, "Mata Uang"),
            CurrencySpec("GBP/USD", "British Pound", "gbp", true, "Mata Uang"),
            CurrencySpec("USD/JPY", "USD/JPY", "jpy", false, "Mata Uang"),
            CurrencySpec("AUD/USD", "Australian Dollar", "aud", true, "Mata Uang"),
            CurrencySpec("USD/SGD", "USD/SGD", "sgd", false, "Mata Uang"),
            CurrencySpec("USD/CNY", "USD/CNY", "cny", false, "Mata Uang"),
            CurrencySpec("USD/MYR", "USD/MYR", "myr", false, "Mata Uang"),
            CurrencySpec("USD/THB", "USD/THB", "thb", false, "Mata Uang"),
            CurrencySpec("USD/KRW", "USD/KRW", "krw", false, "Mata Uang"),
            CurrencySpec("Emas", "XAU/USD", "xau", true, "Komoditas"),
            CurrencySpec("Perak", "XAG/USD", "xag", true, "Komoditas")
        )
        return specs.mapNotNull { spec ->
            val currentRate = currentRates.optDouble(spec.code, Double.NaN)
            if (currentRate.isNaN()) return@mapNotNull null
            val currentPrice = if (spec.invert) 1.0 / currentRate else currentRate
            val previousRate = previousRates?.optDouble(spec.code, Double.NaN)
            val previousPrice = if (previousRate != null && !previousRate.isNaN()) {
                if (spec.invert) 1.0 / previousRate else previousRate
            } else {
                currentPrice
            }
            val change = currentPrice - previousPrice
            val changePercent = if (previousPrice == 0.0) 0.0 else (change / previousPrice) * 100.0
            val history = fetchCurrencyHistory(spec.code, spec.invert, currentPrice)
            val formatter = formatterFor(spec)
            MarketAsset(
                name = spec.name,
                symbol = spec.symbol,
                price = formatter(currentPrice),
                changeValue = formatSignedNumber(change, spec),
                changePercent = changePercent,
                updatedAt = "$date",
                sparkline = history,
                category = spec.category,
                source = "Currency API",
                unit = spec.symbol,
                bid = formatter(currentPrice * 0.9996),
                ask = formatter(currentPrice * 1.0004),
                dayRange = "${formatter(minOf(currentPrice, previousPrice))} - ${formatter(maxOf(currentPrice, previousPrice))}",
                yearRange = "-",
                previousClose = formatter(previousPrice),
                open = formatter(previousPrice)
            )
        }
    }

    fun fetchSpaceflightNews(limit: Int = 20): List<NewsArticle> {
        val json = request("https://api.spaceflightnewsapi.net/v4/articles/?limit=$limit")
        val results = JSONObject(json).getJSONArray("results")
        return List(results.length()) { index ->
            val item = results.getJSONObject(index)
            val title = item.optString("title").trim()
            val source = item.optString("news_site", "Spaceflight News")
            val published = item.optString("published_at")
            NewsArticle(
                id = item.optInt("id", index).toString(),
                title = title,
                source = source,
                timeAgo = relativeTime(published),
                category = inferNewsCategory(title, source),
                summary = item.optString("summary").trim(),
                content = item.optString("summary").trim(),
                imageColor = colorFor(title.ifBlank { source }),
                tags = emptyList<AssetTag>(),
                isPro = false,
                sourceUrl = item.optString("url"),
                imageUrl = item.optString("image_url")
            )
        }.filter { it.title.isNotBlank() }
    }

    private fun inferNewsCategory(title: String, source: String): String {
        val text = "$title $source".lowercase(Locale.US)
        return when {
            "nasa" in text -> "NASA"
            "spacex" in text || "starship" in text || "falcon" in text -> "SpaceX"
            "policy" in text || "china" in text || "europe" in text || "india" in text || "japan" in text -> "World"
            "science" in text || "technology" in text || "cargo" in text || "station" in text -> "Technology"
            else -> "Real News"
        }
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NewsIN Android")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) error("HTTP $code: $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSparkline(values: JSONArray?): List<Float> {
        if (values == null || values.length() == 0) return emptyList()
        val step = (values.length() / 24).coerceAtLeast(1)
        val points = mutableListOf<Float>()
        var index = 0
        while (index < values.length()) {
            points.add(values.optDouble(index).toFloat())
            index += step
        }
        return points.takeLast(28)
    }

    private fun fetchCurrencyHistory(code: String, invert: Boolean, fallback: Double): List<Float> {
        val dates = lastDates(7)
        val values = mutableListOf<Float>()
        dates.forEach { date ->
            val value = runCatching {
                val rates = JSONObject(request("https://$date.currency-api.pages.dev/v1/currencies/usd.json")).getJSONObject("usd")
                val rate = rates.optDouble(code, Double.NaN)
                if (rate.isNaN()) fallback else if (invert) 1.0 / rate else rate
            }.getOrDefault(fallback)
            values.add(value.toFloat())
        }
        return values
    }

    private fun formatUsd(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = if (abs(value) < 10) 4 else 2
            minimumFractionDigits = if (abs(value) < 10) 2 else 0
        }
        return "$" + formatter.format(value)
    }

    private fun formatIdrLike(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }
        return formatter.format(value)
    }

    private fun formatPlain(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale).apply {
            maximumFractionDigits = if (abs(value) < 10) 4 else 2
            minimumFractionDigits = if (abs(value) < 10) 2 else 0
        }
        return formatter.format(value)
    }

    private fun formatSignedUsd(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return sign + formatUsd(abs(value))
    }

    private fun formatSignedNumber(value: Double, spec: CurrencySpec): String {
        val sign = if (value >= 0) "+" else "-"
        return sign + formatterFor(spec)(abs(value))
    }

    private fun formatterFor(spec: CurrencySpec): (Double) -> String = when {
        spec.code == "idr" -> ::formatIdrLike
        spec.invert -> ::formatUsd
        else -> ::formatPlain
    }

    private fun formatUpdatedAt(iso: String): String {
        val date = parseIso(iso) ?: return "baru saja"
        return SimpleDateFormat("HH:mm 'WIB'", idLocale).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(date)
    }

    private fun relativeTime(iso: String): String {
        val date = parseIso(iso) ?: return "baru saja"
        val minutes = ((System.currentTimeMillis() - date.time) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "baru saja"
            minutes < 60 -> "$minutes menit yang lalu"
            minutes < 24 * 60 -> "${minutes / 60} jam yang lalu"
            else -> "${minutes / (24 * 60)} hari yang lalu"
        }
    }

    private fun parseIso(iso: String): Date? {
        val normalized = iso.replace(Regex("\\.\\d+Z$"), "Z")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)
        }.getOrNull()
    }

    private fun colorFor(seed: String): Int {
        val colors = intArrayOf(
            Color.rgb(255, 107, 0),
            Color.rgb(55, 114, 207),
            Color.rgb(27, 166, 115),
            Color.rgb(195, 125, 13),
            Color.rgb(212, 86, 86)
        )
        return colors[abs(seed.hashCode()) % colors.size]
    }

    private fun previousDate(date: String): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = formatter.parse(date) ?: Date()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return formatter.format(calendar.time)
    }

    private fun lastDates(count: Int): List<String> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dates = mutableListOf<String>()
        repeat(count) {
            dates.add(formatter.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dates.reversed()
    }

    private data class CurrencySpec(
        val name: String,
        val symbol: String,
        val code: String,
        val invert: Boolean,
        val category: String
    )
}
