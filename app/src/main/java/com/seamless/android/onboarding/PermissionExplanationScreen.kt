@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.onboarding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke

import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.seamless.android.R
import com.seamless.android.ui.theme.SeamlessColors

/**
 * Permission explanation screen shown before requesting permissions
 * Explains why bitchat needs each permission and reassures users about privacy
 */
@Composable
fun PermissionExplanationScreen(
    modifier: Modifier,
    permissionCategories: List<PermissionCategory>,
    onContinue: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 88.dp) // Leave space for the fixed button
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header Section - matching AboutSheet style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
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
                }

                Text(
                    text = stringResource(R.string.about_tagline),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    color = colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Privacy assurance section - matching AboutSheet card style
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = stringResource(R.string.cd_privacy_protected),
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.privacy_protected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.privacy_bullets),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Default,
                                color = colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Section header
            Text(
                text = stringResource(R.string.permissions_header),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // Permission categories - Glassmorphic List
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                permissionCategories.forEachIndexed { index, category ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        // Staggered delay based on index
                        kotlinx.coroutines.delay(index * 100L) 
                        isVisible = true
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500))
                    ) {
                        PermissionCategoryCard(
                            category = category,
                            colorScheme = colorScheme
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp)) // Extra space for button
        }

        // Fixed button at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.grant_permissions),
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
private fun PermissionCategoryCard(
    category: PermissionCategory,
    colorScheme: ColorScheme
) {
    val isLocation = category.type == PermissionType.PRECISE_LOCATION
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (androidx.compose.foundation.isSystemInDarkTheme()) 
            com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceDark 
        else 
            com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isLocation) com.seamless.android.ui.theme.SeamlessColors.Warning.copy(alpha = 0.5f)
                    else if (androidx.compose.foundation.isSystemInDarkTheme())
                        com.seamless.android.ui.theme.SeamlessColors.GlassBorderDark
                    else
                        com.seamless.android.ui.theme.SeamlessColors.GlassBorderLight
        )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Tint icon based on permission type for visual variety
            val iconTint = when (category.type) {
                PermissionType.PRECISE_LOCATION -> SeamlessColors.Warning
                else -> colorScheme.primary
            }

            Icon(
                imageVector = getPermissionIcon(category.type),
                contentDescription = category.type.nameValue,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = category.type.nameValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onBackground.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Default,
                    lineHeight = 16.sp
                )

                if (isLocation) {
                    // Extra emphasis for location permission
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(
                                color = com.seamless.android.ui.theme.SeamlessColors.Warning.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.cd_warning),
                            tint = com.seamless.android.ui.theme.SeamlessColors.Warning,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.location_tracking_warning),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Bold,
                                color = com.seamless.android.ui.theme.SeamlessColors.Warning
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun getPermissionIcon(permissionType: PermissionType): ImageVector {
    return when (permissionType) {
        PermissionType.NEARBY_DEVICES -> Icons.Filled.Bluetooth
        PermissionType.PRECISE_LOCATION -> Icons.Filled.LocationOn
        PermissionType.MICROPHONE -> Icons.Filled.Mic
        PermissionType.NOTIFICATIONS -> Icons.Filled.Notifications
        PermissionType.BATTERY_OPTIMIZATION -> Icons.Filled.Power
        PermissionType.OTHER -> Icons.Filled.Settings
    }
}
