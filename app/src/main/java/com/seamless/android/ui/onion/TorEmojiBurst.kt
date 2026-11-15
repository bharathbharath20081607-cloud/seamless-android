@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A brief, local celebration for a sent reaction; it never changes message data. */
@Composable
internal fun TorEmojiBurst(token: Int, message: String) {
    val reaction = remember(message) { reactionFor(message) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(token) {
        if (token == 0 || reaction == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1_250, easing = FastOutSlowInEasing))
    }
    if (reaction == null || progress.value >= 1f) return

    val positions = listOf(
        Alignment.TopStart to 28.dp,
        Alignment.TopCenter to (-44).dp,
        Alignment.TopEnd to 24.dp,
        Alignment.CenterStart to (-18).dp,
        Alignment.Center to 42.dp,
        Alignment.CenterEnd to (-30).dp,
        Alignment.BottomStart to 34.dp,
        Alignment.BottomCenter to (-24).dp,
        Alignment.BottomEnd to 18.dp
    )
    Box(Modifier.fillMaxSize()) {
        positions.forEachIndexed { index, (alignment, xOffset) ->
            val arc = (if (index % 2 == 0) -1 else 1) * (28 + index * 9)
            Text(
                text = reaction,
                fontSize = (30 + (index % 3) * 8).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(alignment)
                    .offset(x = xOffset, y = 90.dp)
                    .graphicsLayer {
                        translationY = -progress.value * (150 + index * 18)
                        translationX = progress.value * arc
                        scaleX = 0.65f + (1f - progress.value) * 0.7f
                        scaleY = scaleX
                        alpha = (1f - progress.value).coerceIn(0f, 1f)
                        rotationZ = progress.value * (if (index % 2 == 0) -16f else 16f)
                    }
            )
        }
    }
}

private fun reactionFor(message: String): String? = when {
    message.contains("😂") || message.contains("🤣") -> "😂"
    message.contains("❤") || message.contains("♥") || message.contains("💕") -> "❤"
    message.contains("🔥") -> "🔥"
    message.contains("🎉") || message.contains("🥳") -> "🎉"
    message.contains("😎") -> "😎"
    message.contains("👍") || message.contains("👏") -> "✨"
    // Kotlin Char values are UTF-16 code units, so checking Char.code misses
    // almost every modern emoji (which is commonly a surrogate pair). Iterate
    // Unicode code points so the fallback celebration always fires.
    containsEmojiCodePoint(message) -> "✦"
    else -> null
}

private fun containsEmojiCodePoint(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val codePoint = Character.codePointAt(value, index)
        if (codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF) return true
        index += Character.charCount(codePoint)
    }
    return false
}
