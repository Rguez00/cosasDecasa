package org.example.project.domain.model

data class Holding(
    val ticker: String,
    val quantity: Int,
    val avgBuyPrice: Double // precio medio ponderado de compra
) {
    init {
        require(quantity >= 0) { "Holding.quantity no puede ser negativo" }
        require(avgBuyPrice >= 0.0) { "Holding.avgBuyPrice no puede ser negativo" }
    }
}
