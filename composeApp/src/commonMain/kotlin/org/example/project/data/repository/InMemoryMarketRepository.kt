package org.example.project.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.project.core.util.ThreadSafeMap
import org.example.project.domain.model.MarketTrend
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.Sector
import org.example.project.domain.model.Stock
import org.example.project.domain.model.StockSnapshot
import org.example.project.presentation.state.MarketState

class InMemoryMarketRepository(
    initialStocks: List<Stock>
) : MarketRepository {

    // =========================
    // Storage thread-safe
    // =========================
    private val snapshots = ThreadSafeMap<String, StockSnapshot>()
    private val sectorBiasPercent = ThreadSafeMap<String, Double>() // key = Sector.name

    // Bias global por tick (% aprox). Ej: +0.10 => +0.10% por tick
    @Volatile
    private var trendBiasPercent: Double = 0.0

    // =========================
    // Flow + StateFlow (requisitos)
    // =========================
    private val _marketState = MutableStateFlow(
        MarketState(
            isOpen = true,
            isPaused = false,
            simSpeed = 1.0,
            trend = MarketTrend.NEUTRAL,
            stocks = emptyList(),
            news = emptyList()
        )
    )
    override val marketState: StateFlow<MarketState> = _marketState

    private val _priceUpdates = MutableSharedFlow<StockSnapshot>(
        replay = 0,
        extraBufferCapacity = 128
    )
    override val priceUpdates: Flow<StockSnapshot> = _priceUpdates

    // =========================
    // Init
    // =========================
    init {
        initialStocks.forEach { stock ->
            val price = stock.initialPrice

            val snap = StockSnapshot(
                name = stock.name,
                ticker = stock.ticker,
                sector = stock.sector,
                volatility = stock.volatility, // ✅ corregido (una sola coma)

                currentPrice = price,
                openPrice = price,
                highPrice = price,
                lowPrice = price,

                changeEuro = 0.0,
                changePercent = 0.0,

                volume = 0L,
                priceHistory = listOf(price)
            )

            snapshots.put(stock.ticker, snap)
        }

        // Inicializamos bias de sectores a 0
        Sector.values().forEach { sector ->
            sectorBiasPercent.put(sector.name, 0.0)
        }

        publish()
    }

    // =========================
    // MarketRepository implementation
    // =========================
    override fun getSnapshot(ticker: String): StockSnapshot? =
        snapshots.get(ticker)

    override fun updateSnapshot(ticker: String, newSnapshot: StockSnapshot) {
        snapshots.put(ticker, newSnapshot)

        // Flow de cambios de precios (requisito)
        _priceUpdates.tryEmit(newSnapshot)

        publish()
    }

    override fun setMarketOpen(isOpen: Boolean) {
        _marketState.value = _marketState.value.copy(isOpen = isOpen)
    }

    override fun setPaused(isPaused: Boolean) {
        _marketState.value = _marketState.value.copy(isPaused = isPaused)
    }

    override fun setSimSpeed(speed: Double) {
        val safe = speed.coerceIn(0.25, 10.0)
        _marketState.value = _marketState.value.copy(simSpeed = safe)
    }

    override fun getTrendBiasPercent(): Double = trendBiasPercent

    override fun setTrend(trend: MarketTrend, biasPercent: Double) {
        trendBiasPercent = biasPercent
        _marketState.value = _marketState.value.copy(trend = trend)
    }

    override fun getSectorBiasPercent(sector: Sector): Double =
        sectorBiasPercent.get(sector.name) ?: 0.0

    override fun setSectorBias(sector: Sector, biasPercent: Double) {
        sectorBiasPercent.put(sector.name, biasPercent)
    }

    override fun pushNews(event: NewsEvent) {
        val updated = (_marketState.value.news + event).takeLast(20)
        _marketState.value = _marketState.value.copy(news = updated)
    }

    override fun publish() {
        // Publicamos stocks ordenadas por ticker para UI estable
        _marketState.value = _marketState.value.copy(
            stocks = snapshots.values().sortedBy { it.ticker }
        )
    }

    // =========================
    // Helper opcional
    // =========================
    suspend fun expireSectorBiasLater(sector: Sector, delayMs: Long) {
        delay(delayMs)
        setSectorBias(sector, 0.0)
    }
}
