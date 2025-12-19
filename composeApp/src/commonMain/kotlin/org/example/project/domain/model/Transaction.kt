package org.example.project.domain.model

import kotlinx.datetime.Instant

data class Transaction(
    val id: Int,
    val timestamp: Instant,

    val type: TransactionType,

    // identificador
    val ticker: String,

    // metadatos opcionales (útiles para CSV/estadísticas sin depender del market)
    val companyName: String? = null,
    val sector: Sector? = null,

    val quantity: Int,

    val pricePerShare: Double,
    val grossTotal: Double,  // quantity * pricePerShare
    val commission: Double,  // 0.5% típico
    val netTotal: Double     // BUY: gross + commission | SELL: gross - commission
) {
    init {
        require(id >= 0) { "Transaction.id no puede ser negativo" }
        require(quantity > 0) { "Transaction.quantity debe ser > 0" }
        require(pricePerShare >= 0.0) { "Transaction.pricePerShare no puede ser negativo" }
        require(grossTotal >= 0.0) { "Transaction.grossTotal no puede ser negativo" }
        require(commission >= 0.0) { "Transaction.commission no puede ser negativo" }
    }
}
