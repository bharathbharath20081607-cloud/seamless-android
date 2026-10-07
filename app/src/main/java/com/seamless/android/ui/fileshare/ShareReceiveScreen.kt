package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessTeal
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Bolt
import com.seamless.android.R
import com.seamless.android.service.HyperDropIdentity
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sin
import kotlin.random.Random

/**
 * HyperDrop - Premium file sharing main screen
 * Provides a visually stunning interface for send/receive file options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiveScreen(
    onShareThingsClick: () -> Unit,
    onReceiveThingsClick: () -> Unit,
    onBackClick: () -> Unit
) {


        // Global Animated Background
        HyperDropBackground {
            // Content
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

            // Top bar
            HyperDropTopBar(onBackClick = onBackClick)
            
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo/Icon section
                HyperDropLogo()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tagline
                Text(
                    text = stringResource(R.string.share_receive_subtitle),
                    style = HyperDropTypography.BodyLarge,
                    color = HyperDropColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(48.dp))

                // Send Button
                HyperDropActionCard(
                    title = stringResource(R.string.share_things),
                    subtitle = stringResource(R.string.share_things_desc),
                    icon = Icons.Filled.Share,
                    primaryColor = HyperDropColors.TextPrimary,
                    secondaryColor = HyperDropColors.TextSecondary,
                    onClick = onShareThingsClick
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Receive Button
                HyperDropActionCard(
                    title = stringResource(R.string.receive_things),
                    subtitle = stringResource(R.string.receive_things_desc),
                    icon = Icons.Filled.Download,
                    primaryColor = HyperDropColors.TextPrimary,
                    secondaryColor = HyperDropColors.TextSecondary,
                    onClick = onReceiveThingsClick
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Speed indicator
                SpeedIndicator()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Device Identity Card (AirDrop/Quick Share inspired)
                DeviceIdentityChip()
            }
        }
    }
}

@Composable
private fun HyperDropTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            @Suppress("DEPRECATION")
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = HyperDropColors.TextPrimary
            )
        }
    }
}

@Composable
private fun HyperDropLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")
    
    // Glow pulse animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // Outer glow ring
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        HyperDropColors.PrimaryCyan.copy(alpha = glowAlpha),
                        HyperDropColors.SecondaryTeal.copy(alpha = 0.1f),
                        HyperDropColors.PrimaryCyan.copy(alpha = glowAlpha)
                    )
                ),
                radius = size.minDimension / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }
        
        // Inner circle with icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            HyperDropColors.PrimaryCyan.copy(alpha = 0.2f),
                            HyperDropColors.SecondaryTeal.copy(alpha = 0.2f)
                        )
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = HyperDropColors.PrimaryCyan,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun HyperDropActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "card_animation")
    
    // Subtle scale pulse
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Border glow
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Pulsing radial glow behind icon
    val iconGlowRadius by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_glow"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .scale(scale),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        showShimmerBorder = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = (-HyperDropSpacing.cardPadding))
                .offset(y = (-HyperDropSpacing.cardPadding))
        ) {
            // Specular light accent
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with pulsing radial glow behind it
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    // Pulsing radial glow Canvas
                    Canvas(modifier = Modifier.size(56.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.35f * iconGlowRadius),
                                    primaryColor.copy(alpha = 0.1f * iconGlowRadius),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension / 2 * (0.8f + iconGlowRadius * 0.4f)
                            )
                        )
                    }
                    // Icon circle
                    HyperDropIconCircle(
                        icon = icon,
                        tint = primaryColor,
                        backgroundColor = primaryColor.copy(alpha = 0.15f),
                        size = 52.dp,
                        iconSize = 28.dp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = HyperDropTypography.TitleLarge
                    )
                    Text(
                        text = subtitle,
                        style = HyperDropTypography.BodyMedium.copy(
                            color = HyperDropColors.TextTertiary
                        )
                    )
                }
                
                // Arrow indicator
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = primaryColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "speed_indicator")
    
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = null,
            tint = HyperDropColors.PrimaryCyan.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            // TODO: localize "Lightning Speed"
            text = "Lightning Speed",
            style = HyperDropTypography.BodySmall.copy(
                color = HyperDropColors.TextTertiary
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        
        // Animated dots
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(HyperDropColors.PrimaryCyan.copy(alpha = dotAlpha1))
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(HyperDropColors.PrimaryCyan.copy(alpha = dotAlpha2))
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(HyperDropColors.PrimaryCyan.copy(alpha = dotAlpha3))
        )
    }
}



/**
 * Device Identity Chip - Shows the user's quirky alias and visibility status
 * Inspired by AirDrop and Quick Share's device visibility display
 * Now interactive: tap to cycle through visibility modes
 */
@Composable
private fun DeviceIdentityChip() {
    val context = LocalContext.current
    val deviceAlias = remember { HyperDropIdentity.getAlias(context) }
    var visibilityMode by remember { mutableStateOf(HyperDropIdentity.getVisibilityMode(context)) }

    // Scale animation on tap
    var tapped by remember { mutableStateOf(false) }
    val chipScale by animateFloatAsState(
        targetValue = if (tapped) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale",
        finishedListener = { tapped = false }
    )

    // TODO: localize visibility text strings
    val visibilityText = when (visibilityMode) {
        HyperDropIdentity.VisibilityMode.HIDDEN -> "Hidden"
        HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> "Contacts Only"
        HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> "Everyone (10 min)"
        HyperDropIdentity.VisibilityMode.EVERYONE -> "Everyone"
    }
    
    val visibilityColor = when (visibilityMode) {
        HyperDropIdentity.VisibilityMode.HIDDEN -> HyperDropColors.VisibilityHidden
        HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> HyperDropColors.VisibilityContacts
        HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> HyperDropColors.VisibilityEveryone
        HyperDropIdentity.VisibilityMode.EVERYONE -> HyperDropColors.VisibilityEveryone
    }

    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier
            .scale(chipScale)
            .clip(RoundedCornerShape(24.dp))
            .background(HyperDropColors.CardSurface.copy(alpha = 0.85f))
            .border(1.dp, HyperDropColors.BorderSubtle, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tapped = true
                // Cycle: HIDDEN → CONTACTS_ONLY → EVERYONE_10_MIN → EVERYONE → HIDDEN
                val nextMode = when (visibilityMode) {
                    HyperDropIdentity.VisibilityMode.HIDDEN -> HyperDropIdentity.VisibilityMode.CONTACTS_ONLY
                    HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN
                    HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> HyperDropIdentity.VisibilityMode.EVERYONE
                    HyperDropIdentity.VisibilityMode.EVERYONE -> HyperDropIdentity.VisibilityMode.HIDDEN
                }
                HyperDropIdentity.setVisibilityMode(context, nextMode)
                visibilityMode = nextMode
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device alias
        Text(
            text = deviceAlias,
            style = HyperDropTypography.TitleMedium
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(HyperDropColors.TextDisabled)
        )
        
        // Visibility indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(visibilityColor, CircleShape)
            )
            Text(
                text = visibilityText,
                style = HyperDropTypography.BodySmall.copy(
                    color = visibilityColor.copy(alpha = 0.9f)
                )
            )
        }
    }
}
