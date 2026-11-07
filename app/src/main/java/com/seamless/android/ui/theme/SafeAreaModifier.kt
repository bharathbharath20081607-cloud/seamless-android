@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Safe Area Modifiers
 * Ensures UI content respects system bars, notches, and display cutouts
 */

/**
 * Apply full safe area padding - respects all system UI elements
 * Use this for main screen content to prevent cutoff by notches, status bar, nav bar
 */
@Composable
fun Modifier.safeAreaPadding(): Modifier {
    return this.windowInsetsPadding(WindowInsets.safeDrawing)
}

/**
 * Apply only top safe area (status bar + display cutout)
 * Use for headers and top-aligned content
 */
@Composable
fun Modifier.safeTopPadding(): Modifier {
    return this
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.displayCutout)
}

/**
 * Apply only bottom safe area (navigation bar)
 * Use for bottom buttons and FABs
 */
@Composable
fun Modifier.safeBottomPadding(): Modifier {
    return this.windowInsetsPadding(WindowInsets.navigationBars)
}

/**
 * Apply horizontal safe area (display cutout on sides - landscape mode)
 * Use for content that needs to respect notches in landscape
 */
@Composable
fun Modifier.safeHorizontalPadding(): Modifier {
    return this.windowInsetsPadding(WindowInsets.displayCutout)
}

/**
 * Get safe area padding values for custom layouts
 */
@Composable
fun getSafeAreaPadding(): PaddingValues {
    return WindowInsets.safeDrawing.asPaddingValues()
}

/**
 * Get status bar height
 */
@Composable
fun getStatusBarHeight(): Dp {
    val layoutDirection = LocalLayoutDirection.current
    val statusBarInsets = WindowInsets.statusBars.asPaddingValues()
    return statusBarInsets.calculateTopPadding()
}

/**
 * Get navigation bar height  
 */
@Composable
fun getNavigationBarHeight(): Dp {
    val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
    return navBarInsets.calculateBottomPadding()
}

/**
 * Combined modifier for screen content with both safe area and responsive padding
 * This is the recommended modifier for main screen content
 */
@Composable
fun Modifier.screenContentPadding(
    additionalHorizontal: Dp = 0.dp,
    additionalVertical: Dp = 0.dp
): Modifier {
    val dimensions = rememberDimensions()
    return this
        .safeAreaPadding()
        .padding(
            horizontal = dimensions.screenPadding + additionalHorizontal,
            vertical = additionalVertical
        )
}

/**
 * Button safe area modifier - ensures buttons aren't cut off at screen edges
 * Adds horizontal padding based on screen size and ensures bottom safe area
 */
@Composable
fun Modifier.buttonSafeArea(): Modifier {
    val dimensions = rememberDimensions()
    return this
        .padding(horizontal = dimensions.screenPadding)
        .safeBottomPadding()
}
