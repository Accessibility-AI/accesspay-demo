package com.accesspay.demo.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.android.AccessAI
import com.accessai.compose.AccessAIConsentScreen
import com.accessai.compose.AccessAIModeScreen
import com.accessai.compose.AccessAIScreen
import com.accessai.compose.AccessAITheme
import com.accessai.core.announce
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

    val turnOn = {
        controller.enable(greeting = announce {
            text("AccessAI is on. ")
            balance?.let { text("Your balance is "); naira(it.balanceMinor); text(". ") }
            text("What would you like to do?")
        })
    }

    BackHandler(enabled = !enabled && (showConsent || route != Route.HOME)) {
        if (showConsent) showConsent = false else navigator.back()
    }

    when {
        showConsent -> AccessAIConsentScreen(
            appName = "AccessPay",
            onContinue = {
                controller.acceptConsent()
                showConsent = false
                turnOn()
            },
            onNotNow = { showConsent = false },
        )
        enabled -> AccessAITheme { AccessAIModeScreen(onExit = {}) }
        else -> when (route) {
            Route.HOME -> HomeScreen(
                balance = balance,
                onTurnOnAccessAI = { if (consented) turnOn() else showConsent = true },
                onOpen = navigator::open,
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
}
