package com.accesspay.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.accesspay.demo.bank.Beneficiary

@Composable
fun BeneficiariesScreen(beneficiaries: List<Beneficiary>, onBack: () -> Unit, onSend: () -> Unit) {
    ScreenScaffold(title = "Beneficiaries", onBack = onBack) {
        if (beneficiaries.isEmpty()) {
            Text("No saved beneficiaries.", style = MaterialTheme.typography.bodyLarge)
        }
        Column {
            beneficiaries.forEach { b ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${b.name}, ${b.bank}, account ending ${b.accountLast4.toList().joinToString(" ")}"
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${b.bank} · account ending ${b.accountLast4}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
        PrimaryAction("Send money", onClick = onSend)
    }
}
