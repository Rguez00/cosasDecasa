package org.example.project

import org.example.project.presentation.strategies.StrategiesConfigDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.core.config.InitialData
import org.example.project.data.repository.InMemoryMarketRepository
import org.example.project.data.repository.InMemoryPortfolioRepository
import org.example.project.domain.model.MarketTrend
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.PositionSnapshot
import org.example.project.domain.model.Sector
import org.example.project.domain.model.StockSnapshot
import org.example.project.domain.model.Transaction
import org.example.project.engine.MarketEngine
import org.example.project.presentation.ui.TradeDialog
import org.example.project.presentation.vm.PortfolioViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import org.example.project.presentation.state.MarketState
import org.example.project.presentation.state.PortfolioState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.example.project.domain.strategy.InMemoryStrategiesRepository
import org.example.project.domain.strategy.StrategyRule
import org.example.project.domain.strategy.DipReference



// =========================================================
// NAV
// =========================================================
private enum class AppTab(val label: String, val glyph: String) {
    MARKET("Mercado", "📈"),
    PORTFOLIO("Portfolio", "💼"),
    CHARTS("Gráficos", "📊"),
    ALERTS("Alertas", "🔔")
}

// =========================================================
// ALERTS (simple, en-memory, entregable)
// =========================================================
private enum class AlertType(val label: String) {
    PRICE_ABOVE("Precio ≥"),
    PRICE_BELOW("Precio ≤"),
    PCT_ABOVE("% ≥"),
    PCT_BELOW("% ≤")
}

private data class AlertRule(
    val id: Long,
    val ticker: String,
    val type: AlertType,
    val threshold: Double,
    val createdTick: Long,
    val enabled: Boolean = true,
    val triggered: Boolean = false,
    val triggeredTick: Long? = null
)

private data class FiredAlert(
    val id: Long,
    val ticker: String,
    val type: AlertType,
    val threshold: Double,
    val valueAtFire: Double,
    val firedTick: Long
)

// =========================================================
// APP
// =========================================================
@Composable
@Preview
fun App() {
    // Scope ligado al ciclo de vida de Compose (se cancela al salir)
    val appScope = rememberCoroutineScope()

    // Repos
    val marketRepo = remember { InMemoryMarketRepository(InitialData.defaultStocks()) }
    val portfolioRepo = remember { InMemoryPortfolioRepository(marketRepo) }
    val strategiesRepo = remember { InMemoryStrategiesRepository() }

    // Engine (ahora ya tiene todo lo que necesita)
    val engine = remember {
        MarketEngine(
            marketRepo = marketRepo,
            portfolioRepo = portfolioRepo,
            strategiesRepo = strategiesRepo,
            externalScope = appScope
        )
    }

    // (Opcional pero recomendado) Cargar reglas demo una sola vez
    LaunchedEffect(Unit) {
        // 🔧 Cambia "NBS" por el ticker que quieras probar
        strategiesRepo.upsert(
            StrategyRule.AutoBuyDip(
                id = 1,
                ticker = "NBS",
                dropPercent = 2.0,
                reference = DipReference.OPEN,
                budgetEuro = 250.0,
                cooldownMs = 12_000L
            )
        )

        strategiesRepo.upsert(
            StrategyRule.TakeProfit(
                id = 2,
                ticker = "NBS",
                profitPercent = 3.0,
                sellFraction = 1.0,
                cooldownMs = 12_000L
            )
        )

        strategiesRepo.upsert(
            StrategyRule.StopLoss(
                id = 3,
                ticker = "NBS",
                lossPercent = 3.0,
                sellFraction = 1.0,
                cooldownMs = 12_000L
            )
        )
    }

    // VM
    val portfolioVm = remember {
        PortfolioViewModel(
            repo = portfolioRepo,
            canTradeProvider = { engine.marketState.value.isOpen && !engine.marketState.value.isPaused }
        )
    }

    LaunchedEffect(Unit) { engine.startAllTickers() }

    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            portfolioRepo.close()
            portfolioVm.close()
        }
    }

    val marketState by engine.marketState.collectAsState()
    val portfolioState by portfolioVm.portfolioState.collectAsState()
    val canTrade = marketState.isOpen && !marketState.isPaused

    // UI state
    var tabKey by rememberSaveable { mutableStateOf(AppTab.MARKET.name) }
    val tab = AppTab.valueOf(tabKey)
    var showStrategiesDialog by rememberSaveable { mutableStateOf(false) }

    var selectedTicker by rememberSaveable { mutableStateOf("NBS") }
    var showFeatured by rememberSaveable { mutableStateOf(false) }
    var showNewsExpanded by rememberSaveable { mutableStateOf(false) }
    var showPortfolioExpanded by rememberSaveable { mutableStateOf(true) }

    // ✅ Featured: siempre el que mejor % tenga (máximo changePercent)
    val featured: StockSnapshot? = marketState.stocks.maxByOrNull { it.changePercent }

    val p = remember { AppPalette.darkFintechWhiteBackdrop() }

    fun pctColor(pct: Double): Color = when {
        pct > 0.0001 -> p.success
        pct < -0.0001 -> p.danger
        else -> p.neutral
    }

    fun arrow(pct: Double): String = when {
        pct > 0.0001 -> "▲"
        pct < -0.0001 -> "▼"
        else -> "•"
    }

    // ✅ Evita que “rompa” al pulsar CERRAR/PAUSAR si el engine lanza excepción
    val safeToggleOpen: () -> Unit = {
        runCatching { engine.setMarketOpen(!marketState.isOpen) }
            .onFailure { it.printStackTrace() }
    }
    val safeTogglePause: () -> Unit = {
        runCatching { engine.setPaused(!marketState.isPaused) }
            .onFailure { it.printStackTrace() }
    }

    // =========================================================
    // CHART DATA (históricos in-memory, sin tocar repos)
    // =========================================================
    val maxPoints = 120
    val priceHistory = remember { mutableStateMapOf<String, MutableList<Double>>() }
    val valueHistory = remember { mutableStateListOf<Double>() }

    // “Reloj” interno (tick) para alerts + dedupe sencillo
    var tickCounter by remember { mutableStateOf(0L) }

    LaunchedEffect(marketState.stocks, portfolioState.cash, portfolioState.positions) {
        tickCounter += 1

        // Históricos por ticker
        for (s in marketState.stocks) {
            val t = s.ticker
            val list = priceHistory.getOrPut(t) { mutableListOf() }
            val last = list.lastOrNull()
            if (last == null || abs(last - s.currentPrice) > 1e-6) {
                list.add(s.currentPrice)
                if (list.size > maxPoints) list.removeAt(0)
            }
        }

        // Histórico valor total (cash + posiciones)
        val total = portfolioState.cash + portfolioState.positions.sumOf { it.valueNow }
        val lastTotal = valueHistory.lastOrNull()
        if (lastTotal == null || abs(lastTotal - total) > 1e-6) {
            valueHistory.add(total)
            if (valueHistory.size > maxPoints) valueHistory.removeAt(0)
        }
    }

    // =========================================================
    // ALERTS STATE (in-memory)
    // =========================================================
    val alerts = remember { mutableStateListOf<AlertRule>() }
    val fired = remember { mutableStateListOf<FiredAlert>() }
    var banner by remember { mutableStateOf<String?>(null) }
    var showCreateAlert by remember { mutableStateOf(false) }


    fun upsertAlert(rule: AlertRule) {
        val idx = alerts.indexOfFirst { it.id == rule.id }
        if (idx >= 0) alerts[idx] = rule else alerts.add(rule)
    }

    // Evaluador de alertas: cada vez que cambia el mercado
    LaunchedEffect(marketState.stocks, tickCounter) {
        val byTicker = marketState.stocks.associateBy { it.ticker }

        for (a in alerts) {
            if (!a.enabled || a.triggered) continue
            val s = byTicker[a.ticker] ?: continue

            val firedNow = when (a.type) {
                AlertType.PRICE_ABOVE -> s.currentPrice >= a.threshold
                AlertType.PRICE_BELOW -> s.currentPrice <= a.threshold
                AlertType.PCT_ABOVE -> s.changePercent >= a.threshold
                AlertType.PCT_BELOW -> s.changePercent <= a.threshold
            }

            if (firedNow) {
                val valueAtFire = when (a.type) {
                    AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> s.currentPrice
                    AlertType.PCT_ABOVE, AlertType.PCT_BELOW -> s.changePercent
                }

                // Actualiza regla (triggered + disabled)
                upsertAlert(
                    a.copy(
                        enabled = false,
                        triggered = true,
                        triggeredTick = tickCounter
                    )
                )

                fired.add(
                    FiredAlert(
                        id = a.id,
                        ticker = a.ticker,
                        type = a.type,
                        threshold = a.threshold,
                        valueAtFire = valueAtFire,
                        firedTick = tickCounter
                    )
                )

                banner = "🔔 Alerta ${a.ticker}: ${a.type.label} ${fmt2(a.threshold)} (ahora ${fmt2(valueAtFire)})"
            }
        }
    }

    AppTheme(p) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 900.dp

            Scaffold(
                containerColor = Color.Transparent,

                bottomBar = {
                    if (!isWide) {
                        BottomTabs(
                            selected = tab,
                            onSelect = { tabKey = it.name },
                            surface = p.surface0,
                            stroke = p.strokeSoft,
                            textSoft = p.textSoft,
                            textStrong = p.textStrong,
                            brand = p.brand
                        )
                    }
                },
            ) { pad ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray)
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
                        )
                        .padding(pad),
                    color = Color.Gray
                ) {

                    val outerPad = 6.dp
                    val innerPadH = 8.dp
                    val innerPadV = 8.dp
                    val sectionGap = 8.dp

                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(outerPad),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LeftRail(
                                selected = tab,
                                onSelect = { tabKey = it.name },
                                surface = p.surface0,
                                stroke = p.strokeSoft,
                                textSoft = p.textSoft,
                                textStrong = p.textStrong,
                                brand = p.brand
                            )

                            MainCard(
                                modifier = Modifier.weight(1f),
                                p = p,
                                sectionGap = sectionGap,
                                innerPadH = innerPadH,
                                innerPadV = innerPadV,
                                marketState = marketState,
                                portfolioState = portfolioState,
                                tab = tab,
                                featured = featured,
                                selectedTicker = selectedTicker,
                                onSelectTicker = { selectedTicker = it },
                                showFeatured = showFeatured,
                                onToggleFeatured = { showFeatured = !showFeatured },
                                showNewsExpanded = showNewsExpanded,
                                onToggleNews = { showNewsExpanded = !showNewsExpanded },
                                showPortfolioExpanded = showPortfolioExpanded,
                                onTogglePortfolio = { showPortfolioExpanded = !showPortfolioExpanded },
                                pctColor = { pctColor(it) },
                                arrow = { arrow(it) },
                                onToggleOpen = safeToggleOpen,
                                onTogglePause = safeTogglePause,
                                onSetSpeed = { engine.setSimSpeed(it) },
                                canTrade = canTrade,
                                onBuy = { t -> if (canTrade) portfolioVm.openTrade(t, PortfolioViewModel.Mode.BUY) },
                                onSell = { t -> if (canTrade) portfolioVm.openTrade(t, PortfolioViewModel.Mode.SELL) },
                                // charts + alerts state
                                priceHistory = priceHistory,
                                valueHistory = valueHistory,
                                alerts = alerts,
                                fired = fired,
                                banner = banner,
                                onDismissBanner = { banner = null },
                                onCreateAlert = { showCreateAlert = true },
                                onUpdateAlert = { upsertAlert(it) },
                                onDeleteAlert = { id -> alerts.removeAll { it.id == id } },
                                onOpenStrategies = { showStrategiesDialog = true }
                            )
                        }
                    } else {
                        MainCard(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(outerPad),
                            p = p,
                            sectionGap = sectionGap,
                            innerPadH = innerPadH,
                            innerPadV = innerPadV,
                            marketState = marketState,
                            portfolioState = portfolioState,
                            tab = tab,
                            featured = featured,
                            selectedTicker = selectedTicker,
                            onSelectTicker = { selectedTicker = it },
                            showFeatured = showFeatured,
                            onToggleFeatured = { showFeatured = !showFeatured },
                            showNewsExpanded = showNewsExpanded,
                            onToggleNews = { showNewsExpanded = !showNewsExpanded },
                            showPortfolioExpanded = showPortfolioExpanded,
                            onTogglePortfolio = { showPortfolioExpanded = !showPortfolioExpanded },
                            pctColor = { pctColor(it) },
                            arrow = { arrow(it) },
                            onToggleOpen = safeToggleOpen,
                            onTogglePause = safeTogglePause,
                            onSetSpeed = { engine.setSimSpeed(it) },
                            canTrade = canTrade,
                            onBuy = { t -> if (canTrade) portfolioVm.openTrade(t, PortfolioViewModel.Mode.BUY) },
                            onSell = { t -> if (canTrade) portfolioVm.openTrade(t, PortfolioViewModel.Mode.SELL) },
                            // charts + alerts state
                            priceHistory = priceHistory,
                            valueHistory = valueHistory,
                            alerts = alerts,
                            fired = fired,
                            banner = banner,
                            onDismissBanner = { banner = null },
                            onCreateAlert = { showCreateAlert = true },
                            onUpdateAlert = { upsertAlert(it) },
                            onDeleteAlert = { id -> alerts.removeAll { it.id == id } },
                            onOpenStrategies = { showStrategiesDialog = true }
                        )
                    }

                    TradeDialog(
                        vm = portfolioVm,
                        dialogSurface = p.surface0,
                        innerSurface = p.surface1,
                        stroke = p.stroke,
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        textMuted = p.textMuted,
                        success = p.success,
                        danger = p.danger,
                        neutral = p.neutral
                    )

                    // Dialog: crear alerta
                    if (showCreateAlert) {
                        CreateAlertDialog(
                            tick = tickCounter,
                            defaultTicker = selectedTicker.ifBlank { marketState.stocks.firstOrNull()?.ticker.orEmpty() },
                            tickers = marketState.stocks.map { it.ticker },
                            surface = p.surface0,
                            stroke = p.stroke,
                            textStrong = p.textStrong,
                            textSoft = p.textSoft,
                            neutral = p.neutral,
                            brand = p.brand,
                            onDismiss = { showCreateAlert = false },
                            onCreate = { rule ->
                                upsertAlert(rule)
                                showCreateAlert = false
                                banner = "✅ Alerta creada: ${rule.ticker} · ${rule.type.label} ${fmt2(rule.threshold)}"
                            }
                        )
                    }
                    // ✅ Dialog: configurar estrategias automáticas
                    if (showStrategiesDialog) {
                        StrategiesConfigDialog(
                            strategiesRepo = strategiesRepo,
                            tickers = marketState.stocks.map { it.ticker },
                            initialTicker = selectedTicker.ifBlank { marketState.stocks.firstOrNull()?.ticker.orEmpty() },
                            onClose = { showStrategiesDialog = false }
                        )
                    }

                }
            }
        }
    }
}

// =========================================================
// MAIN CARD (contenido)
// =========================================================
@Composable
private fun MainCard(
    modifier: Modifier,
    p: AppPalette,
    sectionGap: Dp,
    innerPadH: Dp,
    innerPadV: Dp,
    marketState: MarketState,
    portfolioState: PortfolioState,
    tab: AppTab,
    featured: StockSnapshot?,
    selectedTicker: String,
    onSelectTicker: (String) -> Unit,
    showFeatured: Boolean,
    onToggleFeatured: () -> Unit,
    showNewsExpanded: Boolean,
    onToggleNews: () -> Unit,
    showPortfolioExpanded: Boolean,
    onTogglePortfolio: () -> Unit,
    pctColor: (Double) -> Color,
    arrow: (Double) -> String,
    onToggleOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onSetSpeed: (Double) -> Unit,
    canTrade: Boolean,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit,
    // charts + alerts
    priceHistory: Map<String, List<Double>>,
    valueHistory: List<Double>,
    alerts: List<AlertRule>,
    fired: List<FiredAlert>,
    banner: String?,
    onDismissBanner: () -> Unit,
    onCreateAlert: () -> Unit,
    onUpdateAlert: (AlertRule) -> Unit,
    onDeleteAlert: (Long) -> Unit,
    onOpenStrategies: () -> Unit,

    ) {
    val mainShape = RoundedCornerShape(20.dp)

    Card(
        modifier = modifier,
        shape = mainShape,
        colors = CardDefaults.cardColors(containerColor = p.surface0),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, p.stroke, mainShape)
                .padding(horizontal = innerPadH, vertical = innerPadV)
        ) {
            HeaderCompact(
                isOpen = marketState.isOpen,
                simSpeed = marketState.simSpeed,
                trend = marketState.trend,
                stocksCount = marketState.stocks.size,
                success = p.success,
                danger = p.danger,
                textStrong = p.textStrong,
                textSoft = p.textSoft,
                stroke = p.strokeSoft
            )

            Spacer(Modifier.height(sectionGap))
            Divider(color = p.strokeSoft)
            Spacer(Modifier.height(sectionGap))

            CompactTopBarUltra(
                cash = portfolioState.cash,
                value = portfolioState.portfolioValue,
                pnlEuro = portfolioState.pnlEuro,
                pnlPercent = portfolioState.pnlPercent,
                isOpen = marketState.isOpen,
                isPaused = marketState.isPaused,
                simSpeed = marketState.simSpeed,
                surface = p.surface1,
                inner = p.surface2,
                stroke = p.strokeSoft,
                textStrong = p.textStrong,
                textSoft = p.textSoft,
                textMuted = p.textMuted,
                success = p.success,
                danger = p.danger,
                neutral = p.neutral,
                onToggleOpen = onToggleOpen,
                onTogglePause = onTogglePause,
                onSetSpeed = onSetSpeed
            )

            Spacer(Modifier.height(sectionGap))

            when (tab) {
                AppTab.MARKET -> {
                    FeaturedMini(
                        featured = featured,
                        show = showFeatured,
                        onToggle = onToggleFeatured,
                        brand = p.brand,
                        brand2 = p.brand2,
                        surface = p.surface1,
                        inner = p.surface2,
                        stroke = p.strokeSoft,
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        textMuted = p.textMuted,
                        pctColor = pctColor,
                        arrow = arrow
                    )

                    Spacer(Modifier.height(sectionGap))

                    NewsTickerScrollable(
                        news = marketState.news,
                        expanded = showNewsExpanded,
                        onToggle = onToggleNews,
                        surface = p.surface1,
                        inner = p.surface2,
                        stroke = p.strokeSoft,
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        neutral = p.neutral,
                        success = p.success,
                        danger = p.danger
                    )

                    Spacer(Modifier.height(sectionGap))
                    Divider(color = p.strokeSoft)
                    Spacer(Modifier.height(sectionGap))

                    MarketHeader(
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        stroke = p.strokeSoft
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(marketState.stocks, key = { it.ticker }) { stock ->
                            MarketRow(
                                isSelected = stock.ticker == selectedTicker,
                                ticker = stock.ticker,
                                name = stock.name,
                                sector = stock.sector,
                                volume = stock.volume,
                                openPrice = stock.openPrice,
                                highPrice = stock.highPrice,
                                lowPrice = stock.lowPrice,
                                price = stock.currentPrice,
                                changeEuro = stock.changeEuro,
                                changePercent = stock.changePercent,
                                surface = p.surface2,
                                stroke = p.strokeSoft,
                                textStrong = p.textStrong,
                                textSoft = p.textSoft,
                                textMuted = p.textMuted,
                                neutral = p.neutral,
                                success = p.success,
                                danger = p.danger,
                                canTrade = canTrade,
                                onSelect = { onSelectTicker(stock.ticker) },
                                onBuy = { onBuy(stock.ticker) },
                                onSell = { onSell(stock.ticker) }
                            )
                        }
                    }
                }

                AppTab.PORTFOLIO -> {
                    // Reparte el alto disponible entre Portfolio y Transacciones
                    val wPortfolio = if (showPortfolioExpanded) 0.62f else 0.32f
                    val wTx = 1f - wPortfolio

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ✅ Fila propia para el botón (no se superpone)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = onOpenStrategies) {
                                Text("⚙ Estrategias automáticas")
                            }

                        }

                        Spacer(Modifier.height(sectionGap))

                        PortfolioPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(wPortfolio),
                            positions = portfolioState.positions,
                            show = showPortfolioExpanded,
                            onToggle = onTogglePortfolio,
                            surface = p.surface1,
                            inner = p.surface2,
                            stroke = p.strokeSoft,
                            textStrong = p.textStrong,
                            textSoft = p.textSoft,
                            textMuted = p.textMuted,
                            neutral = p.neutral,
                            success = p.success,
                            danger = p.danger,
                            canTrade = canTrade,
                            onBuy = onBuy,
                            onSell = onSell
                        )

                        Spacer(Modifier.height(sectionGap))

                        TransactionsPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(wTx),
                            txs = portfolioState.transactions,
                            surface = p.surface1,
                            inner = p.surface2,
                            stroke = p.strokeSoft,
                            textStrong = p.textStrong,
                            textSoft = p.textSoft,
                            textMuted = p.textMuted,
                            neutral = p.neutral
                        )
                    }
                }



                AppTab.CHARTS -> {
                    ChartsPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        marketStocks = marketState.stocks,
                        positions = portfolioState.positions,
                        selectedTicker = selectedTicker.ifBlank { marketState.stocks.firstOrNull()?.ticker.orEmpty() },
                        onSelectTicker = onSelectTicker,
                        priceHistory = priceHistory,
                        valueHistory = valueHistory,
                        surface = p.surface1,
                        inner = p.surface2,
                        stroke = p.strokeSoft,
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        textMuted = p.textMuted,
                        brand = p.brand,
                        brand2 = p.brand2,
                        neutral = p.neutral,
                        success = p.success,
                        danger = p.danger
                    )
                }


                AppTab.ALERTS -> {
                    AlertsPanel(
                        marketStocks = marketState.stocks,
                        canTrade = canTrade,
                        alerts = alerts,
                        fired = fired,
                        banner = banner,
                        onDismissBanner = onDismissBanner,
                        onCreate = onCreateAlert,
                        onUpdate = onUpdateAlert,
                        onDelete = onDeleteAlert,
                        surface = p.surface1,
                        inner = p.surface2,
                        stroke = p.strokeSoft,
                        textStrong = p.textStrong,
                        textSoft = p.textSoft,
                        textMuted = p.textMuted,
                        brand = p.brand,
                        neutral = p.neutral,
                        success = p.success,
                        danger = p.danger
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// =========================================================
// BOTTOM TABS (Android)
// =========================================================
@Composable
private fun BottomTabs(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    surface: Color,
    stroke: Color,
    textSoft: Color,
    textStrong: Color,
    brand: Color
) {
    NavigationBar(
        containerColor = surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, stroke)
    ) {
        AppTab.values().forEach { tab ->
            val isSel = tab == selected
            NavigationBarItem(
                selected = isSel,
                onClick = { onSelect(tab) },
                icon = { Text(text = tab.glyph, style = MaterialTheme.typography.titleSmall) },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

// =========================================================
// LEFT RAIL (Desktop / tablets)
// =========================================================
@Composable
private fun LeftRail(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    surface: Color,
    stroke: Color,
    textSoft: Color,
    textStrong: Color,
    brand: Color
) {
    val shape = RoundedCornerShape(18.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .width(180.dp)
            .fillMaxSize()
            .border(1.dp, stroke, shape)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Secciones",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = textStrong
            )
            Spacer(Modifier.height(10.dp))

            NavigationRail(
                containerColor = Color.Transparent,
                header = {
                    Text(text = "Bolsa", color = brand, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
            ) {
                AppTab.values().forEach { tab ->
                    val isSel = tab == selected
                    NavigationRailItem(
                        selected = isSel,
                        onClick = { onSelect(tab) },
                        icon = { Text(tab.glyph) },
                        label = {
                            Text(text = tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    }
}

// =========================================================
// THEME / PALETTE
// =========================================================
private data class AppPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val stroke: Color,
    val strokeSoft: Color,
    val brand: Color,
    val brand2: Color,
    val success: Color,
    val danger: Color,
    val neutral: Color,
    val textStrong: Color,
    val textSoft: Color,
    val textMuted: Color
) {
    companion object {
        fun darkFintechWhiteBackdrop(): AppPalette = AppPalette(
            bgTop = Color.White,
            bgBottom = Color.White,

            surface0 = Color(0xFF0B1428),
            surface1 = Color(0xFF0F1D38),
            surface2 = Color(0xFF152A4C),

            stroke = Color(0x40FFFFFF),
            strokeSoft = Color(0x2AFFFFFF),

            brand = Color(0xFF22D3EE),
            brand2 = Color(0xFF7C3AED),

            success = Color(0xFF2DD4BF),
            danger = Color(0xFFFB7185),
            neutral = Color(0xFF94A3B8),

            textStrong = Color(0xFFEAF1FF),
            textSoft = Color(0xFFB7C6DE),
            textMuted = Color(0xFF8EA5C6)
        )
    }
}

@Composable
private fun AppTheme(p: AppPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = p.brand,
        secondary = p.brand2,
        background = Color.White,
        surface = p.surface0,
        error = p.danger,
        onBackground = Color.Black,
        onSurface = p.textStrong,
        onPrimary = Color(0xFF001018),
        onSecondary = Color.White,
        onError = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// =========================================================
// HEADER compacto
// =========================================================
@Composable
private fun HeaderCompact(
    isOpen: Boolean,
    simSpeed: Double,
    trend: MarketTrend,
    stocksCount: Int,
    success: Color,
    danger: Color,
    textStrong: Color,
    textSoft: Color,
    stroke: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bolsa Cotarelo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = textStrong,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${if (isOpen) "ABIERTO" else "CERRADO"} · x${fmt2(simSpeed)} · ${trendLabel(trend)} · $stocksCount acciones",
                style = MaterialTheme.typography.bodySmall,
                color = textSoft,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        val chipBg = if (isOpen) Color(0xFF0B2A1F) else Color(0xFF351218)
        val chipStroke = if (isOpen) success.copy(alpha = 0.45f) else danger.copy(alpha = 0.45f)
        val chipShape = RoundedCornerShape(999.dp)

        Card(
            shape = chipShape,
            colors = CardDefaults.cardColors(containerColor = chipBg),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.border(1.dp, chipStroke, chipShape)
        ) {
            Text(
                text = if (isOpen) "OPEN" else "CLOSED",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFEAF2FF),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun trendLabel(t: MarketTrend): String = when (t) {
    MarketTrend.BULLISH -> "ALCISTA"
    MarketTrend.BEARISH -> "BAJISTA"
    MarketTrend.NEUTRAL -> "NEUTRAL"
}

// =========================================================
// TOP BAR ultra-compacta
// =========================================================
@Composable
private fun CompactTopBarUltra(
    cash: Double,
    value: Double,
    pnlEuro: Double,
    pnlPercent: Double,
    isOpen: Boolean,
    isPaused: Boolean,
    simSpeed: Double,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    success: Color,
    danger: Color,
    neutral: Color,
    onToggleOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onSetSpeed: (Double) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    val pnlColor = when {
        pnlEuro > 0.0001 -> success
        pnlEuro < -0.0001 -> danger
        else -> neutral
    }
    val signEuro = if (pnlEuro >= 0) "+" else ""
    val signPct = if (pnlPercent >= 0) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPill("Saldo", "${fmt2(cash)} €", Modifier.weight(1f), inner, stroke, textMuted, textStrong, compact = true)
                MiniPill("Valor", "${fmt2(value)} €", Modifier.weight(1f), inner, stroke, textMuted, textStrong, compact = true)
                MiniPill(
                    "PnL",
                    "$signEuro${fmt2(pnlEuro)}€",
                    Modifier.weight(1f),
                    inner,
                    stroke,
                    textMuted,
                    pnlColor,
                    sub = "$signPct${fmt2(pnlPercent)}%",
                    compact = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlChip(
                    text = if (isOpen) "CERRAR" else "ABRIR",
                    bg = (if (isOpen) danger else success).copy(alpha = 0.16f),
                    stroke = (if (isOpen) danger else success).copy(alpha = 0.38f),
                    fg = if (isOpen) danger else success,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleOpen
                )
                ControlChip(
                    text = if (isPaused) "REANUDAR" else "PAUSAR",
                    bg = neutral.copy(alpha = 0.14f),
                    stroke = neutral.copy(alpha = 0.30f),
                    fg = Color(0xFFE3ECFF),
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePause
                )

                SpeedInline(
                    current = simSpeed,
                    textSoft = textSoft,
                    strong = textStrong,
                    muted = textMuted,
                    stroke = stroke,
                    inner = inner,
                    onSetSpeed = onSetSpeed
                )
            }
        }
    }
}

@Composable
private fun MiniPill(
    title: String,
    value: String,
    modifier: Modifier,
    surface: Color,
    stroke: Color,
    titleColor: Color,
    valueColor: Color,
    sub: String? = null,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    val padV = if (compact) 6.dp else 8.dp
    val padH = if (compact) 8.dp else 10.dp

    Box(
        modifier = modifier
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(horizontal = padH, vertical = padV)
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = titleColor, maxLines = 1)
            Spacer(Modifier.height(1.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1
            )
            if (sub != null) {
                Spacer(Modifier.height(1.dp))
                Text(text = sub, style = MaterialTheme.typography.labelSmall, color = valueColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ControlChip(
    text: String,
    bg: Color,
    stroke: Color,
    fg: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun SpeedInline(
    current: Double,
    textSoft: Color,
    strong: Color,
    muted: Color,
    stroke: Color,
    inner: Color,
    onSetSpeed: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(inner)
            .border(1.dp, stroke, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "x${fmt2(current)}",
            style = MaterialTheme.typography.labelSmall,
            color = textSoft,
            maxLines = 1
        )
        SpeedDot("0.5", 0.5, current, strong, muted, stroke, inner, onSetSpeed)
        SpeedDot("1", 1.0, current, strong, muted, stroke, inner, onSetSpeed)
        SpeedDot("2", 2.0, current, strong, muted, stroke, inner, onSetSpeed)
        SpeedDot("5", 5.0, current, strong, muted, stroke, inner, onSetSpeed)
    }
}

@Composable
private fun SpeedDot(
    label: String,
    speed: Double,
    current: Double,
    strong: Color,
    muted: Color,
    stroke: Color,
    inner: Color,
    onSetSpeed: (Double) -> Unit
) {
    val selected = abs(current - speed) < 1e-6
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) strong.copy(alpha = 0.14f) else inner
    val br = if (selected) strong.copy(alpha = 0.42f) else stroke
    val fg = if (selected) strong else muted

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, br, shape)
            .clickable { onSetSpeed(speed) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1
        )
    }
}

// =========================================================
// Portfolio panel
// =========================================================
@Composable
private fun PortfolioPanel(
    modifier: Modifier = Modifier,   // ✅ AÑADIR
    positions: List<PositionSnapshot>,
    show: Boolean,
    onToggle: () -> Unit,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    neutral: Color,
    success: Color,
    danger: Color,
    canTrade: Boolean,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    val totalValue = positions.sumOf { it.valueNow }
    val totalInvested = positions.sumOf { it.invested }
    val totalPnl = totalValue - totalInvested

    val pnlColor = when {
        totalPnl > 0.0001 -> success
        totalPnl < -0.0001 -> danger
        else -> neutral
    }

    Card(
        modifier = modifier, // ✅ ANTES era Modifier.fillMaxWidth()
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // ✅ IMPORTANTE para que weight funcione dentro
                .border(1.dp, stroke, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // ✅ SOLO el header hace toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggle() }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(pnlColor.copy(alpha = 0.95f))
                )
                Spacer(Modifier.width(10.dp))

                Text(
                    text = "Portfolio",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val label = if (show) "Ocultar" else "Ver"
                Text(
                    text = "$label · ${positions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSoft,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(10.dp))

            if (positions.isEmpty()) {
                Text(
                    text = "Sin posiciones. Compra alguna acción para verla aquí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(inner)
                        .border(1.dp, stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Valor: ${fmt2(totalValue)} €",
                        style = MaterialTheme.typography.labelMedium,
                        color = textStrong,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pnlColor.copy(alpha = 0.14f))
                        .border(1.dp, pnlColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    val sign = if (totalPnl >= 0) "+" else ""
                    Text(
                        text = "PnL: $sign${fmt2(totalPnl)} €",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pnlColor,
                        maxLines = 1
                    )
                }
            }

            if (show) {
                Spacer(Modifier.height(10.dp))
                Divider(color = stroke)
                Spacer(Modifier.height(10.dp))

                val listState = rememberLazyListState()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),            // ✅ esto hace que el listado use el “resto” del panel
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(
                        items = positions.sortedByDescending { it.valueNow },
                        key = { it.ticker }
                    ) { pos ->
                        PositionRow(
                            pos = pos,
                            surface = inner,
                            stroke = stroke,
                            textStrong = textStrong,
                            textSoft = textSoft,
                            textMuted = textMuted,
                            neutral = neutral,
                            success = success,
                            danger = danger,
                            canTrade = canTrade,
                            onBuy = { onBuy(pos.ticker) },
                            onSell = { onSell(pos.ticker) }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap para ver detalle",
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted
                )

                Spacer(Modifier.weight(1f)) // ✅ rellena el panel cuando está colapsado
            }

        }
    }
}


@Composable
private fun PositionRow(
    pos: PositionSnapshot,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    neutral: Color,
    success: Color,
    danger: Color,
    // ✅ nuevos
    canTrade: Boolean,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    val pnlColor = when {
        pos.pnlEuro > 0.0001 -> success
        pos.pnlEuro < -0.0001 -> danger
        else -> neutral
    }
    val signEuro = if (pos.pnlEuro >= 0) "+" else ""
    val signPct = if (pos.pnlPercent >= 0) "+" else ""

    val rowShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(surface)
            .border(1.dp, stroke, rowShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pos.ticker,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                val badgeShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(pnlColor.copy(alpha = 0.16f))
                        .border(1.dp, pnlColor.copy(alpha = 0.35f), badgeShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$signEuro${fmt2(pos.pnlEuro)}€ · $signPct${fmt2(pos.pnlPercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = pnlColor,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Qty: ${pos.quantity} · Avg: ${fmt2(pos.avgBuyPrice)}€",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Now: ${fmt2(pos.currentPrice)}€",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft,
                    maxLines = 1
                )
            }

            Text(
                text = "Valor: ${fmt2(pos.valueNow)}€ · Invertido: ${fmt2(pos.invested)}€",
                style = MaterialTheme.typography.bodySmall,
                color = textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ✅ BUY/SELL (mismo estilo que Mercado)
            val buyBg = if (canTrade) success.copy(alpha = 0.14f) else neutral.copy(alpha = 0.10f)
            val buyStroke = if (canTrade) success.copy(alpha = 0.35f) else neutral.copy(alpha = 0.25f)
            val buyFg = if (canTrade) success else neutral

            val canSell = canTrade && pos.quantity > 0
            val sellBg = if (canSell) danger.copy(alpha = 0.14f) else neutral.copy(alpha = 0.10f)
            val sellStroke = if (canSell) danger.copy(alpha = 0.35f) else neutral.copy(alpha = 0.25f)
            val sellFg = if (canSell) danger else neutral

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TradeChip(
                    text = "BUY",
                    enabled = canTrade,
                    bg = buyBg,
                    stroke = buyStroke,
                    fg = buyFg,
                    modifier = Modifier.weight(1f),
                    onClick = onBuy
                )
                TradeChip(
                    text = "SELL",
                    enabled = canSell,
                    bg = sellBg,
                    stroke = sellStroke,
                    fg = sellFg,
                    modifier = Modifier.weight(1f),
                    onClick = onSell
                )
            }

            Text(
                text = if (canTrade) "Trade disponible" else "Mercado pausado/cerrado",
                style = MaterialTheme.typography.labelSmall,
                color = textMuted
            )
        }
    }
}

// =========================================================
// Transactions panel
// =========================================================
@Composable
private fun TransactionsPanel(
    modifier: Modifier = Modifier,   // ✅ AÑADIR
    txs: List<Transaction>,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    neutral: Color
) {
    val shape = RoundedCornerShape(16.dp)
    val ordered = txs.asReversed() // ✅ más recientes arriba
    val listState = rememberLazyListState()

    Card(
        modifier = modifier, // ✅ controlado por weight en el padre
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, stroke, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Transacciones",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${txs.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSoft
                )
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = stroke)
            Spacer(Modifier.height(10.dp))

            if (ordered.isEmpty()) {
                Text(
                    text = "Aún no hay operaciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textMuted
                )
            } else {
                // ✅ LISTA SCROLL INDEPENDIENTE
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(items = ordered, key = { it.id }) { tx ->
                        TxRow(tx, inner, stroke, textStrong, textSoft, neutral)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxRow(
    tx: Transaction,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "ID ${tx.id} · ${tx.type} · ${tx.ticker} x${tx.quantity}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Neto: ${fmt2(tx.netTotal)} € · ${tx.timestamp.toString().take(19)}",
                style = MaterialTheme.typography.labelSmall,
                color = textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =========================================================
// Featured
// =========================================================
@Composable
private fun FeaturedMini(
    featured: StockSnapshot?,
    show: Boolean,
    onToggle: () -> Unit,
    brand: Color,
    brand2: Color,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    pctColor: (Double) -> Color,
    arrow: (Double) -> String
) {
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.verticalGradient(listOf(brand, brand2)))
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = featured?.name ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textStrong,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ticker: ${featured?.ticker ?: "--"} · ${featured?.sector ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSoft,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val pct = featured?.changePercent ?: 0.0
                val c = pctColor(pct)
                val badgeShape = RoundedCornerShape(999.dp)

                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(c.copy(alpha = 0.14f))
                        .border(1.dp, c.copy(alpha = 0.32f), badgeShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${arrow(pct)} ${fmt2(pct)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = c,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (show) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = featured?.currentPrice?.let { "${fmt2(it)} €" } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textStrong,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap para ocultar",
                        style = MaterialTheme.typography.labelSmall,
                        color = textMuted
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(inner)
                        .border(1.dp, stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    val line = buildString {
                        append("Apertura: ")
                        append(featured?.openPrice?.let { fmt2(it) } ?: "--")
                        append("  ·  Máx: ")
                        append(featured?.highPrice?.let { fmt2(it) } ?: "--")
                        append("  ·  Mín: ")
                        append(featured?.lowPrice?.let { fmt2(it) } ?: "--")
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = textSoft,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap para ver detalle",
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted
                )
            }
        }
    }
}

// =========================================================
// NEWS
// =========================================================
@Composable
private fun NewsTickerScrollable(
    news: List<NewsEvent>,
    expanded: Boolean,
    onToggle: () -> Unit,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    success: Color,
    danger: Color
) {
    val shape = RoundedCornerShape(16.dp)
    val last = news.lastOrNull()

    val impactColor = when {
        last == null -> neutral
        last.impactPercent > 0.05 -> success
        last.impactPercent < -0.05 -> danger
        else -> neutral
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(impactColor.copy(alpha = 0.95f))
                )
                Spacer(Modifier.width(10.dp))

                Text(
                    text = if (last == null) "Sin noticias todavía…" else last.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textStrong,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val label = if (expanded) "Ocultar" else "Ver"
                Text(
                    text = "$label · ${news.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSoft
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = stroke)
                Spacer(Modifier.height(10.dp))

                if (news.isEmpty()) {
                    Text(
                        text = "Sin noticias todavía…",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSoft
                    )
                } else {
                    val ordered = news.asReversed()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 2.dp)
                    ) {
                        items(items = ordered, key = { it.hashCode() }) { item ->
                            NewsRowCompact(
                                item = item,
                                surface = inner,
                                stroke = stroke,
                                textStrong = textStrong,
                                textSoft = textSoft,
                                neutral = neutral,
                                success = success,
                                danger = danger
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsRowCompact(
    item: NewsEvent,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    success: Color,
    danger: Color
) {
    val impactColor = when {
        item.impactPercent > 0.05 -> success
        item.impactPercent < -0.05 -> danger
        else -> neutral
    }
    val sign = if (item.impactPercent >= 0) "+" else ""
    val pct = fmt1(item.impactPercent)
    val rowShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(surface)
            .border(1.dp, stroke, rowShape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(impactColor.copy(alpha = 0.95f))
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Sector: ${item.sector}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSoft,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val badgeShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .clip(badgeShape)
                    .background(impactColor.copy(alpha = 0.14f))
                    .border(1.dp, impactColor.copy(alpha = 0.32f), badgeShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$sign$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = impactColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

// =========================================================
// Market header + row (con bloqueo de trade final)
// =========================================================
@Composable
private fun MarketHeader(
    textStrong: Color,
    textSoft: Color,
    stroke: Color
) {
    val wMarket = 1.35f
    val wPrice = 0.70f
    val wVar = 1.05f
    val wTrade = 0.85f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Mercado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textStrong,
            modifier = Modifier.weight(wMarket),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Precio",
            style = MaterialTheme.typography.labelMedium,
            color = textSoft,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(wPrice)
                .padding(end = 6.dp),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "Var.",
            style = MaterialTheme.typography.labelMedium,
            color = textSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(wVar),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "Trade",
            style = MaterialTheme.typography.labelMedium,
            color = textSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(wTrade),
            maxLines = 1,
            softWrap = false
        )
    }

    Spacer(Modifier.height(8.dp))
    Divider(color = stroke)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MarketRow(
    isSelected: Boolean,
    ticker: String,
    name: String,
    sector: Sector,
    volume: Long,
    openPrice: Double,
    highPrice: Double,
    lowPrice: Double,
    price: Double,
    changeEuro: Double,
    changePercent: Double,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    neutral: Color,
    success: Color,
    danger: Color,
    canTrade: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    val baseColor = when {
        changePercent > 0.0001 -> success
        changePercent < -0.0001 -> danger
        else -> neutral
    }
    val accent by animateColorAsState(baseColor, label = "rowAccent")

    val arrow = when {
        changePercent > 0.0001 -> "▲"
        changePercent < -0.0001 -> "▼"
        else -> "•"
    }

    val signEuro = if (changeEuro >= 0) "+" else ""
    val signPct = if (changePercent >= 0) "+" else ""

    val wMarket = 1.35f
    val wPrice = 0.70f
    val wVar = 1.05f
    val wTrade = 0.85f

    val minPrice = 78.dp
    val minVar = 108.dp
    val minTrade = 86.dp

    val shape = RoundedCornerShape(16.dp)
    val badgeShape = RoundedCornerShape(999.dp)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.55f) else stroke,
        label = "rowBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.95f))
            )
            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(wMarket)
                    .padding(end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ticker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textStrong,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = sectorShort(sector),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Vol $volume · D ${fmt2(openPrice)} · H ${fmt2(highPrice)} · L ${fmt2(lowPrice)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${fmt2(price)} €",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textStrong,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .weight(wPrice)
                    .widthIn(min = minPrice)
                    .padding(end = 6.dp)
            )

            Box(
                modifier = Modifier
                    .weight(wVar)
                    .widthIn(min = minVar)
                    .clip(badgeShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), badgeShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$arrow $signEuro${fmt2(changeEuro)}€",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "$signPct${fmt2(changePercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            val buyBg = if (canTrade) success.copy(alpha = 0.14f) else neutral.copy(alpha = 0.10f)
            val buyStroke = if (canTrade) success.copy(alpha = 0.35f) else neutral.copy(alpha = 0.25f)
            val buyFg = if (canTrade) success else neutral

            val sellBg = if (canTrade) danger.copy(alpha = 0.14f) else neutral.copy(alpha = 0.10f)
            val sellStroke = if (canTrade) danger.copy(alpha = 0.35f) else neutral.copy(alpha = 0.25f)
            val sellFg = if (canTrade) danger else neutral

            Column(
                modifier = Modifier
                    .weight(wTrade)
                    .widthIn(min = minTrade),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                TradeChip(
                    text = "BUY",
                    enabled = canTrade,
                    bg = buyBg,
                    stroke = buyStroke,
                    fg = buyFg,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBuy
                )
                TradeChip(
                    text = "SELL",
                    enabled = canTrade,
                    bg = sellBg,
                    stroke = sellStroke,
                    fg = sellFg,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSell
                )
            }
        }
    }
}

private fun sectorShort(s: Sector): String = when (s) {
    Sector.TECHNOLOGY -> "TECH"
    Sector.ENERGY -> "ENER"
    Sector.BANKING -> "BANK"
    Sector.RETAIL -> "RETL"
    Sector.HEALTHCARE -> "HLTH"
}

@Composable
private fun TradeChip(
    text: String,
    enabled: Boolean,
    bg: Color,
    stroke: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.08f))
            .border(1.dp, if (enabled) stroke else stroke.copy(alpha = 0.18f), shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) fg else fg.copy(alpha = 0.35f),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

// =========================================================
// CHARTS (final, sin libs extra)
// =========================================================
@Composable
private fun ChartsPanel(
    modifier: Modifier = Modifier,
    marketStocks: List<StockSnapshot>,
    positions: List<PositionSnapshot>,
    selectedTicker: String,
    onSelectTicker: (String) -> Unit,
    priceHistory: Map<String, List<Double>>,
    valueHistory: List<Double>,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    brand: Color,
    brand2: Color,
    neutral: Color,
    success: Color,
    danger: Color
) {
    val shape = RoundedCornerShape(16.dp)

    val stock = marketStocks.firstOrNull { it.ticker == selectedTicker } ?: marketStocks.firstOrNull()
    val ticker = stock?.ticker ?: selectedTicker
    val hist = priceHistory[ticker].orEmpty()

    val totalValueNow = positions.sumOf { it.valueNow }
    val investedNow = positions.sumOf { it.invested }
    val pnlNow = totalValueNow - investedNow

    val sectorByTicker = marketStocks.associateBy({ it.ticker }, { it.sector })
    val bySector = positions
        .groupBy { sectorByTicker[it.ticker] ?: Sector.TECHNOLOGY }
        .mapValues { (_, list) -> list.sumOf { it.valueNow } }
        .toList()
        .sortedByDescending { it.second }

    val pnlByTicker = positions
        .map { it.ticker to it.pnlEuro }
        .sortedByDescending { it.second }

    Card(
        modifier = modifier, // 👈 antes: Modifier.fillMaxWidth()
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, stroke, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp) // 👈 para que no se “coma” lo último
        ) {

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gráficos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textStrong,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(brand.copy(alpha = 0.14f))
                            .border(1.dp, brand.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ticker: ${ticker.ifBlank { "--" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = brand,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            item {
                if (marketStocks.isNotEmpty()) {
                    val top = marketStocks.take(12)

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 24.dp) // 👈 evita que el último chip quede “debajo” del scrollbar
                    ) {
                        items(items = top, key = { it.ticker }) { s ->
                            val sel = s.ticker == ticker
                            val bg = if (sel) brand2.copy(alpha = 0.16f) else inner
                            val br = if (sel) brand2.copy(alpha = 0.35f) else stroke
                            val fg = if (sel) Color(0xFFEAF1FF) else textSoft

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(bg)
                                    .border(1.dp, br, RoundedCornerShape(999.dp))
                                    .clickable { onSelectTicker(s.ticker) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = s.ticker,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item { Divider(color = stroke) }

            item {
                ChartBlock(
                    title = "Precio · $ticker",
                    subtitle = if (hist.size < 2) "Sin histórico suficiente todavía" else "Últimos ${hist.size} puntos",
                    surface = inner,
                    stroke = stroke,
                    textStrong = textStrong,
                    textSoft = textSoft,
                    textMuted = textMuted
                ) {
                    LineChart(
                        values = hist,
                        lineColor = brand,
                        strokeColor = brand.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }

            item {
                ChartBlock(
                    title = "Valor total del portfolio",
                    subtitle = if (valueHistory.size < 2) "Sin histórico suficiente todavía" else "Últimos ${valueHistory.size} puntos",
                    surface = inner,
                    stroke = stroke,
                    textStrong = textStrong,
                    textSoft = textSoft,
                    textMuted = textMuted
                ) {
                    LineChart(
                        values = valueHistory,
                        lineColor = if (pnlNow >= 0) success else danger,
                        strokeColor = (if (pnlNow >= 0) success else danger).copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }

            item {
                ChartBlock(
                    title = "Distribución por sector",
                    subtitle = if (bySector.isEmpty()) "Sin posiciones todavía" else "Por valor actual (€)",
                    surface = inner,
                    stroke = stroke,
                    textStrong = textStrong,
                    textSoft = textSoft,
                    textMuted = textMuted
                ) {
                    if (bySector.isEmpty()) {
                        Text("Compra acciones para ver la distribución.", color = textMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        val colors = bySector.map { (s, _) -> sectorColor(s, brand, brand2, success, danger, neutral) }
                        DonutChart(
                            values = bySector.map { it.second },
                            colors = colors,
                            stroke = stroke,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            item {
                ChartBlock(
                    title = "PnL por acción",
                    subtitle = if (pnlByTicker.isEmpty()) "Sin posiciones todavía" else "Barra centrada (0€) · positivo/negativo",
                    surface = inner,
                    stroke = stroke,
                    textStrong = textStrong,
                    textSoft = textSoft,
                    textMuted = textMuted
                ) {
                    if (pnlByTicker.isEmpty()) {
                        Text("Sin posiciones.", color = textMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        val top = pnlByTicker.take(10)
                        PnlBarsChart(
                            items = top,
                            positive = success,
                            negative = danger,
                            neutral = stroke,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartBlock(
    title: String,
    subtitle: String,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = textStrong, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = textMuted, style = MaterialTheme.typography.labelSmall)
        content()
    }
}

@Composable
private fun LineChart(
    values: List<Double>,
    lineColor: Color,
    strokeColor: Color,
    modifier: Modifier
) {
    val safe = values.filter { !it.isNaN() && !it.isInfinite() }
    if (safe.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("—", color = strokeColor, style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    val minV = safe.minOrNull() ?: 0.0
    val maxV = safe.maxOrNull() ?: 1.0
    val range = max(1e-9, maxV - minV)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 10f

        // “borde” suave
        drawRoundRect(
            color = strokeColor.copy(alpha = 0.25f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 1.2f)
        )

        val stepX = (w - pad * 2) / (safe.size - 1).toFloat()

        fun yOf(v: Double): Float {
            val norm = ((v - minV) / range).toFloat()
            return (h - pad) - norm * (h - pad * 2)
        }

        // línea
        for (i in 0 until safe.size - 1) {
            val x1 = pad + i * stepX
            val x2 = pad + (i + 1) * stepX
            val y1 = yOf(safe[i])
            val y2 = yOf(safe[i + 1])
            drawLine(
                color = lineColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )
        }

        // punto final
        val xLast = pad + (safe.size - 1) * stepX
        val yLast = yOf(safe.last())
        drawCircle(color = lineColor, radius = 5.5f, center = Offset(xLast, yLast))
    }
}

@Composable
private fun DonutChart(
    values: List<Double>,
    colors: List<Color>,
    stroke: Color,
    modifier: Modifier
) {
    val total = values.sum().takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = min(w, h) * 0.35f
        val center = Offset(w / 2f, h / 2f)
        var start = -90f

        // fondo
        drawCircle(color = stroke.copy(alpha = 0.25f), radius = r, center = center, style = Stroke(width = r * 0.55f))

        values.forEachIndexed { i, v ->
            val sweep = (v / total * 360f).toFloat()
            val c = colors.getOrNull(i) ?: stroke
            drawArc(
                color = c,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = r * 0.55f, cap = StrokeCap.Round)
            )
            start += sweep
        }
    }
}

@Composable
private fun PnlBarsChart(
    items: List<Pair<String, Double>>,
    positive: Color,
    negative: Color,
    neutral: Color,
    modifier: Modifier
) {
    val maxAbs = items.maxOfOrNull { abs(it.second) }?.takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 10f

        // eje 0
        val midY = h / 2f
        drawLine(
            color = neutral.copy(alpha = 0.55f),
            start = Offset(pad, midY),
            end = Offset(w - pad, midY),
            strokeWidth = 2.0f
        )

        val n = items.size
        val gap = 6f
        val barW = ((w - pad * 2) - gap * (n - 1)) / n
        val maxLen = (h / 2f) - pad

        items.forEachIndexed { idx, (_, v) ->
            val x = pad + idx * (barW + gap)
            val len = (abs(v) / maxAbs * maxLen).toFloat()

            val c = when {
                v > 0.0001 -> positive
                v < -0.0001 -> negative
                else -> neutral
            }

            if (v >= 0) {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(x, midY - len),
                    size = Size(barW, len),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
            } else {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(x, midY),
                    size = Size(barW, len),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                )
            }
        }
    }
}

private fun sectorColor(
    s: Sector,
    brand: Color,
    brand2: Color,
    success: Color,
    danger: Color,
    neutral: Color
): Color = when (s) {
    Sector.TECHNOLOGY -> brand
    Sector.BANKING -> brand2
    Sector.ENERGY -> success
    Sector.RETAIL -> danger
    Sector.HEALTHCARE -> neutral
}

// =========================================================
// ALERTS (final)
// =========================================================
@Composable
private fun AlertsPanel(
    marketStocks: List<StockSnapshot>,
    canTrade: Boolean,
    alerts: List<AlertRule>,
    fired: List<FiredAlert>,
    banner: String?,
    onDismissBanner: () -> Unit,
    onCreate: () -> Unit,
    onUpdate: (AlertRule) -> Unit,
    onDelete: (Long) -> Unit,
    surface: Color,
    inner: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    brand: Color,
    neutral: Color,
    success: Color,
    danger: Color
) {
    val shape = RoundedCornerShape(16.dp)
    val byTicker = marketStocks.associateBy { it.ticker }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Alertas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(brand.copy(alpha = 0.14f))
                        .border(1.dp, brand.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (canTrade) "Mercado OK" else "Mercado pausado/cerrado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canTrade) success else neutral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = banner != null) {
                val b = banner ?: ""
                val bShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bShape)
                        .background(brand.copy(alpha = 0.12f))
                        .border(1.dp, brand.copy(alpha = 0.25f), bShape)
                        .clickable { onDismissBanner() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = b,
                        color = textStrong,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f)
                ) { Text("Nueva alerta") }

                val active = alerts.count { it.enabled && !it.triggered }
                val trig = alerts.count { it.triggered }
                MiniStatPill(
                    title = "Activas",
                    value = "$active",
                    surface = inner,
                    stroke = stroke,
                    textMuted = textMuted,
                    textStrong = textStrong,
                    modifier = Modifier.weight(1f)
                )
                MiniStatPill(
                    title = "Disparadas",
                    value = "$trig",
                    surface = inner,
                    stroke = stroke,
                    textMuted = textMuted,
                    textStrong = textStrong,
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(color = stroke)

            // Lista de alertas
            Text("Reglas", color = textSoft, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)

            if (alerts.isEmpty()) {
                Text("No hay alertas. Crea una para probar 🔔", color = textMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts
                        .sortedWith(compareBy<AlertRule> { it.triggered }.thenByDescending { it.createdTick })
                        .forEach { a ->
                            val s = byTicker[a.ticker]
                            val currentLabel = when (a.type) {
                                AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> s?.currentPrice?.let { fmt2(it) } ?: "--"
                                AlertType.PCT_ABOVE, AlertType.PCT_BELOW -> s?.changePercent?.let { fmt2(it) } ?: "--"
                            }

                            val stateColor = when {
                                a.triggered -> danger
                                a.enabled -> success
                                else -> neutral
                            }

                            AlertRow(
                                a = a,
                                current = currentLabel,
                                stateColor = stateColor,
                                surface = inner,
                                stroke = stroke,
                                textStrong = textStrong,
                                textSoft = textSoft,
                                textMuted = textMuted,
                                onToggle = {
                                    if (a.triggered) return@AlertRow
                                    onUpdate(a.copy(enabled = !a.enabled))
                                },
                                onReset = {
                                    // Rearmar: vuelve a activa/no triggered (útil en demo)
                                    onUpdate(a.copy(enabled = true, triggered = false, triggeredTick = null))
                                },
                                onDelete = { onDelete(a.id) }
                            )
                        }
                }
            }

            Divider(color = stroke)

            // Historial
            Text("Historial", color = textSoft, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)

            if (fired.isEmpty()) {
                Text("Aún no se ha disparado ninguna alerta.", color = textMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fired.takeLast(8).reversed().forEach { f ->
                        FiredRow(
                            f = f,
                            surface = inner,
                            stroke = stroke,
                            textStrong = textStrong,
                            textSoft = textSoft,
                            neutral = neutral
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    title: String,
    value: String,
    surface: Color,
    stroke: Color,
    textMuted: Color,
    textStrong: Color,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall, color = textMuted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, color = textStrong, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun AlertRow(
    a: AlertRule,
    current: String,
    stateColor: Color,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val status = when {
        a.triggered -> "DISPARADA"
        a.enabled -> "ACTIVA"
        else -> "PAUSADA"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(stateColor.copy(alpha = 0.95f))
                )
                Spacer(Modifier.width(10.dp))

                Text(
                    text = "${a.ticker} · ${a.type.label} ${fmt2(a.threshold)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(stateColor.copy(alpha = 0.14f))
                        .border(1.dp, stateColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable {
                            if (a.triggered) onReset() else onToggle()
                        }
                ) {
                    Text(
                        text = if (a.triggered) "RESET" else status,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "✖",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = textMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = "Actual: $current  ·  Estado: $status",
                style = MaterialTheme.typography.labelSmall,
                color = textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (a.triggered && a.triggeredTick != null) {
                Text(
                    text = "Disparada en tick #${a.triggeredTick}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted
                )
            }
        }
    }
}

@Composable
private fun FiredRow(
    f: FiredAlert,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "🔔 ${f.ticker} · ${f.type.label} ${fmt2(f.threshold)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = textStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Valor al disparar: ${fmt2(f.valueAtFire)}  ·  tick #${f.firedTick}",
                style = MaterialTheme.typography.labelSmall,
                color = textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAlertDialog(
    tick: Long,
    defaultTicker: String,
    tickers: List<String>,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    brand: Color,
    onDismiss: () -> Unit,
    onCreate: (AlertRule) -> Unit
) {
    var ticker by remember { mutableStateOf(defaultTicker.ifBlank { tickers.firstOrNull().orEmpty() }) }
    var type by remember { mutableStateOf(AlertType.PRICE_ABOVE) }
    var thresholdText by remember { mutableStateOf("100") }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseDoubleOrNull(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    val dialogShape = RoundedCornerShape(18.dp)
    val innerShape = RoundedCornerShape(14.dp)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = dialogShape,
            colors = CardDefaults.cardColors(containerColor = surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, stroke, dialogShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = "Nueva alerta",
                    color = textStrong,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                // Ticker chips
                Text("Ticker", color = textSoft, style = MaterialTheme.typography.labelSmall)

                if (tickers.isEmpty()) {
                    Text("No hay tickers.", color = neutral, style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tickers.take(12).forEach { t ->
                            val sel = t == ticker
                            val bg = if (sel) brand.copy(alpha = 0.16f) else Color.Transparent
                            val br = if (sel) brand.copy(alpha = 0.35f) else stroke
                            val fg = if (sel) Color(0xFFEAF1FF) else textSoft

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(bg)
                                    .border(1.dp, br, RoundedCornerShape(999.dp))
                                    .clickable { ticker = t; error = null }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = t,
                                    color = fg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                }

                // Type chips
                Text("Tipo", color = textSoft, style = MaterialTheme.typography.labelSmall)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertType.values().forEach { t ->
                        val sel = t == type
                        val bg = if (sel) brand.copy(alpha = 0.16f) else Color.Transparent
                        val br = if (sel) brand.copy(alpha = 0.35f) else stroke
                        val fg = if (sel) Color(0xFFEAF1FF) else textSoft

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(bg)
                                .border(1.dp, br, RoundedCornerShape(999.dp))
                                .clickable { type = t; error = null }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = t.label,
                                color = fg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }


                // Threshold
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it; error = null },
                    label = { Text("Umbral (ej: 105.5 ó 0.8)", color = textSoft) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = innerShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = brand.copy(alpha = 0.55f),
                        unfocusedBorderColor = stroke,
                        cursorColor = brand,
                        focusedLabelColor = brand,
                        unfocusedLabelColor = textSoft
                    )
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = Color(0xFFFB7185),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = textSoft)
                    }

                    Button(
                        onClick = {
                            val th = parseDoubleOrNull(thresholdText)
                            if (ticker.isBlank()) {
                                error = "Ticker vacío"
                                return@Button
                            }
                            if (th == null) {
                                error = "Umbral inválido"
                                return@Button
                            }

                            onCreate(
                                AlertRule(
                                    id = (ticker.trim().uppercase().hashCode().toLong() shl 32) xor (tick + th.toLong()),
                                    ticker = ticker.trim().uppercase(),
                                    type = type,
                                    threshold = th,
                                    createdTick = tick
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brand,
                            contentColor = Color(0xFF001018)
                        )
                    ) {
                        Text("Crear", maxLines = 1)
                    }

                }

            }

        }

    }


}



// =========================================================
// Formatters KMP-safe (✅ anti NaN/Infinity)
// =========================================================
private fun fmt2(value: Double): String = fmtFixed(value, 2)
private fun fmt1(value: Double): String = fmtFixed(value, 1)

private fun fmtFixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "--"

    val sign = if (value < 0) "-" else ""
    val absValue = abs(value)

    val pow10 = pow10(decimals)
    val scaled = (absValue * pow10.toDouble()).roundToLong()

    val intPart = scaled / pow10
    val fracPart = (scaled % pow10).toInt()

    return if (decimals == 0) {
        "$sign$intPart"
    } else {
        "$sign$intPart.${fracPart.toString().padStart(decimals, '0')}"
    }
}

private fun pow10(decimals: Int): Long {
    var p = 1L
    repeat(decimals.coerceAtLeast(0)) { p *= 10L }
    return p
}
