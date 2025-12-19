package org.example.project.presentation.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.domain.model.StockSnapshot

@Composable
fun StockRow(stock: StockSnapshot) {

    val color = when {
        stock.changePercent > 0 -> Color(0xFF2E7D32) // verde
        stock.changePercent < 0 -> Color(0xFFC62828) // rojo
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stock.ticker,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = String.format("%.2f €", stock.currentPrice),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = String.format("%.2f %%", stock.changePercent),
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
