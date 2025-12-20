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

            // 1) Respetar mercado y pausa (si está cerrado o pausado, no actualizamos)
            val stateBeforeDelay = repo.marketState.value
            if (!stateBeforeDelay.isOpen || stateBeforeDelay.isPaused) {
                delay(300)
                continue
            }

            // 2) Delay escalado por simSpeed (0.25x..10x)
            val speed = stateBeforeDelay.simSpeed.coerceIn(0.25, 10.0)
            val baseDelay = Random.nextLong(1_000L, 3_000L)
            val scaledDelay = (baseDelay / speed).toLong().coerceAtLeast(50L)
            delay(scaledDelay)

            // 3) Re-chequeo tras el delay (evita "un tick de más" si se pausó/cerró mientras esperaba)
            val state = repo.marketState.value
            if (!state.isOpen || state.isPaused) continue

            val current: StockSnapshot = repo.getSnapshot(ticker) ?: continue

            // 4) Base aleatoria (-5%..+5%) con volatilidad, pero RESPETANDO el rango final
            // Volatility típica: 0.5 estable, 1.0 normal, 1.5+ volátil
            val vol = current.volatility.coerceIn(0.5, 2.0)

            // Generamos un movimiento base y lo modulamos por volatilidad,
            // pero lo limitamos a [-5..+5] para cumplir el enunciado.
            val basePercentRaw = Random.nextDouble(-5.0, 5.0)
            val basePercent = (basePercentRaw * vol).coerceIn(-5.0, 5.0)

            // 5) Bias por tendencia global + sector (en % por tick)
            val trendBiasPercent = repo.getTrendBiasPercent()
            val sectorBiasPercent = repo.getSectorBiasPercent(current.sector)

            // 6) Cambio efectivo total en % (y CLAMP final a [-5..+5] por requisito)
            val effectivePercent = (basePercent + trendBiasPercent + sectorBiasPercent)
                .coerceIn(-5.0, 5.0)

            val percentChange = effectivePercent / 100.0

            // 7) Nuevo precio (mínimo 0.01€)
            val newPrice = (current.currentPrice * (1.0 + percentChange))
                .coerceAtLeast(0.01)

            val newHigh = maxOf(current.highPrice, newPrice)
            val newLow = minOf(current.lowPrice, newPrice)

            val changeEuro = newPrice - current.openPrice
            val changePercent = if (current.openPrice > 0.0) {
                (changeEuro / current.openPrice) * 100.0
            } else {
                0.0
            }

            // Historial máximo 100 valores
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
