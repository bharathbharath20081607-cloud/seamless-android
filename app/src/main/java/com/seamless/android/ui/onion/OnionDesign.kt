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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object OnionUi {
    val Canvas = Color(0xFF000000)
    val CanvasRaised = Color(0xFF09090C)
    val Surface = Color(0xFF111116)
    val SurfacePressed = Color(0xFF181820)
    val Outline = Color(0xFF26262E)
    val Accent = Color(0xFFFFFFFF)
    val AccentStrong = Color(0xFF2B2B35)
    val OutgoingStart = Color(0xFF2D5B52)
    val OutgoingEnd = Color(0xFF17342F)
    val IncomingStart = Color(0xFF20222B)
    val IncomingEnd = Color(0xFF13141A)
    val Online = Color(0xFF30D158)
    val Warning = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)
    val TextSecondary = Color(0xFFA8A8B2)
    val TextMuted = Color(0xFF6E6E78)
    val Corner = RoundedCornerShape(16.dp)
    val Hairline = Color.White.copy(alpha = 0.08f)
}

@Composable
internal fun OnionPage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(OnionUi.Canvas, Color(0xFF060609), OnionUi.Canvas)))
    ) { content() }
}

@Composable
internal fun OnionStatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun OnionAvatar(
    label: String,
    online: Boolean,
    avatarId: String = "aurora",
    modifier: Modifier = Modifier
) {
    val (start, end, mark) = when (avatarId) {
        "ember" -> Triple(Color(0xFFFF7A59), Color(0xFF8D2D37), "✦")
        "tide" -> Triple(Color(0xFF35B7FF), Color(0xFF1F4E8C), "◌")
        "orchid" -> Triple(Color(0xFFCC7BFF), Color(0xFF643B88), "✧")
        "mint" -> Triple(Color(0xFF3DDC97), Color(0xFF176B55), "◇")
        "copper" -> Triple(Color(0xFFE6A15A), Color(0xFF7E4630), "●")
        else -> Triple(Color(0xFF7D8CFF), Color(0xFF3C467D), "✦")
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(start, end)))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: mark,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                Text(
                    mark,
                    color = Color.White.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 31.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(OnionUi.Canvas)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (online) OnionUi.Online else OnionUi.TextSecondary)
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
