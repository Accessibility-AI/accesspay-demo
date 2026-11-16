package com.accesspay.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.compose.AccessAIEntryPanel
import com.accessai.core.format.Naira
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.Route
import com.accesspay.demo.bank.Balance
import com.accesspay.demo.bank.Transaction
import kotlinx.coroutines.launch

/**
 * Home, laid out as the AccessAI UI spec's "1 · Entry" screen: header, balance card, quick actions, the latest
 * transaction, and the AccessAI entry panel pinned to the bottom. TalkBack reads them in that order; the
 * heading also carries a "Turn on AccessAI" custom action so voice mode is one step from the top.
 */
@Composable
fun HomeScreen(
    balance: Balance?,
    recent: Transaction?,
    onTurnOnAccessAI: () -> Unit,
    onOpen: (Route) -> Unit,
    entryHint: String? = null,
) {
    val bank = AccessPayApp.bank
    val error by bank.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize().semantics { paneTitle = "AccessPay" }, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "AccessPay",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).semantics {
                        heading()
                        customActions = listOf(CustomAccessibilityAction("Turn on AccessAI accessibility mode") { onTurnOnAccessAI(); true })
                    },
                )

                BalanceCard(balance)

                error?.let {
                    StatusBanner(StatusKind.WARNING, "Can't reach your bank", "$it Your details may be out of date.")
                    SecondaryAction("Try again", onClick = { scope.launch { bank.refresh() } })
                }

                QuickActions(onOpen)

                recent?.let { t ->
                    Tile(
                        onClick = { onOpen(Route.HISTORY) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            // Non-breaking joins keep "· +₦5,000" together when the line wraps.
                            "Recent: ${t.description} · ${if (t.isCredit) "+⁠" else ""}${Naira.display(t.amountMinor)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Text(
                    "Demo app. Simulated funds only: no real money, bank accounts, PINs or one-time codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AccessPayAI { AccessAIEntryPanel(onTurnOn = onTurnOnAccessAI, hint = entryHint) }
        }
    }
}

@Composable
private fun BalanceCard(balance: Balance?) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                balance?.accountName ?: "Main account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                balance?.let { Naira.display(it.balanceMinor) } ?: "Loading…",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Three tiles in a row; stacked instead once large text would squeeze them. */
@Composable
private fun QuickActions(onOpen: (Route) -> Unit) {
    val items = listOf("Send" to Route.TRANSFER, "History" to Route.HISTORY, "Beneficiaries" to Route.BENEFICIARIES)
    val tiles: @Composable (Modifier) -> Unit = { m ->
        items.forEach { (label, route) ->
            Tile(onClick = { onOpen(route) }, modifier = m) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (LocalDensity.current.fontScale > 1.15f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { tiles(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { tiles(Modifier.weight(1f)) }
    }
}

/** A white rounded tile at least 64dp high (the spec's minimum target), clickable as one button. */
@Composable
private fun Tile(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Column(content = content)
        }
    }
}
