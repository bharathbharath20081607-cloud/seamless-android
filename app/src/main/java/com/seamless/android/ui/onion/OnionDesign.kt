@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.PerceptualColorScience
import com.seamless.android.ui.theme.ThemePreferenceManager

internal object OnionUi {
    private val isDark: Boolean
        get() = ThemePreferenceManager.isCurrentlyDark

    // Anti-halation Space Graphite Titanium (Dark) & Studio Optical Neutral (Light) canvas
    val Canvas: Color
        get() = if (isDark) Color(0xFF090B10) else Color(0xFFF5F6F8)

    val CanvasRaised: Color
        get() = if (isDark) Color(0xFF11141C) else Color(0xFFFFFFFF)

    val Surface: Color
        get() = if (isDark) Color(0xFF181C26) else Color(0xFFFFFFFF)

    val SurfacePressed: Color
        get() = if (isDark) Color(0xFF222734) else Color(0xFFEBEEF3)

    val Outline: Color
        get() = if (isDark) Color(0xFF2D3446) else Color(0xFFDCE1EA)

    val TextPrimary: Color
        get() = if (isDark) Color(0xFFF2F4F8) else Color(0xFF0B0F17)

    val Accent: Color
        get() = if (isDark) Color(0xFFF2F4F8) else Color(0xFF0B0F17)

    val AccentContent: Color
        get() = if (isDark) Color(0xFF090B10) else Color(0xFFFFFFFF)

    val AccentStrong: Color
        get() = if (isDark) Color(0xFF252B3B) else Color(0xFFE2E6EE)

    // Precision Display P3 Ultramarine (264° low-melanopic) Primary CTA Tokens
    val PrimaryStart: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.58f, 0.195f, 264f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.48f, 0.190f, 264f)
        }

    val PrimaryEnd: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.48f, 0.185f, 264f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.40f, 0.175f, 264f)
        }

    val PrimaryRim: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.84f, 0.115f, 258f, alpha = 0.36f)
        } else {
            Color.White.copy(alpha = 0.30f)
        }

    val OnPrimary: Color = Color(0xFFFFFFFF)

    val AccentBlue: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.73f, 0.145f, 262f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.47f, 0.185f, 264f)
        }

    // Pre-attentive Figure-Ground Outgoing ("Me") Low-Melanopic P3 Ultramarine Sapphire
    val OutgoingStart: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.30f, 0.088f, 264f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.47f, 0.182f, 264f)
        }

    val OutgoingEnd: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.24f, 0.076f, 264f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.39f, 0.168f, 264f)
        }

    val OutgoingBorder: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.73f, 0.145f, 262f, alpha = 0.28f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.32f, 0.130f, 264f, alpha = 0.22f)
        }

    val OutgoingText: Color = Color(0xFFF5F8FF)

    val OutgoingSubtle: Color = Color(0xFFD6E4FF).copy(alpha = 0.78f)

    // Elevated Machined Graphite (Dark) / Studio Optical White (Light) Incoming ("Peer") gradient
    val IncomingStart: Color
        get() = if (isDark) Color(0xFF181C26) else Color(0xFFFFFFFF)

    val IncomingEnd: Color
        get() = if (isDark) Color(0xFF131720) else Color(0xFFF8FAFC)

    val IncomingBorder: Color
        get() = if (isDark) Color(0xFFF2F4F8).copy(alpha = 0.11f) else Color(0xFF0B0F17).copy(alpha = 0.09f)

    val IncomingText: Color
        get() = TextPrimary

    val IncomingSubtle: Color
        get() = TextSecondary

    // 2025-2026 Display P3 M3 Expressive Semantic Tokens (Anti-Chromostereopsis)
    val Online: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.77f, 0.188f, 152f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.50f, 0.155f, 152f)
        }
    val Warning: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.82f, 0.158f, 80f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.54f, 0.142f, 72f)
        }
    val Danger: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.64f, 0.198f, 25f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.52f, 0.200f, 26f)
        }
    val Info: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.75f, 0.148f, 232f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.53f, 0.145f, 236f)
        }
    val Indigo: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.70f, 0.150f, 286f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.48f, 0.175f, 286f)
        }
    val Ember: Color
        get() = if (isDark) {
            PerceptualColorScience.oklchDisplayP3(0.69f, 0.192f, 44f)
        } else {
            PerceptualColorScience.oklchDisplayP3(0.52f, 0.185f, 42f)
        }

    val TextSecondary: Color
        get() = if (isDark) Color(0xFF98A1B3) else Color(0xFF475467)

    val TextMuted: Color = Color(0xFF667085)

    val Corner = RoundedCornerShape(18.dp)

    val Hairline: Color
        get() = if (isDark) Color(0xFFF2F4F8).copy(alpha = 0.10f) else Color(0xFF0B0F17).copy(alpha = 0.08f)
}

@Composable
internal fun OnionPage(content: @Composable () -> Unit) {
    val isDark = ThemePreferenceManager.isCurrentlyDark
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (isDark) {
                        listOf(OnionUi.Canvas, Color(0xFF0D1018), OnionUi.Canvas)
                    } else {
                        listOf(Color(0xFFFAFBFD), OnionUi.Canvas, Color(0xFFEDF0F5))
                    }
                )
            )
    ) { content() }
}

@Composable
internal fun OnionStatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(color.copy(alpha = 0.36f), color.copy(alpha = 0.14f)),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = RoundedCornerShape(999.dp)
        ),
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.18f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun OnionAvatar(
    label: String,
    online: Boolean,
    avatarId: String = "aurora",
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val (start, end, mark) = when (avatarId) {
        "ember" -> Triple(Color(0xFFFF7A2F), Color(0xFF9A3412), "✦")
        "tide" -> Triple(Color(0xFF0EA5E9), Color(0xFF0369A1), "◌")
        "orchid" -> Triple(Color(0xFF8B5CF6), Color(0xFF4C1D95), "✧")
        "mint" -> Triple(Color(0xFF10B981), Color(0xFF065F46), "◇")
        "copper" -> Triple(Color(0xFF6366F1), Color(0xFF312E81), "●")
        else -> Triple(Color(0xFF2B6EF6), Color(0xFF1E3A8A), "✦")
    }
    val dotSize = (size.value * 0.27f).coerceIn(11f, 15f).dp
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(start, end)))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                mark,
                color = Color.White.copy(alpha = 0.24f),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.56f).sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = size * 0.10f, top = size * 0.04f)
            )
            Text(
                label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: mark,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(OnionUi.Canvas)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (online) OnionUi.Online else OnionUi.TextMuted)
        )
    }
}

/** Enlarges only compact emoji reactions, keeping ordinary messages readable. */
internal fun isExpressiveEmoji(text: String): Boolean {
    val compact = text.trim()
    if (compact.isEmpty() || compact.length > 8) return false
    var index = 0
    while (index < compact.length) {
        val codePoint = Character.codePointAt(compact, index)
        if (codePoint in 0x2600..0x27BF || codePoint in 0x1F000..0x1FAFF) return true
        index += Character.charCount(codePoint)
    }
    return false
}
