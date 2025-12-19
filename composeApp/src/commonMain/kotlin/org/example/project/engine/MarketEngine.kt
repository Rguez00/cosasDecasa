package org.example.project.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.example.project.data.repository.InMemoryMarketRepository
import org.example.project.presentation.state.MarketState

class MarketEngine(
    private val marketRepo: InMemoryMarketRepository,
    private val externalScope: CoroutineScope? = null
) {

    private val job = SupervisorJob()
    private val scope: CoroutineScope =
        externalScope ?: CoroutineScope(Dispatchers.Default + job)

    val marketState: StateFlow<MarketState> = marketRepo.marketState

    // 1 job por ticker
    private val updaterJobs: MutableMap<String, Job> = mutableMapOf()

    // Generador global único
    private val globalGenerator by lazy { NewsAndTrendGenerator(marketRepo) }

    // Jobs globales (tendencia + noticias)
    private var trendJob: Job? = null
    private var newsJob: Job? = null

    // ============================================================
    // START / STOP
    // ============================================================

    fun startAllTickers() {
        startGlobalGeneratorsIfNeeded()

        // si está cerrado o pausado, no arrancamos tickers (ahorra recursos)
        val state = marketState.value
        if (!state.isOpen || state.isPaused) return

        val tickers = state.stocks.map { it.ticker }
        tickers.forEach { startTicker(it) }
    }

    fun startTicker(ticker: String) {
        startGlobalGeneratorsIfNeeded()

        val state = marketState.value
        if (!state.isOpen || state.isPaused) return

        // evita lanzar dos veces el mismo ticker
        if (updaterJobs[ticker]?.isActive == true) return

        val newJob = scope.launch {
            SingleStockPriceUpdater(marketRepo).run(ticker)
        }
        updaterJobs[ticker] = newJob
    }

    private fun startGlobalGeneratorsIfNeeded() {
        // Tendencia
        if (trendJob?.isActive != true) {
            trendJob = scope.launch { globalGenerator.runTrend() }
        }
        // Noticias
        if (newsJob?.isActive != true) {
            newsJob = scope.launch { globalGenerator.runNews() }
        }
    }

    fun stopTicker(ticker: String) {
        updaterJobs[ticker]?.cancel()
        updaterJobs.remove(ticker)
    }

    fun stopAll() {
        // parar tickers
        updaterJobs.values.forEach { it.cancel() }
        updaterJobs.clear()

        // parar generadores globales
        trendJob?.cancel()
        newsJob?.cancel()
        trendJob = null
        newsJob = null
    }

    fun stopEngine() {
        // Cancela TODO el scope del engine (engine muerto)
        stopAll()
        job.cancel()
    }

    // ============================================================
    // CONTROLES (enunciado: pausar/reanudar, abrir/cerrar, velocidad)
    // ============================================================

    fun setPaused(paused: Boolean) {
        marketRepo.setPaused(paused)

        if (paused) {
            // pausado => paramos tickers para ahorrar CPU
            updaterJobs.values.forEach { it.cancel() }
            updaterJobs.clear()
        } else {
            // reanudar => re-lanzamos tickers
            startAllTickers()
        }
    }

    fun setMarketOpen(open: Boolean) {
        marketRepo.setMarketOpen(open)

        if (!open) {
            // cerrado => paramos tickers
            updaterJobs.values.forEach { it.cancel() }
            updaterJobs.clear()
        } else {
            // abierto => arrancamos tickers
            startAllTickers()
        }
    }

    fun setSimSpeed(speed: Double) {
        marketRepo.setSimSpeed(speed)
        // No hace falta reiniciar jobs: delays en generadores ya usan simSpeed
        // y el updater por ticker lo aplicaremos cuando lo ajustemos (siguiente paso).
    }
}
