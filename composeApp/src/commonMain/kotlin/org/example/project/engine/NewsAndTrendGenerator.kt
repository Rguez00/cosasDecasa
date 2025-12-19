package org.example.project.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.random.Random
import org.example.project.data.repository.InMemoryMarketRepository
import org.example.project.domain.model.MarketTrend
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.Sector

class NewsAndTrendGenerator(
    private val repo: InMemoryMarketRepository
) {

    /**
     * Cambia la tendencia global cada 30–60s (escalado por simSpeed).
     * Guarda un bias por tick en el repo (% por tick aprox).
     */
    suspend fun runTrend() {
        val ctx = currentCoroutineContext()

        while (ctx.isActive) {
            val state = repo.marketState.value

            // Respetamos mercado y pausa
            if (!state.isOpen || state.isPaused) {
                delay(500)
                continue
            }

            val speed = state.simSpeed.coerceIn(0.25, 10.0)

            // Delay escalado por velocidad de simulación
            val baseDelay = Random.nextLong(30_000L, 60_000L)
            val scaledDelay = (baseDelay / speed).toLong().coerceAtLeast(250L)
            delay(scaledDelay)

            val trend = listOf(
                MarketTrend.BULLISH,
                MarketTrend.BEARISH,
                MarketTrend.NEUTRAL
            ).random()

            // Bias global por tick (en %)
            val trendBias = when (trend) {
                MarketTrend.BULLISH -> Random.nextDouble(0.05, 0.20)    // +0.05%..+0.20%
                MarketTrend.BEARISH -> Random.nextDouble(-0.20, -0.05)  // -0.20%..-0.05%
                MarketTrend.NEUTRAL -> 0.0
            }

            repo.setTrend(trend, trendBias)
        }
    }

    /**
     * Genera noticias por sector cada 8–15s (escalado por simSpeed).
     * - pushNews() para UI
     * - setSectorBias() temporal (en % por tick)
     * - expira el bias pasado un tiempo
     */
    suspend fun runNews() = coroutineScope {
        val ctx = currentCoroutineContext()
        val sectors = Sector.values().toList()

        while (ctx.isActive) {
            val state = repo.marketState.value

            // Respetamos mercado y pausa
            if (!state.isOpen || state.isPaused) {
                delay(500)
                continue
            }

            val speed = state.simSpeed.coerceIn(0.25, 10.0)

            // Delay escalado por velocidad de simulación
            val baseDelay = Random.nextLong(8_000L, 15_000L)
            val scaledDelay = (baseDelay / speed).toLong().coerceAtLeast(200L)
            delay(scaledDelay)

            val sector = sectors.random()

            // Impacto headline en % (ej. -4.0 .. +4.0)
            val impactHeadline = Random.nextDouble(-4.0, 4.0)

            val event = NewsEvent(
                timestamp = Clock.System.now(),
                sector = sector,
                title = buildTitle(sector, impactHeadline),
                impactPercent = impactHeadline
            )

            repo.pushNews(event)

            // Convertimos noticia a un bias por tick (% por tick aprox)
            // Nota: si impacto es grande, bias algo mayor; si es pequeño, bias menor.
            val intensity = (abs(impactHeadline) / 4.0).coerceIn(0.2, 1.0)
            val factor = Random.nextDouble(0.08, 0.20) * intensity

            val perTickBiasPercent = (impactHeadline * factor).coerceIn(-1.0, 1.0) // clamp por seguridad
            repo.setSectorBias(sector, perTickBiasPercent)

            // Expiración del bias en background dentro del MISMO scope (sin fugas)
            val expireBase = Random.nextLong(20_000L, 40_000L)
            val expireScaled = (expireBase / speed).toLong().coerceAtLeast(500L)

            launch {
                repo.expireSectorBiasLater(sector, expireScaled)
            }
        }
    }

    private fun buildTitle(sector: Sector, impact: Double): String {
        val sign = if (impact >= 0) "+" else ""
        val pct = String.format("%.1f", impact)
        return when (sector) {
            Sector.TECHNOLOGY -> "Tecnología en movimiento: $sign$pct%"
            Sector.ENERGY -> "Tensión energética: $sign$pct%"
            Sector.BANKING -> "Noticias bancarias: $sign$pct%"
            Sector.RETAIL -> "Consumo y comercio: $sign$pct%"
            Sector.HEALTHCARE -> "Salud e investigación: $sign$pct%"
        }
    }
}
