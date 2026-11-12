package com.accesspay.demo.ui.accessai

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accessai.android.AccessAIController
import com.accessai.android.ListeningState
import com.accessai.core.engine.DialogState
import com.accessai.core.engine.InputKind
import com.accessai.core.engine.Speaker
import com.accessai.core.format.Naira
import com.accesspay.demo.ai.AccessAiActionOutcome
import com.accesspay.demo.ai.AccessAiSessionTracker
import com.accesspay.demo.ai.PendingTransferDetails
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.ceil

enum class SheetUiState {
    CONSENT,
    LISTENING,
    THINKING,
    ANSWER,
    CLARIFICATION,
    CONFIRMATION,
    SUCCESS,
    FAILURE,
    MIC_DENIED,
    OFFLINE,
    NOT_UNDERSTOOD,
    STOPPED,
}

@Composable
fun AccessAiSheetOverlay(
    controller: AccessAIController,
    tracker: AccessAiSessionTracker,
    showConsent: Boolean,
    showStopped: Boolean,
    hasMicPermission: Boolean,
    highContrastOverride: Boolean?,
    reducedMotionOverride: Boolean?,
    onAcceptConsent: () -> Unit,
    onDeclineConsent: () -> Unit,
    onStop: () -> Unit,
    onTurnOnAgain: () -> Unit,
    onCloseStopped: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current

    val enabled by controller.enabled.collectAsStateWithLifecycle()
    val dialogState by controller.engine.state.collectAsStateWithLifecycle()
    val listeningState by controller.listening.collectAsStateWithLifecycle()
    val speaking by controller.speaking.collectAsStateWithLifecycle()
    val transcript by controller.engine.transcript.collectAsStateWithLifecycle()
    val lastAnnouncement by controller.engine.lastAnnouncement.collectAsStateWithLifecycle()
    val outcome by tracker.outcome.collectAsStateWithLifecycle()
    val pendingTransfer by tracker.pendingTransfer.collectAsStateWithLifecycle()

    var typeInsteadOpen by rememberSaveable { mutableStateOf(false) }
    var typedRequest by rememberSaveable { mutableStateOf("") }
    var consecutiveMisses by rememberSaveable { mutableIntStateOf(0) }
    var userDismissedAnswer by rememberSaveable { mutableStateOf(false) }
    var userPausedMic by rememberSaveable { mutableStateOf(false) }
    var lastAutoListenedToken by remember { mutableStateOf<String?>(null) }

    val isVoiceAvailable = controller.isVoiceAvailable
    val isOnline = remember(dialogState, lastAnnouncement, outcome) { isNetworkAvailable(context) }

    val lastUserText = remember(transcript) {
        transcript.lastOrNull { it.speaker == Speaker.USER }?.text.orEmpty()
    }
    val lastAiText = remember(transcript) {
        transcript.lastOrNull { it.speaker == Speaker.ACCESSAI }?.text.orEmpty()
    }

    // Reset stale previous-turn outcomes whenever a new voice or typed request starts interpreting.
    LaunchedEffect(dialogState) {
        when (dialogState) {
            is DialogState.Interpreting -> {
                userDismissedAnswer = false
                tracker.recordOutcome(null)
            }
            is DialogState.Executing -> {
                userDismissedAnswer = false
            }
            else -> Unit
        }
    }

    val announcementLower = lastAnnouncement?.display.orEmpty().lowercase()
    val isOfflineAnnouncement = announcementLower.contains("internet") ||
        announcementLower.contains("connection") ||
        announcementLower.contains("couldn't reach") ||
        announcementLower.contains("offline")
    val isNotUnderstoodAnnouncement = announcementLower.contains("didn't catch") ||
        announcementLower.contains("didn't understand") ||
        announcementLower.contains("try:") ||
        announcementLower.contains("not sure") ||
        announcementLower.contains("couldn't find")

    // Determine which of the 12 spec states to display
    val uiState: SheetUiState = when {
        showConsent -> SheetUiState.CONSENT
        showStopped -> SheetUiState.STOPPED
        dialogState is DialogState.AwaitingConfirmation -> SheetUiState.CONFIRMATION
        dialogState is DialogState.AwaitingInput -> SheetUiState.CLARIFICATION
        dialogState is DialogState.Interpreting ||
            dialogState is DialogState.AwaitingAuthentication ||
            dialogState is DialogState.Executing -> SheetUiState.THINKING
        dialogState is DialogState.ReadingList -> SheetUiState.ANSWER
        !userDismissedAnswer && outcome is AccessAiActionOutcome.TransferSuccess -> SheetUiState.SUCCESS
        !userDismissedAnswer && outcome is AccessAiActionOutcome.TransferFailure -> SheetUiState.FAILURE
        !userDismissedAnswer && (outcome is AccessAiActionOutcome.Offline || (!isOnline && lastUserText.isNotBlank())) -> SheetUiState.OFFLINE
        !userDismissedAnswer && (outcome is AccessAiActionOutcome.Balance || outcome is AccessAiActionOutcome.History) -> SheetUiState.ANSWER
        !userDismissedAnswer && isOfflineAnnouncement && lastUserText.isNotBlank() -> SheetUiState.OFFLINE
        !userDismissedAnswer && isNotUnderstoodAnnouncement && lastUserText.isNotBlank() -> SheetUiState.NOT_UNDERSTOOD
        !userDismissedAnswer && lastAiText.isNotBlank() && lastUserText.isNotBlank() -> SheetUiState.ANSWER
        (!hasMicPermission || listeningState is ListeningState.Unavailable) && !typeInsteadOpen -> SheetUiState.MIC_DENIED
        else -> SheetUiState.LISTENING
    }

    LaunchedEffect(uiState) {
        if (uiState == SheetUiState.NOT_UNDERSTOOD) {
            consecutiveMisses += 1
        } else if (uiState == SheetUiState.ANSWER || uiState == SheetUiState.SUCCESS || uiState == SheetUiState.CONFIRMATION) {
            consecutiveMisses = 0
        }
    }

    // Distinct sounds & haptics per 1h
    var prevMicListening by remember { mutableStateOf(false) }
    LaunchedEffect(uiState, listeningState) {
        val nowListening = uiState == SheetUiState.LISTENING && listeningState is ListeningState.Listening
        if (nowListening && !prevMicListening) {
            AccessAiAudioCues.micOpen()
        }
        prevMicListening = nowListening
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            SheetUiState.THINKING -> AccessAiAudioCues.thinkingTick()
            SheetUiState.ANSWER -> AccessAiAudioCues.answerChime()
            SheetUiState.CLARIFICATION -> AccessAiAudioCues.clarifyQuestion()
            SheetUiState.CONFIRMATION -> AccessAiAudioCues.confirmationPrompt()
            SheetUiState.SUCCESS -> {
                AccessAiAudioCues.success()
                val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(haptic)
            }
            SheetUiState.FAILURE -> {
                AccessAiAudioCues.failure()
                val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(haptic)
            }
            SheetUiState.OFFLINE -> AccessAiAudioCues.offline()
            SheetUiState.NOT_UNDERSTOOD -> AccessAiAudioCues.notUnderstood()
            SheetUiState.STOPPED -> AccessAiAudioCues.micClose()
            SheetUiState.LISTENING, SheetUiState.CONSENT, SheetUiState.MIC_DENIED -> Unit
        }
    }

    val headingFocusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState) {
        delay(60)
        runCatching { headingFocusRequester.requestFocus() }
    }

    // Voice-first hands-free flow: once AccessAI finishes speaking a greeting or prompt, open the mic automatically.
    val autoListenToken = when (uiState) {
        SheetUiState.LISTENING -> "listening:$userDismissedAnswer"
        SheetUiState.CLARIFICATION -> "clarify:${(dialogState as? DialogState.AwaitingInput)?.paramName}:${(dialogState as? DialogState.AwaitingInput)?.choices?.size}"
        SheetUiState.CONFIRMATION -> "confirm:${(dialogState as? DialogState.AwaitingConfirmation)?.expiresAt}"
        else -> null
    }
    LaunchedEffect(enabled, uiState, speaking, hasMicPermission, isVoiceAvailable, typeInsteadOpen, userPausedMic, autoListenToken) {
        if (!enabled || !hasMicPermission || !isVoiceAvailable || typeInsteadOpen || userPausedMic) return@LaunchedEffect
        val token = autoListenToken ?: run {
            lastAutoListenedToken = null
            return@LaunchedEffect
        }
        if (!speaking && listeningState is ListeningState.Idle && lastAutoListenedToken != token) {
            delay(400)
            if (!speaking && listeningState is ListeningState.Idle && !userPausedMic && !typeInsteadOpen) {
                lastAutoListenedToken = token
                controller.startListening()
            }
        }
    }

    val panelModifier = modifier
        .semantics { isTraversalGroup = true }

    val highContrast = highContrastOverride ?: isSystemHighContrast()
    val reducedMotion = reducedMotionOverride ?: !animationsEnabled()

    AccessAiPanel(
        theme = AccessPayAiTheme,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
        modifier = panelModifier,
    ) {
        val t = LocalAccessAi.current

        // Sheet top bar: "AccessAI" + "Stop" (Stop is top-right visually, last in TalkBack focus order per 1h)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AccessAI",
                style = AccessAiTypography.title(t),
                color = t.primary,
            )
            if (uiState != SheetUiState.STOPPED) {
                Surface(
                    color = t.container,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(t.strokeWidth, t.outline),
                    modifier = Modifier
                        .defaultMinSize(minWidth = Size.minTarget, minHeight = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            typeInsteadOpen = false
                            onStop()
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Stop AccessAI"
                            traversalIndex = 100f
                        },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Stop",
                            style = AccessAiTypography.label(t),
                            color = t.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.block))

        val submitTyped: () -> Unit = {
            val text = typedRequest.trim()
            if (text.isNotEmpty()) {
                typedRequest = ""
                typeInsteadOpen = false
                userDismissedAnswer = false
                tracker.recordOutcome(null)
                controller.submitText(text)
            }
        }

        if (uiState == SheetUiState.CONFIRMATION && dialogState is DialogState.AwaitingConfirmation) {
            ConfirmationSheetBody(
                state = dialogState as DialogState.AwaitingConfirmation,
                details = pendingTransfer,
                headingFocusRequester = headingFocusRequester,
                onStop = onStop,
                onConfirm = { controller.confirm() },
                onCancel = {
                    tracker.clear()
                    userDismissedAnswer = true
                    controller.cancel()
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.block),
            ) {
                when (uiState) {
                    SheetUiState.CONSENT -> ConsentBody(
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onAgree = onAcceptConsent,
                        onNotNow = onDeclineConsent,
                    )

                    SheetUiState.LISTENING -> ListeningBody(
                        listeningState = listeningState,
                        lastUserText = lastUserText,
                        typeInsteadOpen = typeInsteadOpen,
                        typedRequest = typedRequest,
                        onTypedChange = { typedRequest = it },
                        onSendTyped = submitTyped,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onToggleMic = {
                            if (!hasMicPermission) {
                                onRequestMicPermission()
                            } else if (listeningState is ListeningState.Listening) {
                                userPausedMic = true
                                controller.stopListening()
                            } else {
                                userPausedMic = false
                                userDismissedAnswer = true
                                controller.startListening()
                            }
                        },
                        onHelp = {
                            userDismissedAnswer = false
                            tracker.recordOutcome(null)
                            controller.help()
                        },
                        onToggleType = { typeInsteadOpen = !typeInsteadOpen },
                    )

                    SheetUiState.THINKING -> ThinkingBody(
                        lastUserText = lastUserText,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onCancel = {
                            tracker.clear()
                            userDismissedAnswer = true
                            controller.cancel()
                        },
                    )

                    SheetUiState.ANSWER -> AnswerBody(
                        dialogState = dialogState,
                        outcome = outcome,
                        fallbackAnswer = lastAnnouncement?.display.orEmpty().ifBlank { lastAiText },
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onRepeat = { controller.repeat() },
                        onNext = { controller.next() },
                        onPrevious = { controller.previous() },
                        onAskSomethingElse = {
                            userPausedMic = false
                            userDismissedAnswer = true
                            tracker.recordOutcome(null)
                            if (hasMicPermission && isVoiceAvailable) {
                                controller.startListening()
                            } else {
                                typeInsteadOpen = true
                            }
                        },
                    )

                    SheetUiState.CLARIFICATION -> ClarificationBody(
                        state = dialogState as DialogState.AwaitingInput,
                        lastUserText = lastUserText,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onChoose = { index ->
                            userDismissedAnswer = false
                            controller.choose(index)
                        },
                        onSubmitText = { text ->
                            userDismissedAnswer = false
                            controller.submitText(text)
                        },
                        onRepeat = { controller.repeat() },
                        onCancel = {
                            tracker.clear()
                            userDismissedAnswer = true
                            controller.cancel()
                        },
                    )

                    SheetUiState.SUCCESS -> SuccessBody(
                        outcome = outcome as AccessAiActionOutcome.TransferSuccess,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onDone = {
                            tracker.clear()
                            userDismissedAnswer = true
                            onCloseStopped()
                        },
                        onRepeat = { controller.repeat() },
                    )

                    SheetUiState.FAILURE -> FailureBody(
                        outcome = outcome as AccessAiActionOutcome.TransferFailure,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onTryAgain = {
                            userPausedMic = false
                            tracker.clear()
                            userDismissedAnswer = true
                            if (hasMicPermission && isVoiceAvailable) {
                                controller.startListening()
                            } else {
                                typeInsteadOpen = true
                            }
                        },
                        onDone = {
                            tracker.clear()
                            userDismissedAnswer = true
                            onCloseStopped()
                        },
                    )

                    SheetUiState.MIC_DENIED -> MicDeniedBody(
                        typedRequest = typedRequest,
                        onTypedChange = { typedRequest = it },
                        onSend = submitTyped,
                        onOpenSettings = {
                            if (!hasMicPermission) {
                                onRequestMicPermission()
                            } else {
                                openAppSettings(context)
                            }
                        },
                        onOpenSystemSettings = { openAppSettings(context) },
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                    )

                    SheetUiState.OFFLINE -> OfflineBody(
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onTryAgain = {
                            userPausedMic = false
                            tracker.clear()
                            userDismissedAnswer = true
                            if (hasMicPermission && isVoiceAvailable) {
                                controller.startListening()
                            } else {
                                typeInsteadOpen = true
                            }
                        },
                        onUseWithoutVoice = {
                            tracker.clear()
                            onCloseStopped()
                        },
                    )

                    SheetUiState.NOT_UNDERSTOOD -> NotUnderstoodBody(
                        misses = consecutiveMisses,
                        headingFocusRequester = headingFocusRequester,
                        onStop = onStop,
                        onTryAgain = {
                            userPausedMic = false
                            userDismissedAnswer = true
                            tracker.recordOutcome(null)
                            if (hasMicPermission && isVoiceAvailable) {
                                controller.startListening()
                            } else {
                                typeInsteadOpen = true
                            }
                        },
                        onTypeInstead = {
                            userDismissedAnswer = true
                            tracker.recordOutcome(null)
                            typeInsteadOpen = true
                        },
                        onHelp = {
                            userDismissedAnswer = false
                            tracker.recordOutcome(null)
                            controller.help()
                        },
                    )

                    SheetUiState.STOPPED -> StoppedBody(
                        headingFocusRequester = headingFocusRequester,
                        onTurnOnAgain = {
                            tracker.clear()
                            userDismissedAnswer = true
                            onTurnOnAgain()
                        },
                        onClose = {
                            tracker.clear()
                            onCloseStopped()
                        },
                    )

                    SheetUiState.CONFIRMATION -> Unit
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(
    text: String,
    focusRequester: FocusRequester,
    onStop: (() -> Unit)?,
) {
    val t = LocalAccessAi.current
    Text(
        text = text,
        style = AccessAiTypography.headline(t),
        color = t.onSurface,
        modifier = Modifier
            .focusRequester(focusRequester)
            .semantics {
                heading()
                traversalIndex = -1f
                if (onStop != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Stop AccessAI") {
                            onStop()
                            true
                        },
                    )
                }
            },
    )
}

@Composable
private fun ConsentBody(
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onAgree: () -> Unit,
    onNotNow: () -> Unit,
) {
    val t = LocalAccessAi.current
    SheetHeading("Before you start", headingFocusRequester, onStop)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConsentRow(
            title = "What is sent",
            body = "Your voice and the words on this screen go to our secure cloud. This is only to work out what you mean.",
        )
        ConsentRow(
            title = "Nothing is kept",
            body = "Audio and text are deleted as soon as each request ends.",
        )
        ConsentRow(
            title = "How to leave",
            body = "Say “Stop”, or tap Stop at any time.",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Agree and turn on",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.CHECK,
            tall = true,
            onClick = onAgree,
        )
        ActionButton(
            label = "Not now",
            variant = ActionVariant.SECONDARY,
            onClick = onNotNow,
        )
    }
    Spacer(Modifier.height(0.dp))
    Text(
        text = "You always confirm before money moves.",
        style = AccessAiTypography.body(t),
        color = t.mutedText,
    )
}

@Composable
private fun ConsentRow(title: String, body: String) {
    val t = LocalAccessAi.current
    Surface(
        color = t.container,
        shape = RoundedCornerShape(t.cardRadius),
        border = BorderStroke(t.strokeWidth, t.outline),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = AccessAiTypography.label(t), color = t.onSurface)
            Text(body, style = AccessAiTypography.body(t), color = t.mutedText)
        }
    }
}

@Composable
private fun ListeningBody(
    listeningState: ListeningState,
    lastUserText: String,
    typeInsteadOpen: Boolean,
    typedRequest: String,
    onTypedChange: (String) -> Unit,
    onSendTyped: () -> Unit,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onToggleMic: () -> Unit,
    onHelp: () -> Unit,
    onToggleType: () -> Unit,
) {
    val t = LocalAccessAi.current
    val isListening = listeningState is ListeningState.Listening
    val partial = (listeningState as? ListeningState.Listening)?.partial.orEmpty()
    val micState = if (isListening) MicUiState.LISTENING else MicUiState.PAUSED

    SheetHeading(
        text = if (isListening) "Listening" else "Paused",
        focusRequester = headingFocusRequester,
        onStop = onStop,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MicButton(state = micState, onClick = onToggleMic)
        Text(
            text = if (isListening) "Listening" else "Paused",
            style = AccessAiTypography.label(t),
            color = t.onSurface,
        )
        Text(
            text = if (isListening) "Tap the microphone to pause." else "Tap the microphone to listen.",
            style = AccessAiTypography.body(t),
            color = t.mutedText,
            textAlign = TextAlign.Center,
        )
    }

    val transcriptText = partial.ifBlank { lastUserText }
    if (transcriptText.isNotBlank()) {
        TranscriptView(
            label = "You said",
            text = transcriptText,
            isFinal = partial.isBlank(),
        )
    }

    if (typeInsteadOpen) {
        RequestTextField(
            value = typedRequest,
            onValueChange = onTypedChange,
            onSend = onSendTyped,
        )
        ActionButton(
            label = "Send",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.SEND,
            enabled = typedRequest.isNotBlank(),
            onClick = onSendTyped,
        )
    }

    Text(
        text = "Say “help” to hear what I can do.",
        style = AccessAiTypography.body(t),
        color = t.mutedText,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Help",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.HELP,
            contentDescriptionOverride = "Help. Hear what I can do",
            onClick = onHelp,
        )
        ActionButton(
            label = if (typeInsteadOpen) "Hide keyboard" else "Type instead",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.KEYBOARD,
            onClick = onToggleType,
        )
    }
}

@Composable
private fun ThinkingBody(
    lastUserText: String,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalAccessAi.current
    var stillWorking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(6000)
        stillWorking = true
    }

    SheetHeading("Working on it", headingFocusRequester, onStop)

    Text(
        text = "Nothing has been sent.",
        style = AccessAiTypography.body(t),
        color = t.mutedText,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (stillWorking) "Still working." else "Thinking. Please wait."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThinkingDots(reducedMotion = t.reducedMotion, color = t.primary)
        Text(
            text = if (stillWorking) "Still working…" else "Thinking…",
            style = AccessAiTypography.label(t),
            color = t.onSurface,
        )
    }

    if (lastUserText.isNotBlank()) {
        TranscriptView(label = "You asked", text = lastUserText, isFinal = true)
    }

    ActionButton(
        label = "Cancel",
        variant = ActionVariant.SECONDARY,
        icon = ActionIcon.CLOSE,
        onClick = onCancel,
    )
}

@Composable
private fun AnswerBody(
    dialogState: DialogState,
    outcome: AccessAiActionOutcome?,
    fallbackAnswer: String,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAskSomethingElse: () -> Unit,
) {
    val t = LocalAccessAi.current
    val readingList = dialogState as? DialogState.ReadingList

    when (outcome) {
        is AccessAiActionOutcome.Balance -> {
            val formatted = Naira.display(outcome.balanceMinor)
            SheetHeading("Your balance", headingFocusRequester, onStop)
            StatusBanner(
                kind = BannerKind.INFO,
                label = "Read only",
                text = "I only read this. Nothing was changed.",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "$formatted. ${outcome.accountName}, as of ${outcome.asOf}."
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatted,
                    style = AccessAiTypography.display(t),
                    color = t.onSurface,
                )
                Text(
                    text = "${outcome.accountName}, as of ${outcome.asOf}.",
                    style = AccessAiTypography.body(t),
                    color = t.mutedText,
                )
            }
        }
        is AccessAiActionOutcome.History -> {
            SheetHeading("Transaction history", headingFocusRequester, onStop)
            StatusBanner(
                kind = BannerKind.INFO,
                label = "Read only",
                text = "I only read this. Nothing was changed.",
            )
            val currentItem = if (readingList != null) {
                readingList.items.getOrNull(readingList.index)?.display ?: fallbackAnswer
            } else {
                outcome.items.firstOrNull() ?: fallbackAnswer
            }
            Surface(
                color = t.container,
                shape = RoundedCornerShape(t.cardRadius),
                border = BorderStroke(t.strokeWidth, t.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (readingList != null && readingList.items.isNotEmpty()) {
                        Text(
                            text = "Item ${readingList.index + 1} of ${readingList.items.size}",
                            style = AccessAiTypography.caption(t),
                            color = t.mutedText,
                        )
                    }
                    Text(
                        text = currentItem,
                        style = AccessAiTypography.title(t).copy(fontWeight = FontWeight.Normal),
                        color = t.onSurface,
                    )
                }
            }
            if (readingList != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.button)) {
                    ActionButton(
                        label = "Previous",
                        variant = ActionVariant.SECONDARY,
                        enabled = readingList.index > 0,
                        onClick = onPrevious,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        label = "Next",
                        variant = ActionVariant.PRIMARY,
                        enabled = readingList.index + 1 < readingList.items.size,
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        else -> {
            SheetHeading("AccessAI", headingFocusRequester, onStop)
            StatusBanner(
                kind = BannerKind.INFO,
                label = "Read only",
                text = "I only read this. Nothing was changed.",
            )
            Surface(
                color = t.container,
                shape = RoundedCornerShape(t.cardRadius),
                border = BorderStroke(t.strokeWidth, t.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = fallbackAnswer,
                    style = AccessAiTypography.title(t).copy(fontWeight = FontWeight.Normal),
                    color = t.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Repeat",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.VOLUME,
            onClick = onRepeat,
        )
        ActionButton(
            label = "Ask something else",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.MIC,
            onClick = onAskSomethingElse,
        )
    }
}

@Composable
private fun ClarificationBody(
    state: DialogState.AwaitingInput,
    lastUserText: String,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onChoose: (Int) -> Unit,
    onSubmitText: (String) -> Unit,
    onRepeat: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalAccessAi.current
    var replyText by rememberSaveable { mutableStateOf("") }

    if (state.choices.isNotEmpty()) {
        val firstName = parseChoiceLabel(state.choices.first()).first.split(" ").firstOrNull()
        val heading = if (state.kind == InputKind.DISAMBIGUATION && !firstName.isNullOrBlank()) {
            "Which $firstName?"
        } else {
            "Choose one"
        }
        SheetHeading(heading, headingFocusRequester, onStop)
        Text(
            text = if (state.choices.size == 2) {
                "I found two people. Say “one” or “two”, or tap a name."
            } else {
                state.question.display
            },
            style = AccessAiTypography.body(t),
            color = t.mutedText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
            state.choices.forEachIndexed { index, rawChoice ->
                val (name, last4) = parseChoiceLabel(rawChoice)
                val numberWord = when (index) {
                    0 -> "One"
                    1 -> "Two"
                    2 -> "Three"
                    else -> "${index + 1}"
                }
                val spokenDigits = last4.toList().joinToString(" ")
                val desc = if (last4.isNotBlank()) {
                    "$numberWord. $name. Account ending $spokenDigits. Double-tap to choose."
                } else {
                    "$numberWord. $name. Double-tap to choose."
                }
                Surface(
                    color = t.container,
                    shape = RoundedCornerShape(t.cardRadius),
                    border = BorderStroke(t.strokeWidth, t.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Size.button)
                        .clip(RoundedCornerShape(t.cardRadius))
                        .clickable { onChoose(index) }
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = desc
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(t.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = AccessAiTypography.label(t),
                                color = t.onPrimary,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, style = AccessAiTypography.label(t), color = t.onSurface)
                            if (last4.isNotBlank()) {
                                Text("Account ending $last4", style = AccessAiTypography.body(t), color = t.mutedText)
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Nothing is sent until you confirm.",
            style = AccessAiTypography.body(t),
            color = t.mutedText,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
            ActionButton(
                label = "Repeat options",
                variant = ActionVariant.SECONDARY,
                icon = ActionIcon.VOLUME,
                onClick = onRepeat,
            )
            ActionButton(
                label = "None of these",
                variant = ActionVariant.SECONDARY,
                icon = ActionIcon.CLOSE,
                onClick = onCancel,
            )
        }
    } else {
        SheetHeading(
            text = if (state.paramName == "amount_minor") "How much?" else "More details needed",
            focusRequester = headingFocusRequester,
            onStop = onStop,
        )
        Text(
            text = state.question.display,
            style = AccessAiTypography.title(t).copy(fontWeight = FontWeight.Normal),
            color = t.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        if (lastUserText.isNotBlank()) {
            TranscriptView("You asked", lastUserText, isFinal = true)
        }
        RequestTextField(
            value = replyText,
            onValueChange = { replyText = it },
            placeholder = if (state.paramName == "amount_minor") "For example: 5000" else "Type your reply",
            onSend = {
                val trimmed = replyText.trim()
                if (trimmed.isNotEmpty()) {
                    replyText = ""
                    onSubmitText(trimmed)
                }
            },
        )
        ActionButton(
            label = "Send",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.SEND,
            enabled = replyText.isNotBlank(),
            onClick = {
                val trimmed = replyText.trim()
                if (trimmed.isNotEmpty()) {
                    replyText = ""
                    onSubmitText(trimmed)
                }
            },
        )
        ActionButton(
            label = "Cancel",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.CLOSE,
            onClick = onCancel,
        )
    }
}

@Composable
private fun ConfirmationSheetBody(
    state: DialogState.AwaitingConfirmation,
    details: PendingTransferDetails?,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalAccessAi.current
    val totalSeconds = remember(state.expiresAt) {
        secondsRemaining(state.expiresAt).coerceAtLeast(1)
    }
    var secondsLeft by remember(state.expiresAt) {
        mutableIntStateOf(totalSeconds)
    }
    var milestoneMessage by remember(state.expiresAt) { mutableStateOf("") }

    LaunchedEffect(state.expiresAt) {
        while (true) {
            val left = secondsRemaining(state.expiresAt)
            secondsLeft = left
            if (left == 30 || left == 20 || left == 10 || left == 5) {
                milestoneMessage = "Cancels in $left seconds."
            }
            if (left <= 0) {
                milestoneMessage = "Cancelled. Nothing was sent."
                onCancel()
                break
            }
            delay(1000)
        }
    }

    // Scrollable top section + pinned Confirm & Cancel buttons at bottom (1d)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.block)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            SheetHeading("Confirm payment", headingFocusRequester, onStop)
            Text(
                text = "Money leaves your account when you confirm. Say “Confirm” or “Cancel”.",
                style = AccessAiTypography.body(t),
                color = t.mutedText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )

            val amountDisplay = details?.let { Naira.display(it.amountMinor) } ?: extractAmount(state.summary.display)
            val recipientName = details?.recipientName ?: extractRecipient(state.summary.display)
            val last4 = details?.accountLast4.orEmpty()
            val fromAccount = details?.fromAccount ?: "Main account"
            val feeDisplay = details?.let { Naira.display(it.feeMinor) } ?: "₦0"

            ConfirmationCard(
                amountDisplay = amountDisplay,
                spokenAmount = amountDisplay,
                recipientName = recipientName,
                accountLast4 = last4,
                fromAccount = fromAccount,
                feeDisplay = feeDisplay,
                secondsLeft = secondsLeft,
                totalSeconds = totalSeconds,
            )

            if (milestoneMessage.isNotBlank()) {
                // Hidden polite live region for 20s, 10s, 5s countdown milestones (1h)
                Text(
                    text = milestoneMessage,
                    style = AccessAiTypography.caption(t),
                    color = t.surface,
                    modifier = Modifier
                        .height(1.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        val confirmDesc = if (details != null) {
            "Confirm. Send ${Naira.display(details.amountMinor)} to ${details.recipientName}."
        } else {
            "Confirm."
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
            ActionButton(
                label = "Confirm",
                variant = ActionVariant.PRIMARY,
                icon = ActionIcon.CHECK,
                tall = true,
                contentDescriptionOverride = confirmDesc,
                onClick = onConfirm,
            )
            ActionButton(
                label = "Cancel",
                variant = ActionVariant.SECONDARY,
                icon = ActionIcon.CLOSE,
                tall = true,
                contentDescriptionOverride = "Cancel. Do not send.",
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun SuccessBody(
    outcome: AccessAiActionOutcome.TransferSuccess,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onDone: () -> Unit,
    onRepeat: () -> Unit,
) {
    val t = LocalAccessAi.current
    val sentAmount = Naira.display(outcome.amountMinor)
    val newBalance = Naira.display(outcome.newBalanceMinor)

    SheetHeading("Payment sent", headingFocusRequester, onStop)

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StatusBanner(
            kind = BannerKind.SUCCESS,
            label = "Success",
            text = "Sent $sentAmount to ${outcome.recipientName}.",
        )
    }

    Text(
        text = "Reference ${outcome.reference} · ${outcome.time}. New balance $newBalance.",
        style = AccessAiTypography.body(t),
        color = t.onSurface,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Done",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.CHECK,
            onClick = onDone,
        )
        ActionButton(
            label = "Repeat",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.VOLUME,
            onClick = onRepeat,
        )
    }
}

@Composable
private fun FailureBody(
    outcome: AccessAiActionOutcome.TransferFailure,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onTryAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val t = LocalAccessAi.current
    val bal = Naira.display(outcome.balanceMinor)

    SheetHeading("Payment not sent", headingFocusRequester, onStop)

    Text(
        text = "No money left your account. Your balance is still $bal.",
        style = AccessAiTypography.body(t),
        color = t.onSurface,
    )

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StatusBanner(
            kind = BannerKind.ERROR,
            label = "Failed",
            text = outcome.reason,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Try again",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.REFRESH,
            contentDescriptionOverride = "Try again. You will need to confirm again.",
            onClick = onTryAgain,
        )
        ActionButton(
            label = "Done",
            variant = ActionVariant.SECONDARY,
            onClick = onDone,
        )
    }
}

@Composable
private fun MicDeniedBody(
    typedRequest: String,
    onTypedChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
) {
    SheetHeading("Microphone is off", headingFocusRequester, onStop)

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StatusBanner(
            kind = BannerKind.WARNING,
            label = "Needs permission",
            text = "AccessAI can’t hear you. Allow the microphone, or type instead.",
        )
    }

    RequestTextField(
        value = typedRequest,
        onValueChange = onTypedChange,
        onSend = onSend,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Send",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.SEND,
            enabled = typedRequest.isNotBlank(),
            onClick = onSend,
        )
        ActionButton(
            label = "Open settings",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.SETTINGS,
            onClick = onOpenSettings,
        )
        ActionButton(
            label = "App permission settings",
            variant = ActionVariant.TERTIARY,
            onClick = onOpenSystemSettings,
        )
    }
}

@Composable
private fun OfflineBody(
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onTryAgain: () -> Unit,
    onUseWithoutVoice: () -> Unit,
) {
    SheetHeading("No connection", headingFocusRequester, onStop)

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StatusBanner(
            kind = BannerKind.WARNING,
            label = "Offline",
            text = "I need the internet to understand you. Nothing was sent or changed.",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Try again",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.REFRESH,
            onClick = onTryAgain,
        )
        ActionButton(
            label = "Use the app without voice",
            variant = ActionVariant.SECONDARY,
            onClick = onUseWithoutVoice,
        )
    }
}

@Composable
private fun NotUnderstoodBody(
    misses: Int,
    headingFocusRequester: FocusRequester,
    onStop: () -> Unit,
    onTryAgain: () -> Unit,
    onTypeInstead: () -> Unit,
    onHelp: () -> Unit,
) {
    SheetHeading("I didn’t catch that", headingFocusRequester, onStop)

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        StatusBanner(
            kind = BannerKind.WARNING,
            label = "Not understood",
            text = "Try: “What’s my balance?” or “Send money”.",
        )
    }

    // Per 1h: "After 3 misses, offers Type instead first."
    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        if (misses >= 3) {
            ActionButton(
                label = "Type instead",
                variant = ActionVariant.PRIMARY,
                icon = ActionIcon.KEYBOARD,
                onClick = onTypeInstead,
            )
            ActionButton(
                label = "Try again",
                variant = ActionVariant.SECONDARY,
                icon = ActionIcon.MIC,
                onClick = onTryAgain,
            )
        } else {
            ActionButton(
                label = "Try again",
                variant = ActionVariant.PRIMARY,
                icon = ActionIcon.MIC,
                onClick = onTryAgain,
            )
            ActionButton(
                label = "Type instead",
                variant = ActionVariant.SECONDARY,
                icon = ActionIcon.KEYBOARD,
                onClick = onTypeInstead,
            )
        }
        ActionButton(
            label = "Hear what I can do",
            variant = ActionVariant.SECONDARY,
            icon = ActionIcon.HELP,
            onClick = onHelp,
        )
    }
}

@Composable
private fun StoppedBody(
    headingFocusRequester: FocusRequester,
    onTurnOnAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalAccessAi.current
    SheetHeading("AccessAI is off", headingFocusRequester, onStop = null)

    Text(
        text = "TalkBack works as normal. Nothing was saved.",
        style = AccessAiTypography.body(t),
        color = t.mutedText,
    )

    Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StatusBanner(
            kind = BannerKind.INFO,
            label = "Stopped",
            text = "Listening has ended. The microphone is off.",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.button)) {
        ActionButton(
            label = "Turn on again",
            variant = ActionVariant.PRIMARY,
            icon = ActionIcon.MIC,
            onClick = onTurnOnAgain,
        )
        ActionButton(
            label = "Close",
            variant = ActionVariant.SECONDARY,
            onClick = onClose,
        )
    }
}

@Composable
private fun RequestTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String = "For example: What’s my balance?",
) {
    val t = LocalAccessAi.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(200)) },
        label = { Text("Type your request", style = AccessAiTypography.caption(t)) },
        placeholder = { Text(placeholder, style = AccessAiTypography.body(t), color = t.mutedText) },
        textStyle = AccessAiTypography.body(t).copy(color = t.onSurface),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        shape = RoundedCornerShape(t.cardRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = t.onSurface,
            unfocusedTextColor = t.onSurface,
            focusedContainerColor = t.container,
            unfocusedContainerColor = t.container,
            focusedBorderColor = t.primary,
            unfocusedBorderColor = t.outline,
            focusedLabelColor = t.primary,
            unfocusedLabelColor = t.mutedText,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Size.button)
            .semantics {
                contentDescription = "Type your request. Editable."
            },
    )
}

private fun parseChoiceLabel(raw: String): Pair<String, String> {
    val match = Regex("""^(.+?)\s*\(…?(\d{4})\)$""").find(raw.trim())
    return if (match != null) {
        match.groupValues[1].trim() to match.groupValues[2]
    } else {
        raw.trim() to ""
    }
}

private fun secondsRemaining(expiresAt: Instant): Int {
    val millis = expiresAt.toEpochMilli() - System.currentTimeMillis()
    return ceil(millis / 1000.0).toInt().coerceAtLeast(0)
}

private fun extractAmount(summary: String): String =
    Regex("""₦[\d,]+(?:\.\d{2})?""").find(summary)?.value ?: "₦0"

private fun extractRecipient(summary: String): String =
    Regex("""to\s+([^,]+)""").find(summary)?.groupValues?.getOrNull(1)?.trim() ?: "Recipient"

private fun isNetworkAvailable(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
