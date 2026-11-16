package com.accesspay.demo.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.android.AccessAI
import com.accessai.compose.AccessAIConsentScreen
import com.accessai.compose.AccessAIModeScreen
import com.accessai.compose.AccessAIScreen
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.Route

// FragmentActivity (not ComponentActivity): AccessAI shows the biometric / screen-lock prompt before transfers.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccessPayTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    AccessPayRoot()
                }
            }
        }
    }
}

@Composable
private fun AccessPayRoot() {
    val bank = AccessPayApp.bank
    val navigator = AccessPayApp.navigator
    val controller = AccessAI.controller

    val enabled by controller.enabled.collectAsStateWithLifecycle()
    val consented by controller.consented.collectAsStateWithLifecycle()
    val route by navigator.route.collectAsStateWithLifecycle()
    val balance by bank.balance.collectAsStateWithLifecycle()
    val transactions by bank.transactions.collectAsStateWithLifecycle()
    val beneficiaries by bank.beneficiaries.collectAsStateWithLifecycle()
    var showConsent by rememberSaveable { mutableStateOf(false) }

    // Reload on start and whenever AccessAI mode closes, since it may have moved money.
    LaunchedEffect(enabled) { if (!enabled) bank.refresh() }

    // Published whether or not AccessAI mode is showing, so "where am I" and the interpreter know the screen.
    AccessAIScreen(descriptorFor(route, balance, transactions, beneficiaries))

    val turnOn = { controller.enable() }
    val onTurnOnAccessAI = { if (consented) turnOn() else showConsent = true }
    val overlay = enabled || showConsent

    BackHandler(enabled = !enabled && (showConsent || route != Route.HOME)) {
        if (showConsent) showConsent = false else navigator.back()
    }

    // The host app stays visible under AccessAI's panel, but hidden from TalkBack so focus stays in the panel.
    Box(Modifier.fillMaxSize().then(if (overlay) Modifier.clearAndSetSemantics { } else Modifier)) {
        when (route) {
            Route.HOME -> HomeScreen(
                balance = balance,
                recent = transactions.firstOrNull(),
                onTurnOnAccessAI = onTurnOnAccessAI,
                onOpen = navigator::open,
                entryHint = if (consented) null else "Opens a short privacy notice.",
            )
            Route.TRANSFER -> TransferScreen(onBack = { navigator.back() }, onDone = { navigator.open(Route.HOME) })
            Route.HISTORY -> HistoryScreen(transactions, onBack = { navigator.back() })
            Route.BENEFICIARIES -> BeneficiariesScreen(
                beneficiaries,
                onBack = { navigator.back() },
                onSend = { navigator.open(Route.TRANSFER) },
            )
        }
    }

    when {
        showConsent -> AccessPayAI {
            AccessAIConsentScreen(
                appName = "AccessPay",
                onContinue = {
                    controller.acceptConsent()
                    showConsent = false
                    turnOn()
                },
                onNotNow = { showConsent = false },
            )
        }
        enabled -> AccessPayAI {
            AccessAIModeScreen(
                onExit = { navigator.open(Route.HOME) },
                onUseWithoutVoice = {
                    controller.disable()
                    navigator.open(Route.HOME)
                },
            )
        }
    }
}
