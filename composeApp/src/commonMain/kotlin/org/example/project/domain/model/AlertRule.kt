package org.example.project.domain.model

sealed class AlertRule {
    abstract val ticker: String

    data class PriceAbove(
        override val ticker: String,
        val threshold: Double
    ) : AlertRule()

    data class PriceBelow(
        override val ticker: String,
        val threshold: Double
    ) : AlertRule()

    data class PercentChangeAbove(
        override val ticker: String,
        val thresholdPercent: Double
    ) : AlertRule()
}
