package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import org.example.project.core.config.InitialData
import org.example.project.data.repository.InMemoryMarketRepository
import org.example.project.domain.model.NewsEvent
import org.example.project.engine.MarketEngine
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val marketRepo = remember { InMemoryMarketRepository(InitialData.defaultStocks()) }
    val engine = remember { MarketEngine(marketRepo) }

    LaunchedEffect(Unit) { engine.startAllTickers() }

    val marketState by engine.marketState.collectAsState()
    val featured = marketState.stocks.firstOrNull { it.ticker == "NBS" }

    // =========================================================
    // PALETA (fondo metálico claro + tarjetas dark fintech)
    // =========================================================

    // ✅ Fondo metálico uniforme (tipo “metal” claro)
    val metalBase = Color(0xFFC7CCD3) // gris metálico claro (uniforme)

    // Cards dark
    val surface0 = Color(0xFF0D1628) // cards
    val surface1 = Color(0xFF0F1C33) // inner blocks
    val stroke = Color(0x22FFFFFF)   // borde suave en dark

    // Accents
    val brand = Color(0xFF38BDF8)   // cyan
    val brand2 = Color(0xFF6366F1)  // indigo
    val success = Color(0xFF22C55E) // green
    val danger = Color(0xFFFB7185)  // rose
    val neutral = Color(0xFF94A3B8) // slate-400

    // ✅ Textos para CARDS dark
    val cardTextStrong = Color(0xFFE2E8F0) // slate-200
    val cardTextSoft = Color(0xFFB6C2D2)   // más suave (no igual que strong)
    val cardTextMuted = Color(0xFF7B8AA1)  // muted

    // ✅ Textos para HEADER sobre metal claro (alto contraste)
    val headerTitle = Color(0xFF0B1220) // casi negro
    val headerSub = Color(0xFF263244)   // gris oscuro
    val headerHairline = Color(0x330B1220)

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

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(metalBase) // ✅ todo el fondo igual
                .safeContentPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
                    .padding(top = 10.dp, bottom = 12.dp)
            ) {
                // =========================================================
                // HEADER (FIJO) - contraste alto en fondo metálico
                // =========================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bolsa Cotarelo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = headerTitle
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Estado: ${if (marketState.isOpen) "ABIERTO" else "CERRADO"} · " +
                                    "Acciones: ${marketState.stocks.size} · " +
                                    "Tendencia: ${marketState.trend}",
                            style = MaterialTheme.typography.bodySmall,
                            color = headerSub
                        )
                    }

                    // Chip estado (fondo oscuro + texto claro para contrastar)
                    val chipBg = if (marketState.isOpen) Color(0xFF0E2F1F) else Color(0xFF3A0F14)
                    val chipText = Color(0xFFEAF2FF)
                    val chipStroke = if (marketState.isOpen) success.copy(alpha = 0.25f) else danger.copy(alpha = 0.25f)
                    val chipShape = RoundedCornerShape(999.dp)

                    Card(
                        shape = chipShape,
                        colors = CardDefaults.cardColors(containerColor = chipBg),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.border(1.dp, chipStroke, chipShape)
                    ) {
                        Text(
                            text = if (marketState.isOpen) "MARKET OPEN" else "MARKET CLOSED",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = chipText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // línea muy fina para separar el header del bloque dark (queda pro)
                Spacer(Modifier.height(10.dp))
                Divider(color = headerHairline)
                Spacer(Modifier.height(12.dp))

                // =========================================================
                // FEATURED (FIJO)
                // =========================================================
                val featuredShape = RoundedCornerShape(18.dp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = featuredShape,
                    colors = CardDefaults.cardColors(containerColor = surface0),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, stroke, featuredShape)
                            .padding(14.dp)
                    ) {
                        // banda superior “brand”
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(brand, brand2))
                                )
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = featured?.name ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cardTextStrong
                                )
                                Text(
                                    text = "Ticker: ${featured?.ticker ?: "--"} · ${featured?.sector ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cardTextSoft
                                )
                            }

                            val pct = featured?.changePercent ?: 0.0
                            val c = pctColor(pct)
                            val badgeShape = RoundedCornerShape(999.dp)

                            Box(
                                modifier = Modifier
                                    .clip(badgeShape)
                                    .background(c.copy(alpha = 0.16f))
                                    .border(1.dp, c.copy(alpha = 0.25f), badgeShape)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${arrow(pct)} ${featured?.changePercent?.let { String.format("%.2f%%", it) } ?: "--"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = featured?.currentPrice?.let { String.format("%.2f €", it) } ?: "--",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = cardTextStrong
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricBox(
                                title = "Apertura",
                                value = featured?.openPrice?.let { String.format("%.2f", it) } ?: "--",
                                modifier = Modifier.weight(1f),
                                surface = surface1,
                                stroke = stroke,
                                titleColor = cardTextMuted,
                                valueColor = cardTextStrong
                            )
                            MetricBox(
                                title = "Máx",
                                value = featured?.highPrice?.let { String.format("%.2f", it) } ?: "--",
                                modifier = Modifier.weight(1f),
                                surface = surface1,
                                stroke = stroke,
                                titleColor = cardTextMuted,
                                valueColor = cardTextStrong
                            )
                            MetricBox(
                                title = "Mín",
                                value = featured?.lowPrice?.let { String.format("%.2f", it) } ?: "--",
                                modifier = Modifier.weight(1f),
                                surface = surface1,
                                stroke = stroke,
                                titleColor = cardTextMuted,
                                valueColor = cardTextStrong
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // =========================================================
                // NEWS (FIJO)
                // =========================================================
                val lastNews = marketState.news.takeLast(3).reversed()
                NewsPanel(
                    news = lastNews,
                    surface = surface0,
                    stroke = stroke,
                    textStrong = cardTextStrong,
                    textSoft = cardTextSoft,
                    neutral = neutral,
                    success = success,
                    danger = danger
                )

                Spacer(Modifier.height(12.dp))

                // =========================================================
                // MARKET (BLOQUE ÚNICO: header + lista) ✅
                // =========================================================
                val marketShape = RoundedCornerShape(18.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = marketShape,
                    colors = CardDefaults.cardColors(containerColor = surface0),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, stroke, marketShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mercado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cardTextStrong,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Precio",
                                style = MaterialTheme.typography.labelMedium,
                                color = cardTextSoft,
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                text = "Var.",
                                style = MaterialTheme.typography.labelMedium,
                                color = cardTextSoft,
                                modifier = Modifier.width(86.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Divider(color = stroke)
                        Spacer(Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
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
                                    changePercent = stock.changePercent,
                                    surface = surface1,
                                    stroke = stroke,
                                    textStrong = cardTextStrong,
                                    textSoft = cardTextSoft,
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
}

@Composable
private fun NewsPanel(
    news: List<NewsEvent>,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    success: Color,
    danger: Color
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Noticias",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${news.size}/3",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSoft
                )
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = stroke)
            Spacer(Modifier.height(10.dp))

            if (news.isEmpty()) {
                Text(
                    text = "Sin noticias todavía...",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    news.forEach { item ->
                        NewsRow(
                            item = item,
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

@Composable
private fun NewsRow(
    item: NewsEvent,
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

    val badgeShape = RoundedCornerShape(999.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(impactColor.copy(alpha = 0.9f))
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textStrong
            )
            Text(
                text = "Sector: ${item.sector}",
                style = MaterialTheme.typography.bodySmall,
                color = textSoft
            )
        }

        Box(
            modifier = Modifier
                .clip(badgeShape)
                .background(impactColor.copy(alpha = 0.16f))
                .border(1.dp, impactColor.copy(alpha = 0.25f), badgeShape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$sign$pct%",
                style = MaterialTheme.typography.labelMedium,
                color = impactColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    surface: Color,
    stroke: Color,
    titleColor: Color,
    valueColor: Color
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = titleColor
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun MarketRow(
    ticker: String,
    name: String,
    price: Double,
    changePercent: Double,
    surface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    neutral: Color,
    success: Color,
    danger: Color
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

    val shape = RoundedCornerShape(16.dp)
    val badgeShape = RoundedCornerShape(999.dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, stroke, shape)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.9f))
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft
                )
            }

            Text(
                text = String.format("%.2f €", price),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textStrong,
                modifier = Modifier.width(90.dp)
            )

            Box(
                modifier = Modifier
                    .width(86.dp)
                    .clip(badgeShape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.25f), badgeShape)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$arrow ${String.format("%.2f%%", changePercent)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
