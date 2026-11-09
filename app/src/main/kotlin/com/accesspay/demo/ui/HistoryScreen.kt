package com.accesspay.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accessai.core.format.Naira
import com.accesspay.demo.ai.SpokenDate
import com.accesspay.demo.bank.Transaction

@Composable
fun HistoryScreen(transactions: List<Transaction>, onBack: () -> Unit) {
    ScreenScaffold(title = "Transaction history", onBack = onBack) {
        if (transactions.isEmpty()) {
            Text("No transactions yet.", style = MaterialTheme.typography.bodyLarge)
        }
        Column {
            transactions.forEach { t ->
                val direction = if (t.isCredit) "Received" else "Paid"
                val amount = Naira.display(t.amountMinor)
                val date = SpokenDate.of(t.createdAt)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        // One stop per transaction for TalkBack, in a fixed order.
                        .semantics(mergeDescendants = true) { contentDescription = "$direction $amount. ${t.description}. $date." },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.description, style = MaterialTheme.typography.bodyLarge)
                        Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (t.isCredit) "+ " else "− ") + amount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (t.isCredit) StatusColors.positive() else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
