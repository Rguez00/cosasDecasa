package org.example.project.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import org.example.project.data.repository.MarketRepository
import org.example.project.presentation.state.MarketState

/**
 * Motor de simulación:
 * - 1 coroutine por ticker (actualiza precio cada 1-3s, escalado por simSpeed).
 * - 2 coroutines globales: tendencia + noticias.
 * - Permite pausar/reanudar, abrir/cerrar mercado y cambiar velocidad.
 * - Cancelación limpia al cerrar la app.
 *
 * Nota importante (proyecto final):
 * - Aunque se pase externalScope (que suele ser Main), el motor corre en Dispatchers.Default.
 * - Si externalScope se cancela, el motor se cancela (job hijo).
 * - close() cancela SOLO el motor (no el scope externo).
 */
class MarketEngine(
    private val marketRepo: MarketRepository,
    externalScope: CoroutineScope? = null
) {
    // Job del engine:
    // - Si hay externalScope, hacemos nuestro job hijo del Job externo (se cancela con él).
    // - Si no hay externalScope, es un job independiente.
    private val engineJob: Job = SupervisorJob(externalScope?.coroutineContext?.get(Job))

    // Scope real del motor: SIEMPRE Default (evitar trabajo pesado en Main)
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + engineJob)

    val marketState: StateFlow<MarketState> = marketRepo.marketState

    // 1 job por ticker
    private val updaterJobs: MutableMap<String, Job> = mutableMapOf()

    // Jobs globales
    private var trendJob: Job? = null
    private var newsJob: Job? = null

    // Reutilizamos updater (no crear objetos en bucle)
    private val updater = SingleStockPriceUpdater(marketRepo)

    // ÚNICO generador global (no recrearlo)
    private val generator: NewsAndTrendGenerator by lazy {
        NewsAndTrendGenerator(marketRepo, scope)
    }

    // ============================================================
    // START / STOP
    // ============================================================

    fun startAllTickers() {
        startGlobalGeneratorsIfNeeded()

        val state = marketState.value
        if (!state.isOpen || state.isPaused) return

        state.stocks.forEach { startTicker(it.ticker) }
    }

    fun startTicker(ticker: String) {
        startGlobalGeneratorsIfNeeded()

        val state = marketState.value
        if (!state.isOpen || state.isPaused) return

        if (updaterJobs[ticker]?.isActive == true) return

        updaterJobs[ticker] = scope.launch {
            updater.run(ticker)
        }
    }

    private fun startGlobalGeneratorsIfNeeded() {
        if (!engineJob.isActive) return

        if (trendJob?.isActive != true) {
            trendJob = scope.launch { generator.runTrend() }
        }
        if (newsJob?.isActive != true) {
            newsJob = scope.launch { generator.runNews() }
        }
    }

    fun stopTicker(ticker: String) {
        updaterJobs.remove(ticker)?.cancel()
    }

    fun stopAllTickers() {
        updaterJobs.values.forEach { it.cancel() }
        updaterJobs.clear()
    }

    fun stopAll() {
        stopAllTickers()

        trendJob?.cancel()
        newsJob?.cancel()
        trendJob = null
        newsJob = null
    }

    /**
     * Cierre final del engine (Desktop onClose / Android onDestroy).
     * Cancela TODO lo del motor, sin tocar scopes externos.
     */
    fun close() {
        stopAll()
        scope.cancel() // cancela engineJob + hijos
    }

    // ============================================================
    // CONTROLES (enunciado)
    // ============================================================

    fun setPaused(paused: Boolean) {
        marketRepo.setPaused(paused)

        if (paused) {
            stopAllTickers()
        } else {
            startAllTickers()
        }
    }

    fun setMarketOpen(open: Boolean) {
        marketRepo.setMarketOpen(open)

        if (!open) {
            stopAllTickers()
        } else {
            startAllTickers()
        }
    }

    fun setSimSpeed(speed: Double) {
        marketRepo.setSimSpeed(speed)
        // No reiniciamos jobs:
        // - SingleStockPriceUpdater consulta state.simSpeed.
        // - NewsAndTrendGenerator consulta simSpeed en delays.
    }
}
