package org.example.project.presentation.strategies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.domain.strategy.DipReference
import org.example.project.domain.strategy.StrategiesRepository
import org.example.project.domain.strategy.StrategyRule

@Composable
fun StrategiesConfigDialog(
    strategiesRepo: StrategiesRepository,
    ticker: String,
    onClose: () -> Unit,
    // Colores opcionales si quieres que encaje con tu theme (puedes simplificar luego)
    surface: Color = Color(0xFF0B1220),
    stroke: Color = Color(0xFF22314D),
    textStrong: Color = Color(0xFFEAF1FF),
    textSoft: Color = Color(0xFFB9C5DD),
    brand: Color = Color(0xFF4EA1FF),
) {
    val rules by strategiesRepo.rules.collectAsState()

    // Buscar reglas de este ticker (o null -> global)
    val autoBuy = rules.filterIsInstance<StrategyRule.AutoBuyDip>()
        .firstOrNull { it.ticker == null || it.ticker.equals(ticker, ignoreCase = true) }

    val takeProfit = rules.filterIsInstance<StrategyRule.TakeProfit>()
        .firstOrNull { it.ticker == null || it.ticker.equals(ticker, ignoreCase = true) }

    val stopLoss = rules.filterIsInstance<StrategyRule.StopLoss>()
        .firstOrNull { it.ticker == null || it.ticker.equals(ticker, ignoreCase = true) }

    var buyDipPct by remember { mutableStateOf(autoBuy?.dropPercent ?: 2.0) }
    var takeProfitPct by remember { mutableStateOf(takeProfit?.profitPercent ?: 3.0) }
    var stopLossPct by remember { mutableStateOf(stopLoss?.lossPercent ?: 3.0) }

    val dialogShape = RoundedCornerShape(18.dp)

    // IDs deterministas (evita nextId y bloqueos)
    fun idFor(kind: Int): Int = (ticker.trim().uppercase().hashCode() * 10) + kind
    val idAutoBuy = autoBuy?.id ?: idFor(1)
    val idTakeProfit = takeProfit?.id ?: idFor(2)
    val idStopLoss = stopLoss?.id ?: idFor(3)

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = dialogShape,
            colors = CardDefaults.cardColors(containerColor = surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, stroke, dialogShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Estrategias automáticas ($ticker)",
                    color = textStrong,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                // AutoBuyDip
                Text("Compra si baja X% (AutoBuyDip)", color = textSoft, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = buyDipPct.toFloat(),
                    onValueChange = { buyDipPct = it.toDouble() },
                    valueRange = 0.1f..10f
                )
                Text("X = ${"%.2f".format(buyDipPct)}%", color = textStrong)

                Spacer(Modifier.padding(vertical = 2.dp).background(Color.Transparent))

                // TakeProfit
                Text("Vende si gana Y% (TakeProfit)", color = textSoft, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = takeProfitPct.toFloat(),
                    onValueChange = { takeProfitPct = it.toDouble() },
                    valueRange = 0.1f..10f
                )
                Text("Y = ${"%.2f".format(takeProfitPct)}%", color = textStrong)

                Spacer(Modifier.padding(vertical = 2.dp).background(Color.Transparent))

                // StopLoss
                Text("Stop-loss si pierde Z% (StopLoss)", color = textSoft, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = stopLossPct.toFloat(),
                    onValueChange = { stopLossPct = it.toDouble() },
                    valueRange = 0.1f..10f
                )
                Text("Z = ${"%.2f".format(stopLossPct)}%", color = textStrong)

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancelar", color = textSoft) }

                    Button(
                        onClick = {
                            strategiesRepo.upsert(
                                StrategyRule.AutoBuyDip(
                                    id = idAutoBuy,
                                    ticker = ticker,
                                    dropPercent = buyDipPct,
                                    reference = DipReference.OPEN,
                                    budgetEuro = autoBuy?.budgetEuro ?: 250.0,
                                    cooldownMs = autoBuy?.cooldownMs ?: 12_000L
                                )
                            )
                            strategiesRepo.upsert(
                                StrategyRule.TakeProfit(
                                    id = idTakeProfit,
                                    ticker = ticker,
                                    profitPercent = takeProfitPct,
                                    sellFraction = takeProfit?.sellFraction ?: 1.0,
                                    cooldownMs = takeProfit?.cooldownMs ?: 12_000L
                                )
                            )
                            strategiesRepo.upsert(
                                StrategyRule.StopLoss(
                                    id = idStopLoss,
                                    ticker = ticker,
                                    lossPercent = stopLossPct,
                                    sellFraction = stopLoss?.sellFraction ?: 1.0,
                                    cooldownMs = stopLoss?.cooldownMs ?: 12_000L
                                )
                            )
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brand,
                            contentColor = Color(0xFF001018)
                        )
                    ) { Text("Aplicar", maxLines = 1) }
                }
            }
        }
    }
}
