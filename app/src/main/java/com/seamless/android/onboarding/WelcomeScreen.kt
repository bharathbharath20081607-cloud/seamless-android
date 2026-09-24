package com.seamless.android.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Seamless",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chat without servers",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
            
            // Feature cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 24.dp)
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
                    delay = 100
                )
                FeatureCard(
                    icon = Icons.Outlined.PersonOff,
                    title = "No Sign-Up Required",
                    description = "No phone number, email, or tracking",
                    isAnimating = isAnimating,
                    delay = 200
                )
            }
            
            // Call to action button
            AnimatedVisibility(
                visible = isAnimating,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 500)) +
                       slideInVertically(
                           animationSpec = tween(800, delayMillis = 500),
                           initialOffsetY = { it / 2 }
                       )
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.buttonHeight),
                    shape = RoundedCornerShape(dimensions.cardCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
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
        targetValue = if (isAnimating) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isAnimating) 0f else -180f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "logoRotation"
    )
    
    Box(
        modifier = modifier
            .size(120.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Hub,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .alpha(0.8f),
            tint = MaterialTheme.colorScheme.primary
        )
        
        // Pulsing ring effect
        if (isAnimating) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
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
        animationSpec = tween(600, delayMillis = 300 + delay),
        label = "featureAlpha"
    )
    
    val offsetX by animateDpAsState(
        targetValue = if (isAnimating) 0.dp else (-20).dp,
        animationSpec = tween(600, delayMillis = 300 + delay),
        label = "featureOffset"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(x = offsetX),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshOffset"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundStart)
    ) {
        val width = size.width
        val height = size.height
        
        // Premium gradient mesh background
        val brush = Brush.linearGradient(
            colors = listOf(
                com.seamless.android.ui.theme.SeamlessColors.BrandGradientStart.copy(alpha = 0.15f),
                com.seamless.android.ui.theme.SeamlessColors.BrandGradientEnd.copy(alpha = 0.05f)
            ),
            start = Offset(0f, 0f),
            end = Offset(width, height)
        )
        
        drawRect(brush = brush)

        if (isAnimating) {
            val nodeCount = 8
            val nodeRadius = 4.dp.toPx()
            
            // Draw animated mesh connections
            for (i in 0 until nodeCount) {
                for (j in i + 1 until nodeCount) {
                    val x1 = (size.width * (0.2f + 0.6f * kotlin.math.cos(i * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                    val y1 = (size.height * (0.2f + 0.6f * kotlin.math.sin(i * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                    val x2 = (size.width * (0.2f + 0.6f * kotlin.math.cos(j * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                    val y2 = (size.height * (0.2f + 0.6f * kotlin.math.sin(j * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                    
                    val distance = kotlin.math.sqrt(
                        (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
                    )
                    
                    if (distance < size.width * 0.4f) {
                        drawLine(
                            color = SeamlessCyan,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.dp.toPx(),
                            alpha = (1f - distance / (size.width * 0.4f)) * 0.5f
                        )
                    }
                }
                
                // Draw nodes
                val x = (size.width * (0.2f + 0.6f * kotlin.math.cos(i * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                val y = (size.height * (0.2f + 0.6f * kotlin.math.sin(i * 0.8f + animatedOffset * 2 * kotlin.math.PI.toFloat()).toFloat()))
                
                drawCircle(
                    color = SeamlessCyan,
                    radius = nodeRadius,
                    center = Offset(x, y),
                    alpha = 0.8f
                )
            }
        }
    }
}
