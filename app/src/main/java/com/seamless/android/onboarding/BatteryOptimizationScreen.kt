@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.onboarding
import com.seamless.android.ui.theme.SeamlessColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.R

/**
 * Screen shown when checking battery optimization status or requesting battery optimization disable
 */

@Composable
fun BatteryOptimizationScreen(
    modifier: Modifier,
    status: BatteryOptimizationStatus,
    onDisableBatteryOptimization: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    // Initialize preference manager
    LaunchedEffect(Unit) {
        BatteryOptimizationPreferenceManager.init(context)
    }

    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            BatteryOptimizationStatus.ENABLED -> {
                BatteryOptimizationEnabledContent(
                    onDisableBatteryOptimization = onDisableBatteryOptimization,
                    onRetry = onRetry,
                    onSkip = onSkip,
                    colorScheme = colorScheme,
                    isLoading = isLoading,
                    modifier = modifier // Pass modifier down
                )
            }
            
            BatteryOptimizationStatus.DISABLED -> {
                BatteryOptimizationCheckingContent(
                    colorScheme = colorScheme
                )
            }
            
            BatteryOptimizationStatus.NOT_SUPPORTED -> {
                BatteryOptimizationNotSupportedContent(
                    onRetry = onRetry,
                    colorScheme = colorScheme
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationEnabledContent(
    onDisableBatteryOptimization: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    colorScheme: ColorScheme,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        listOf(
                            com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundStart,
                            com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundEnd
                        )
                    } else {
                        listOf(
                            com.seamless.android.ui.theme.SeamlessColors.LightBackground,
                            com.seamless.android.ui.theme.SeamlessColors.LightBackground
                        )
                    }
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section - matching AboutSheet style
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 100)) + fadeIn(animationSpec = tween(500, delayMillis = 100))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = colorScheme.primary
                    )

                    Text(
                        text = stringResource(R.string.battery_optimization_detected_title),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        color = colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Battery optimization info section - Glassmorphic
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 300)) + fadeIn(animationSpec = tween(500, delayMillis = 300))
            ) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Power,
                                contentDescription = stringResource(R.string.cd_battery_optimization),
                                tint = colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.battery_optimization_enabled_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.battery_optimization_explanation_short),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Benefits section - Glassmorphic
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 500)) + fadeIn(animationSpec = tween(500, delayMillis = 500))
            ) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.cd_benefits),
                                tint = colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.benefits_of_disabling),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.battery_benefits_short),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Fixed buttons at the bottom
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDisableBatteryOptimization,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.disable_battery_optimization),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.check_again),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Default
                        )
                    )
                }
                
                TextButton(
                    onClick = {
                        BatteryOptimizationPreferenceManager.setSkipped(context, true)
                        onSkip()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.battery_optimization_skip),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Default
                        )
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BatteryOptimizationCheckingContent(
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section - matching AboutSheet style
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.battery_optimization_disabled_title),
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                color = colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        Icon(
            imageVector = Icons.Filled.BatteryStd,
            contentDescription = stringResource(R.string.cd_checking_battery_optimization),
            modifier = Modifier
                .size(64.dp)
                .rotate(rotation),
            tint = colorScheme.primary
        )
        
        Text(
            text = stringResource(R.string.battery_optimization_success_message),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Default,
                color = colorScheme.onBackground.copy(alpha = 0.8f)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BatteryOptimizationNotSupportedContent(
    onRetry: () -> Unit,
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section - matching AboutSheet style
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.battery_optimization_not_required),
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                color = colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.cd_not_supported_battery_optimization),
            modifier = Modifier.size(64.dp),
            tint = colorScheme.primary
        )
        
        Text(
            text = stringResource(R.string.battery_optimization_not_supported_message),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Default,
                color = colorScheme.onBackground.copy(alpha = 0.8f)
            ),
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.continue_btn),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
