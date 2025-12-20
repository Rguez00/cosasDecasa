package org.example.project.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.presentation.vm.PortfolioViewModel

@Composable
fun TradeDialog(
    vm: PortfolioViewModel,

    // Paleta (tu estilo dark fintech)
    dialogSurface: Color,
    innerSurface: Color,
    stroke: Color,
    textStrong: Color,
    textSoft: Color,
    textMuted: Color,
    success: Color,
    danger: Color,
    neutral: Color
) {
    if (!vm.dialogOpen) return

    val mode = vm.mode
    val accent = when (mode) {
        PortfolioViewModel.Mode.BUY -> success
        PortfolioViewModel.Mode.SELL -> danger
    }

    val title = when (mode) {
        PortfolioViewModel.Mode.BUY -> "Comprar"
        PortfolioViewModel.Mode.SELL -> "Vender"
    }

    val shape = RoundedCornerShape(18.dp)

    AlertDialog(
        onDismissRequest = { vm.closeTrade() },

        // Importante: en dark UI, definimos el contenido entero
        title = {
            Column {
                Text(
                    text = "$title · ${vm.ticker}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textStrong
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Confirmación previa (incluye comisión 0.5%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSoft
                )
            }
        },

        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // =========================
                // Cantidad
                // =========================
                OutlinedTextField(
                    value = vm.quantityText,
                    onValueChange = { raw ->
                        // Solo dígitos, evita problemas al escribir
                        val filtered = raw.filter { it.isDigit() }.take(6)
                        vm.updateQuantityText(filtered.ifBlank { "0" })
                    },
                    label = { Text("Cantidad", color = textSoft) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // =========================
                // Error (si lo hay)
                // =========================
                if (vm.error != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = vm.error ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textStrong
                        )
                    }
                }

                // =========================
                // Preview (si existe)
                // =========================
                val p = vm.preview
                if (p != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = innerSurface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, stroke, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Previsualización",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textStrong,
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                                        .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (mode == PortfolioViewModel.Mode.BUY) "BUY" else "SELL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                }
                            }

                            Divider(color = stroke)

                            PreviewRow("Precio / acción", fmtEuro(p.pricePerShare), textSoft, textStrong)
                            PreviewRow("Bruto", fmtEuro(p.grossTotal), textSoft, textStrong)
                            PreviewRow("Comisión (0.5%)", fmtEuro(p.commission), textSoft, textStrong)

                            Divider(color = stroke)

                            val netLabel = if (mode == PortfolioViewModel.Mode.BUY) "Total a pagar" else "Total a recibir"
                            PreviewRow(netLabel, fmtEuro(p.netTotal), textMuted, accent)
                        }
                    }
                } else {
                    // Si no hay preview todavía, mostramos hint suave
                    Text(
                        text = "Introduce una cantidad válida para ver el cálculo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = neutral
                    )
                }

                // =========================
                // Última transacción (si se ejecutó)
                // =========================
                val last = vm.lastTx
                if (last != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = dialogSurface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, stroke, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Operación realizada",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textStrong
                            )
                            Text(
                                text = "ID: ${last.id} · ${last.type} · ${last.ticker} x${last.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSoft
                            )
                            Text(
                                text = "Neto: ${fmtEuro(last.netTotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }
                    }
                }
            }
        },

        confirmButton = {
            val canConfirm = (vm.preview != null) && (vm.error == null)
            TextButton(
                onClick = { vm.confirm() },
                enabled = canConfirm
            ) {
                Text(
                    text = "Confirmar",
                    color = if (canConfirm) accent else neutral,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },

        dismissButton = {
            TextButton(onClick = { vm.closeTrade() }) {
                Text("Cerrar", color = textSoft)
            }
        },

        // Material3 permite esto en Desktop/Android
        containerColor = dialogSurface
    )
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

private fun fmtEuro(v: Double): String = String.format("%.2f €", v)
