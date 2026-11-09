package com.accesspay.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.compose.AccessAIToggleButton
import com.accessai.core.format.Naira
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.Route
import com.accesspay.demo.bank.Balance
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(balance: Balance?, onTurnOnAccessAI: () -> Unit, onOpen: (Route) -> Unit) {
    val bank = AccessPayApp.bank
    val error by bank.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "AccessPay", onBack = null) {
        // First control after the heading, so TalkBack users reach it straight away.
        AccessAIToggleButton(onClick = onTurnOnAccessAI)

        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(balance?.accountName ?: "Main account", style = MaterialTheme.typography.titleMedium)
                Text("Available balance", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    balance?.let { Naira.display(it.balanceMinor) } ?: "Loading…",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        error?.let {
            StatusBanner(StatusKind.WARNING, "Can't reach your bank", "$it Your details may be out of date.")
            SecondaryAction("Try again", onClick = { scope.launch { bank.refresh() } })
        }

        PrimaryAction("Send money", onClick = { onOpen(Route.TRANSFER) })
        SecondaryAction("Transaction history", onClick = { onOpen(Route.HISTORY) })
        SecondaryAction("Beneficiaries", onClick = { onOpen(Route.BENEFICIARIES) })

        Text(
            "Demo app. Simulated funds only: no real money, bank accounts, PINs or one-time codes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
