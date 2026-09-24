package com.seamless.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Emoji Reaction Picker — WhatsApp/Instagram-style floating emoji bar
//
// Appears above a message on long-press. User taps an emoji to react.
// Features spring scale-in animation with staggered emoji entrances.
// ─────────────────────────────────────────────────────────────────────────────

/** The 6 quick-react emojis shown in the picker */
val QUICK_REACT_EMOJIS = listOf("❤️", "😂", "😮", "🔥", "👍", "😢")

@Composable
fun EmojiReactionPicker(
    isVisible: Boolean,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val haptic = LocalHapticFeedback.current

    // Overall picker entrance animation
    val pickerScale = remember { Animatable(0f) }
    val pickerAlpha = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            launch {
                pickerAlpha.animateTo(
                    1f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                )
            }
            pickerScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .scale(pickerScale.value)
            .graphicsLayer { alpha = pickerAlpha.value },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
            shadowElevation = 12.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QUICK_REACT_EMOJIS.forEachIndexed { index, emoji ->
                    EmojiReactionButton(
                        emoji = emoji,
                        delayMs = index * 40,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEmojiSelected(emoji)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiReactionButton(
    emoji: String,
    delayMs: Int,
    onClick: () -> Unit
) {
    // Staggered entrance per emoji
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale.value * pressScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            }
            .background(
                color = if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 26.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reaction Badges — Small pills below message bubbles showing reactions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReactionBadges(
    reactions: Map<String, Int>, // emoji -> count
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return

    // Entrance animation
    val badgeScale = remember { Animatable(0f) }
    LaunchedEffect(reactions.size) {
        badgeScale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Row(
        modifier = modifier
            .scale(badgeScale.value)
            .offset(y = (-4).dp) // Overlap slightly with bubble
            .padding(horizontal = if (isSelf) 8.dp else 48.dp),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 2.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                reactions.forEach { (emoji, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 14.sp
                        )
                        if (count > 1) {
                            Text(
                                text = "$count",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
