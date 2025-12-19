package org.example.project.domain.model

/**
 * Posición "enriquecida" para UI:
 * combina lo que tengo (Holding) con datos de mercado (precio actual),
 * y calcula valor y PnL en tiempo real.
 */
data class PositionSnapshot(
    val ticker: String,
    val quantity: Int,

    val avgBuyPrice: Double,
    val currentPrice: Double,

    val invested: Double,     // avgBuyPrice * quantity
    val valueNow: Double,     // currentPrice * quantity

    val pnlEuro: Double,      // valueNow - invested
    val pnlPercent: Double    // (pnlEuro / invested) * 100 (si invested > 0)
)
