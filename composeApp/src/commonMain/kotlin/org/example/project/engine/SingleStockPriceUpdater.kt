package org.example.project.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random
import org.example.project.data.repository.MarketRepository
import org.example.project.domain.model.StockSnapshot

class SingleStockPriceUpdater(
    private val repo: MarketRepository
) {

    suspend fun run(ticker: String) {
        val ctx = currentCoroutineContext()

        while (ctx.isActive) {

            // Respetar mercado y pausa
            val state = repo.marketState.value
            if (!state.isOpen || state.isPaused) {
                delay(300)
                continue
            }

            // Delay escalado por simSpeed
            val speed = state.simSpeed.coerceAtLeast(0.25).coerceAtMost(10.0)
            val baseDelay = Random.nextLong(1_000L, 3_000L)
            val scaledDelay = (baseDelay / speed).toLong().coerceAtLeast(50L)
            delay(scaledDelay)

            val current: StockSnapshot = repo.getSnapshot(ticker) ?: continue

            // Volatilidad por acción (requisito)
            val volatility = current.volatility.coerceIn(0.3, 2.5)

            // Base aleatoria escalada por volatilidad
            val maxBaseMove = 5.0 * volatility
            val basePercent = Random.nextDouble(-maxBaseMove, maxBaseMove)

            // Bias por tendencia + sector
            val trendBiasPercent = repo.getTrendBiasPercent()
            val sectorBiasPercent = repo.getSectorBiasPercent(current.sector)

            // Cambio total (%)
            val effectivePercent = basePercent + trendBiasPercent + sectorBiasPercent
            val percentChange = effectivePercent / 100.0

            val newPrice = (current.currentPrice * (1.0 + percentChange))
                .coerceAtLeast(0.01)

            val newHigh = maxOf(current.highPrice, newPrice)
            val newLow = minOf(current.lowPrice, newPrice)

            val changeEuro = newPrice - current.openPrice
            val changePercent = (changeEuro / current.openPrice) * 100.0

            val newHistory = (current.priceHistory + newPrice).takeLast(100)

            // Volumen más “realista”: mayor movimiento => mayor volumen
            val moveFactor = (abs(effectivePercent) / 5.0).coerceIn(0.2, 2.0)
            val addVolume = (Random.nextLong(10, 200) * moveFactor).toLong()

            val newSnapshot = current.copy(
                currentPrice = newPrice,
                highPrice = newHigh,
                lowPrice = newLow,
                changeEuro = changeEuro,
                changePercent = changePercent,
                volume = current.volume + addVolume,
                priceHistory = newHistory
            )

            repo.updateSnapshot(ticker, newSnapshot)
        }
    }
}
