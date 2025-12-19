package org.example.project.domain.model

data class StockSnapshot(
    val name: String,
    val ticker: String,
    val sector: Sector,

    val volatility: Double,   // ✅ necesario para tu repo + updater

    val currentPrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,

    val changeEuro: Double,
    val changePercent: Double,

    val volume: Long,
    val priceHistory: List<Double>
)
