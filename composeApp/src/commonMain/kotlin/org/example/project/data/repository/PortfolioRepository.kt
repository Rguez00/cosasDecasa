package org.example.project.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.example.project.domain.model.PortfolioSnapshot
import org.example.project.domain.model.Transaction
import org.example.project.presentation.state.PortfolioState

/**
 * Repositorio del portfolio del usuario.
 *
 * Requisitos del enunciado cubiertos aquí:
 * - Cash inicial 10.000€ (en PortfolioState / implementación).
 * - Validaciones: no comprar sin dinero / no vender más de lo poseído.
 * - Comisión: 0.5% por operación.
 * - Confirmación previa: previewBuy/previewSell.
 * - Historial de transacciones: getTransactions + export CSV.
 * - Snapshot enriquecido para UI: getSnapshot (incluye positions, PnL, etc.).
 *
 * Nota:
 * - La implementación DEBE ser thread-safe (locks o mutex).
 * - El precio por acción debe obtenerse del MarketRepository / MarketState.
 */
interface PortfolioRepository {

    /** Estado “live” para UI. La implementación recalcula valores al actualizar mercado o portfolio. */
    val portfolioState: StateFlow<PortfolioState>

    /**
     * Previsualiza una compra SIN ejecutarla.
     *
     * Reglas:
     * - quantity debe ser > 0, si no -> Result.failure(...)
     * - pricePerShare es el precio actual de mercado
     * - grossTotal = pricePerShare * quantity
     * - commission = grossTotal * 0.005
     * - netTotal (BUY) = grossTotal + commission
     */
    suspend fun previewBuy(ticker: String, quantity: Int): Result<TransactionPreview>

    /**
     * Previsualiza una venta SIN ejecutarla.
     *
     * Reglas:
     * - quantity debe ser > 0, si no -> Result.failure(...)
     * - Validar que el usuario posee >= quantity (si no, failure)
     * - grossTotal = pricePerShare * quantity
     * - commission = grossTotal * 0.005
     * - netTotal (SELL) = grossTotal - commission
     */
    suspend fun previewSell(ticker: String, quantity: Int): Result<TransactionPreview>

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

    /** Historial completo en memoria (orden cronológico). */
    suspend fun getTransactions(): List<Transaction>

    /**
     * Export a CSV (devuelve el contenido como String).
     * Desktop lo guardará a fichero; Android lo compartirá/guardará.
     *
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
