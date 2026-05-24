package id.rahmat.marketedge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import id.rahmat.marketedge.data.api.PublicApiClient
import id.rahmat.marketedge.data.repository.RealMarketEdgeRepository
import id.rahmat.marketedge.domain.model.ChatMessage
import id.rahmat.marketedge.domain.model.MarketAsset
import id.rahmat.marketedge.domain.model.MarketDetailStats
import id.rahmat.marketedge.domain.model.NewsArticle
import id.rahmat.marketedge.domain.model.PastChampion
import id.rahmat.marketedge.presentation.components.SparklineView
import id.rahmat.marketedge.presentation.components.addGap
import id.rahmat.marketedge.presentation.components.card
import id.rahmat.marketedge.presentation.components.chip
import id.rahmat.marketedge.presentation.components.dp
import id.rahmat.marketedge.presentation.components.editText
import id.rahmat.marketedge.presentation.components.horizontalChips
import id.rahmat.marketedge.presentation.components.iconButton
import id.rahmat.marketedge.presentation.components.rounded
import id.rahmat.marketedge.presentation.components.roundedRaw
import id.rahmat.marketedge.presentation.components.screenScroll
import id.rahmat.marketedge.presentation.components.sectionHeader
import id.rahmat.marketedge.presentation.components.text
import id.rahmat.marketedge.presentation.components.topBar
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val apiClient = PublicApiClient()
    private val repository = RealMarketEdgeRepository(apiClient)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val handler = Handler(Looper.getMainLooper())
    private val imageCache = ConcurrentHashMap<String, Bitmap>()
    private val articleContentCache = ConcurrentHashMap<String, String>()
    private val articleContentErrors = ConcurrentHashMap<String, String>()
    private val articleContentLoading = ConcurrentHashMap<String, Boolean>()
    private val marketChartCache = ConcurrentHashMap<String, List<Float>>()
    private val marketChartErrors = ConcurrentHashMap<String, String>()
    private val marketChartLoading = ConcurrentHashMap<String, Boolean>()
    private val marketDetailStatsCache = ConcurrentHashMap<String, MarketDetailStats>()
    private val marketDetailStatsErrors = ConcurrentHashMap<String, String>()
    private val marketDetailStatsLoading = ConcurrentHashMap<String, Boolean>()
    private val chatMessages = mutableListOf<ChatMessage>()
    private var activeAiSurface = AiSurface.PAGE
    private var marketAssets = emptyList<MarketAsset>()
    private var newsArticles = emptyList<NewsArticle>()
    private val watchlistSymbols = linkedSetOf("BTC", "ETH", "SOL")
    private val savedArticleIds = linkedSetOf<String>()
    private var watchlistEditMode = false
    private var marketLoading = false
    private var newsLoading = false
    private var marketError: String? = null
    private var newsError: String? = null
    private var selectedMarketCategory = "Populer 🔥"
    private val marketCategories = listOf("Populer 🔥", "Indeks", "Futures Indeks", "Saham", "Kripto", "Komoditas", "Mata Uang")
    private var selectedMarketChartRange = "1Mgg"
    private var activeMarketChartKey: String? = null
    private var activeMarketSymbol: String? = null
    private var selectedMarketDetailTab = "Ikhtisar"
    private var marketDetailExpanded = false
    private var selectedNewsCategory = "Real News"
    private val newsCategories = listOf("Real News", "Latest", "NASA", "SpaceX", "World", "Technology")
    private lateinit var content: FrameLayout
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.marketedge_surface)
        requestNotificationPermission()
        showSplash()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun showSplash() {
        val splash = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.marketedge_background))
            val logo = text("MarketEdge", 42f, R.color.marketedge_accent, Typeface.BOLD).apply {
                alpha = 0f
                gravity = Gravity.CENTER
            }
            val subtitle = text("Market intelligence, berita, dan WarrenAI", 14f, R.color.marketedge_text_secondary).apply {
                alpha = 0f
                gravity = Gravity.CENTER
            }
            val stack = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(logo)
                addGap(6)
                addView(subtitle)
            }
            addView(stack, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            logo.animate().alpha(1f).translationY(-10f).setDuration(700).start()
            subtitle.animate().alpha(1f).translationY(-10f).setDuration(900).setStartDelay(250).start()
        }
        setContentView(splash)
        handler.postDelayed({ showMain() }, 2000)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun showMain() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.marketedge_background))
        }
        content = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.marketedge_background))
        }
        bottomNav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.bottom_nav_menu)
            setBackgroundColor(getColor(R.color.marketedge_surface))
            elevation = dp(10).toFloat()
            setPadding(0, dp(4), 0, dp(6))
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconTintList = navTint()
            itemTextColor = navTint()
            itemRippleColor = ColorStateList.valueOf(getColor(R.color.marketedge_card_soft))
            itemActiveIndicatorColor = ColorStateList.valueOf(Color.argb(42, 255, 107, 0))
            setOnItemSelectedListener { item ->
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                when (item.itemId) {
                    R.id.nav_market -> renderMarket()
                    R.id.nav_news -> renderNews()
                    R.id.nav_ideas -> renderIdeas()
                    R.id.nav_watchlist -> renderWatchlist()
                    R.id.nav_more -> renderMore()
                }
                true
            }
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        setContentView(root)
        applyInsets(root)
        bottomNav.selectedItemId = R.id.nav_market
    }

    private fun navTint(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(getColor(R.color.marketedge_accent), getColor(R.color.marketedge_text_muted))
    )

    private fun syncBottomNav(itemId: Int) {
        if (!::bottomNav.isInitialized) return
        bottomNav.visibility = View.VISIBLE
        if (bottomNav.selectedItemId != itemId) {
            bottomNav.menu.findItem(itemId)?.isChecked = true
        }
    }

    private fun applyInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
    }

    private fun display(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun displayScroll(container: LinearLayout) {
        display(container.tag as View)
    }

    private fun renderMarket() {
        syncBottomNav(R.id.nav_market)
        val screen = screenScroll()
        screen.addView(topBar("MarketEdge", showLogo = true) { renderSearch() })
        screen.addView(marketCategoryChips())
        when {
            marketLoading -> {
                screen.addView(loadingCard("Memperbarui data pasar terkini..."))
                screen.addGap(10)
            }
            marketError != null -> {
                screen.addView(errorCard("Gagal memuat market real", marketError.orEmpty()) { loadMarkets(force = true) { renderMarket() } })
                screen.addGap(10)
            }
            marketAssets.isEmpty() -> {
                screen.addView(loadingCard("Menyiapkan ringkasan pasar..."))
                loadMarkets { renderMarket() }
                screen.addGap(10)
            }
            else -> {
                val visibleAssets = filteredMarketAssets()
                screen.addView(marketOverview(visibleAssets))
                screen.addGap(10)
                screen.addView(sectionHeader(selectedMarketTitle(), "${visibleAssets.size} instrumen"))
                screen.addGap(6)
                if (visibleAssets.isEmpty()) {
                    screen.addView(emptyMarketCategoryCard())
                } else {
                    visibleAssets.forEachIndexed { index, asset ->
                        screen.addView(marketItem(asset))
                        if (index < visibleAssets.lastIndex) screen.addView(divider())
                    }
                }
            }
        }
        screen.addGap(6)
        screen.addView(infoCard("Ketuk instrumen untuk melihat detail harga, grafik, dan statistik pasar."))
        displayScroll(screen)
    }

    private fun marketCategoryChips(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        marketCategories.forEach { category ->
            row.addView(chip(category, category == selectedMarketCategory).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedMarketCategory = category
                    renderMarket()
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun filteredMarketAssets(): List<MarketAsset> {
        val assets = when (selectedMarketCategory) {
            "Kripto" -> marketAssets.filter { it.category == "Kripto" }
            "Komoditas" -> marketAssets.filter { it.category == "Komoditas" }
            "Mata Uang" -> marketAssets.filter { it.category == "Mata Uang" }
            "Saham", "Indeks", "Futures Indeks" -> marketAssets.filter { it.category == selectedMarketCategory }
            else -> marketAssets.sortedByDescending { kotlin.math.abs(it.changePercent) }
        }
        return assets
    }

    private fun selectedMarketTitle(): String =
        if (selectedMarketCategory == "Populer 🔥") "Populer" else selectedMarketCategory

    private fun marketOverview(assets: List<MarketAsset>): View = card().apply {
        val gainers = assets.count { it.isPositive }
        val losers = assets.size - gainers
        addView(text("${assets.size} aset aktif", 20f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(4)
        addView(text("Naik $gainers • Turun $losers • Filter: ${selectedMarketTitle()}", 13f, R.color.marketedge_text_muted))
        val leader = assets.maxByOrNull { it.changePercent }
        if (leader != null) {
            addGap(8)
            addView(text("Top mover: ${leader.symbol} ${formatPercent(leader.changePercent)}", 14f, if (leader.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        }
    }

    private fun emptyMarketCategoryCard(): View = card().apply {
        addView(text("Belum ada data ${selectedMarketTitle()}", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(6)
        addView(text("Kategori ini belum tersedia. Pantau crypto, mata uang, emas, dan perak dari daftar pasar aktif.", 13f, R.color.marketedge_text_secondary))
    }

    private fun marketItem(asset: MarketAsset): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(12), dp(7), dp(10), dp(7))
            background = rounded(R.color.marketedge_surface, 0)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                openMarketDetail(asset)
            }
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(asset.name, 16f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addGap(2)
            val meta = if (asset.unit.isNotBlank()) "${asset.updatedAt} | ${asset.unit}" else "${asset.updatedAt} | ${asset.symbol}"
            addView(text(meta, 11f, R.color.marketedge_text_muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(text(asset.price, 18f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                gravity = Gravity.END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addGap(2)
            addView(text("${asset.changeValue} (${formatPercent(asset.changePercent)})", 12f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD).apply {
                gravity = Gravity.END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f))
        return row
    }

    private fun openMarketDetail(asset: MarketAsset, refreshData: Boolean = true) {
        if (activeMarketSymbol != asset.symbol) {
            activeMarketSymbol = asset.symbol
            selectedMarketDetailTab = "Ikhtisar"
            selectedMarketChartRange = "1Mgg"
            marketDetailExpanded = false
        }
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(marketDetailBar(asset))
        screen.addGap(8)
        screen.addView(marketDetailQuoteCard(asset))
        screen.addGap(10)
        screen.addView(marketDetailTabs(asset))
        screen.addGap(4)
        val chartKey = marketChartKey(asset)
        val chartData = marketChartCache[chartKey] ?: asset.sparkline
        screen.addView(marketChartToolbar(asset, chartKey))
        screen.addGap(8)
        screen.addView(FrameLayout(this).apply {
            background = rounded(R.color.marketedge_background, 0)
            addView(SparklineView(context).apply {
                submit(chartData.ifEmpty { listOf(1f, 1.2f, 1.1f, 1.4f, 1.3f) }, asset.isPositive, largeChart = true)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(text(asset.price, 13f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(R.color.marketedge_card, 4, R.color.marketedge_hairline)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
            when {
                marketChartLoading[chartKey] == true -> addView(text("Memperbarui grafik...", 12f, R.color.marketedge_accent, Typeface.BOLD).apply {
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = rounded(R.color.marketedge_card, 4, R.color.marketedge_hairline)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
                marketChartErrors[chartKey] != null -> addView(text("Grafik sementara", 12f, R.color.marketedge_text_muted, Typeface.BOLD).apply {
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = rounded(R.color.marketedge_card, 4, R.color.marketedge_hairline)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))
        screen.addGap(6)
        screen.addView(marketChartRangeChips(asset))
        activeMarketChartKey = chartKey
        if (refreshData) {
            loadMarketChart(asset) {
                if (activeMarketChartKey == chartKey) openMarketDetail(asset, refreshData = false)
            }
            loadMarketDetailStats(asset) {
                if (activeMarketSymbol == asset.symbol) openMarketDetail(asset, refreshData = false)
            }
        }
        screen.addGap(18)
        screen.addView(marketDetailTabContent(asset))
        displayScroll(screen)
    }

    private fun marketDetailQuoteCard(asset: MarketAsset): View = card().apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(asset.name, 13f, R.color.marketedge_text_muted, Typeface.BOLD).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addGap(3)
            addView(text(asset.price, 28f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(text(formatPercent(asset.changePercent), 18f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD).apply {
                gravity = Gravity.END
            })
            addGap(3)
            addView(text(asset.changeValue, 13f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD).apply {
                gravity = Gravity.END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.78f))
        addView(row)
        addGap(6)
        addView(text("${asset.updatedAt} - ${asset.source.ifBlank { "Market feed" }}", 11f, R.color.marketedge_text_muted))
    }

    private fun marketChartToolbar(asset: MarketAsset, chartKey: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(selectedMarketChartRange, 12f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
                setOnClickListener { cycleMarketChartRange(asset) }
            })
            addView(text(if (marketChartLoading[chartKey] == true) "Memperbarui..." else "Grafik harga", 12f, R.color.marketedge_text_muted, Typeface.BOLD).apply {
                setPadding(dp(10), 0, dp(10), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(secondaryActionButton("Teknikal") {
                selectedMarketDetailTab = "Teknikal"
                openMarketDetail(asset, refreshData = false)
            }, LinearLayout.LayoutParams(dp(92), dp(34)))
        }

    private fun marketDetailTabs(asset: MarketAsset): HorizontalScrollView {
        val labels = listOf("Ikhtisar", "Teknikal", "Berita", "Analisis", "Data")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        labels.forEach { label ->
            row.addView(chip(label, label == selectedMarketDetailTab).apply {
                setOnClickListener {
                    if (label == selectedMarketDetailTab) return@setOnClickListener
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedMarketDetailTab = label
                    openMarketDetail(asset, refreshData = false)
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun marketDetailTabContent(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            when (selectedMarketDetailTab) {
                "Teknikal" -> addTechnicalTab(asset)
                "Berita" -> addNewsTab(asset)
                "Analisis" -> addAnalysisTab(asset)
                "Data" -> addDataTab(asset)
                else -> addOverviewTab(asset)
            }
        }

    private fun LinearLayout.addOverviewTab(asset: MarketAsset) {
        val stats = marketDetailStats(asset)
        addView(detailStatsCard(asset, stats))
    }

    private fun detailStatsCard(asset: MarketAsset, stats: MarketDetailStats?): View = card().apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addDetailStatRows(stats)
        addGap(8)
        addMoreButton(asset)
    }

    private fun LinearLayout.addDetailStatRows(stats: MarketDetailStats?) {
        addView(statRow("Bid/Ask", detailValue(stats) { it.bidAsk }))
        addView(detailDivider())
        addView(statRow("Rentang harian", detailValue(stats) { it.dayRange }))
        addView(detailDivider())
        addView(statRow("Rentang 52mgg", detailValue(stats) { it.yearRange }))
        addView(detailDivider())
        addView(statRow("Close Sebelumnya", detailValue(stats) { it.previousClose }))
        addView(detailDivider())
        addView(statRow("Pembukaan", detailValue(stats) { it.open }))
        if (marketDetailExpanded) {
            addView(detailDivider())
            addView(statRow("Volume 24j", detailValue(stats) { it.volume24h }))
            addView(detailDivider())
            addView(statRow("Market Cap", detailValue(stats) { it.marketCap }))
            addView(detailDivider())
            addView(statRow("Peringkat", detailValue(stats) { it.rank }))
            addView(detailDivider())
            addView(statRow("Supply Beredar", detailValue(stats) { it.circulatingSupply }))
        }
    }

    private fun LinearLayout.addTechnicalTab(asset: MarketAsset) {
        val points = marketChartCache[marketChartKey(asset)] ?: asset.sparkline
        val first = points.firstOrNull()
        val last = points.lastOrNull()
        val high = points.maxOrNull()
        val low = points.minOrNull()
        val momentum = if (first != null && last != null && first != 0f) ((last - first) / first) * 100.0 else null
        val volatility = if (first != null && high != null && low != null && first != 0f) ((high - low) / first) * 100.0 else null
        val trend = when {
            momentum == null -> "Memperbarui..."
            momentum >= 0 -> "Naik ${formatPercent(momentum)}"
            else -> "Turun ${formatPercent(momentum)}"
        }
        addView(card().apply {
            addView(text("Sinyal Teknikal", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
            addGap(6)
            addView(text("Dihitung dari histori harga pada rentang $selectedMarketChartRange.", 13f, R.color.marketedge_text_muted))
        })
        addGap(12)
        addView(statRow("Trend Rentang", trend))
        addView(detailDivider())
        addView(statRow("Harga Tertinggi", high?.let { formatRawPrice(asset, it.toDouble()) } ?: "-"))
        addView(detailDivider())
        addView(statRow("Harga Terendah", low?.let { formatRawPrice(asset, it.toDouble()) } ?: "-"))
        addView(detailDivider())
        addView(statRow("Volatilitas", volatility?.let { formatPercent(it) } ?: "-"))
    }

    private fun LinearLayout.addNewsTab(asset: MarketAsset) {
        if (newsArticles.isEmpty()) loadNews { if (activeMarketSymbol == asset.symbol) openMarketDetail(asset, refreshData = false) }
        val related = newsArticles.filter {
            it.title.contains(asset.symbol, ignoreCase = true) ||
                it.title.contains(asset.name, ignoreCase = true) ||
                it.summary.contains(asset.symbol, ignoreCase = true) ||
                it.summary.contains(asset.name, ignoreCase = true)
        }.ifEmpty { newsArticles.take(5) }
        if (related.isEmpty()) {
            addView(loadingCard("Memuat headline pasar..."))
            return
        }
        addView(sectionHeader("Headline Pasar", "${related.size} berita"))
        addGap(10)
        related.take(5).forEach { article ->
            addView(newsSmall(article))
            addGap(10)
        }
    }

    private fun LinearLayout.addAnalysisTab(asset: MarketAsset) {
        val stats = marketDetailStats(asset)
        val points = marketChartCache[marketChartKey(asset)] ?: asset.sparkline
        val momentum = if (points.size >= 2 && points.first() != 0f) {
            ((points.last() - points.first()) / points.first()) * 100.0
        } else {
            null
        }
        addView(card().apply {
            addView(text("Ringkasan Analisis", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
            addGap(8)
            addView(text(buildString {
                append("${asset.symbol} bergerak ${formatPercent(asset.changePercent)} dalam 24 jam. ")
                momentum?.let { append("Pada rentang $selectedMarketChartRange, momentumnya ${formatPercent(it)}. ") }
                if (stats != null) append("Rentang harian ${stats.dayRange}, volume 24j ${stats.volume24h}. ")
                append("Gunakan ini sebagai konteks awal, bukan saran beli/jual.")
            }, 14f, R.color.marketedge_text_secondary))
            addGap(12)
            addView(actionButton("Tanyakan AI tentang ${asset.symbol}") { askAiAboutAsset(asset) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        })
    }

    private fun LinearLayout.addDataTab(asset: MarketAsset) {
        val stats = marketDetailStats(asset)
        addView(card().apply {
            addView(text("Data Pasar", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
            addGap(6)
            addView(text("${asset.unit.ifBlank { asset.symbol }} • ${asset.updatedAt}", 13f, R.color.marketedge_text_muted))
        })
        addGap(12)
        addView(detailStatsCard(asset, stats))
    }

    private fun LinearLayout.addMoreButton(asset: MarketAsset) {
        addView(text(if (marketDetailExpanded) "Sembunyikan detail" else "Tampilkan data lainnya", 13f, R.color.marketedge_accent, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(R.color.marketedge_card_soft, 8, R.color.marketedge_hairline)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                marketDetailExpanded = !marketDetailExpanded
                openMarketDetail(asset, refreshData = false)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
    }

    private fun detailValue(stats: MarketDetailStats?, value: (MarketDetailStats) -> String): String =
        stats?.let(value)?.takeIf { it.isNotBlank() } ?: when {
            marketDetailStatsLoading.values.any { it } -> "Memperbarui..."
            else -> "-"
        }

    private fun marketDetailStats(asset: MarketAsset): MarketDetailStats? =
        marketDetailStatsCache[marketDetailStatsKey(asset)]

    private fun marketDetailStatsKey(asset: MarketAsset): String =
        asset.chartId ?: asset.symbol

    private fun formatRawPrice(asset: MarketAsset, value: Double): String =
        if (asset.category == "Kripto" || asset.symbol.contains("/USD")) {
            "$" + String.format(Locale("id", "ID"), if (kotlin.math.abs(value) < 10) "%.4f" else "%.2f", value)
        } else {
            String.format(Locale("id", "ID"), if (kotlin.math.abs(value) < 10) "%.4f" else "%.2f", value)
        }

    private fun marketChartRangeChips(asset: MarketAsset): HorizontalScrollView {
        val labels = listOf("1Hr", "1Mgg", "1Bln", "1Thn", "5Thn", "Maks")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        labels.forEach { label ->
            row.addView(chip(label, label == selectedMarketChartRange).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedMarketChartRange = label
                    openMarketDetail(asset)
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun cycleMarketChartRange(asset: MarketAsset) {
        val labels = listOf("1Hr", "1Mgg", "1Bln", "1Thn", "5Thn", "Maks")
        val next = (labels.indexOf(selectedMarketChartRange).takeIf { it >= 0 } ?: 0) + 1
        selectedMarketChartRange = labels[next % labels.size]
        if (::bottomNav.isInitialized) bottomNav.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        openMarketDetail(asset)
    }

    private fun marketChartKey(asset: MarketAsset): String =
        "${asset.chartId ?: asset.symbol}:${chartDays()}"

    private fun chartDays(): Int = when (selectedMarketChartRange) {
        "1Hr" -> 1
        "1Bln" -> 30
        "1Thn" -> 365
        "5Thn" -> 365 * 5
        "Maks" -> 365 * 10
        else -> 7
    }

    private fun loadMarketChart(asset: MarketAsset, done: () -> Unit) {
        val days = chartDays()
        val key = "${asset.chartId ?: asset.symbol}:$days"
        if (marketChartCache.containsKey(key) || marketChartLoading.putIfAbsent(key, true) == true) return
        marketChartErrors.remove(key)
        executor.execute {
            val result = runCatching { apiClient.fetchMarketChart(asset, days) }
            runOnUiThread {
                result.onSuccess { points ->
                    if (points.size >= 2) {
                        marketChartCache[key] = points
                    } else {
                        marketChartErrors[key] = "Data grafik belum tersedia"
                    }
                }.onFailure {
                    marketChartErrors[key] = it.message ?: it.javaClass.simpleName
                }
                marketChartLoading.remove(key)
                done()
            }
        }
    }

    private fun loadMarketDetailStats(asset: MarketAsset, done: () -> Unit) {
        val key = marketDetailStatsKey(asset)
        if (marketDetailStatsCache.containsKey(key) || marketDetailStatsLoading.putIfAbsent(key, true) == true) return
        marketDetailStatsErrors.remove(key)
        executor.execute {
            val result = runCatching { apiClient.fetchMarketDetailStats(asset) }
            runOnUiThread {
                result.onSuccess { marketDetailStatsCache[key] = it }
                    .onFailure { marketDetailStatsErrors[key] = it.message ?: it.javaClass.simpleName }
                marketDetailStatsLoading.remove(key)
                done()
            }
        }
    }

    private fun marketDetailBar(asset: MarketAsset): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(drawableToolbarButton("Kembali", R.drawable.ic_back) { renderMarket() }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(asset.symbol, 20f, R.color.marketedge_text_primary, Typeface.BOLD))
                addView(text(asset.unit.ifBlank { asset.source }, 11f, R.color.marketedge_text_muted).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(detailToolbarButton("Cari", "⌕") {
                renderSearch { openMarketDetail(asset, refreshData = false) }
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(6) })
            addView(detailToolbarButton("Tanyakan AI", "AI") {
                askAiAboutAsset(asset)
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(6) })
            addView(text(if (isInWatchlist(asset)) "★" else "☆", 25f, if (isInWatchlist(asset)) R.color.marketedge_accent else R.color.marketedge_text_muted, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                contentDescription = "Watchlist"
                background = rounded(R.color.marketedge_card, 18)
                setOnClickListener {
                    toggleWatchlist(asset)
                    openMarketDetail(asset, refreshData = false)
                }
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
    }

    private fun detailToolbarButton(label: String, symbol: String, onClick: () -> Unit): TextView =
        text(symbol, if (symbol.length <= 1) 18f else 12f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            contentDescription = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = rounded(R.color.marketedge_card_soft, 12, R.color.marketedge_hairline)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    private fun drawableToolbarButton(label: String, drawableRes: Int, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            contentDescription = label
            setImageResource(drawableRes)
            setColorFilter(getColor(R.color.marketedge_text_primary))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(R.color.marketedge_card_soft, 12, R.color.marketedge_hairline)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    private fun renderNews() {
        syncBottomNav(R.id.nav_news)
        val screen = screenScroll()
        screen.addView(topBar("Berita") { renderSearch { renderNews() } })
        screen.addView(newsCategoryChips())
        when {
            newsLoading -> {
                screen.addView(loadingCard("Memperbarui berita terbaru..."))
                displayScroll(screen)
                return
            }
            newsError != null -> {
                screen.addView(errorCard("Gagal memuat berita real", newsError.orEmpty()) { loadNews(force = true) { renderNews() } })
                displayScroll(screen)
                return
            }
            newsArticles.isEmpty() -> {
                screen.addView(loadingCard("Menyiapkan headline pilihan..."))
                loadNews { renderNews() }
                displayScroll(screen)
                return
            }
        }
        val visibleNews = filteredNewsArticles()
        if (visibleNews.isEmpty()) {
            screen.addView(emptyNewsCategoryCard())
            displayScroll(screen)
            return
        }
        screen.addView(newsBrief(visibleNews))
        screen.addGap(10)
        screen.addView(featuredNews(visibleNews.first()))
        screen.addGap(10)
        screen.addView(sectionHeader(if (selectedNewsCategory == "Latest") "Berita Terbaru" else selectedNewsCategory, "${visibleNews.size} artikel"))
        screen.addGap(10)
        visibleNews.drop(1).forEach { article ->
            screen.addView(newsSmall(article))
            screen.addGap(10)
        }
        displayScroll(screen)
    }

    private fun newsCategoryChips(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        newsCategories.forEach { category ->
            row.addView(chip(category, category == selectedNewsCategory).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedNewsCategory = category
                    renderNews()
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun filteredNewsArticles(): List<NewsArticle> = when (selectedNewsCategory) {
        "Latest", "Real News" -> newsArticles
        else -> newsArticles.filter { it.category == selectedNewsCategory }
    }

    private fun newsBrief(articles: List<NewsArticle>): View = card().apply {
        addView(text("${articles.size} headline aktif", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(4)
        addView(text("Headline terbaru • Filter: $selectedNewsCategory", 12f, R.color.marketedge_text_muted))
    }

    private fun emptyNewsCategoryCard(): View = card().apply {
        addView(text("Belum ada berita $selectedNewsCategory", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(6)
        addView(text("Coba kategori Latest atau Real News untuk melihat semua headline terbaru yang tersedia.", 13f, R.color.marketedge_text_secondary))
    }

    private fun featuredNews(article: NewsArticle): View =
        FrameLayout(this).apply {
            addView(card().apply {
                addView(articleImage(article, 172))
                addGap(12)
                addView(text(article.title, 20f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                })
                addGap(8)
                addView(text("${article.source} • ${article.timeAgo}", 12f, R.color.marketedge_text_muted))
                addGap(10)
                addTagRow(article)
                addGap(10)
                addView(actionButton("Tanyakan AI") { askAiAboutNews(article) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
                setOnClickListener { openNewsDetail(article) }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(newsBookmarkButton(article) { renderNews() }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(10)
                rightMargin = dp(10)
            })
        }

    private fun newsSmall(article: NewsArticle): View =
        FrameLayout(this).apply {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(42), dp(12))
                background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
                setOnClickListener { openNewsDetail(article) }
            }
            row.addView(articleImage(article, 84, compact = true), LinearLayout.LayoutParams(dp(92), dp(84)).apply { marginEnd = dp(12) })
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(article.title, 15f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(text("${article.source} • ${article.timeAgo}", 12f, R.color.marketedge_text_muted))
                addGap(6)
                addTagRow(article)
                addGap(8)
                addView(secondaryActionButton("Tanyakan AI") { askAiAboutNews(article) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
            }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(newsBookmarkButton(article) { renderNews() }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(14)
                rightMargin = dp(10)
            })
        }

    private fun LinearLayout.addTagRow(article: NewsArticle) {
        val tagRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        article.tags.take(3).forEach { tag ->
            tagRow.addView(context.chip("${tag.symbol} ${formatPercent(tag.changePercent)}", positive = tag.isPositive))
        }
        if (article.isPro) tagRow.addView(proBadge())
        addView(tagRow)
    }

    private fun proBadge(): TextView = text("Pro", 11f, R.color.white, Typeface.BOLD).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(3), dp(8), dp(3))
        background = rounded(R.color.marketedge_accent, 12)
    }

    private fun newsBookmarkButton(article: NewsArticle, onChanged: () -> Unit): TextView =
        text(if (isSavedArticle(article)) "★" else "☆", 20f, if (isSavedArticle(article)) R.color.marketedge_accent else R.color.marketedge_text_muted, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = if (isSavedArticle(article)) "Hapus dari Watchlist" else "Simpan ke Watchlist"
            background = roundedRaw(Color.argb(210, 34, 34, 34), 12)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleSavedArticle(article)
                onChanged()
            }
        }

    private fun openNewsDetail(article: NewsArticle) {
        requestFullArticle(article)
        val screen = screenScroll()
        screen.addView(backBar("Berita") { renderNews() })
        screen.addView(articleImage(article, 210))
        screen.addGap(14)
        screen.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(text(article.title, 24f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(newsBookmarkButton(article) { openNewsDetail(article) }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(10)
            })
        })
        screen.addGap(8)
        screen.addView(text("${article.source} • ${article.timeAgo}", 13f, R.color.marketedge_text_muted))
        screen.addGap(12)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Bagikan") { shareArticle(article) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
            addView(actionButton("Tanyakan AI") { askAiAboutNews(article) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
            addView(secondaryActionButton("Sumber") { openArticleSource(article) }, LinearLayout.LayoutParams(0, dp(44), 0.78f))
        }
        screen.addView(actions)
        screen.addGap(16)
        screen.addView(articleContentCard(article))
        screen.addGap(18)
        screen.addView(sectionHeader("Aset Terkait"))
        screen.addGap(8)
        val related = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        article.tags.forEach { related.addView(chip("${it.symbol} ${formatPercent(it.changePercent)}", positive = it.isPositive)) }
        screen.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(related) })
        screen.addGap(18)
        screen.addView(sectionHeader("Berita Terkait"))
        screen.addGap(10)
        newsArticles.filter { it.id != article.id }.take(2).forEach {
            screen.addView(newsSmall(it))
            screen.addGap(10)
        }
        displayScroll(screen)
    }

    private fun articleContentCard(article: NewsArticle): View = card().apply {
        addView(text("Artikel Lengkap", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(8)
        val fullText = articleContentCache[article.id]
        val error = articleContentErrors[article.id]
        when {
            fullText != null -> addView(text(fullText, 16f, R.color.marketedge_text_secondary))
            articleContentLoading[article.id] == true -> {
                addView(text(cleanSummary(article), 16f, R.color.marketedge_text_secondary))
                addGap(12)
                addView(text("Memuat artikel lengkap...", 13f, R.color.marketedge_accent, Typeface.BOLD))
            }
            error != null -> {
                addView(text(cleanSummary(article), 16f, R.color.marketedge_text_secondary))
                addGap(12)
                addView(text("Artikel penuh belum bisa dimuat: $error", 13f, R.color.marketedge_negative, Typeface.BOLD))
            }
            else -> addView(text(cleanSummary(article), 16f, R.color.marketedge_text_secondary))
        }
    }

    private fun requestFullArticle(article: NewsArticle) {
        val url = article.sourceUrl?.takeIf { it.startsWith("https://") } ?: return
        if (articleContentCache.containsKey(article.id) ||
            articleContentErrors.containsKey(article.id) ||
            articleContentLoading.putIfAbsent(article.id, true) == true
        ) return
        executor.execute {
            val result = runCatching { fetchArticleText(url) }
            runOnUiThread {
                articleContentLoading.remove(article.id)
                result.onSuccess { content ->
                    if (content.length > cleanSummary(article).length) {
                        articleContentCache[article.id] = content
                    } else {
                        articleContentErrors[article.id] = "konten sumber terlalu pendek"
                    }
                }.onFailure {
                    articleContentErrors[article.id] = it.message ?: it.javaClass.simpleName
                }
                openNewsDetail(article)
            }
        }
    }

    private fun fetchArticleText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mozilla/5.0 MarketEdge Android")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            extractReadableArticle(html)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractReadableArticle(html: String): String {
        val articleHtml = Regex(
            "<article[\\s\\S]*?</article>",
            setOf(RegexOption.IGNORE_CASE)
        ).find(html)?.value ?: html
        val withoutNoise = articleHtml
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<nav[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<footer[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<aside[\\s\\S]*?</aside>", RegexOption.IGNORE_CASE), " ")
        val paragraphs = Regex("<p\\b[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)
            .findAll(withoutNoise)
            .map { htmlToText(it.groupValues[1]) }
            .filter { paragraph ->
                paragraph.length >= 45 &&
                    !paragraph.contains("cookie", ignoreCase = true) &&
                    !paragraph.contains("subscribe", ignoreCase = true) &&
                    !paragraph.contains("advertisement", ignoreCase = true)
            }
            .distinct()
            .take(12)
            .toList()
        if (paragraphs.isEmpty()) error("paragraf artikel tidak ditemukan")
        return paragraphs.joinToString("\n\n")
    }

    private fun htmlToText(value: String): String {
        val withBreaks = value
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(div|section|li|h\\d)>", RegexOption.IGNORE_CASE), "\n")
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanSummary(article: NewsArticle): String =
        article.content
            .replace("[...]", "")
            .replace("…", "")
            .trim()

    private fun openArticleSource(article: NewsArticle) {
        val url = article.sourceUrl?.takeIf { it.startsWith("http") } ?: return
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    private fun articleImage(article: NewsArticle, heightDp: Int, compact: Boolean = false): FrameLayout =
        FrameLayout(this).apply {
            background = roundedRaw(article.imageColor, 8)
            clipToOutline = true
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(article.imageColor)
            }
            addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(text(if (compact) article.category else "MarketEdge", if (compact) 10f else 13f, R.color.white, Typeface.BOLD).apply {
                alpha = 0.9f
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = roundedRaw(Color.argb(110, 0, 0, 0), 8)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(8)
                topMargin = dp(8)
            })
            if (article.isPro) addView(proBadge(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24), Gravity.TOP or Gravity.END))
            loadArticleImage(article, image)
            if (layoutParams == null) layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
        }

    private fun loadArticleImage(article: NewsArticle, imageView: ImageView) {
        val url = article.imageUrl?.takeIf { it.startsWith("https://") } ?: return
        imageView.tag = url
        imageCache[url]?.let {
            imageView.setImageBitmap(it)
            return
        }
        imageExecutor.execute {
            val bitmap = runCatching { downloadBitmap(url) }.getOrNull() ?: return@execute
            imageCache[url] = bitmap
            runOnUiThread {
                if (imageView.tag == url) imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "MarketEdge Android")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, 1200, 800)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            connection.disconnect()
        }
    }

    private fun bitmapSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((width / sample) > maxWidth || (height / sample) > maxHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun actionButton(label: String, onClick: (View) -> Unit): TextView =
        text(label, 14f, R.color.white, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.marketedge_accent, 8)
            setOnClickListener(onClick)
        }

    private fun secondaryActionButton(label: String, onClick: (View) -> Unit): TextView =
        text(label, 13f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.marketedge_card_soft, 8, R.color.marketedge_hairline)
            setOnClickListener(onClick)
        }

    private fun shareArticle(article: NewsArticle) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\n${article.summary}")
        }, getString(R.string.share)))
    }

    private fun renderIdeas() {
        activeAiSurface = AiSurface.PAGE
        syncBottomNav(R.id.nav_ideas)
        val pick = repository.aiPick()
        val screen = screenScroll()
        screen.addView(topBar("AI") { renderSearch() })
        if (chatMessages.isEmpty()) chatMessages.addAll(repository.initialChat())
        if (marketAssets.isEmpty()) loadMarkets { renderIdeas() }
        if (newsArticles.isEmpty()) loadNews { renderIdeas() }
        screen.addView(sectionHeader("Aset Pilihan AI", pick.market))
        screen.addGap(10)
        screen.addView(aiPickCard())
        screen.addGap(16)
        screen.addView(sectionHeader("Dampak Berita", "AI brief"))
        screen.addGap(10)
        screen.addView(newsImpactCard())
        screen.addGap(16)
        screen.addView(sectionHeader("Berita untuk Ditanyakan", "${newsArticles.size} headline"))
        screen.addGap(10)
        screen.addView(aiNewsRail())
        screen.addGap(16)
        screen.addView(sectionHeader("Aset untuk Ditanyakan", "${marketAssets.size} aset"))
        screen.addGap(10)
        screen.addView(aiAssetRail())
        screen.addGap(16)
        screen.addView(sectionHeader("Tanya AI"))
        screen.addGap(10)
        screen.addView(aiSuggestions())
        screen.addGap(10)
        chatMessages.takeLast(6).forEach {
            screen.addView(chatBubble(it))
            screen.addGap(10)
        }
        screen.addView(aiInputRow())
        screen.addGap(16)
        screen.addView(sectionHeader("Daftar Aset Real", "${marketAssets.size} aset"))
        screen.addGap(10)
        marketAssets.take(12).forEach {
            screen.addView(assetIdeaItem(it))
            screen.addGap(10)
        }
        displayScroll(screen)
    }

    private fun aiPickCard(): View = card().apply {
        val strongest = marketAssets.maxByOrNull { it.changePercent }
        val weakest = marketAssets.minByOrNull { it.changePercent }
        addView(text(strongest?.let { "${it.symbol} ${formatPercent(it.changePercent)}" } ?: "Muat data market", 34f, if (strongest?.isPositive != false) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        addView(text("Pilihan berbasis momentum 24 jam dan data market real", 13f, R.color.marketedge_text_muted))
        addGap(12)
        addView(performanceBar(0.86f, "Aset terkuat", strongest?.symbol ?: "-", R.color.marketedge_accent))
        addGap(8)
        addView(performanceBar(0.32f, "Aset terlemah", weakest?.symbol ?: "-", R.color.marketedge_text_muted))
        addGap(12)
        addView(text(aiPickExplanation(strongest, weakest), 14f, R.color.marketedge_text_secondary))
    }

    private fun aiPickExplanation(strongest: MarketAsset?, weakest: MarketAsset?): String = when {
        strongest == null -> "Data market sedang dimuat. AI akan memakai harga crypto, mata uang, emas, perak, dan headline berita yang tersedia."
        weakest == null -> "${strongest.symbol} sedang menjadi mover terkuat. Tetap cek berita terkait dan risiko volatilitas sebelum mengambil keputusan."
        else -> "${strongest.symbol} terlihat paling kuat, sementara ${weakest.symbol} paling lemah. Ini bukan saran beli/jual, tapi sinyal awal untuk ditanyakan ke AI bersama konteks berita terbaru."
    }

    private fun newsImpactCard(): View = card().apply {
        val latest = newsArticles.firstOrNull()
        val strongest = marketAssets.maxByOrNull { kotlin.math.abs(it.changePercent) }
        addView(text(latest?.title ?: "Memuat berita terbaru...", 17f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(6)
        addView(text(latest?.let { "${it.source} • ${it.timeAgo}" } ?: "Headline terbaru", 12f, R.color.marketedge_text_muted))
        addGap(10)
        val impact = if (latest != null && strongest != null) {
            "Pertanyaan yang bagus untuk AI: berita ini memengaruhi sentimen risiko atau tidak, dan apakah ada kaitannya dengan pergerakan ${strongest.symbol} ${formatPercent(strongest.changePercent)}?"
        } else {
            "AI akan membantu menjelaskan apakah sebuah berita kemungkinan berdampak ke aset, sentimen pasar, atau hanya headline umum."
        }
        addView(text(impact, 14f, R.color.marketedge_text_secondary))
        if (latest != null) {
            addGap(12)
            addView(actionButton("Tanyakan Berita Ini") { askAiAboutNews(latest) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        }
    }

    private fun aiNewsRail(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        val articles = newsArticles.take(8)
        if (articles.isEmpty()) {
            row.addView(infoCard("Berita sedang dimuat."))
        } else {
            articles.forEach { article ->
                row.addView(aiNewsContextCard(article), LinearLayout.LayoutParams(dp(206), dp(188)).apply {
                    marginEnd = dp(10)
                })
            }
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun aiNewsContextCard(article: NewsArticle): View = card().apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(FrameLayout(context).apply {
            addView(articleImage(article, 72), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))
            addView(newsBookmarkButton(article) { renderIdeas() }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(6)
                rightMargin = dp(6)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))
        addGap(8)
        addView(text(article.title, 13f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        addGap(4)
        addView(text("${article.source} • ${article.category}", 10f, R.color.marketedge_text_muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(actionButton("Tanyakan AI") { askAiAboutNews(article) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        setOnClickListener { openNewsDetail(article) }
    }

    private fun aiAssetRail(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        val assets = marketAssets.sortedByDescending { kotlin.math.abs(it.changePercent) }.take(10)
        if (assets.isEmpty()) {
            row.addView(infoCard("Aset sedang dimuat."))
        } else {
            assets.forEach { asset ->
                row.addView(aiAssetContextCard(asset), LinearLayout.LayoutParams(dp(144), dp(144)).apply {
                    marginEnd = dp(10)
                })
            }
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun aiAssetContextCard(asset: MarketAsset): View = card().apply {
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(text(asset.symbol, 21f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addGap(2)
        addView(text(asset.name, 10f, R.color.marketedge_text_muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addGap(6)
        addView(text(asset.price, 13f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(text(formatPercent(asset.changePercent), 16f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(actionButton("Tanyakan") { askAiAboutAsset(asset) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        setOnClickListener { askAiAboutAsset(asset) }
    }

    private fun aiSuggestions(): HorizontalScrollView {
        val suggestions = listOf(
            "Berita terbaru ngaruh ke aset apa?",
            "Aset mana yang paling kuat?",
            "Jelaskan risiko HYPE",
            "Ringkas market hari ini"
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        suggestions.forEach { suggestion ->
            row.addView(chip(suggestion, false).apply {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    sendRealAiQuestion(suggestion)
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun aiInputRow(): View {
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = editText("Tanya dampak berita atau aset...")
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        inputRow.addView(actionButton("Kirim") {
            val query = input.text.toString().trim()
            if (query.isNotBlank()) sendRealAiQuestion(query)
        }, LinearLayout.LayoutParams(dp(82), dp(44)))
        return inputRow
    }

    private fun performanceBar(percent: Float, label: String, value: String, colorRes: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("$label  $value", 13f, R.color.marketedge_text_secondary, Typeface.BOLD))
        box.addGap(5)
        val track = FrameLayout(this).apply {
            background = rounded(R.color.marketedge_card_soft, 8)
            addView(View(context).apply { background = rounded(colorRes, 8) }, FrameLayout.LayoutParams(0, dp(10)).apply {
                width = (resources.displayMetrics.widthPixels * percent).toInt().coerceAtLeast(dp(40))
            })
        }
        box.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        return box
    }

    private fun championItem(champion: PastChampion): View = card().apply {
        addView(text("${champion.symbol}  ${champion.companyName}", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(5)
        addView(text("${champion.returnPercent} sejak ditambahkan", 20f, R.color.marketedge_positive, Typeface.BOLD))
        addGap(7)
        addView(text("${champion.dateAdded} - ${champion.dateRemoved} • ${champion.addedPrice} → ${champion.removedPrice}", 12f, R.color.marketedge_text_muted))
        addGap(8)
        addView(text(champion.thesis, 13f, R.color.marketedge_text_secondary))
    }

    private fun assetIdeaItem(asset: MarketAsset): View = card().apply {
        addView(text("${asset.symbol}  ${asset.name}", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(5)
        addView(text(formatPercent(asset.changePercent), 22f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        addGap(7)
        addView(text("${asset.price} • update ${asset.updatedAt}", 12f, R.color.marketedge_text_muted))
        addGap(8)
        addView(text("Harga, perubahan 24 jam, dan grafik mini diperbarui dari market feed.", 13f, R.color.marketedge_text_secondary))
        addGap(10)
        addView(secondaryActionButton("Tanyakan AI tentang ${asset.symbol}") { askAiAboutAsset(asset) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        setOnClickListener { askAiAboutAsset(asset) }
    }

    private fun renderWatchlist() {
        syncBottomNav(R.id.nav_watchlist)
        val screen = screenScroll()
        screen.addView(watchlistToolbar())
        if (marketAssets.isEmpty()) {
            screen.addView(loadingCard("Memperbarui aset watchlist..."))
            loadMarkets { renderWatchlist() }
        } else {
            val watched = watchlistAssets()
            screen.addView(watchlistSummaryCard(watched))
            screen.addGap(10)
            if (watched.isEmpty()) {
                screen.addView(emptyWatchlistCard())
            } else {
                watched.forEach {
                    screen.addView(watchlistAssetItem(it))
                    screen.addGap(10)
                }
            }
        }
        screen.addGap(4)
        screen.addView(actionButton("Tambah Aset ke Watchlist") { renderWatchlistPicker() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        screen.addGap(16)
        if (newsArticles.isEmpty()) {
            screen.addView(sectionHeader("Berita tersimpan", "memuat"))
            screen.addGap(8)
            screen.addView(loadingCard("Memuat berita untuk watchlist..."))
            loadNews { renderWatchlist() }
        } else {
            val saved = savedNewsArticles()
            screen.addView(sectionHeader("Berita tersimpan", "${saved.size} disimpan"))
            screen.addGap(8)
            if (saved.isEmpty()) {
                screen.addView(emptySavedNewsCard())
                screen.addGap(8)
                screen.addView(sectionHeader("Rekomendasi berita", "tap bintang untuk simpan"))
                screen.addGap(8)
                watchlistNewsArticles(watchlistAssets()).take(3).forEach {
                    screen.addView(watchlistNewsItem(it, savedMode = false))
                    screen.addGap(8)
                }
            } else {
                saved.forEach {
                    screen.addView(watchlistNewsItem(it, savedMode = true))
                    screen.addGap(8)
                }
            }
        }
        displayScroll(screen)
    }

    private fun watchlistToolbar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(text("Watchlist", 22f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(if (watchlistEditMode) "Selesai" else "Edit", if (watchlistEditMode) "✓" else "✎").apply {
                setOnClickListener {
                    watchlistEditMode = !watchlistEditMode
                    renderWatchlist()
                }
            })
            addView(iconButton("Tambah", "+").apply {
                setOnClickListener { renderWatchlistPicker() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        }

    private fun watchlistSummaryCard(assets: List<MarketAsset>): View = card().apply {
        val gainers = assets.count { it.isPositive }
        val losers = assets.size - gainers
        addView(text("Watchlist Saya", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(4)
        addView(text("${assets.size} aset dipantau • Naik $gainers • Turun $losers", 12f, R.color.marketedge_text_muted))
        val top = assets.maxByOrNull { kotlin.math.abs(it.changePercent) }
        if (top != null) {
            addGap(8)
            addView(text("Pergerakan terbesar: ${top.symbol} ${formatPercent(top.changePercent)}", 13f, if (top.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        }
    }

    private fun emptyWatchlistCard(): View = card().apply {
        addView(text("Belum ada aset di watchlist", 17f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(6)
        addView(text("Tekan tombol + atau buka detail market lalu tekan bintang untuk menambahkan aset.", 13f, R.color.marketedge_text_secondary))
    }

    private fun emptySavedNewsCard(): View = card().apply {
        addView(text("Belum ada berita tersimpan", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
        addGap(5)
        addView(text("Tekan bintang di kartu berita untuk menyimpannya ke Watchlist.", 12f, R.color.marketedge_text_secondary))
    }

    private fun watchlistAssetItem(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
            setOnClickListener { openMarketDetail(asset) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("${asset.symbol}  ${asset.name}", 16f, R.color.marketedge_text_primary, Typeface.BOLD).apply { maxLines = 1 })
                addGap(4)
                addView(text("${asset.price} • ${asset.category} • ${asset.updatedAt}", 12f, R.color.marketedge_text_muted).apply { maxLines = 1 })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(text(formatPercent(asset.changePercent), 16f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD).apply { gravity = Gravity.END })
                if (watchlistEditMode) {
                    addGap(8)
                    addView(secondaryActionButton("Hapus") {
                        watchlistSymbols.remove(asset.symbol)
                        renderWatchlist()
                    }, LinearLayout.LayoutParams(dp(72), dp(34)))
                }
            })
        }

    private fun savedNewsArticles(): List<NewsArticle> =
        savedArticleIds.mapNotNull { id -> newsArticles.firstOrNull { it.id == id } }

    private fun watchlistNewsArticles(assets: List<MarketAsset>): List<NewsArticle> {
        val symbols = assets.flatMap { listOf(it.symbol, it.name) }
            .filter { it.isNotBlank() }
        val related = newsArticles.filter { article ->
            symbols.any { key ->
                article.title.contains(key, ignoreCase = true) ||
                    article.summary.contains(key, ignoreCase = true)
            }
        }
        return related.ifEmpty { newsArticles }.take(4)
    }

    private fun watchlistNewsItem(article: NewsArticle, savedMode: Boolean = true): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
            setOnClickListener { openNewsDetail(article) }
            addView(articleImage(article, 54, compact = true), LinearLayout.LayoutParams(dp(64), dp(54)).apply {
                marginEnd = dp(10)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(article.title, 13f, R.color.marketedge_text_primary, Typeface.BOLD).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addGap(3)
                addView(text("${article.source} • ${article.timeAgo}", 10f, R.color.marketedge_text_muted).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(if (savedMode) "★" else "☆", 18f, if (isSavedArticle(article)) R.color.marketedge_accent else R.color.marketedge_text_muted, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = rounded(R.color.marketedge_card_soft, 8, R.color.marketedge_hairline)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    toggleSavedArticle(article)
                    renderWatchlist()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                marginStart = dp(8)
            })
        }

    private fun renderWatchlistPicker() {
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(backBar("Tambah Watchlist") { renderWatchlist() })
        screen.addView(text("Pilih aset yang ingin dipantau.", 13f, R.color.marketedge_text_muted))
        screen.addGap(12)
        if (marketAssets.isEmpty()) {
            screen.addView(loadingCard("Memuat daftar aset..."))
            loadMarkets { renderWatchlistPicker() }
        } else {
            marketAssets.forEach { asset ->
                screen.addView(watchlistPickerItem(asset))
                screen.addGap(8)
            }
        }
        displayScroll(screen)
    }

    private fun watchlistPickerItem(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("${asset.symbol}  ${asset.name}", 16f, R.color.marketedge_text_primary, Typeface.BOLD))
                addView(text("${asset.price} • ${formatPercent(asset.changePercent)}", 12f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(if (isInWatchlist(asset)) "✓" else "+", 22f, if (isInWatchlist(asset)) R.color.marketedge_positive else R.color.marketedge_accent, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            setOnClickListener {
                toggleWatchlist(asset)
                renderWatchlistPicker()
            }
        }

    private fun watchlistAssets(): List<MarketAsset> =
        watchlistSymbols.mapNotNull { symbol -> marketAssets.firstOrNull { it.symbol == symbol } }

    private fun isInWatchlist(asset: MarketAsset): Boolean = watchlistSymbols.contains(asset.symbol)

    private fun toggleWatchlist(asset: MarketAsset) {
        if (!watchlistSymbols.add(asset.symbol)) watchlistSymbols.remove(asset.symbol)
    }

    private fun isSavedArticle(article: NewsArticle): Boolean = savedArticleIds.contains(article.id)

    private fun toggleSavedArticle(article: NewsArticle) {
        if (!savedArticleIds.add(article.id)) savedArticleIds.remove(article.id)
    }

    private fun toolbarWithActions(title: String, first: String, second: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(text(title, 22f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton("Edit", first).apply { setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } })
            addView(iconButton("Tambah", second).apply { setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        }

    private fun compactAsset(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(asset.symbol, 14f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(asset.price, 14f, R.color.marketedge_text_secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(formatPercent(asset.changePercent), 14f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
    }

    private fun renderMore() {
        syncBottomNav(R.id.nav_more)
        val screen = screenScroll()
        screen.addView(topBar("Lainnya") { renderSearch() })
        screen.addView(profileCard())
        screen.addGap(12)
        screen.addView(upgradeBanner())
        screen.addGap(16)
        screen.addView(sectionHeader("Akses Cepat"))
        screen.addGap(10)
        screen.addView(quickGrid())
        screen.addGap(16)
        screen.addView(sectionHeader("Monitor"))
        listOf("Peringatan", "Item Tersimpan", "Sentimen Saya", "Versi Bebas Iklan").forEach {
            screen.addView(menuRow(it))
        }
        screen.addGap(16)
        screen.addView(sectionHeader("Live Market"))
        screen.addGap(8)
        if (marketAssets.isEmpty()) {
            loadMarkets { renderMore() }
            screen.addView(loadingCard("Memperbarui preview market..."))
        } else {
            marketAssets.take(2).forEach { screen.addView(compactAsset(it)) }
        }
        displayScroll(screen)
    }

    private fun profileCard(): View = card().apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = "R"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(R.color.marketedge_accent, 28)
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Rahmat", 18f, R.color.marketedge_text_primary, Typeface.BOLD))
            addView(text("rahmat@marketedge.local", 13f, R.color.marketedge_text_muted))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun upgradeBanner(): View = card().apply {
        background = roundedRaw(Color.rgb(255, 107, 0), 8)
        addView(text("InvestingPro", 18f, R.color.white, Typeface.BOLD))
        addView(text("Diskon 45% untuk data premium, valuasi, dan sinyal AI.", 13f, R.color.white))
    }

    private fun quickGrid(): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(listOf("Kalender", "WarrenAI"), listOf("Temukan Broker Terbaik", "Saham Undervalued"))
        rows.forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { label ->
                row.addView(quickTile(label), LinearLayout.LayoutParams(0, dp(86), 1f).apply { marginEnd = dp(8); bottomMargin = dp(8) })
            }
            grid.addView(row)
        }
        return grid
    }

    private fun quickTile(label: String): View =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.marketedge_text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(R.color.marketedge_card, 8, R.color.marketedge_hairline)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (label == "WarrenAI") renderAiChat()
            }
        }

    private fun menuRow(label: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            addView(text(label, 15f, R.color.marketedge_text_primary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("›", 24f, R.color.marketedge_text_muted))
        }

    private fun renderAiChat() {
        activeAiSurface = AiSurface.FULLSCREEN
        bottomNav.visibility = View.GONE
        if (chatMessages.isEmpty()) chatMessages.addAll(repository.initialChat())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.marketedge_background))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(backBar("WarrenAI") { renderMore() })
        val suggestions = listOf("Saham apa yang bagus sekarang?", "Analisis BBCA", "Rekomendasikan aset kripto")
        root.addView(horizontalChips(suggestions, -1))
        val messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        val scroll = ScrollView(this).apply { addView(messagesContainer) }
        chatMessages.forEach { messagesContainer.addView(chatBubble(it)); messagesContainer.addGap(10) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = editText("Tanya WarrenAI...")
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        inputRow.addView(iconButton("Mikrofon", "◉"), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        inputRow.addView(actionButton("Kirim") {
            val query = input.text.toString().trim()
            if (query.isNotBlank()) {
                sendRealAiQuestion(query)
            }
        }, LinearLayout.LayoutParams(dp(74), dp(44)))
        root.addView(inputRow)
        display(root)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun chatBubble(message: ChatMessage): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (message.fromUser) Gravity.END else Gravity.START
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = if (message.fromUser) rounded(R.color.marketedge_accent, 14) else rounded(R.color.marketedge_card, 14, R.color.marketedge_hairline)
            addView(text(message.text, 14f, if (message.fromUser) R.color.white else R.color.marketedge_text_primary))
            if (!message.fromUser && message.recommendations.isNotEmpty()) {
                addGap(8)
                message.recommendations.forEach { addView(assetRecommendation(it)); addGap(6) }
            }
            if (!message.fromUser && message.relatedNews.isNotEmpty()) {
                addGap(8)
                addView(text("Berita terkait", 12f, R.color.marketedge_text_muted, Typeface.BOLD))
                message.relatedNews.take(2).forEach { addView(text("• ${it.title}", 12f, R.color.marketedge_text_secondary)) }
            }
            addGap(4)
            addView(text(message.timestamp, 11f, if (message.fromUser) R.color.white else R.color.marketedge_text_muted))
        }
        wrapper.addView(bubble, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        return wrapper
    }

    private fun assetRecommendation(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(R.color.marketedge_surface, 8, R.color.marketedge_hairline)
            addView(text(asset.symbol, 13f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(asset.price, 13f, R.color.marketedge_text_secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(formatPercent(asset.changePercent), 13f, if (asset.isPositive) R.color.marketedge_positive else R.color.marketedge_negative, Typeface.BOLD))
        }

    private fun renderSearch(onBack: () -> Unit = { renderMarket() }) {
        bottomNav.visibility = View.GONE
        val screen = screenScroll()
        screen.addView(backBar("Cari") {
            bottomNav.visibility = View.VISIBLE
            onBack()
        })
        val input = editText("Cari berita atau aset")
        screen.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        screen.addGap(16)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screen.addView(results)
        fun refreshResults(query: String) {
            results.removeAllViews()
            val cleanQuery = query.trim()
            val filteredAssets = searchMarketAssets(cleanQuery)
            val filteredNews = searchNews(cleanQuery)
            results.addView(sectionHeader(if (cleanQuery.isBlank()) "Hasil Populer" else "Aset", "${filteredAssets.size} hasil"))
            results.addGap(10)
            if (marketLoading && marketAssets.isEmpty()) {
                results.addView(loadingCard("Memuat data market..."))
                results.addGap(10)
            } else if (filteredAssets.isEmpty()) {
                results.addView(infoCard("Tidak ada aset yang cocok. Coba cari BTC, USD, emas, solana, atau nama aset lain."))
                results.addGap(10)
            } else {
                filteredAssets.forEach { asset ->
                    results.addView(marketItem(asset))
                    results.addGap(8)
                }
            }
            results.addGap(8)
            results.addView(sectionHeader("Berita", "${filteredNews.size} hasil"))
            results.addGap(10)
            if (newsLoading && newsArticles.isEmpty()) {
                results.addView(loadingCard("Memuat berita..."))
            } else if (filteredNews.isEmpty()) {
                results.addView(infoCard("Belum ada berita yang cocok dengan pencarian ini."))
            } else {
                filteredNews.forEach { article ->
                    results.addView(newsSmall(article))
                    results.addGap(10)
                }
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        if (marketAssets.isEmpty()) loadMarkets { refreshResults(input.text.toString()) }
        if (newsArticles.isEmpty()) loadNews { refreshResults(input.text.toString()) }
        refreshResults("")
        displayScroll(screen)
        input.requestFocus()
    }

    private fun searchMarketAssets(query: String): List<MarketAsset> {
        val source = if (selectedMarketCategory == "Populer 🔥") marketAssets else filteredMarketAssets()
        if (query.isBlank()) return source.take(20)
        val needle = query.lowercase(Locale.ROOT)
        return marketAssets.filter {
            it.name.lowercase(Locale.ROOT).contains(needle) ||
                it.symbol.lowercase(Locale.ROOT).contains(needle) ||
                it.unit.lowercase(Locale.ROOT).contains(needle) ||
                it.category.lowercase(Locale.ROOT).contains(needle)
        }.take(40)
    }

    private fun searchNews(query: String): List<NewsArticle> {
        if (query.isBlank()) return filteredNewsArticles().take(8)
        val needle = query.lowercase(Locale.ROOT)
        return newsArticles.filter {
            it.title.lowercase(Locale.ROOT).contains(needle) ||
                it.summary.lowercase(Locale.ROOT).contains(needle) ||
                it.source.lowercase(Locale.ROOT).contains(needle) ||
                it.category.lowercase(Locale.ROOT).contains(needle)
        }.take(12)
    }

    private fun backBar(title: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(drawableToolbarButton("Kembali", R.drawable.ic_back) { onBack() }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
            addView(text(title, 22f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun infoCard(message: String): View = card().apply {
        addView(text(message, 13f, R.color.marketedge_text_muted))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.marketedge_hairline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun detailDivider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.marketedge_hairline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        }
    }

    private fun statRow(label: String, value: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
            addView(text(label, 14f, R.color.marketedge_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(value, 14f, R.color.marketedge_text_primary).apply {
                gravity = Gravity.END
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun loadingCard(message: String): View = card().apply {
        addView(text(message, 14f, R.color.marketedge_text_secondary, Typeface.BOLD))
    }

    private fun errorCard(title: String, message: String, retry: () -> Unit): View = card().apply {
        addView(text(title, 16f, R.color.marketedge_negative, Typeface.BOLD))
        addGap(6)
        addView(text(message.ifBlank { "Periksa koneksi internet atau coba lagi beberapa saat lagi." }, 13f, R.color.marketedge_text_secondary))
        addGap(10)
        addView(actionButton("Coba Lagi") { retry() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
    }

    private fun loadMarkets(force: Boolean = false, done: () -> Unit) {
        if (marketLoading || (marketAssets.isNotEmpty() && !force)) return
        marketLoading = true
        marketError = null
        executor.execute {
            val result = runCatching { repository.marketAssets() }
            runOnUiThread {
                result.onSuccess { marketAssets = it }
                    .onFailure { marketError = it.message ?: it.javaClass.simpleName }
                marketLoading = false
                done()
            }
        }
    }

    private fun loadNews(force: Boolean = false, done: () -> Unit) {
        if (newsLoading || (newsArticles.isNotEmpty() && !force)) return
        newsLoading = true
        newsError = null
        executor.execute {
            val result = runCatching { repository.topNews() }
            runOnUiThread {
                result.onSuccess { newsArticles = it }
                    .onFailure { newsError = it.message ?: it.javaClass.simpleName }
                newsLoading = false
                done()
            }
        }
    }

    private fun sendRealAiQuestion(query: String, visibleQuestion: String = query) {
        chatMessages.add(ChatMessage(UUID.randomUUID().toString(), visibleQuestion, "Baru saja", true))
        chatMessages.add(ChatMessage(UUID.randomUUID().toString(), "Menganalisis data terbaru...", "Baru saja", false))
        renderActiveAiSurface()
        executor.execute {
            val answer = runCatching { answerWithConfiguredAi(query) }
            runOnUiThread {
                chatMessages.removeLastOrNull()
                chatMessages.add(answer.getOrElse {
                    localAiAnswer(query, friendlyAiError(it.message.orEmpty()))
                })
                renderActiveAiSurface()
            }
        }
    }

    private fun askAiAboutNews(article: NewsArticle) {
        activeAiSurface = AiSurface.PAGE
        bottomNav.selectedItemId = R.id.nav_ideas
        val relatedAssets = marketAssets.sortedByDescending { kotlin.math.abs(it.changePercent) }
            .take(5)
            .joinToString(", ") { "${it.symbol} ${formatPercent(it.changePercent)}" }
        val prompt = buildString {
            append("Jelaskan dampak berita ini ke market dan aset yang perlu diperhatikan: \"${article.title}\". ")
            append("Sumber ${article.source}, kategori ${article.category}. ")
            if (article.summary.isNotBlank()) append("Ringkasan: ${article.summary}. ")
            if (relatedAssets.isNotBlank()) append("Aset yang sedang bergerak besar: $relatedAssets. ")
            append("Jawab simpel: dampaknya apa, aset mana yang mungkin sensitif, dan risiko salah tafsirnya.")
        }
        sendRealAiQuestion(prompt, "Tanyakan dampak berita: ${article.title}")
    }

    private fun askAiAboutAsset(asset: MarketAsset) {
        activeAiSurface = AiSurface.PAGE
        bottomNav.selectedItemId = R.id.nav_ideas
        val latestNews = newsArticles.take(4).joinToString("; ") { it.title }
        val prompt = buildString {
            append("Analisis ${asset.symbol} (${asset.name}) berdasarkan data ini: harga ${asset.price}, perubahan ${formatPercent(asset.changePercent)}, kategori ${asset.category}, sumber ${asset.source}. ")
            if (latestNews.isNotBlank()) append("Hubungkan dengan headline terbaru: $latestNews. ")
            append("Jelaskan apakah pergerakannya terlihat karena momentum pasar, sentimen berita, atau perlu hati-hati karena volatilitas.")
        }
        sendRealAiQuestion(prompt, "Tanyakan analisis aset: ${asset.symbol}")
    }

    private fun renderActiveAiSurface() {
        if (activeAiSurface == AiSurface.FULLSCREEN) renderAiChat() else renderIdeas()
    }

    private fun answerWithConfiguredAi(query: String): ChatMessage {
        if (BuildConfig.AI_API_KEY.isBlank()) return localAiAnswer(query)
        val response = runCatching { requestAiCompletion(query) }.getOrElse {
            return localAiAnswer(query, friendlyAiError(it.message.orEmpty()))
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = response,
            timestamp = "Baru saja",
            fromUser = false,
            recommendations = marketAssets.sortedByDescending { kotlin.math.abs(it.changePercent) }.take(3),
            relatedNews = newsArticles.take(2)
        )
    }

    private fun localAiAnswer(query: String, notice: String? = null): ChatMessage {
        val strongest = marketAssets.maxByOrNull { it.changePercent }
        val weakest = marketAssets.minByOrNull { it.changePercent }
        val latest = newsArticles.firstOrNull()
        val text = buildString {
            if (notice != null) {
                append(notice)
                append(" Saya pakai analisis lokal dari data aplikasi. ")
            } else {
                append("Saya memakai analisis lokal dari data pasar dan berita terbaru. ")
            }
            if (strongest != null && weakest != null) {
                append("${strongest.symbol} paling kuat (${formatPercent(strongest.changePercent)}), ")
                append("${weakest.symbol} paling lemah (${formatPercent(weakest.changePercent)}). ")
            }
            if (latest != null) {
                append("Headline terbaru: \"${latest.title}\". ")
                append("Berita seperti ini perlu dicek apakah berdampak ke sentimen risiko, sektor terkait, atau hanya informasi umum. ")
            }
            append("Pertanyaan kamu: \"${query.take(180)}${if (query.length > 180) "..." else ""}\". ")
            if (notice == null) append("Gunakan ini sebagai ringkasan awal, bukan saran investasi personal.")
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = "Baru saja",
            fromUser = false,
            recommendations = marketAssets.sortedByDescending { kotlin.math.abs(it.changePercent) }.take(3),
            relatedNews = newsArticles.take(2)
        )
    }

    private fun requestAiCompletion(query: String): String {
        val payload = JSONObject().apply {
            put("model", BuildConfig.AI_MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Kamu adalah analis market MarketEdge. Jawab dalam Bahasa Indonesia yang mudah dipahami. Jelaskan dampak berita ke aset, risiko, dan konteks, tapi jangan memberi saran investasi personal.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", buildAiContext(query))
                })
            })
            put("temperature", 0.4)
        }
        val connection = (URL(BuildConfig.AI_BASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error(parseAiError(connection.responseCode, body))
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAiError(code: Int, body: String): String {
        val parsed = runCatching {
            val error = JSONObject(body).getJSONObject("error")
            val type = error.optString("type")
            val message = error.optString("message")
            when {
                type == "insufficient_quota" -> "Kuota AI habis atau billing belum aktif."
                message.isNotBlank() -> message
                else -> "AI HTTP $code"
            }
        }.getOrDefault("AI HTTP $code")
        return parsed
    }

    private fun friendlyAiError(message: String): String = when {
        message.contains("insufficient_quota", ignoreCase = true) ||
            message.contains("Kuota AI habis", ignoreCase = true) ->
            "Layanan AI sedang tidak tersedia. Saya pakai analisis lokal dari data aplikasi."
        message.contains("429") ->
            "AI sedang membatasi request atau kuota akun habis."
        message.contains("401") || message.contains("invalid", ignoreCase = true) ->
            "Layanan AI belum bisa dipakai. Saya pakai analisis lokal dari data aplikasi."
        message.isBlank() ->
            "AI online belum bisa dihubungi."
        else ->
            "AI online belum bisa dipakai: ${message.take(120)}"
    }

    private fun buildAiContext(query: String): String = buildString {
        appendLine("Pertanyaan user: $query")
        appendLine()
        appendLine("Data market terbaru:")
        marketAssets.take(20).forEach {
            appendLine("- ${it.symbol} ${it.name}: ${it.price}, perubahan ${formatPercent(it.changePercent)}, kategori ${it.category}")
        }
        appendLine()
        appendLine("Headline berita terbaru:")
        newsArticles.take(8).forEach {
            appendLine("- ${it.title} (${it.source}, ${it.timeAgo}) ringkasan: ${it.summary}")
        }
    }

    private fun formatPercent(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${String.format(java.util.Locale("id", "ID"), "%.2f", value)}%"
    }

    private enum class AiSurface {
        PAGE,
        FULLSCREEN
    }
}
