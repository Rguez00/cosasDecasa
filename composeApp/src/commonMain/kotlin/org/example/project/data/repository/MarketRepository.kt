package org.example.project.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.example.project.domain.model.MarketTrend
import org.example.project.domain.model.NewsEvent
import org.example.project.domain.model.Sector
import org.example.project.domain.model.StockSnapshot
import org.example.project.presentation.state.MarketState

interface MarketRepository {

    val marketState: StateFlow<MarketState>

    // Requisito: Flow que emite cambios de precio en tiempo real
    val priceUpdates: Flow<StockSnapshot>

    fun getSnapshot(ticker: String): StockSnapshot?
    fun updateSnapshot(ticker: String, newSnapshot: StockSnapshot)

    fun setMarketOpen(isOpen: Boolean)
    fun setPaused(isPaused: Boolean)

    // Velocidad de simulación (0.25x..10x)
    fun setSimSpeed(speed: Double)

    // Tendencia global
    fun getTrendBiasPercent(): Double
    fun setTrend(trend: MarketTrend, biasPercent: Double)

    // Bias por sector
    fun getSectorBiasPercent(sector: Sector): Double
    fun setSectorBias(sector: Sector, biasPercent: Double)

    // Noticias
    fun pushNews(event: NewsEvent)

    // Publicación del listado consolidado para UI
    fun publish()
}
