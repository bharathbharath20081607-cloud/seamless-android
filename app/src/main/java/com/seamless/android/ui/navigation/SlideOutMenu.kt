@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.tactilePress

/**
 * Slide-out navigation menu component — Flagship Obsidian & Optical Light edition
 * Features top-lit specular borders, tactile spring compression, and calibrated contrast.
 */
@Composable
fun SlideOutMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onMenuItemClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val menuItems = remember { MenuItemFactory.createMenuItems() }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(292.dp),
        color = if (isDark) Color(0xFF060608) else colorScheme.surface,
        shadowElevation = 20.dp
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF0A0A0E), Color(0xFF000000))
                            } else {
                                listOf(colorScheme.surface, colorScheme.background)
                            }
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Luxury Architectural Header
                MenuHeader(isDark = isDark)

                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else colorScheme.outline.copy(alpha = 0.25f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Tactile Menu Items
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(menuItems, key = { it.id }) { item ->
                        MenuItemRow(
                            item = item,
                            isDark = isDark,
                            onClick = {
                                if (item.enabled) {
                                    onMenuItemClick(item)
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }

            // Right-edge 1.dp top-lit specular border
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        Brush.verticalGradient(
                            if (isDark) {
                                listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            } else {
                                listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.06f)
                                )
                            }
                        )
                    )
            )
        }
    }
}

/**
 * Menu header with architectural branding and live encrypted status badge
 */
@Composable
private fun MenuHeader(isDark: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color(0xFF15151C), Color(0xFF0D0D12))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFF3F3F7))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))
                    } else {
                        listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.03f))
                    }
                ),
                shape = cardShape
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "seamless",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        letterSpacing = (-0.6).sp
                    ),
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // Live Sovereignty Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SeamlessColors.Emerald.copy(alpha = 0.14f))
                        .border(
                            width = 1.dp,
                            color = SeamlessColors.Emerald.copy(alpha = 0.30f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SeamlessColors.Emerald)
                    )
                    Text(
                        text = "E2EE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeamlessColors.Emerald
                    )
                }
            }

            Text(
                text = "Decentralized Mesh · Tor · HyperDrop",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Individual menu item row with tactile spring press & specular icon squircle
 */
@Composable
private fun MenuItemRow(
    item: MenuItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowAlpha = if (item.enabled) 1f else 0.45f
    val rowShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(rowShape)
            .background(
                if (isDark) Color(0xFF101015).copy(alpha = 0.72f)
                else colorScheme.surfaceVariant.copy(alpha = 0.42f)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f))
                    } else {
                        listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.02f))
                    }
                ),
                shape = rowShape
            )
            .tactilePress(
                scaleDown = 0.968f,
                enabled = item.enabled,
                onClick = onClick
            )
            .padding(vertical = 11.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Specular glass squircle icon badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                if (isDark) {
                                    listOf(Color(0xFF1F1F28), Color(0xFF15151B))
                                } else {
                                    listOf(Color.White, Color(0xFFEEEEF4))
                                }
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                if (isDark) {
                                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                                } else {
                                    listOf(Color.Black.copy(alpha = 0.09f), Color.Black.copy(alpha = 0.04f))
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (item.enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = if (item.enabled) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Badge
            item.badge?.let { badge ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (item.enabled) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (item.enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
