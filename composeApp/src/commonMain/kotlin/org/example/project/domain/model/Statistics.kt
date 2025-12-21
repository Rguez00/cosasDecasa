package org.example.project.domain.model

/**
 * Estadísticas calculadas a partir de transacciones y posiciones
 */
data class PortfolioStatistics(
    // 1. Mejor/Peor transacción (solo SELL)
    val bestTransaction: TransactionSummary?,
    val worstTransaction: TransactionSummary?,

    // 2. Acción más rentable (posiciones actuales)
    val mostProfitableStock: StockProfitability?,

    // 3. Tasa de éxito
    val successRate: Double,  // % de ventas con beneficio
    val totalSells: Int,
    val profitableSells: Int,

    // 4. Rentabilidad media
    val averageProfitability: Double,  // Promedio de PnL% de todas las posiciones
    val totalPositions: Int
)

/**
 * Resumen simplificado de una transacción para mostrar en UI
 */
data class TransactionSummary(
    val id: Int,
    val ticker: String,
    val quantity: Int,
    val pricePerShare: Double,
    val netTotal: Double,
    val profitLoss: Double,  // Beneficio/pérdida calculado
    val profitLossPercent: Double,
    val timestamp: String
)

/**
 * Rentabilidad de una acción específica
 */
data class StockProfitability(
    val ticker: String,
    val quantity: Int,
    val invested: Double,
    val currentValue: Double,
    val pnlEuro: Double,
    val pnlPercent: Double
)

/**
 * Función para calcular estadísticas a partir de transacciones y posiciones
 */
fun calculateStatistics(
    transactions: List<Transaction>,
    positions: List<PositionSnapshot>
): PortfolioStatistics {

    // 🐛 DEBUG - AGREGAR ESTAS LÍNEAS
    println("=== DEBUG STATISTICS (Desktop) ===")
    println("Total transacciones: ${transactions.size}")
    transactions.forEach { tx ->
        println("TX #${tx.id}: ${tx.type} - ${tx.ticker} x${tx.quantity} @ ${tx.pricePerShare}€")
    }

    // === 1. MEJOR/PEOR TRANSACCIÓN ===
    // Para calcular beneficio de una venta, necesitamos encontrar la compra correspondiente
    val sells = transactions.filter { it.type == TransactionType.SELL }
    val buys = transactions.filter { it.type == TransactionType.BUY }

    // Mapear compras por ticker (precio promedio de compra)
    val avgBuyPrices = buys
        .groupBy { it.ticker }
        .mapValues { (_, txList) ->
            val totalCost = txList.sumOf { it.netTotal }
            val totalQty = txList.sumOf { it.quantity }
            if (totalQty > 0) totalCost / totalQty else 0.0
        }

    // Calcular beneficio de cada venta
    val sellSummaries = sells.map { sell ->
        val avgBuyPrice = avgBuyPrices[sell.ticker] ?: sell.pricePerShare
        val costBasis = avgBuyPrice * sell.quantity
        val revenue = sell.grossTotal
        val profitLoss = revenue - costBasis
        val profitLossPercent = if (costBasis > 0) (profitLoss / costBasis) * 100.0 else 0.0

        TransactionSummary(
            id = sell.id,
            ticker = sell.ticker,
            quantity = sell.quantity,
            pricePerShare = sell.pricePerShare,
            netTotal = sell.netTotal,
            profitLoss = profitLoss,
            profitLossPercent = profitLossPercent,
            timestamp = sell.timestamp.toString().take(19)
        )
    }

    val bestTx = sellSummaries.maxByOrNull { it.profitLoss }
    val worstTx = sellSummaries.minByOrNull { it.profitLoss }

    // === 2. ACCIÓN MÁS RENTABLE ===
    val mostProfitable = positions.maxByOrNull { it.pnlPercent }?.let {
        StockProfitability(
            ticker = it.ticker,
            quantity = it.quantity,
            invested = it.invested,
            currentValue = it.valueNow,
            pnlEuro = it.pnlEuro,
            pnlPercent = it.pnlPercent
        )
    }

    // === 3. TASA DE ÉXITO ===
    val profitableSells = sellSummaries.count { it.profitLoss > 0 }
    val totalSells = sellSummaries.size
    val successRate = if (totalSells > 0) {
        (profitableSells.toDouble() / totalSells) * 100.0
    } else 0.0

    // === 4. RENTABILIDAD MEDIA ===
    val avgProfitability = if (positions.isNotEmpty()) {
        positions.map { it.pnlPercent }.average()
    } else 0.0

    return PortfolioStatistics(
        bestTransaction = bestTx,
        worstTransaction = worstTx,
        mostProfitableStock = mostProfitable,
        successRate = successRate,
        totalSells = totalSells,
        profitableSells = profitableSells,
        averageProfitability = avgProfitability,
        totalPositions = positions.size
    )
}