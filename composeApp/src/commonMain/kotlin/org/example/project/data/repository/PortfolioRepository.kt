package org.example.project.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.example.project.domain.model.PortfolioSnapshot
import org.example.project.domain.model.Transaction
import org.example.project.presentation.state.PortfolioState

/**
 * Repositorio del portfolio del usuario.
 *
 * Requisitos del enunciado cubiertos aquí:
 * - Cash inicial 10.000€ (PortfolioState / implementación).
 * - Validaciones:
 *   - No comprar sin dinero suficiente
 *   - No vender más de lo que se posee
 *   - quantity > 0
 * - Comisión 0.5% por operación.
 * - Confirmación previa: previewBuy / previewSell.
 * - Historial: getTransactions + export CSV.
 * - Snapshot enriquecido para UI: getSnapshot (positions, PnL, totals…).
 *
 * Importante:
 * - Implementación thread-safe (Mutex/lock).
 * - Precio actual obtenido del MarketRepository / MarketState.
 */
interface PortfolioRepository {

    /** Estado “live” para UI (cash, holdings, transacciones, PnL...). */
    val portfolioState: StateFlow<PortfolioState>

    // ============================================================
    // PREVIEWS (confirmación previa)
    // ============================================================

    /**
     * Previsualiza una compra SIN ejecutarla.
     *
     * Reglas:
     * - quantity > 0
     * - pricePerShare = precio actual de mercado
     * - grossTotal = pricePerShare * quantity
     * - commission = grossTotal * 0.005
     * - netTotal(BUY) = grossTotal + commission
     */
    suspend fun previewBuy(ticker: String, quantity: Int): Result<TransactionPreview>

    /**
     * Previsualiza una venta SIN ejecutarla.
     *
     * Reglas:
     * - quantity > 0
     * - Validar que el usuario posee >= quantity
     * - grossTotal = pricePerShare * quantity
     * - commission = grossTotal * 0.005
     * - netTotal(SELL) = grossTotal - commission
     */
    suspend fun previewSell(ticker: String, quantity: Int): Result<TransactionPreview>

    // ============================================================
    // EJECUCIÓN (operación confirmada)
    // ============================================================

    /**
     * Ejecuta una compra (ya confirmada por el usuario).
     *
     * Reglas:
     * - Validar cash suficiente para netTotal
     * - Actualizar holdings (avgBuyPrice ponderado)
     * - Registrar Transaction (id incremental)
     */
    suspend fun buy(ticker: String, quantity: Int): Result<Transaction>

    /**
     * Ejecuta una venta (ya confirmada por el usuario).
     *
     * Reglas:
     * - Validar holdings suficientes
     * - Actualizar holdings (reducir o eliminar)
     * - Registrar Transaction (id incremental)
     */
    suspend fun sell(ticker: String, quantity: Int): Result<Transaction>

    // ============================================================
    // HISTORIAL / EXPORT / SNAPSHOT
    // ============================================================

    /** Historial completo en memoria (orden cronológico). */
    suspend fun getTransactions(): List<Transaction>

    /**
     * Export a CSV (devuelve el contenido como String).
     * Recomendación: incluir cabecera:
     * id,timestamp,type,ticker,quantity,pricePerShare,grossTotal,commission,netTotal
     */
    suspend fun exportTransactionsCsv(): String

    /**
     * Snapshot “puro” (modelo de dominio) para pantallas/estadísticas.
     * (Incluye positions enriquecidas, PnL, totals, etc.)
     */
    suspend fun getSnapshot(): PortfolioSnapshot
}

/**
 * Resultado calculado para confirmación previa.
 *
 * - grossTotal: precio * cantidad
 * - commission: 0.5% del grossTotal
 * - netTotal:
 *   - BUY  -> grossTotal + commission
 *   - SELL -> grossTotal - commission
 */
data class TransactionPreview(
    val ticker: String,
    val quantity: Int,
    val pricePerShare: Double,
    val grossTotal: Double,
    val commission: Double,
    val netTotal: Double
)

// ============================================================
// Errores tipados (para Result.failure)
// ============================================================

sealed class PortfolioError(message: String) : IllegalArgumentException(message) {
    class InvalidQuantity(qty: Int) : PortfolioError("Cantidad inválida: $qty (debe ser > 0)")
    class UnknownTicker(ticker: String) : PortfolioError("Ticker no encontrado en mercado: $ticker")
    class InsufficientCash(required: Double, available: Double) :
        PortfolioError("Saldo insuficiente. Necesario: %.2f €, Disponible: %.2f €".format(required, available))

    class InsufficientHoldings(ticker: String, requested: Int, owned: Int) :
        PortfolioError("No puedes vender $requested de $ticker. Posees: $owned")
}
