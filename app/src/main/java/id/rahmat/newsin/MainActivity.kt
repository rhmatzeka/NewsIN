package id.rahmat.newsin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import id.rahmat.newsin.data.api.PublicApiClient
import id.rahmat.newsin.data.repository.RealNewsInRepository
import id.rahmat.newsin.domain.model.ChatMessage
import id.rahmat.newsin.domain.model.MarketAsset
import id.rahmat.newsin.domain.model.NewsArticle
import id.rahmat.newsin.domain.model.PastChampion
import id.rahmat.newsin.presentation.components.SparklineView
import id.rahmat.newsin.presentation.components.addGap
import id.rahmat.newsin.presentation.components.card
import id.rahmat.newsin.presentation.components.chip
import id.rahmat.newsin.presentation.components.coloredBlock
import id.rahmat.newsin.presentation.components.dp
import id.rahmat.newsin.presentation.components.editText
import id.rahmat.newsin.presentation.components.horizontalChips
import id.rahmat.newsin.presentation.components.iconButton
import id.rahmat.newsin.presentation.components.rounded
import id.rahmat.newsin.presentation.components.roundedRaw
import id.rahmat.newsin.presentation.components.screenScroll
import id.rahmat.newsin.presentation.components.sectionHeader
import id.rahmat.newsin.presentation.components.text
import id.rahmat.newsin.presentation.components.topBar
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val repository = RealNewsInRepository(PublicApiClient())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val chatMessages = mutableListOf<ChatMessage>()
    private var marketAssets = emptyList<MarketAsset>()
    private var newsArticles = emptyList<NewsArticle>()
    private var marketLoading = false
    private var newsLoading = false
    private var marketError: String? = null
    private var newsError: String? = null
    private lateinit var content: FrameLayout
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.newsin_surface)
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
            setBackgroundColor(getColor(R.color.newsin_background))
            val logo = text("NewsIN", 42f, R.color.newsin_accent, Typeface.BOLD).apply {
                alpha = 0f
                gravity = Gravity.CENTER
            }
            val subtitle = text("Market intelligence, berita, dan WarrenAI", 14f, R.color.newsin_text_secondary).apply {
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
        super.onDestroy()
    }

    private fun showMain() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.newsin_background))
        }
        content = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.newsin_background))
        }
        bottomNav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.bottom_nav_menu)
            setBackgroundColor(getColor(R.color.newsin_surface))
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconTintList = navTint()
            itemTextColor = navTint()
            itemRippleColor = ColorStateList.valueOf(getColor(R.color.newsin_card_soft))
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
        intArrayOf(getColor(R.color.newsin_accent), getColor(R.color.newsin_text_muted))
    )

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
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(topBar("NewsIN", showLogo = true) { renderSearch() })
        screen.addView(horizontalChips(listOf("Populer 🔥", "Indeks", "Futures Indeks", "Saham", "Kripto", "Komoditas", "Mata Uang")))
        when {
            marketLoading -> {
                screen.addView(loadingCard("Mengambil harga real dari CoinGecko dan Currency API..."))
                screen.addGap(10)
            }
            marketError != null -> {
                screen.addView(errorCard("Gagal memuat market real", marketError.orEmpty()) { loadMarkets(force = true) { renderMarket() } })
                screen.addGap(10)
            }
            marketAssets.isEmpty() -> {
                screen.addView(loadingCard("Menyiapkan data market real..."))
                loadMarkets { renderMarket() }
                screen.addGap(10)
            }
            else -> {
                screen.addView(sectionHeader("Populer", "${marketAssets.size} instrumen"))
                screen.addGap(6)
                marketAssets.forEachIndexed { index, asset ->
                    screen.addView(marketItem(asset))
                    if (index < marketAssets.lastIndex) screen.addView(divider())
                }
            }
        }
        screen.addGap(6)
        screen.addView(infoCard("Sumber market: CoinGecko API + Currency API dari public-apis. Ketuk instrumen untuk membuka detail."))
        displayScroll(screen)
    }

    private fun marketItem(asset: MarketAsset): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = rounded(R.color.newsin_surface, 0)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                openMarketDetail(asset)
            }
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(asset.name, 20f, R.color.newsin_text_primary, Typeface.BOLD).apply {
                maxLines = 1
            })
            addGap(4)
            val meta = if (asset.unit.isNotBlank()) "${asset.updatedAt} | ${asset.unit}" else "${asset.updatedAt} | ${asset.symbol}"
            addView(text(meta, 14f, R.color.newsin_text_muted))
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(text(asset.price, 22f, R.color.newsin_text_primary, Typeface.BOLD).apply {
                gravity = Gravity.END
                maxLines = 1
            })
            addGap(4)
            addView(text("${asset.changeValue} (${formatPercent(asset.changePercent)})", 15f, if (asset.isPositive) R.color.newsin_positive else R.color.newsin_negative, Typeface.BOLD).apply {
                gravity = Gravity.END
                maxLines = 1
            })
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f))
        return row
    }

    private fun openMarketDetail(asset: MarketAsset) {
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(marketDetailBar(asset))
        screen.addGap(10)
        screen.addView(text(asset.name, 17f, R.color.newsin_text_muted, Typeface.BOLD))
        screen.addGap(4)
        screen.addView(text(asset.price, 38f, R.color.newsin_text_primary, Typeface.BOLD))
        screen.addView(text("${asset.changeValue} (${formatPercent(asset.changePercent)})", 18f, if (asset.isPositive) R.color.newsin_positive else R.color.newsin_negative, Typeface.BOLD))
        screen.addGap(4)
        screen.addView(text("${asset.updatedAt} - Real time. ${asset.source}", 14f, R.color.newsin_text_muted))
        screen.addGap(18)
        screen.addView(horizontalChips(listOf("Ikhtisar", "Teknikal", "Berita", "Analisis", "Data")))
        screen.addGap(10)
        screen.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "↗"
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.newsin_text_muted))
                textSize = 22f
                background = rounded(R.color.newsin_card, 28, R.color.newsin_hairline)
            }, LinearLayout.LayoutParams(dp(58), dp(58)))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(text("▣ Analisis grafik", 15f, R.color.newsin_text_primary, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(R.color.newsin_card, 8, R.color.newsin_hairline)
            })
        })
        screen.addGap(12)
        screen.addView(FrameLayout(this).apply {
            background = rounded(R.color.newsin_background, 0)
            addView(SparklineView(context).apply {
                submit(asset.sparkline.ifEmpty { listOf(1f, 1.2f, 1.1f, 1.4f, 1.3f) }, asset.isPositive, largeChart = true)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(text(asset.price, 13f, R.color.newsin_text_primary, Typeface.BOLD).apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(R.color.newsin_card, 4, R.color.newsin_hairline)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        screen.addGap(14)
        screen.addView(horizontalChips(listOf("1Hr", "1Mgg", "1Bln", "1Thn", "5Thn", "Maks")))
        screen.addGap(18)
        screen.addView(statRow("Bid/Ask", "${asset.bid}/${asset.ask}"))
        screen.addView(detailDivider())
        screen.addView(statRow("Rentang harian", asset.dayRange))
        screen.addView(detailDivider())
        screen.addView(statRow("Rentang 52mgg", asset.yearRange))
        screen.addView(detailDivider())
        screen.addView(statRow("Close Sebelumnya", asset.previousClose))
        screen.addView(detailDivider())
        screen.addView(statRow("Pembukaan", asset.open))
        screen.addGap(20)
        screen.addView(text("TAMPILKAN LEBIH BANYAK ⌄", 16f, R.color.newsin_text_primary, Typeface.BOLD).apply {
            setPadding(dp(6), dp(10), dp(6), dp(10))
        })
        displayScroll(screen)
    }

    private fun marketDetailBar(asset: MarketAsset): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(iconButton("Kembali", "‹").apply { setOnClickListener { renderMarket() } }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(asset.symbol, 23f, R.color.newsin_text_primary, Typeface.BOLD))
                addView(text(asset.unit.ifBlank { asset.source }, 12f, R.color.newsin_text_muted))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton("Cari", "⌕"), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) })
            addView(iconButton("Peringatan", "♧"), LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) })
            addView(text("★", 28f, R.color.newsin_accent, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(42), dp(42)))
        }

    private fun renderNews() {
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(topBar("Berita") { renderSearch() })
        screen.addView(horizontalChips(listOf("Real News", "Latest", "NASA", "SpaceX", "World", "Technology")))
        when {
            newsLoading -> {
                screen.addView(loadingCard("Mengambil headline real dari Spaceflight News API..."))
                displayScroll(screen)
                return
            }
            newsError != null -> {
                screen.addView(errorCard("Gagal memuat berita real", newsError.orEmpty()) { loadNews(force = true) { renderNews() } })
                displayScroll(screen)
                return
            }
            newsArticles.isEmpty() -> {
                screen.addView(loadingCard("Menyiapkan berita real..."))
                loadNews { renderNews() }
                displayScroll(screen)
                return
            }
        }
        screen.addView(featuredNews(newsArticles.first()))
        screen.addGap(10)
        screen.addView(sectionHeader("Berita Terbaru", "Sumber API"))
        screen.addGap(10)
        newsArticles.drop(1).forEach { article ->
            screen.addView(newsSmall(article))
            screen.addGap(10)
        }
        displayScroll(screen)
    }

    private fun featuredNews(article: NewsArticle): View = card().apply {
        addView(coloredBlock(article.imageColor, 154))
        addGap(12)
        addView(text(article.title, 20f, R.color.newsin_text_primary, Typeface.BOLD))
        addGap(8)
        addView(text("${article.source} • ${article.timeAgo}", 12f, R.color.newsin_text_muted))
        addGap(10)
        addTagRow(article)
        setOnClickListener { openNewsDetail(article) }
    }

    private fun newsSmall(article: NewsArticle): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(R.color.newsin_card, 8, R.color.newsin_hairline)
            setOnClickListener { openNewsDetail(article) }
        }
        row.addView(FrameLayout(this).apply {
            background = roundedRaw(article.imageColor, 8)
            if (article.isPro) addView(proBadge(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24), Gravity.TOP or Gravity.END))
        }, LinearLayout.LayoutParams(dp(92), dp(84)).apply { marginEnd = dp(12) })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(article.title, 15f, R.color.newsin_text_primary, Typeface.BOLD))
            addView(text("${article.source} • ${article.timeAgo}", 12f, R.color.newsin_text_muted))
            addGap(6)
            addTagRow(article)
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
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
        background = rounded(R.color.newsin_accent, 12)
    }

    private fun openNewsDetail(article: NewsArticle) {
        val screen = screenScroll()
        screen.addView(backBar("Berita") { renderNews() })
        screen.addView(coloredBlock(article.imageColor, 188))
        screen.addGap(14)
        screen.addView(text(article.title, 24f, R.color.newsin_text_primary, Typeface.BOLD))
        screen.addGap(8)
        screen.addView(text("${article.source} • ${article.timeAgo}", 13f, R.color.newsin_text_muted))
        screen.addGap(12)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("Bagikan") { shareArticle(article) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
            addView(actionButton("Simpan") { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        screen.addView(actions)
        screen.addGap(16)
        screen.addView(text(article.content, 16f, R.color.newsin_text_secondary))
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

    private fun actionButton(label: String, onClick: (View) -> Unit): TextView =
        text(label, 14f, R.color.white, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.newsin_accent, 8)
            setOnClickListener(onClick)
        }

    private fun shareArticle(article: NewsArticle) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\n${article.summary}")
        }, getString(R.string.share)))
    }

    private fun renderIdeas() {
        bottomNav.visibility = View.VISIBLE
        val pick = repository.aiPick()
        val screen = screenScroll()
        screen.addView(topBar("Ide") { renderSearch() })
        screen.addView(sectionHeader("Saham Pilihan AI", pick.market))
        screen.addGap(10)
        screen.addView(card().apply {
            val strongest = marketAssets.maxByOrNull { it.changePercent }
            addView(text(strongest?.let { "${it.symbol} ${formatPercent(it.changePercent)}" } ?: "Muat data market", 34f, R.color.newsin_positive, Typeface.BOLD))
            addView(text("Top mover 24 jam • CoinGecko real-time", 13f, R.color.newsin_text_muted))
            addGap(12)
            addView(performanceBar(0.86f, "Aset terkuat", strongest?.symbol ?: "-", R.color.newsin_accent))
            addGap(8)
            addView(performanceBar(0.34f, "Benchmark", pick.benchmarkReturn, R.color.newsin_text_muted))
            addGap(12)
            addView(text(pick.highlight, 14f, R.color.newsin_text_secondary))
        })
        screen.addGap(16)
        screen.addView(sectionHeader("Daftar Aset Real"))
        screen.addGap(10)
        if (marketAssets.isEmpty()) loadMarkets { renderIdeas() }
        marketAssets.forEach {
            screen.addView(assetIdeaItem(it))
            screen.addGap(10)
        }
        screen.addGap(4)
        screen.addView(actionButton("Cek Semua Data Pro") { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        displayScroll(screen)
    }

    private fun performanceBar(percent: Float, label: String, value: String, colorRes: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("$label  $value", 13f, R.color.newsin_text_secondary, Typeface.BOLD))
        box.addGap(5)
        val track = FrameLayout(this).apply {
            background = rounded(R.color.newsin_card_soft, 8)
            addView(View(context).apply { background = rounded(colorRes, 8) }, FrameLayout.LayoutParams(0, dp(10)).apply {
                width = (resources.displayMetrics.widthPixels * percent).toInt().coerceAtLeast(dp(40))
            })
        }
        box.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        return box
    }

    private fun championItem(champion: PastChampion): View = card().apply {
        addView(text("${champion.symbol}  ${champion.companyName}", 16f, R.color.newsin_text_primary, Typeface.BOLD))
        addGap(5)
        addView(text("${champion.returnPercent} sejak ditambahkan", 20f, R.color.newsin_positive, Typeface.BOLD))
        addGap(7)
        addView(text("${champion.dateAdded} - ${champion.dateRemoved} • ${champion.addedPrice} → ${champion.removedPrice}", 12f, R.color.newsin_text_muted))
        addGap(8)
        addView(text(champion.thesis, 13f, R.color.newsin_text_secondary))
    }

    private fun assetIdeaItem(asset: MarketAsset): View = card().apply {
        addView(text("${asset.symbol}  ${asset.name}", 16f, R.color.newsin_text_primary, Typeface.BOLD))
        addGap(5)
        addView(text(formatPercent(asset.changePercent), 22f, if (asset.isPositive) R.color.newsin_positive else R.color.newsin_negative, Typeface.BOLD))
        addGap(7)
        addView(text("${asset.price} • update ${asset.updatedAt}", 12f, R.color.newsin_text_muted))
        addGap(8)
        addView(text("Data harga, perubahan 24 jam, dan sparkline berasal dari CoinGecko API.", 13f, R.color.newsin_text_secondary))
    }

    private fun renderWatchlist() {
        bottomNav.visibility = View.VISIBLE
        val screen = screenScroll()
        screen.addView(toolbarWithActions("Portofolio Saya", "✎", "+"))
        if (marketAssets.isEmpty()) {
            screen.addView(loadingCard("Mengambil watchlist real dari CoinGecko..."))
            loadMarkets { renderWatchlist() }
        } else {
            listOf(
                id.rahmat.newsin.domain.model.WatchlistGroup("Crypto Watchlist Real", marketAssets.size, marketAssets)
            ).forEach { group ->
            screen.addView(card().apply {
                addView(text(group.name, 18f, R.color.newsin_text_primary, Typeface.BOLD))
                addView(text("${group.symbolCount} simbol", 12f, R.color.newsin_text_muted))
                addGap(10)
                group.assets.take(3).forEach { addView(compactAsset(it)) }
            })
            screen.addGap(10)
            }
        }
        screen.addView(actionButton("Portofolio Baru") { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        displayScroll(screen)
    }

    private fun toolbarWithActions(title: String, first: String, second: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(text(title, 22f, R.color.newsin_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton("Edit", first).apply { setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } })
            addView(iconButton("Tambah", second).apply { setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        }

    private fun compactAsset(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(asset.symbol, 14f, R.color.newsin_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(asset.price, 14f, R.color.newsin_text_secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(formatPercent(asset.changePercent), 14f, if (asset.isPositive) R.color.newsin_positive else R.color.newsin_negative, Typeface.BOLD))
        }

    private fun renderMore() {
        bottomNav.visibility = View.VISIBLE
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
            screen.addView(loadingCard("Mengambil preview market real..."))
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
            background = rounded(R.color.newsin_accent, 28)
        }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Rahmat", 18f, R.color.newsin_text_primary, Typeface.BOLD))
            addView(text("rahmat@newsin.local", 13f, R.color.newsin_text_muted))
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
            setTextColor(getColor(R.color.newsin_text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(R.color.newsin_card, 8, R.color.newsin_hairline)
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
            addView(text(label, 15f, R.color.newsin_text_primary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("›", 24f, R.color.newsin_text_muted))
        }

    private fun renderAiChat() {
        bottomNav.visibility = View.GONE
        if (chatMessages.isEmpty()) chatMessages.addAll(repository.initialChat())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.newsin_background))
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
                chatMessages.add(ChatMessage(UUID.randomUUID().toString(), query, "Baru saja", true))
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
            background = if (message.fromUser) rounded(R.color.newsin_accent, 14) else rounded(R.color.newsin_card, 14, R.color.newsin_hairline)
            addView(text(message.text, 14f, if (message.fromUser) R.color.white else R.color.newsin_text_primary))
            if (!message.fromUser && message.recommendations.isNotEmpty()) {
                addGap(8)
                message.recommendations.forEach { addView(assetRecommendation(it)); addGap(6) }
            }
            if (!message.fromUser && message.relatedNews.isNotEmpty()) {
                addGap(8)
                addView(text("Berita terkait", 12f, R.color.newsin_text_muted, Typeface.BOLD))
                message.relatedNews.take(2).forEach { addView(text("• ${it.title}", 12f, R.color.newsin_text_secondary)) }
            }
            addGap(4)
            addView(text(message.timestamp, 11f, if (message.fromUser) R.color.white else R.color.newsin_text_muted))
        }
        wrapper.addView(bubble, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        return wrapper
    }

    private fun assetRecommendation(asset: MarketAsset): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(R.color.newsin_surface, 8, R.color.newsin_hairline)
            addView(text(asset.symbol, 13f, R.color.newsin_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(asset.price, 13f, R.color.newsin_text_secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(formatPercent(asset.changePercent), 13f, if (asset.isPositive) R.color.newsin_positive else R.color.newsin_negative, Typeface.BOLD))
        }

    private fun renderSearch() {
        bottomNav.visibility = View.GONE
        val screen = screenScroll()
        screen.addView(backBar("Cari") { bottomNav.visibility = View.VISIBLE; renderMarket() })
        val input = editText("Cari berita atau aset")
        screen.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        screen.addGap(16)
        screen.addView(sectionHeader("Hasil Populer"))
        screen.addGap(10)
        if (marketAssets.isEmpty()) loadMarkets { renderSearch() }
        if (newsArticles.isEmpty()) loadNews { renderSearch() }
        marketAssets.take(3).forEach { screen.addView(marketItem(it)); screen.addGap(10) }
        newsArticles.take(2).forEach { screen.addView(newsSmall(it)); screen.addGap(10) }
        displayScroll(screen)
    }

    private fun backBar(title: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(iconButton("Kembali", "‹").apply { setOnClickListener { onBack() } }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
            addView(text(title, 22f, R.color.newsin_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun infoCard(message: String): View = card().apply {
        addView(text(message, 13f, R.color.newsin_text_muted))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.newsin_hairline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun detailDivider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.newsin_hairline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    private fun statRow(label: String, value: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(text(label, 18f, R.color.newsin_text_primary, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(value, 18f, R.color.newsin_text_primary).apply {
                gravity = Gravity.END
                maxLines = 1
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun loadingCard(message: String): View = card().apply {
        addView(text(message, 14f, R.color.newsin_text_secondary, Typeface.BOLD))
    }

    private fun errorCard(title: String, message: String, retry: () -> Unit): View = card().apply {
        addView(text(title, 16f, R.color.newsin_negative, Typeface.BOLD))
        addGap(6)
        addView(text(message.ifBlank { "Periksa koneksi internet atau rate limit API." }, 13f, R.color.newsin_text_secondary))
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

    private fun sendRealAiQuestion(query: String) {
        chatMessages.add(ChatMessage(UUID.randomUUID().toString(), query, "Baru saja", true))
        chatMessages.add(ChatMessage(UUID.randomUUID().toString(), "Mengambil data real terbaru...", "Baru saja", false))
        renderAiChat()
        executor.execute {
            val answer = runCatching { repository.answerFor(query) }
            runOnUiThread {
                chatMessages.removeLastOrNull()
                chatMessages.add(answer.getOrElse {
                    ChatMessage(UUID.randomUUID().toString(), "Gagal mengambil data real: ${it.message}", "Baru saja", false)
                })
                renderAiChat()
            }
        }
    }

    private fun formatPercent(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${String.format(java.util.Locale("id", "ID"), "%.2f", value)}%"
    }
}
