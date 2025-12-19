package org.example.project.presentation.state

import org.example.project.domain.model.Holding
import org.example.project.domain.model.Transaction

data class PortfolioState(
    val cash: Double = 10_000.0,

    val holdings: List<Holding> = emptyList(),
    val transactions: List<Transaction> = emptyList(),

    // Lo recalcula el repo en tiempo real: cash + valor de posiciones
    val portfolioValue: Double = cash,

    // PnL global (holdings): valorActual - totalInvertido
    val pnlEuro: Double = 0.0,
    val pnlPercent: Double = 0.0
)
