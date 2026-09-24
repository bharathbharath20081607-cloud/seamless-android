package com.seamless.android.ui.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Contextual Reaction Animations
//
// Different animated effects based on the emoji used to react on a message.
// Each animation type creates a unique full-screen particle effect.
// ─────────────────────────────────────────────────────────────────────────────

/** State holder for reaction animations */
class ReactionAnimationState {
    private val _activeReaction = mutableStateOf<ReactionEffect?>(null)
    val activeReaction: State<ReactionEffect?> = _activeReaction

    fun trigger(emoji: String) {
        _activeReaction.value = ReactionEffect(emoji = emoji, id = System.nanoTime())
    }

    fun clear() {
        _activeReaction.value = null
    }
}

data class ReactionEffect(val emoji: String, val id: Long)

@Composable
fun rememberReactionAnimationState(): ReactionAnimationState {
    return remember { ReactionAnimationState() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Dispatcher — routes emoji to the right animation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReactionAnimationOverlay(
    effect: ReactionEffect?,
    onComplete: () -> Unit
) {
    if (effect == null) return

    when {
        effect.emoji.contains("❤") || effect.emoji.contains("💕") || effect.emoji.contains("💗") ->
            HeartsFloatAnimation(onComplete = onComplete)
        effect.emoji.contains("🔥") || effect.emoji.contains("💥") ->
            FireBurstAnimation(onComplete = onComplete)
        effect.emoji.contains("😂") || effect.emoji.contains("🤣") ->
            LaughBounceAnimation(onComplete = onComplete)
        effect.emoji.contains("😮") || effect.emoji.contains("😱") || effect.emoji.contains("🤯") ->
            ExplosionBurstAnimation(onComplete = onComplete)
        effect.emoji.contains("👍") ->
            ThumbsUpBounceAnimation(onComplete = onComplete)
        effect.emoji.contains("😢") || effect.emoji.contains("😭") ->
            TearDropAnimation(onComplete = onComplete)
        else ->
            // Fallback: use existing EmojiRainEffect style
            GenericEmojiAnimation(emoji = effect.emoji, onComplete = onComplete)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ❤️ Hearts Float — Hearts rise upward with rotation and sway
// ─────────────────────────────────────────────────────────────────────────────

private data class HeartParticle(
    val startX: Float,
    val size: Float,
    val speed: Float,
    val swayAmplitude: Float,
    val swayPhase: Float,
    val rotation: Float,
    val delay: Float,
    val alpha: Float
)

@Composable
private fun HeartsFloatAnimation(
    particleCount: Int = 30,
    durationMs: Int = 3500,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val hearts = listOf("❤️", "💕", "💗", "💖", "💓", "💝")
    
    val particles = remember {
        List(particleCount) {
            HeartParticle(
                startX = Random.nextFloat(),
                size = Random.nextFloat() * 20f + 16f,
                speed = Random.nextFloat() * 0.4f + 0.8f,
                swayAmplitude = Random.nextFloat() * 60f + 20f,
                swayPhase = Random.nextFloat() * PI.toFloat() * 2f,
                rotation = Random.nextFloat() * 40f - 20f,
                delay = Random.nextFloat() * 0.3f,
                alpha = Random.nextFloat() * 0.4f + 0.6f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(durationMs, easing = LinearEasing)
        )
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEachIndexed { i, p ->
            val adjusted = ((progress.value - p.delay).coerceIn(0f, 1f)) / (1f - p.delay)
            if (adjusted <= 0f) return@forEachIndexed

            val x = p.startX * w + sin(adjusted * 4f + p.swayPhase) * p.swayAmplitude
            val y = h - (adjusted * p.speed * (h + 200f))
            
            val fadeIn = if (adjusted < 0.1f) adjusted / 0.1f else 1f
            val fadeOut = if (adjusted > 0.7f) 1f - ((adjusted - 0.7f) / 0.3f) else 1f
            val alpha = fadeIn * fadeOut * p.alpha

            if (alpha > 0f && y > -100f) {
                val heart = hearts[i % hearts.size]
                val textLayout = textMeasurer.measure(
                    AnnotatedString(heart),
                    style = TextStyle(fontSize = p.size.sp)
                )
                
                rotate(degrees = p.rotation * sin(adjusted * 6f), pivot = Offset(x, y)) {
                    scale(scale = 1f, pivot = Offset(x, y)) {
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(x - textLayout.size.width / 2f, y - textLayout.size.height / 2f),
                            alpha = alpha
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 🔥 Fire Burst — Particles burst outward from center then fall
// ─────────────────────────────────────────────────────────────────────────────

private data class FireParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val rotSpeed: Float,
    val gravity: Float
)

@Composable
private fun FireBurstAnimation(
    particleCount: Int = 35,
    durationMs: Int = 2800,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val fires = listOf("🔥", "💥", "✨", "🔥", "⚡")

    val particles = remember {
        List(particleCount) {
            FireParticle(
                angle = Random.nextFloat() * 360f,
                speed = Random.nextFloat() * 400f + 200f,
                size = Random.nextFloat() * 18f + 14f,
                rotSpeed = Random.nextFloat() * 720f - 360f,
                gravity = Random.nextFloat() * 300f + 100f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(durationMs, easing = LinearEasing)
        )
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.45f

        particles.forEachIndexed { i, p ->
            val t = progress.value
            val rad = Math.toRadians(p.angle.toDouble()).toFloat()
            
            val x = cx + cos(rad) * p.speed * t
            val y = cy + sin(rad) * p.speed * t + p.gravity * t * t
            
            val fadeOut = if (t > 0.6f) 1f - ((t - 0.6f) / 0.4f) else 1f
            val scaleDown = if (t > 0.5f) 1f - ((t - 0.5f) / 0.5f) * 0.5f else 1f

            if (fadeOut > 0f) {
                val fire = fires[i % fires.size]
                val textLayout = textMeasurer.measure(
                    AnnotatedString(fire),
                    style = TextStyle(fontSize = (p.size * scaleDown).sp)
                )
                
                rotate(degrees = p.rotSpeed * t, pivot = Offset(x, y)) {
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(x - textLayout.size.width / 2f, y - textLayout.size.height / 2f),
                        alpha = fadeOut
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 😂 Laugh Bounce — Emojis bounce around the screen
// ─────────────────────────────────────────────────────────────────────────────

private data class BounceParticle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val rotation: Float
)

@Composable
private fun LaughBounceAnimation(
    particleCount: Int = 20,
    durationMs: Int = 3000,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val laughs = listOf("😂", "🤣", "😆", "😂", "😹")

    val particles = remember {
        List(particleCount) {
            BounceParticle(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.6f + 0.2f,
                velocityX = Random.nextFloat() * 300f - 150f,
                velocityY = -(Random.nextFloat() * 500f + 300f),
                size = Random.nextFloat() * 16f + 18f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(durationMs, easing = LinearEasing)
        )
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEachIndexed { i, p ->
            val t = progress.value
            val gravity = 800f
            val x = p.startX * w + p.velocityX * t
            val y = p.startY * h + p.velocityY * t + 0.5f * gravity * t * t
            
            val fadeOut = if (t > 0.7f) 1f - ((t - 0.7f) / 0.3f) else 1f
            val bounceScale = 1f + 0.15f * sin(t * 12f).absoluteValue

            if (fadeOut > 0f && y < h + 100f) {
                val laugh = laughs[i % laughs.size]
                val textLayout = textMeasurer.measure(
                    AnnotatedString(laugh),
                    style = TextStyle(fontSize = (p.size * bounceScale).sp)
                )
                
                rotate(degrees = p.rotation + t * 180f, pivot = Offset(x, y)) {
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(x - textLayout.size.width / 2f, y - textLayout.size.height / 2f),
                        alpha = fadeOut
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 😮 Explosion Burst — Radial burst outward from center
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExplosionBurstAnimation(
    durationMs: Int = 2500,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val emojis = listOf("😮", "💫", "⭐", "✨", "💥", "🤯", "😱")

    val particles = remember {
        List(28) { i ->
            val angle = (i * 360f / 28f) + Random.nextFloat() * 10f
            Triple(angle, Random.nextFloat() * 300f + 250f, Random.nextFloat() * 14f + 16f)
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.4f

        particles.forEachIndexed { i, (angle, dist, fontSize) ->
            val t = progress.value
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val eased = t * t * (3f - 2f * t) // smooth step

            val x = cx + cos(rad) * dist * eased
            val y = cy + sin(rad) * dist * eased
            
            val alpha = if (t > 0.6f) 1f - ((t - 0.6f) / 0.4f) else 1f
            val s = if (t < 0.2f) t / 0.2f else 1f

            if (alpha > 0f) {
                val emoji = emojis[i % emojis.size]
                val textLayout = textMeasurer.measure(
                    AnnotatedString(emoji),
                    style = TextStyle(fontSize = (fontSize * s).sp)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(x - textLayout.size.width / 2f, y - textLayout.size.height / 2f),
                    alpha = alpha
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 👍 Thumbs Up — Giant emoji scales up with bounce then fades
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThumbsUpBounceAnimation(
    durationMs: Int = 2000,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        
        // Bouncy scale: starts at 0, overshoots to 1.3, settles at 1.0, then shrinks
        val scalePhase = when {
            t < 0.15f -> t / 0.15f * 1.4f        // Scale up fast (overshoot)
            t < 0.3f -> 1.4f - ((t - 0.15f) / 0.15f) * 0.4f  // Settle to 1.0
            t < 0.7f -> 1.0f                        // Hold
            else -> 1.0f - ((t - 0.7f) / 0.3f)     // Shrink out
        }
        
        val alpha = if (t > 0.75f) 1f - ((t - 0.75f) / 0.25f) else 1f
        val rotation = sin(t * 8f) * 15f * (1f - t) // Wobble that dampens
        
        if (alpha > 0f && scalePhase > 0f) {
            val fontSize = 80f * scalePhase
            val textLayout = textMeasurer.measure(
                AnnotatedString("👍"),
                style = TextStyle(fontSize = fontSize.sp)
            )
            
            val cx = size.width / 2f
            val cy = size.height * 0.4f
            
            rotate(degrees = rotation, pivot = Offset(cx, cy)) {
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(cx - textLayout.size.width / 2f, cy - textLayout.size.height / 2f),
                    alpha = alpha
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 😢 Tear Drops — Tears rain from top
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TearDropAnimation(
    particleCount: Int = 25,
    durationMs: Int = 3000,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val tears = listOf("😢", "😿", "💧", "😥", "🥲")

    val particles = remember {
        List(particleCount) {
            Triple(
                Random.nextFloat(),            // x position
                Random.nextFloat() * 0.3f,     // delay
                Random.nextFloat() * 12f + 14f // size
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEachIndexed { i, (startX, delayFrac, fontSize) ->
            val t = progress.value
            val adjusted = ((t - delayFrac) / (1f - delayFrac)).coerceIn(0f, 1f)
            if (adjusted <= 0f) return@forEachIndexed

            val x = startX * w + sin(adjusted * 3f + i * 0.7f) * 20f
            val y = -50f + adjusted * adjusted * (h + 150f) // Accelerating fall
            
            val alpha = if (adjusted > 0.8f) 1f - ((adjusted - 0.8f) / 0.2f) else 1f

            if (alpha > 0f) {
                val tear = tears[i % tears.size]
                val textLayout = textMeasurer.measure(
                    AnnotatedString(tear),
                    style = TextStyle(fontSize = fontSize.sp)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(x - textLayout.size.width / 2f, y),
                    alpha = alpha
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic Fallback — Emoji cascade with rotation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GenericEmojiAnimation(
    emoji: String,
    particleCount: Int = 25,
    durationMs: Int = 3000,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    val particles = remember {
        List(particleCount) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat() * 0.3f,
                Random.nextFloat() * 14f + 16f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        particles.forEachIndexed { i, (startX, delayFrac, fontSize) ->
            val t = progress.value
            val adjusted = ((t - delayFrac) / (1f - delayFrac)).coerceIn(0f, 1f)
            if (adjusted <= 0f) return@forEachIndexed

            val x = startX * w + sin(adjusted * 5f + i.toFloat()) * 40f
            val y = -80f + adjusted * (h + 200f)
            val alpha = if (adjusted > 0.8f) 1f - ((adjusted - 0.8f) / 0.2f) else 1f

            if (alpha > 0f) {
                val textLayout = textMeasurer.measure(
                    AnnotatedString(emoji),
                    style = TextStyle(fontSize = fontSize.sp)
                )
                rotate(degrees = adjusted * 360f * (if (i % 2 == 0) 1f else -1f), pivot = Offset(x, y)) {
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(x - textLayout.size.width / 2f, y - textLayout.size.height / 2f),
                        alpha = alpha
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// graphicsLayer import helper (used by EmojiReactionPicker)
// ─────────────────────────────────────────────────────────────────────────────
// Note: graphicsLayer is from androidx.compose.ui.graphics.graphicsLayer
