package org.example.project.presentation.vm

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.example.project.data.repository.PortfolioRepository
import org.example.project.data.repository.TransactionPreview
import org.example.project.domain.model.Transaction

class PortfolioViewModel(
    private val repo: PortfolioRepository,
    private val externalScope: CoroutineScope? = null
) {
    private val job = SupervisorJob()
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(Dispatchers.Default + job)

    val portfolioState = repo.portfolioState

    enum class Mode { BUY, SELL }

    var dialogOpen by mutableStateOf(false)
        private set

    var mode by mutableStateOf(Mode.BUY)
        private set

    var ticker by mutableStateOf("")
        private set

    var quantityText by mutableStateOf("1")
        private set

    var preview by mutableStateOf<TransactionPreview?>(null)
        private set

    var lastTx by mutableStateOf<Transaction?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun openTrade(ticker: String, mode: Mode) {
        this.ticker = ticker
        this.mode = mode
        this.quantityText = "1"
        this.preview = null
        this.lastTx = null
        this.error = null
        this.dialogOpen = true
        refreshPreview()
    }

    fun closeTrade() {
        dialogOpen = false
    }

    // ✅ RENOMBRADA para evitar clash con el setter de quantityText
    fun updateQuantityText(text: String) {
        quantityText = text
        refreshPreview()
    }

    fun refreshPreview() {
        val qty = quantityText.toIntOrNull() ?: 0

        scope.launch {
            val result = when (mode) {
                Mode.BUY -> repo.previewBuy(ticker, qty)
                Mode.SELL -> repo.previewSell(ticker, qty)
            }

            result.fold(
                onSuccess = {
                    preview = it
                    error = null
                },
                onFailure = { e ->
                    preview = null
                    error = e.message ?: "Error"
                }
            )
        }
    }

    fun confirm() {
        val qty = quantityText.toIntOrNull() ?: 0

        scope.launch {
            val result = when (mode) {
                Mode.BUY -> repo.buy(ticker, qty)
                Mode.SELL -> repo.sell(ticker, qty)
            }

            result.fold(
                onSuccess = { tx ->
                    lastTx = tx
                    error = null
                    refreshPreview()
                },
                onFailure = { e ->
                    lastTx = null
                    error = e.message ?: "Error"
                }
            )
        }
    }

    fun close() {
        if (externalScope == null) job.cancel()
    }
}
