package org.example.project.domain.model

data class PortfolioSnapshot(
    val cash: Double,

    // Holdings base (para persistencia/operaciones)
    val holdings: List<Holding>,

    // Posiciones enriquecidas para UI (precio actual, pnl por ticker, etc.)
    val positions: List<PositionSnapshot>,

    // Total invertido (suma de invested de todas las posiciones / o basado en holdings)
    val totalInvested: Double,

    // Valor actual de todas las posiciones (suma de valueNow)
    val holdingsValue: Double,

    // Valor total (cash + holdingsValue)
    val portfolioValue: Double,

    // PnL global SOLO holdings (holdingsValue - totalInvested)
    val pnlEuro: Double,

    // PnL% global SOLO holdings
    val pnlPercent: Double
)
