package com.accesspay.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.core.format.Naira
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.Route
import com.accesspay.demo.bank.Balance
import com.accesspay.demo.bank.Transaction
import com.accesspay.demo.ui.accessai.AccessAiPanel
import com.accesspay.demo.ui.accessai.AccessAiTypography
import com.accesspay.demo.ui.accessai.AccessPayAiTheme
import com.accesspay.demo.ui.accessai.ActionButton
import com.accesspay.demo.ui.accessai.ActionIcon
import com.accesspay.demo.ui.accessai.ActionVariant
import com.accesspay.demo.ui.accessai.LocalAccessAi
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    balance: Balance?,
    transactions: List<Transaction>,
    consented: Boolean,
    highContrast: Boolean,
    wakeListeningActive: Boolean,
    onToggleWakeListening: () -> Unit,
    onToggleHighContrast: () -> Unit,
    entryFocusRequester: FocusRequester,
    showEntrySheet: Boolean = true,
    onTurnOnAccessAI: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val bank = AccessPayApp.bank
    val error by bank.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var quickInfo by remember { mutableStateOf<String?>(null) }

    val hostBg = Color(0xFFF2F4F3)
    val hostInk = Color(0xFF10201A)
    val hostCardBg = Color(0xFF1B6B2A)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = hostBg,
        contentColor = hostInk,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AccessPay",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = hostInk,
                        modifier = Modifier.semantics { heading() },
                    )
                    Surface(
                        color = if (highContrast) Color.Black else Color.White,
                        contentColor = if (highContrast) Color.White else hostInk,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.5.dp, if (highContrast) Color.Black else Color(0xFF6B7A71)),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onToggleHighContrast)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (highContrast) {
                                    "Switch AccessAI sheet to light mode"
                                } else {
                                    "Switch AccessAI sheet to high-contrast dark mode"
                                }
                            },
                    ) {
                        Text(
                            text = if (highContrast) "HC dark" else "Light",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }

                // Green Main Account card matching spec 1b / 1c
                Surface(
                    color = hostCardBg,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = balance?.accountName ?: "Main account",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Text(
                            text = balance?.let { Naira.display(it.balanceMinor) } ?: "Loading…",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Quick actions row: Send, Add money, Bills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HostQuickAction(
                        label = "Send",
                        onClick = { onOpen(Route.TRANSFER) },
                        modifier = Modifier.weight(1f),
                    )
                    HostQuickAction(
                        label = "Add money",
                        onClick = {
                            quickInfo = "Simulated funds only. Restart the app to reset the balance to ₦84,250."
                        },
                        modifier = Modifier.weight(1f),
                    )
                    HostQuickAction(
                        label = "Bills",
                        onClick = {
                            quickInfo = "Bills demo: use Send or ask AccessAI to send a transfer to a saved beneficiary."
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Recent transaction strip + Beneficiaries button
                val latest = transactions.firstOrNull()
                Surface(
                    color = Color.White,
                    contentColor = hostInk,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFD5DDD8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(Route.HISTORY) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (latest != null) {
                                "Recent: ${latest.description} · ${Naira.display(latest.amountMinor)}"
                            } else {
                                "Recent transactions"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = hostInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = hostCardBg,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HostQuickAction(
                        label = "Beneficiaries",
                        onClick = { onOpen(Route.BENEFICIARIES) },
                        modifier = Modifier.weight(1f),
                    )
                    HostQuickAction(
                        label = "All history",
                        onClick = { onOpen(Route.HISTORY) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Surface(
                    color = if (wakeListeningActive) Color(0xFFEAF3EC) else Color.White,
                    contentColor = hostInk,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (wakeListeningActive) 2.dp else 1.dp,
                        color = if (wakeListeningActive) hostCardBg else Color(0xFFD5DDD8),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleWakeListening)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = if (wakeListeningActive) {
                                "Voice wake active. Say Hello Access AI or Start Access AI to launch. Double-tap to pause."
                            } else {
                                "Voice wake off. Double-tap to enable Hello Access AI."
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (wakeListeningActive) {
                                "Say “Hello Access AI” or “Start Access AI”"
                            } else {
                                "Voice wake off · Enable “Hello Access AI”"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = hostInk,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (wakeListeningActive) "On" else "Off",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (wakeListeningActive) hostCardBg else Color(0xFF3D4A42),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                quickInfo?.let { msg ->
                    StatusBanner(StatusKind.WARNING, "Demo notice", msg)
                }

                error?.let {
                    StatusBanner(StatusKind.WARNING, "Can't reach your bank", "$it Your details may be out of date.")
                    SecondaryAction("Try again", onClick = { scope.launch { bank.refresh() } })
                }
            }

            // Bottom Entry Sheet (State 1 · Entry)
            if (showEntrySheet) {
                AccessAiPanel(
                    theme = AccessPayAiTheme,
                    highContrast = highContrast,
                ) {
                    val t = LocalAccessAi.current
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Use this app by voice",
                            style = AccessAiTypography.headline(t),
                            color = t.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "Ask for your balance, send money and more. You always confirm before money moves.",
                            style = AccessAiTypography.body(t),
                            color = t.mutedText,
                        )
                        Spacer(Modifier.height(2.dp))
                        ActionButton(
                            label = "Turn on AccessAI accessibility mode",
                            variant = ActionVariant.PRIMARY,
                            icon = ActionIcon.MIC,
                            tall = true,
                            contentDescriptionOverride = "Turn on AccessAI accessibility mode.",
                            modifier = Modifier
                                .focusRequester(entryFocusRequester)
                                .semantics {
                                    if (!consented) {
                                        onClick(label = "Opens a short privacy notice.", action = null)
                                    }
                                },
                            onClick = onTurnOnAccessAI,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostQuickAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White,
        contentColor = Color(0xFF10201A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFD5DDD8)),
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10201A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
