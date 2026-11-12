package com.accesspay.demo.ui.accessai

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MicUiState { IDLE, LISTENING, PAUSED, DENIED, DISABLED }

/**
 * MicButton (1g / 1e):
 * - 128dp circle (160dp at 200% font scale), icon 56dp, ring 3dp, 10dp out.
 * - Listening ring: opacity 25% to 75%, 3.2s ease-in-out. Static when reducedMotion is true.
 */
@Composable
fun MicButton(
    state: MicUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalAccessAi.current
    val fontScale = LocalDensity.current.fontScale
    val buttonSize = if (fontScale >= 1.75f) Size.micLarge else Size.mic
    val ringSize = buttonSize + 20.dp // 10dp out on each side

    val ringAlpha: Float = if (state == MicUiState.LISTENING && !t.reducedMotion) {
        val transition = rememberInfiniteTransition(label = "micRing")
        val animated by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "micRingOpacity",
        )
        animated
    } else if (state == MicUiState.LISTENING) {
        0.75f
    } else {
        0f
    }

    val stateDesc = when (state) {
        MicUiState.LISTENING -> "Listening"
        MicUiState.PAUSED -> "Paused"
        MicUiState.IDLE -> "Ready"
        MicUiState.DENIED -> "Microphone permission needed"
        MicUiState.DISABLED -> "Unavailable"
    }
    val contentDesc = when (state) {
        MicUiState.LISTENING -> "Microphone. Listening. Double-tap to pause."
        MicUiState.PAUSED -> "Microphone. Paused. Double-tap to listen."
        MicUiState.IDLE -> "Microphone. Double-tap to listen."
        MicUiState.DENIED -> "Microphone off. Permission needed."
        MicUiState.DISABLED -> "Microphone unavailable."
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(ringSize)
            .defaultMinSize(minWidth = Size.minTarget, minHeight = Size.minTarget),
        contentAlignment = Alignment.Center,
    ) {
        // Outer listening ring (3dp, 10dp out) or focus ring (4dp)
        Canvas(modifier = Modifier.size(ringSize)) {
            if (state == MicUiState.LISTENING) {
                drawCircle(
                    color = t.primary.copy(alpha = ringAlpha),
                    radius = (size.minDimension - 3.dp.toPx()) / 2f,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            if (isFocused) {
                drawCircle(
                    color = t.onSurface,
                    radius = (size.minDimension - 4.dp.toPx()) / 2f,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }

        val fillColor = when (state) {
            MicUiState.LISTENING, MicUiState.IDLE -> t.primary
            MicUiState.PAUSED, MicUiState.DENIED, MicUiState.DISABLED -> t.container
        }
        val iconColor = when (state) {
            MicUiState.LISTENING, MicUiState.IDLE -> t.onPrimary
            MicUiState.PAUSED -> t.onSurface
            MicUiState.DENIED -> t.warning
            MicUiState.DISABLED -> t.mutedText
        }
        val borderColor = when (state) {
            MicUiState.LISTENING, MicUiState.IDLE -> t.primary
            MicUiState.PAUSED -> t.outline
            MicUiState.DENIED -> t.warning
            MicUiState.DISABLED -> t.outline
        }
        val isDashed = state == MicUiState.DENIED || state == MicUiState.DISABLED

        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(fillColor)
                .focusable(interactionSource = interactionSource)
                .clickable(
                    enabled = state != MicUiState.DISABLED,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    role = Role.Button
                    stateDescription = stateDesc
                    contentDescription = contentDesc
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(buttonSize)) {
                val strokePx = t.strokeWidth.toPx()
                drawCircle(
                    color = borderColor,
                    radius = (size.minDimension - strokePx) / 2f,
                    style = Stroke(
                        width = strokePx,
                        pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                    ),
                )
            }
            Canvas(modifier = Modifier.size(56.dp)) {
                drawMicGlyph(color = iconColor, slashed = state == MicUiState.DENIED)
            }
        }
    }
}

/**
 * Thinking dots (1e): three dots fade in turn, 3 s cycle, 0.5 s offset. Static when reducedMotion.
 */
@Composable
fun ThinkingDots(
    reducedMotion: Boolean = LocalAccessAi.current.reducedMotion,
    color: Color = LocalAccessAi.current.primary,
    modifier: Modifier = Modifier,
) {
    val alphas = if (reducedMotion) {
        listOf(1f, 0.7f, 0.45f)
    } else {
        val transition = rememberInfiniteTransition(label = "thinkingDots")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 3000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000),
                repeatMode = RepeatMode.Restart,
            ),
            label = "thinkingPhase",
        )
        listOf(0f, 500f, 1000f).map { offset ->
            val local = ((phase - offset + 3000f) % 3000f) / 1500f
            if (local <= 1f) 0.25f + 0.75f * kotlin.math.sin(local * Math.PI).toFloat() else 0.25f
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        alphas.forEach { a ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = a)),
            )
        }
    }
}

/**
 * TranscriptView (1g / 1h):
 * - Full width, min height 72dp, text 22sp, 14dp padding, 4dp between label and text, 16dp radius.
 * - Partial results are silent to TalkBack; final text uses Polite live region.
 */
@Composable
fun TranscriptView(
    label: String,
    text: String,
    isFinal: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalAccessAi.current
    val semanticsModifier = if (isFinal && text.isNotBlank()) {
        Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$label: $text"
        }
    } else {
        Modifier.semantics(mergeDescendants = true) {
            if (text.isNotBlank()) contentDescription = "$label: $text"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .then(semanticsModifier),
        color = t.container,
        shape = RoundedCornerShape(t.cardRadius),
        border = BorderStroke(t.strokeWidth, t.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = AccessAiTypography.caption(t),
                color = t.mutedText,
            )
            Text(
                text = text.ifBlank { "…" },
                style = AccessAiTypography.title(t).copy(fontWeight = FontWeight.Normal),
                color = if (text.isBlank()) t.mutedText else t.onSurface,
            )
        }
    }
}

/**
 * ConfirmationCard (1g / 1h / 1d):
 * - Amount 56sp bold (capped at 76sp at 200%), Recipient 32sp bold, Account 22sp.
 * - 18dp padding, 4dp between rows, 16dp to countdown, 3dp outline.
 * - Summary is merged into one TalkBack node; countdown is a progressbar with bold text <= 5s.
 */
@Composable
fun ConfirmationCard(
    amountDisplay: String,
    spokenAmount: String,
    recipientName: String,
    accountLast4: String,
    fromAccount: String,
    feeDisplay: String,
    secondsLeft: Int,
    totalSeconds: Int = 30,
    modifier: Modifier = Modifier,
) {
    val t = LocalAccessAi.current
    val spokenLast4 = accountLast4.toList().joinToString(" ")
    val summaryContentDescription =
        "Send $spokenAmount to $recipientName, account ending $spokenLast4. From $fromAccount. Fee $feeDisplay."
    val progress = if (totalSeconds > 0) (secondsLeft.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val expiring = secondsLeft <= 5

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = t.container,
        shape = RoundedCornerShape(t.cardRadius),
        border = BorderStroke(3.dp, t.outline),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = summaryContentDescription
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Send",
                    style = AccessAiTypography.caption(t),
                    color = t.mutedText,
                )
                Text(
                    text = amountDisplay,
                    style = AccessAiTypography.display(t),
                    color = t.onSurface,
                )
                Text(
                    text = "To",
                    style = AccessAiTypography.caption(t),
                    color = t.mutedText,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = recipientName,
                    style = AccessAiTypography.recipient(t),
                    color = t.onSurface,
                )
                if (accountLast4.isNotBlank()) {
                    Text(
                        text = "Account ending $accountLast4",
                        style = AccessAiTypography.title(t).copy(fontWeight = FontWeight.Normal),
                        color = t.mutedText,
                    )
                }
                Text(
                    text = "From $fromAccount · Fee $feeDisplay",
                    style = AccessAiTypography.body(t),
                    color = t.mutedText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(Space.block))

            // Countdown bar: steps once per second, never blinks (1e / 1g / 1h)
            val countdownLabel = "Cancels in $secondsLeft ${if (secondsLeft == 1) "second" else "seconds"}"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = countdownLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = secondsLeft.toFloat(),
                            range = 0f..totalSeconds.toFloat(),
                            steps = totalSeconds,
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.outline.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (expiring) t.critical else t.primary),
                    )
                }
                Text(
                    text = countdownLabel,
                    style = AccessAiTypography.body(t).copy(
                        fontWeight = if (expiring) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (expiring) t.critical else t.onSurface,
                )
                Text(
                    text = "If you do nothing, nothing is sent.",
                    style = AccessAiTypography.body(t),
                    color = t.mutedText,
                )
            }
        }
    }
}

enum class ActionVariant { PRIMARY, SECONDARY, TERTIARY }

enum class ActionIcon { MIC, SEND, CHECK, CLOSE, REFRESH, HELP, KEYBOARD, SETTINGS, VOLUME }

/**
 * ActionButton (1g):
 * - Min 64dp high (72dp for Confirm / Cancel / entry), full width, 18sp bold, pill shape.
 * - Focused: 4dp outline in onSurface, 3dp offset.
 * - Disabled: dashed border + "unavailable" in contentDescription.
 */
@Composable
fun ActionButton(
    label: String,
    variant: ActionVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ActionIcon? = null,
    tall: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentDescriptionOverride: String? = null,
) {
    val t = LocalAccessAi.current
    val minHeight = if (tall) Size.confirm else Size.button
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveEnabled = enabled && !loading

    val bgColor = if (!effectiveEnabled) {
        t.container
    } else {
        when (variant) {
            ActionVariant.PRIMARY -> if (isPressed) t.primary.copy(alpha = 0.88f) else t.primary
            ActionVariant.SECONDARY -> if (isPressed) t.container else t.surface
            ActionVariant.TERTIARY -> if (isPressed) t.container else Color.Transparent
        }
    }
    val fgColor = if (!effectiveEnabled) {
        t.mutedText
    } else {
        when (variant) {
            ActionVariant.PRIMARY -> t.onPrimary
            ActionVariant.SECONDARY, ActionVariant.TERTIARY -> t.onSurface
        }
    }
    val borderColor = if (!effectiveEnabled) {
        t.outline
    } else {
        when (variant) {
            ActionVariant.PRIMARY -> t.primary
            ActionVariant.SECONDARY -> t.outline
            ActionVariant.TERTIARY -> Color.Transparent
        }
    }

    val a11yDesc = buildString {
        append(contentDescriptionOverride ?: label)
        if (!effectiveEnabled && !loading) append(". Unavailable")
        if (loading) append(". Loading")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.padding(3.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(bgColor)
                .focusable(interactionSource = interactionSource)
                .clickable(
                    enabled = effectiveEnabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = a11yDesc
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = if (isFocused) 4.dp.toPx() else t.strokeWidth.toPx()
                val radius = size.height / 2f
                val drawBorderColor = if (isFocused) t.onSurface else borderColor
                if (drawBorderColor != Color.Transparent) {
                    drawRoundRect(
                        color = drawBorderColor,
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(
                            width = stroke,
                            pathEffect = if (!effectiveEnabled) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.icon, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        drawActionGlyph(icon, fgColor)
                    }
                }
                Text(
                    text = if (loading) "Working…" else label,
                    style = AccessAiTypography.label(t),
                    color = fgColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

enum class BannerKind { INFO, SUCCESS, ERROR, WARNING }

/**
 * StatusBanner (1g / 1i):
 * - Icon shape + word + sentence together, never colour alone.
 * - Full width, icon 28dp, label 18sp bold, border 2dp (3dp HC), 14dp padding, 12dp icon gap.
 * - Body text is always set in `onSurface` for maximum contrast (1i).
 */
@Composable
fun StatusBanner(
    kind: BannerKind,
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalAccessAi.current
    val statusColor = when (kind) {
        BannerKind.INFO -> t.primary
        BannerKind.SUCCESS -> t.positive
        BannerKind.ERROR -> t.critical
        BannerKind.WARNING -> t.warning
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $text"
            },
        color = t.container,
        shape = RoundedCornerShape(t.cardRadius),
        border = BorderStroke(t.strokeWidth, statusColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Canvas(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(28.dp),
            ) {
                drawStatusGlyph(kind, statusColor)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = AccessAiTypography.label(t),
                    color = statusColor,
                )
                Text(
                    text = text,
                    style = AccessAiTypography.body(t),
                    color = t.onSurface,
                )
            }
        }
    }
}

private fun DrawScope.drawMicGlyph(color: Color, slashed: Boolean) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.08f

    // Capsule body
    val capW = w * 0.30f
    val capH = h * 0.44f
    val capLeft = (w - capW) / 2f
    val capTop = h * 0.14f
    drawRoundRect(
        color = color,
        topLeft = Offset(capLeft, capTop),
        size = ComposeSize(capW, capH),
        cornerRadius = CornerRadius(capW / 2f, capW / 2f),
        style = Stroke(width = stroke),
    )

    // U-arc
    val arcW = w * 0.54f
    val arcH = h * 0.42f
    val arcLeft = (w - arcW) / 2f
    val arcTop = h * 0.30f
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(arcLeft, arcTop),
        size = ComposeSize(arcW, arcH),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )

    // Stem & base
    drawLine(
        color = color,
        start = Offset(w * 0.5f, h * 0.72f),
        end = Offset(w * 0.5f, h * 0.88f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(w * 0.34f, h * 0.88f),
        end = Offset(w * 0.66f, h * 0.88f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )

    if (slashed) {
        drawLine(
            color = color,
            start = Offset(w * 0.16f, h * 0.84f),
            end = Offset(w * 0.84f, h * 0.16f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawStatusGlyph(kind: BannerKind, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.10f
    when (kind) {
        BannerKind.INFO -> {
            drawCircle(color = color, radius = w * 0.42f, style = Stroke(width = stroke))
            drawCircle(color = color, radius = stroke * 0.55f, center = Offset(w * 0.5f, h * 0.32f))
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.46f),
                end = Offset(w * 0.5f, h * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        BannerKind.SUCCESS -> {
            drawCircle(color = color, radius = w * 0.42f, style = Stroke(width = stroke))
            val path = Path().apply {
                moveTo(w * 0.30f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.66f)
                lineTo(w * 0.71f, h * 0.37f)
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        BannerKind.ERROR -> {
            val d = w * 0.28f
            val m = w * 0.08f
            val path = Path().apply {
                moveTo(m + d, m)
                lineTo(w - m - d, m)
                lineTo(w - m, m + d)
                lineTo(w - m, h - m - d)
                lineTo(w - m - d, h - m)
                lineTo(m + d, h - m)
                lineTo(m, h - m - d)
                lineTo(m, m + d)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color = color, start = Offset(w * 0.35f, h * 0.35f), end = Offset(w * 0.65f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color = color, start = Offset(w * 0.65f, h * 0.35f), end = Offset(w * 0.35f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        BannerKind.WARNING -> {
            val path = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.90f, h * 0.84f)
                lineTo(w * 0.10f, h * 0.84f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color = color, start = Offset(w * 0.50f, h * 0.36f), end = Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color = color, radius = stroke * 0.52f, center = Offset(w * 0.50f, h * 0.71f))
        }
    }
}

private fun DrawScope.drawActionGlyph(icon: ActionIcon, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.11f
    when (icon) {
        ActionIcon.MIC -> drawMicGlyph(color = color, slashed = false)
        ActionIcon.CHECK -> {
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.54f)
                lineTo(w * 0.42f, h * 0.76f)
                lineTo(w * 0.84f, h * 0.28f)
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        ActionIcon.CLOSE -> {
            drawLine(color = color, start = Offset(w * 0.24f, h * 0.24f), end = Offset(w * 0.76f, h * 0.76f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color = color, start = Offset(w * 0.76f, h * 0.24f), end = Offset(w * 0.24f, h * 0.76f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        ActionIcon.SEND -> {
            val path = Path().apply {
                moveTo(w * 0.14f, h * 0.20f)
                lineTo(w * 0.88f, h * 0.50f)
                lineTo(w * 0.14f, h * 0.80f)
                lineTo(w * 0.28f, h * 0.50f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
        ActionIcon.REFRESH -> {
            drawArc(
                color = color,
                startAngle = 35f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(w * 0.16f, h * 0.16f),
                size = ComposeSize(w * 0.68f, h * 0.68f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(color = color, start = Offset(w * 0.70f, h * 0.14f), end = Offset(w * 0.84f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        ActionIcon.HELP -> {
            drawCircle(color = color, radius = w * 0.40f, style = Stroke(width = stroke))
            drawArc(
                color = color,
                startAngle = 195f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(w * 0.34f, h * 0.26f),
                size = ComposeSize(w * 0.32f, h * 0.26f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(color = color, radius = stroke * 0.5f, center = Offset(w * 0.50f, h * 0.72f))
        }
        ActionIcon.KEYBOARD -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, h * 0.24f),
                size = ComposeSize(w * 0.76f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                style = Stroke(width = stroke),
            )
            drawLine(color = color, start = Offset(w * 0.28f, h * 0.60f), end = Offset(w * 0.72f, h * 0.60f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        ActionIcon.SETTINGS -> {
            drawCircle(color = color, radius = w * 0.20f, style = Stroke(width = stroke))
            drawCircle(color = color, radius = w * 0.38f, style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        ActionIcon.VOLUME -> {
            val path = Path().apply {
                moveTo(w * 0.16f, h * 0.38f)
                lineTo(w * 0.36f, h * 0.38f)
                lineTo(w * 0.58f, h * 0.20f)
                lineTo(w * 0.58f, h * 0.80f)
                lineTo(w * 0.36f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.62f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.56f, h * 0.30f),
                size = ComposeSize(w * 0.26f, h * 0.40f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
