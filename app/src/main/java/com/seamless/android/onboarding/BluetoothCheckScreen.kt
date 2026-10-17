@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.seamless.android.R

/**
 * Screen shown when checking Bluetooth status or requesting Bluetooth enable
 */
@Composable
fun BluetoothCheckScreen(
    modifier: Modifier,
    status: BluetoothStatus,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    isLoading: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500), initialOffsetY = { 50 }) togetherWith
                fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300), targetOffsetY = { -50 })
            },
            label = "status_transition"
        ) { targetStatus ->
            when (targetStatus) {
                BluetoothStatus.DISABLED -> {
                    BluetoothDisabledContent(
                        onEnableBluetooth = onEnableBluetooth,
                        onRetry = onRetry,
                        colorScheme = colorScheme,
                        isLoading = isLoading
                    )
                }
                BluetoothStatus.NOT_SUPPORTED -> {
                    BluetoothNotSupportedContent(
                        colorScheme = colorScheme
                    )
                }
                BluetoothStatus.ENABLED -> {
                    BluetoothCheckingContent(
                        colorScheme = colorScheme
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseBluetoothIcon(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Heartbeat-like double pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                1f at 0
                1.2f at 300 // First beat expand
                1f at 500 // Retract
                1.3f at 800 // Second beat expand larger
                1f at 1000 // Retract
                1f at 1500 // Rest
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0.5f at 0
                0.3f at 300
                0.5f at 500
                0f at 800
                0f at 1500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(pulseScale)
                .background(color.copy(alpha = pulseAlpha), androidx.compose.foundation.shape.CircleShape)
        )
        // Inner static ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(color.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = stringResource(R.string.cd_bluetooth),
                modifier = Modifier.size(48.dp),
                tint = color
            )
        }
    }
}

@Composable
private fun BluetoothDisabledContent(
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    colorScheme: ColorScheme,
    isLoading: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Animated Pulsing Bluetooth Icon
        PulseBluetoothIcon(color = colorScheme.primary)

        Text(
            text = stringResource(R.string.bluetooth_required),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            ),
            textAlign = TextAlign.Center
        )

        // Glassmorphic Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (androidx.compose.foundation.isSystemInDarkTheme()) 
                    com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceDark 
                else 
                    com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (androidx.compose.foundation.isSystemInDarkTheme())
                    com.seamless.android.ui.theme.SeamlessColors.GlassBorderDark
                else
                    com.seamless.android.ui.theme.SeamlessColors.GlassBorderLight
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    Text(
                        text = stringResource(R.string.bluetooth_needs_for),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        fontFamily = FontFamily.Default
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                    Text(
                        text = stringResource(R.string.bluetooth_needs_bullets),
                    style = MaterialTheme.typography.bodyMedium.copy( // Increased readability
                        fontFamily = FontFamily.Default,
                        color = colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 24.sp
                    )
                )
            }
        }

        if (isLoading) {
            BluetoothLoadingIndicator()
        } else {
            // Gradient Button
            Button(
                onClick = onEnableBluetooth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.enable_bluetooth),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun BluetoothNotSupportedContent(
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error icon
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFEBEE)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.warning_emoji),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        Text(
            text = stringResource(R.string.bluetooth_not_supported),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = colorScheme.error
            ),
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.errorContainer.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = stringResource(R.string.bluetooth_unsupported_explanation),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Default,
                    color = colorScheme.onSurface
                ),
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BluetoothCheckingContent(
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            ),
            textAlign = TextAlign.Center
        )

        BluetoothLoadingIndicator()

        Text(
            text = stringResource(R.string.checking_bluetooth_status),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Default,
                color = colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun BluetoothLoadingIndicator() {
    // Animated rotation for the loading indicator
    val infiniteTransition = rememberInfiniteTransition(label = "bluetooth_loading")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
}
