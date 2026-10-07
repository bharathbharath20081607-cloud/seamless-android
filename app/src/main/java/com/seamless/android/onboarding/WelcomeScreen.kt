package com.seamless.android.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.rememberDimensions
import com.seamless.android.ui.theme.safeAreaPadding
import com.seamless.android.ui.theme.safeBottomPadding
import com.seamless.android.ui.theme.responsiveSize
import kotlinx.coroutines.delay
import com.seamless.android.ui.theme.SeamlessCyan

/**
 * Welcome screen that introduces users to Seamless
 * Shows key features and value proposition before permissions
 */

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAnimating by remember { mutableStateOf(false) }
    val dimensions = rememberDimensions()
    
    LaunchedEffect(Unit) {
        delay(300)
        isAnimating = true
    }
    
    val haptic = LocalHapticFeedback.current
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessLow
        ),
        label = "btn_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Animated mesh network background
        AnimatedMeshBackground(
            modifier = Modifier.fillMaxSize(),
            isAnimating = isAnimating
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeAreaPadding()
                .padding(horizontal = dimensions.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Hero section with animated logo
            AnimatedLogo(isAnimating = isAnimating)
            
            // App name and tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Seamless",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Chat without servers",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB0B0B8)
                )
            }
            
            // Feature cards
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                FeatureCard(
                    icon = Icons.Outlined.BluetoothConnected,
                    title = "Works Offline",
                    description = "Messages relay through nearby devices",
                    isAnimating = isAnimating,
                    delay = 0
                )
                FeatureCard(
                    icon = Icons.Outlined.Lock,
                    title = "End-to-End Encrypted",
                    description = "Your conversations stay private",
                    isAnimating = isAnimating,
                    delay = 80
                )
                FeatureCard(
                    icon = Icons.Outlined.PersonOff,
                    title = "No Sign-Up Required",
                    description = "No phone number, email, or tracking",
                    isAnimating = isAnimating,
                    delay = 160
                )
            }
            
            // Call to action button: Solid Optical White Pill CTA
            AnimatedVisibility(
                visible = isAnimating,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 400)) +
                       slideInVertically(
                           animationSpec = spring(
                               dampingRatio = 0.86f,
                               stiffness = Spring.StiffnessMediumLow
                           ),
                           initialOffsetY = { it / 3 }
                       )
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onGetStarted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.buttonHeight)
                        .scale(buttonScale),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    ),
                    interactionSource = buttonInteractionSource
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedLogo(
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "logo_breath")
    val breathPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathPulse"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = modifier
            .size(130.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Optical Light breathing glow halo
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(breathPulse)
                .alpha(haloAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Specular outer hairline ring
        Box(
            modifier = Modifier
                .size(100.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Obsidian & Titanium Emblem Medallion
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = Color(0xFF101014),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.16f)
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Hub,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    isAnimating: Boolean,
    delay: Int,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(500, delayMillis = 200 + delay),
        label = "featureAlpha"
    )
    
    val offsetY by animateDpAsState(
        targetValue = if (isAnimating) 0.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "featureOffset"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(y = offsetY)
            .background(
                color = Color(0xFF0F0F13).copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1A1A20),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.10f)
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = Color(0xFF9898A2)
            )
        }
    }
}

@Composable
fun AnimatedMeshBackground(
    modifier: Modifier = Modifier,
    isAnimating: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshOffset"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val width = size.width
        val height = size.height
        
        // Deep ambient radial illumination centered near top-third
        val ambientBrush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color(0xFF0F0F16).copy(alpha = 0.03f),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, height * 0.28f),
            radius = width * 0.85f
        )
        drawRect(brush = ambientBrush)

        if (isAnimating) {
            val nodeCount = 9
            val nodeRadius = 2.5.dp.toPx()
            
            // Draw ethereal filaments connecting nodes
            for (i in 0 until nodeCount) {
                for (j in i + 1 until nodeCount) {
                    val angle1 = i * 0.72f + animatedOffset * 2 * kotlin.math.PI.toFloat()
                    val angle2 = j * 0.72f + animatedOffset * 2 * kotlin.math.PI.toFloat()
                    
                    val x1 = (width * (0.15f + 0.70f * (0.5f + 0.5f * kotlin.math.cos(angle1).toFloat())))
                    val y1 = (height * (0.10f + 0.75f * (0.5f + 0.5f * kotlin.math.sin(angle1 * 1.3f).toFloat())))
                    val x2 = (width * (0.15f + 0.70f * (0.5f + 0.5f * kotlin.math.cos(angle2).toFloat())))
                    val y2 = (height * (0.10f + 0.75f * (0.5f + 0.5f * kotlin.math.sin(angle2 * 1.3f).toFloat())))
                    
                    val distance = kotlin.math.sqrt(
                        (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
                    )
                    
                    val maxDistance = width * 0.45f
                    if (distance < maxDistance) {
                        val fade = (1f - distance / maxDistance)
                        drawLine(
                            color = Color.White,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.dp.toPx(),
                            alpha = fade * 0.12f
                        )
                    }
                }
                
                // Draw luxury celestial node
                val angle = i * 0.72f + animatedOffset * 2 * kotlin.math.PI.toFloat()
                val x = (width * (0.15f + 0.70f * (0.5f + 0.5f * kotlin.math.cos(angle).toFloat())))
                val y = (height * (0.10f + 0.75f * (0.5f + 0.5f * kotlin.math.sin(angle * 1.3f).toFloat())))
                
                // Subtle node glow
                drawCircle(
                    color = Color.White,
                    radius = nodeRadius * 2.2f,
                    center = Offset(x, y),
                    alpha = 0.08f
                )
                // Crisp core
                drawCircle(
                    color = Color.White,
                    radius = nodeRadius,
                    center = Offset(x, y),
                    alpha = 0.65f
                )
            }
        }
    }
}
