package org.example.project.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.random.Random
import org.example.project.data.repository.MarketRepository
import org.example.project.domain.model.MarketTrend
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.Sector

/**
 * Genera:
 * - Tendencia global (BULLISH/BEARISH/NEUTRAL) con bias por tick.
 * - Noticias por sector con impacto temporal (bias por tick) que expira.
 *
 * IMPORTANTE:
 * - Usa el MISMO scope del engine (sin fugas).
 * - Respeta isOpen/isPaused.
 * - Escala tiempos por simSpeed.
 * - Evita acumular expiraciones: 1 job de expiración por sector (se reemplaza).
 *
 * Nota “final”:
 * - Al cancelar runNews(), cancelamos también las expiraciones pendientes.
 * - La expiración NO debe borrar un bias nuevo (usa expectedBias).
 */
class NewsAndTrendGenerator(
    private val repo: MarketRepository,
    private val engineScope: CoroutineScope
) {
    // 1 expiración activa por sector (si llega otra noticia del mismo sector, sustituye)
    private val expireJobs: MutableMap<Sector, Job> = mutableMapOf()
    private val expireLock = Any()

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

            // Delay escalado por simSpeed
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
                MarketTrend.BULLISH -> Random.nextDouble(0.05, 0.20)     // +0.05%..+0.20%
                MarketTrend.BEARISH -> Random.nextDouble(-0.20, -0.05)   // -0.20%..-0.05%
                MarketTrend.NEUTRAL -> 0.0
            }

            repo.setTrend(trend, trendBias)
        }
    }

    suspend fun runNews() {
        val ctx = currentCoroutineContext()
        val sectors = Sector.values().toList()

        try {
            while (ctx.isActive) {
                val state = repo.marketState.value

                // Respetamos mercado y pausa
                if (!state.isOpen || state.isPaused) {
                    delay(500)
                    continue
                }

                val speed = state.simSpeed.coerceIn(0.25, 10.0)

                // Delay escalado por simSpeed
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

                // Convertimos impacto headline a bias por tick (% por tick aprox)
                val intensity = (abs(impactHeadline) / 4.0).coerceIn(0.2, 1.0)
                val factor = Random.nextDouble(0.08, 0.20) * intensity
                val perTickBiasPercent =
                    (impactHeadline * factor).coerceIn(-1.0, 1.0) // clamp

                repo.setSectorBias(sector, perTickBiasPercent)

                // Expiración escalada (1 job por sector)
                val expireBase = Random.nextLong(20_000L, 40_000L)
                val expireScaled = (expireBase / speed).toLong().coerceAtLeast(500L)

                scheduleSectorBiasExpiration(
                    sector = sector,
                    delayMs = expireScaled,
                    expectedBias = perTickBiasPercent
                )
            }
        } finally {
            // IMPORTANTÍSIMO: si se cancela runNews(), cancelamos expiraciones pendientes
            cancelAllExpirations()
        }
    }

    private fun scheduleSectorBiasExpiration(
        sector: Sector,
        delayMs: Long,
        expectedBias: Double
    ) {
        synchronized(expireLock) {
            // Cancelamos expiración anterior del mismo sector
            expireJobs[sector]?.cancel()

            // Programamos nueva (en engineScope, pero registrada y cancelable)
            val job = engineScope.launch {
                delay(delayMs)

                // No borrar un bias “nuevo” que haya llegado después
                val currentBias = repo.getSectorBiasPercent(sector)
                if (currentBias == expectedBias) {
                    repo.setSectorBias(sector, 0.0)
                }
            }

            expireJobs[sector] = job
        }
    }

    private fun cancelAllExpirations() {
        synchronized(expireLock) {
            expireJobs.values.forEach { it.cancel() }
            expireJobs.clear()
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
