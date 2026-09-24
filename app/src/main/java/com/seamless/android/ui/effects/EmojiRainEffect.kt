package com.seamless.android.ui.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Emoji Rain Effect — A beautiful particle animation that rains emojis across the chat screen.
 * 
 * When a user sends an emoji-only message, the emoji rains down with:
 *  - Smooth fade-in/out
 *  - Gentle horizontal sway  
 *  - Random sizes and speeds
 *  - Staggered particle spawning
 * 
 * Works across all chat modes: public, private, mesh, Tor.
 */

private data class EmojiParticle(
    val emoji: String,
    val startX: Float,       // 0..1 normalized
    val terminalVelocity: Float, // Speed factor
    val size: Float,         // base font size multiplier
    val swayAmplitude: Float,// horizontal sway amount
    val swayPhase: Float,    // Starting phase for organic sway
    val swayFrequency: Float,// sway speed
    val delay: Long,         // spawn delay ms
    val baseRotation: Float, // Initial rotation
    val rotationSpeed: Float,// Continuous rotation speed
    val zDepth: Float        // 0f (back) to 1f (front) for parallax and scaling
)

/**
 * Detects if a string contains only emoji characters (no text)
 */
fun isEmojiOnly(text: String): Boolean {
    if (text.isBlank()) return false
    val stripped = text.trim()
    // Match sequences of emoji codepoints (including modifiers, ZWJ sequences, flags)
    val emojiRegex = Regex(
        "^([\\x{1F600}-\\x{1F64F}]|[\\x{1F300}-\\x{1F5FF}]|[\\x{1F680}-\\x{1F6FF}]|" +
        "[\\x{1F1E0}-\\x{1F1FF}]|[\\x{2702}-\\x{27B0}]|[\\x{2600}-\\x{26FF}]|" +
        "[\\x{FE00}-\\x{FE0F}]|[\\x{1F900}-\\x{1F9FF}]|[\\x{1FA00}-\\x{1FA6F}]|" +
        "[\\x{1FA70}-\\x{1FAFF}]|[\\x{200D}]|[\\x{20E3}]|[\\x{FE0F}]|" +
        "[\\x{E0020}-\\x{E007F}]|\\s)+$"
    )
    return emojiRegex.containsMatchIn(stripped) && !stripped.any { it.isLetterOrDigit() }
}

/**
 * Extracts unique emoji characters from a string
 */
fun extractEmojis(text: String): List<String> {
    val emojis = mutableListOf<String>()
    val codePoints = text.codePoints().toArray()
    var i = 0
    while (i < codePoints.size) {
        val cp = codePoints[i]
        if (Character.getType(cp) == Character.OTHER_SYMBOL.toInt() ||
            Character.getType(cp) == Character.SURROGATE.toInt() ||
            cp in 0x1F600..0x1FAFF || cp in 0x2600..0x27BF || cp in 0xFE00..0xFE0F) {
            // Build the full emoji (handle ZWJ sequences)
            val sb = StringBuilder()
            sb.appendCodePoint(cp)
            while (i + 1 < codePoints.size && (codePoints[i + 1] == 0x200D || codePoints[i + 1] == 0xFE0F)) {
                i++
                sb.appendCodePoint(codePoints[i])
                if (codePoints[i] == 0x200D && i + 1 < codePoints.size) {
                    i++
                    sb.appendCodePoint(codePoints[i])
                }
            }
            emojis.add(sb.toString())
        }
        i++
    }
    return if (emojis.isEmpty()) listOf(text.trim()) else emojis.distinct()
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun EmojiRainEffect(
    emoji: String,
    particleCount: Int = 50, // Increased for denser rain
    durationMs: Int = 4500,  // Slightly longer for graceful floating
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val emojis = remember(emoji) { extractEmojis(emoji) }
    
    // Generate particles with staggering, parallax layers, and physics properties
    val particles = remember(emoji) {
        List(particleCount) {
            val zDepth = Random.nextFloat() // Distribute in 3D "layers"
            EmojiParticle(
                emoji = emojis[it % emojis.size],
                startX = Random.nextFloat(),
                terminalVelocity = (Random.nextFloat() * 0.6f + 0.4f) + (zDepth * 0.8f), // Front particles fall much faster
                size = (Random.nextFloat() * 0.6f + 0.6f) + (zDepth * 1.5f), // Front particles are way bigger
                swayAmplitude = Random.nextFloat() * 60f + (zDepth * 40f), // Front sways more heavily
                swayPhase = Random.nextFloat() * (Math.PI.toFloat() * 2), // Organic distributed sway
                swayFrequency = Random.nextFloat() * 1.5f + 0.5f,
                delay = (it * (durationMs / particleCount / 1.5f)).toLong(),
                baseRotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() * 360f + 90f) * if (Random.nextBoolean()) 1f else -1f, // Tumbling
                zDepth = zDepth
            )
        }.sortedBy { it.zDepth } // Sort back-to-front so large foreground emojis render on top
    }
    
    val textMeasurer = rememberTextMeasurer()
    
    // Smooth animation progress from 0..1
    val animProgress = remember { Animatable(0f) }
    
    LaunchedEffect(emoji) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = LinearEasing
            )
        )
        onComplete()
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val progress = animProgress.value
        
        for (particle in particles) {
            val delayFraction = particle.delay.toFloat() / durationMs
            val adjustedProgress = ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
            
            if (adjustedProgress <= 0f) continue
            
            // Physics: Gravity simulation (quadratic easing for accelerating fall)
            val dropProgress = adjustedProgress * adjustedProgress * particle.terminalVelocity
            val y = dropProgress * (height + 250f) - 100f
            
            // X position: Starts at randomized X, applies sine wave for organic wind sway
            val baseX = particle.startX * width
            val swayX = sin(adjustedProgress * particle.swayFrequency * Math.PI.toFloat() * 2 + particle.swayPhase) * particle.swayAmplitude
            val x = baseX + swayX
            
            // Alpha: Parallax layering means background particles are slightly faded. Global smooth fade-in/out.
            val maxAlpha = 0.5f + (particle.zDepth * 0.5f) // Back ones max at 50% opacity, front at 100%
            val alpha = when {
                adjustedProgress < 0.1f -> (adjustedProgress / 0.1f) * maxAlpha
                adjustedProgress > 0.8f -> ((1f - adjustedProgress) / 0.2f) * maxAlpha
                else -> maxAlpha
            }.coerceIn(0f, 1f)
            
            // Continuous tumbling rotation
            val currentRotation = particle.baseRotation + (particle.rotationSpeed * adjustedProgress)
            
            // Font rendering and dynamic drop shadow for 3D depth on foreground particles
            val fontSize = (28 * particle.size).sp
            val shadowColor = Color.Black.copy(alpha = 0.25f * alpha)
            
            val textStyle = TextStyle(
                fontSize = fontSize,
                color = Color.White.copy(alpha = alpha),
                shadow = if (particle.zDepth > 0.6f) { // Only foreground particles cast deep shadows
                    Shadow(
                        color = shadowColor,
                        offset = Offset(0f, (12f * particle.zDepth)),
                        blurRadius = (16f * particle.zDepth)
                    )
                } else null
            )
            
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(particle.emoji),
                style = textStyle
            )
            
            val halfWidth = textLayoutResult.size.width / 2f
            val halfHeight = textLayoutResult.size.height / 2f
            
            // Draw translated and rotated
            withTransform({
                translate(left = x - halfWidth, top = y - halfHeight)
                rotate(degrees = currentRotation, pivot = Offset(halfWidth, halfHeight))
                
                // Add a slight 3D flipping/wobble effect on the X/Y axes using scaling
                val scaleWobble = 0.85f + 0.15f * kotlin.math.sin(adjustedProgress * Math.PI.toFloat() * 6 + particle.swayPhase)
                scale(scaleX = scaleWobble, scaleY = 1f, pivot = Offset(halfWidth, halfHeight))
            }) {
                drawText(textLayoutResult = textLayoutResult)
            }
        }
    }
}

/**
 * State holder for emoji rain effects in a chat screen
 */
class EmojiRainState {
    private val _activeEmoji = mutableStateOf<String?>(null)
    val activeEmoji: State<String?> = _activeEmoji
    
    fun triggerRain(emoji: String) {
        _activeEmoji.value = emoji
    }
    
    fun clear() {
        _activeEmoji.value = null
    }
}

@Composable
fun rememberEmojiRainState(): EmojiRainState {
    return remember { EmojiRainState() }
}
