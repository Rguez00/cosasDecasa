package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.core.config.InitialData
import org.example.project.data.repository.InMemoryMarketRepository
import org.example.project.data.repository.InMemoryPortfolioRepository
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.StockSnapshot
import org.example.project.engine.MarketEngine
import org.example.project.presentation.ui.TradeDialog
import org.example.project.presentation.vm.PortfolioViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs

@Composable
@Preview
fun App() {
    val marketRepo = remember { InMemoryMarketRepository(InitialData.defaultStocks()) }
    val engine = remember { MarketEngine(marketRepo) }

    val portfolioRepo = remember { InMemoryPortfolioRepository(marketRepo) }
    val portfolioVm = remember { PortfolioViewModel(portfolioRepo) }

    LaunchedEffect(Unit) { engine.startAllTickers() }
    DisposableEffect(Unit) {
        onDispose {
            engine.close()
            portfolioVm.close()
        }
    }

    val marketState by engine.marketState.collectAsState()
    val portfolioState by portfolioVm.portfolioState.collectAsState()

    var selectedTicker by remember { mutableStateOf("NBS") }
    val featured: StockSnapshot? =
        marketState.stocks.firstOrNull { it.ticker == selectedTicker } ?: marketState.stocks.firstOrNull()

    // =========================================================
    // PALETA (más contraste) + layout apretado
    // =========================================================
    val bgTop = Color(0xFF050A14)
    val surface0 = Color(0xFF0A1222)
    val surface1 = Color(0xFF0E1B33)
    val surface2 = Color(0xFF142B4F)
    val stroke = Color(0x44FFFFFF)
    val strokeSoft = Color(0x2EFFFFFF)

    val brand = Color(0xFF38BDF8)
    val brand2 = Color(0xFF6366F1)

    val success = Color(0xFF22C55E)
    val danger = Color(0xFFFB7185)
    val neutral = Color(0xFF94A3B8)

    val textStrong = Color(0xFFEAF1FF)
    val textSoft = Color(0xFFB9C6DA)
    val textMuted = Color(0xFF93A7C4)

    fun pctColor(pct: Double): Color = when {
        pct > 0.0001 -> success
        pct < -0.0001 -> danger
        else -> neutral
    }

    fun arrow(pct: Double): String = when {
        pct > 0.0001 -> "▲"
        pct < -0.0001 -> "▼"
        else -> "•"
    }

    // Layout (menos margen real)
    val outerPad = 6.dp
    val innerPadH = 8.dp
    val innerPadV = 8.dp
    val sectionGap = 6.dp

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(bgTop, surface0)))
                // ✅ Insets SOLO arriba/abajo (evita “margen lateral raro” del emulador)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
        ) {
            val mainShape = RoundedCornerShape(18.dp)

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = outerPad, vertical = outerPad),
                shape = mainShape,
                colors = CardDefaults.cardColors(containerColor = surface0),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, stroke, mainShape)
                        .padding(horizontal = innerPadH, vertical = innerPadV)
                ) {
                    // =========================================================
                    // HEADER compacto
                    // =========================================================
                    HeaderCompact(
                        isOpen = marketState.isOpen,
                        simSpeed = marketState.simSpeed,
                        trend = marketState.trend,
                        stocksCount = marketState.stocks.size,
                        success = success,
                        danger = danger,
                        textStrong = textStrong,
                        textSoft = textSoft,
                        stroke = strokeSoft
                    )

                    Spacer(Modifier.height(sectionGap))
                    Divider(color = strokeSoft)
                    Spacer(Modifier.height(sectionGap))

                    // =========================================================
                    // TOP BAR compacto (sin perder info)
                    // =========================================================
                    CompactTopBar(
                        cash = portfolioState.cash,
                        value = portfolioState.portfolioValue,
                        pnlEuro = portfolioState.pnlEuro,
                        pnlPercent = portfolioState.pnlPercent,
                        isOpen = marketState.isOpen,
                        isPaused = marketState.isPaused,
                        simSpeed = marketState.simSpeed,
                        surface = surface1,
                        inner = surface2,
                        stroke = strokeSoft,
                        textStrong = textStrong,
                        textSoft = textSoft,
                        textMuted = textMuted,
                        success = success,
                        danger = danger,
                        neutral = neutral,
                        onToggleOpen = { engine.setMarketOpen(!marketState.isOpen) },
                        onTogglePause = { engine.setPaused(!marketState.isPaused) },
                        onSetSpeed = { engine.setSimSpeed(it) }
                    )

                    Spacer(Modifier.height(sectionGap))

                    // =========================================================
                    // Featured + News (colapsados por defecto => más mercado)
                    // =========================================================
                    var showFeatured by remember { mutableStateOf(false) }
                    var showNewsExpanded by remember { mutableStateOf(false) }

                    FeaturedMini(
                        featured = featured,
                        show = showFeatured,
                        onToggle = { showFeatured = !showFeatured },
                        brand = brand,
                        brand2 = brand2,
                        surface = surface1,
                        inner = surface2,
                        stroke = strokeSoft,
                        textStrong = textStrong,
                        textSoft = textSoft,
                        textMuted = textMuted,
                        pctColor = { pctColor(it) },
                        arrow = { arrow(it) }
                    )

                    Spacer(Modifier.height(sectionGap))

                    NewsTicker(
                        news = marketState.news,
                        expanded = showNewsExpanded,
                        onToggle = { showNewsExpanded = !showNewsExpanded },
                        surface = surface1,
                        inner = surface2,
                        stroke = strokeSoft,
                        textStrong = textStrong,
                        textSoft = textSoft,
                        neutral = neutral,
                        success = success,
                        danger = danger
                    )

                    Spacer(Modifier.height(sectionGap))
                    Divider(color = strokeSoft)
                    Spacer(Modifier.height(sectionGap))

                    // =========================================================
                    // MARKET HEADER + LIST (reparto por weights, sin cortes)
                    // =========================================================
                    MarketHeader(
                        textStrong = textStrong,
                        textSoft = textSoft,
                        stroke = strokeSoft
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = marketState.stocks,
                            key = { it.ticker }
                        ) { stock ->
                            MarketRow(
                                ticker = stock.ticker,
                                name = stock.name,
                                price = stock.currentPrice,
                                changeEuro = stock.changeEuro,
                                changePercent = stock.changePercent,
                                surface = surface2,
                                stroke = strokeSoft,
                                textStrong = textStrong,
                                textSoft = textSoft,
                                neutral = neutral,
                                success = success,
                                danger = danger,
                                onSelect = { selectedTicker = stock.ticker },
                                onBuy = { portfolioVm.openTrade(stock.ticker, PortfolioViewModel.Mode.BUY) },
                                onSell = { portfolioVm.openTrade(stock.ticker, PortfolioViewModel.Mode.SELL) }
                            )
                        }
                    }
                }
            }

            TradeDialog(
                vm = portfolioVm,
                dialogSurface = surface0,
                innerSurface = surface1,
                stroke = stroke,
                textStrong = textStrong,
                textSoft = textSoft,
                textMuted = textMuted,
                success = success,
                danger = danger,
                neutral = neutral
            )
        }
    }
}

// =========================================================
// HEADER compacto
// =========================================================
@Composable
private fun HeaderCompact(
    isOpen: Boolean,
    simSpeed: Double,
    trend: Any,
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
                text = "${if (isOpen) "ABIERTO" else "CERRADO"} · x${String.format("%.2f", simSpeed)} · $trend · $stocksCount acciones",
                style = MaterialTheme.typography.bodySmall,
                color = textSoft,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        val chipBg = if (isOpen) Color(0xFF0E2F1F) else Color(0xFF3A0F14)
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

// =========================================================
// TOP BAR compacto (Portfolio + Controles)
// =========================================================
@Composable
private fun CompactTopBar(
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPill("Saldo", String.format("%.2f €", cash), Modifier.weight(1f), inner, stroke, textMuted, textStrong)
                MiniPill("Valor", String.format("%.2f €", value), Modifier.weight(1f), inner, stroke, textMuted, textStrong)
                MiniPill(
                    "PnL",
                    "$signEuro${String.format("%.2f", pnlEuro)}€",
                    Modifier.weight(1f),
                    inner,
                    stroke,
                    textMuted,
                    pnlColor,
                    sub = "$signPct${String.format("%.2f", pnlPercent)}%"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlChip(
                    text = if (isOpen) "CERRAR" else "ABRIR",
                    bg = (if (isOpen) danger else success).copy(alpha = 0.18f),
                    stroke = (if (isOpen) danger else success).copy(alpha = 0.40f),
                    fg = if (isOpen) danger else success,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleOpen
                )
                ControlChip(
                    text = if (isPaused) "REANUDAR" else "PAUSAR",
                    bg = neutral.copy(alpha = 0.16f),
                    stroke = neutral.copy(alpha = 0.35f),
                    fg = Color(0xFFE3ECFF),
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePause
                )
            }

            SpeedMiniRow(
                current = simSpeed,
                textSoft = textSoft,
                textStrong = textStrong,
                textMuted = textMuted,
                stroke = stroke,
                inner = inner,
                onSetSpeed = onSetSpeed
            )
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
    sub: String? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(surface)
            .border(1.dp, stroke, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = titleColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
private fun SpeedMiniRow(
    current: Double,
    textSoft: Color,
    textStrong: Color,
    textMuted: Color,
    stroke: Color,
    inner: Color,
    onSetSpeed: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Velocidad: x${String.format("%.2f", current)}",
            style = MaterialTheme.typography.labelMedium,
            color = textSoft,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        SpeedDot("0.5", 0.5, current, textStrong, textMuted, stroke, inner, onSetSpeed)
        SpeedDot("1", 1.0, current, textStrong, textMuted, stroke, inner, onSetSpeed)
        SpeedDot("2", 2.0, current, textStrong, textMuted, stroke, inner, onSetSpeed)
        SpeedDot("5", 5.0, current, textStrong, textMuted, stroke, inner, onSetSpeed)
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
    val bg = if (selected) strong.copy(alpha = 0.18f) else inner
    val br = if (selected) strong.copy(alpha = 0.45f) else stroke
    val fg = if (selected) strong else muted

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, br, shape)
            .clickable { onSetSpeed(speed) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
// Featured (colapsable, compacto)
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
                .padding(horizontal = 10.dp, vertical = 10.dp)
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
                        .background(c.copy(alpha = 0.18f))
                        .border(1.dp, c.copy(alpha = 0.35f), badgeShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${arrow(pct)} ${String.format("%.2f%%", pct)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = c,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (show) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = featured?.currentPrice?.let { String.format("%.2f €", it) } ?: "--",
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

                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(inner)
                        .border(1.dp, stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    val line = buildString {
                        append("Apertura: ")
                        append(featured?.openPrice?.let { String.format("%.2f", it) } ?: "--")
                        append("  ·  Máx: ")
                        append(featured?.highPrice?.let { String.format("%.2f", it) } ?: "--")
                        append("  ·  Mín: ")
                        append(featured?.lowPrice?.let { String.format("%.2f", it) } ?: "--")
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
                Spacer(Modifier.height(6.dp))
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
// News (ticker + expand/collapse)
// =========================================================
@Composable
private fun NewsTicker(
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
                .padding(horizontal = 10.dp, vertical = 10.dp)
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
                Spacer(Modifier.height(8.dp))
                Divider(color = stroke)
                Spacer(Modifier.height(8.dp))

                val last3 = news.takeLast(3).reversed()
                if (last3.isEmpty()) {
                    Text(
                        text = "Sin noticias todavía…",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSoft
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        last3.forEach { item ->
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
    val pct = String.format("%.1f", item.impactPercent)
    val rowShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(surface)
            .border(1.dp, stroke, rowShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    .background(impactColor.copy(alpha = 0.18f))
                    .border(1.dp, impactColor.copy(alpha = 0.35f), badgeShape)
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
// Market header (weights, sin “Mercado” en vertical)
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

    Spacer(Modifier.height(6.dp))
    Divider(color = stroke)
    Spacer(Modifier.height(8.dp))
}

// =========================================================
// Market Row (weights + mínimos => no corta lo importante)
// =========================================================
@Composable
private fun MarketRow(
    ticker: String,
    name: String,
    price: Double,
    changeEuro: Double,
    changePercent: Double,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    success: Color,
    danger: Color,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.95f))
            )
            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(wMarket)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = String.format("%.2f €", price),
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
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.42f), badgeShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$arrow $signEuro${String.format("%.2f", changeEuro)}€",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "$signPct${String.format("%.2f", changePercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(wTrade)
                    .widthIn(min = minTrade),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                TradeChip(
                    text = "BUY",
                    bg = success.copy(alpha = 0.18f),
                    stroke = success.copy(alpha = 0.42f),
                    fg = success,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBuy
                )
                TradeChip(
                    text = "SELL",
                    bg = danger.copy(alpha = 0.18f),
                    stroke = danger.copy(alpha = 0.42f),
                    fg = danger,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSell
                )
            }
        }
    }
}

@Composable
private fun TradeChip(
    text: String,
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
            .background(bg)
            .border(1.dp, stroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
