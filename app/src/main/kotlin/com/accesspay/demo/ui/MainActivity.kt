package com.accesspay.demo.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.android.AccessAI
import com.accessai.compose.AccessAIScreen
import com.accessai.core.announce
import com.accesspay.demo.AccessPayApp
import com.accesspay.demo.Route
import com.accesspay.demo.ai.WakePhraseController
import com.accesspay.demo.ui.accessai.AccessAiSheetOverlay
import com.accesspay.demo.ui.accessai.isSystemHighContrast
import kotlinx.coroutines.delay

// FragmentActivity (not ComponentActivity): AccessAI shows the biometric / screen-lock prompt before transfers.
class MainActivity : FragmentActivity() {
    private var launchAccessAiSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isStartAccessAiIntent(intent)) {
            launchAccessAiSignal += 1
        }
        setContent {
            val systemDarkOrHc = isSystemInDarkTheme() || isSystemHighContrast()
            var highContrastOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val effectiveDark = highContrastOverride ?: systemDarkOrHc

            AccessPayTheme(dark = effectiveDark) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding(),
                ) {
                    AccessPayRoot(
                        highContrast = effectiveDark,
                        launchAccessAiSignal = launchAccessAiSignal,
                        onToggleHighContrast = {
                            highContrastOverride = !effectiveDark
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isStartAccessAiIntent(intent)) {
            launchAccessAiSignal += 1
        }
    }

    private fun isStartAccessAiIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "accesspay" && data.host == "start-accessai"
    }
}

@Composable
private fun AccessPayRoot(
    highContrast: Boolean,
    launchAccessAiSignal: Int,
    onToggleHighContrast: () -> Unit,
) {
    val context = LocalContext.current
    val bank = AccessPayApp.bank
    val navigator = AccessPayApp.navigator
    val tracker = AccessPayApp.aiTracker
    val controller = AccessAI.controller

    val enabled by controller.enabled.collectAsStateWithLifecycle()
    val consented by controller.consented.collectAsStateWithLifecycle()
    val route by navigator.route.collectAsStateWithLifecycle()
    val balance by bank.balance.collectAsStateWithLifecycle()
    val transactions by bank.transactions.collectAsStateWithLifecycle()
    val beneficiaries by bank.beneficiaries.collectAsStateWithLifecycle()

    var showConsent by rememberSaveable { mutableStateOf(false) }
    var showStopped by rememberSaveable { mutableStateOf(false) }
    var closedExplicitly by rememberSaveable { mutableStateOf(false) }
    var wakeWordEnabled by rememberSaveable { mutableStateOf(true) }
    var isForeground by remember { mutableStateOf(true) }
    var hasMicPermission by remember { mutableStateOf(controller.hasMicrophonePermission) }
    val entryFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    hasMicPermission = controller.hasMicrophonePermission
                }
                Lifecycle.Event.ON_PAUSE -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val turnOn: () -> Unit = {
        closedExplicitly = false
        showStopped = false
        tracker.clear()
        controller.enable(
            greeting = announce {
                text("AccessAI is on. ")
                balance?.let { text("Your balance is "); naira(it.balanceMinor); text(". ") }
                text("What would you like to do?")
            },
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted || controller.hasMicrophonePermission
        if (enabled && hasMicPermission) {
            controller.startListening()
        }
    }

    // If the user says "Stop" by voice, SDK disables AccessAI; show State 9 (AccessAI is off).
    var prevEnabled by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        if (prevEnabled && !enabled && !showConsent && !closedExplicitly) {
            showStopped = true
        }
        prevEnabled = enabled
        bank.refresh()
    }

    // Published whether or not AccessAI mode is showing, so "where am I" and the interpreter know the screen.
    AccessAIScreen(descriptorFor(route, balance, transactions, beneficiaries))

    val sheetVisible = showConsent || enabled || showStopped
    val wakeListeningActive = isForeground && !sheetVisible && consented && hasMicPermission && wakeWordEnabled && controller.isVoiceAvailable

    val wakePhraseController = remember(context) {
        WakePhraseController(context) {
            if (!enabled) turnOn()
        }
    }

    DisposableEffect(wakePhraseController) {
        onDispose { wakePhraseController.stop() }
    }

    LaunchedEffect(wakeListeningActive) {
        if (wakeListeningActive) {
            // Brief settle delay after closing AccessAI mode so Stop doesn't immediately re-trigger.
            delay(500)
            wakePhraseController.start()
        } else {
            wakePhraseController.stop()
        }
    }

    var handledLaunchSignal by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(launchAccessAiSignal) {
        if (launchAccessAiSignal > handledLaunchSignal) {
            handledLaunchSignal = launchAccessAiSignal
            if (consented) {
                turnOn()
                if (!controller.hasMicrophonePermission) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                showConsent = true
            }
        }
    }

    BackHandler(enabled = sheetVisible || route != Route.HOME) {
        when {
            showConsent -> showConsent = false
            showStopped -> {
                showStopped = false
                tracker.clear()
            }
            enabled -> {
                controller.disable()
                showStopped = true
            }
            else -> navigator.back()
        }
    }

    // Restore focus to the host app's entry button on Close (1h)
    var wasSheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(sheetVisible) {
        if (wasSheetVisible && !sheetVisible && route == Route.HOME) {
            delay(80)
            runCatching { entryFocusRequester.requestFocus() }
        }
        wasSheetVisible = sheetVisible
    }

    Box(Modifier.fillMaxSize()) {
        val hostModifier = if (sheetVisible) {
            Modifier
                .fillMaxSize()
                .semantics { invisibleToUser() }
        } else {
            Modifier.fillMaxSize()
        }

        Box(hostModifier) {
            when (route) {
                Route.HOME -> HomeScreen(
                    balance = balance,
                    transactions = transactions,
                    consented = consented,
                    highContrast = highContrast,
                    wakeListeningActive = wakeListeningActive,
                    onToggleWakeListening = {
                        if (!consented) {
                            showConsent = true
                        } else if (!hasMicPermission) {
                            wakeWordEnabled = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            wakeWordEnabled = !wakeWordEnabled
                        }
                    },
                    onToggleHighContrast = onToggleHighContrast,
                    entryFocusRequester = entryFocusRequester,
                    showEntrySheet = !sheetVisible,
                    onTurnOnAccessAI = {
                        if (consented) {
                            turnOn()
                            if (!controller.hasMicrophonePermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            showConsent = true
                        }
                    },
                    onOpen = navigator::open,
                )
                Route.TRANSFER -> TransferScreen(
                    onBack = { navigator.back() },
                    onDone = { navigator.open(Route.HOME) },
                )
                Route.HISTORY -> HistoryScreen(
                    transactions = transactions,
                    onBack = { navigator.back() },
                )
                Route.BENEFICIARIES -> BeneficiariesScreen(
                    beneficiaries = beneficiaries,
                    onBack = { navigator.back() },
                    onSend = { navigator.open(Route.TRANSFER) },
                )
            }
        }

        if (sheetVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
            AccessAiSheetOverlay(
                controller = controller,
                tracker = tracker,
                showConsent = showConsent,
                showStopped = showStopped,
                hasMicPermission = hasMicPermission,
                highContrastOverride = highContrast,
                reducedMotionOverride = null,
                onAcceptConsent = {
                    controller.acceptConsent()
                    showConsent = false
                    turnOn()
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDeclineConsent = {
                    showConsent = false
                },
                onStop = {
                    if (showConsent) {
                        showConsent = false
                    } else {
                        controller.disable()
                        showStopped = true
                    }
                },
                onTurnOnAgain = {
                    showStopped = false
                    turnOn()
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onCloseStopped = {
                    closedExplicitly = true
                    if (enabled) controller.disable()
                    showStopped = false
                    tracker.clear()
                },
                onRequestMicPermission = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
