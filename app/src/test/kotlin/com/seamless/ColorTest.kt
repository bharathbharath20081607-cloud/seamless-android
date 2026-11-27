@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.seamless.android.ui.theme.PerceptualColorScience
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.ThemePreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Generate a consistent color for a username based on their peer ID or nickname
 * Returns colors that work well on both light and dark backgrounds
 */

class ColorTest {
    fun getUsernameColor(identifier: String): Color {
        // Hash the identifier to get a consistent number
        val hash = identifier.hashCode().toUInt()

        // Terminal-friendly colors that work on both black and white backgrounds
        val colors = listOf(
            Color(0xFF00FF00), // Bright Green
            Color(0xFF00FFFF), // Cyan
            Color(0xFFFFFF00), // Yellow
            Color(0xFFFF00FF), // Magenta
            Color(0xFF0080FF), // Bright Blue
            Color(0xFFFF8000), // Orange
            Color(0xFF80FF00), // Lime Green
            Color(0xFF8000FF), // Purple
            Color(0xFFFF0080), // Pink
            Color(0xFF00FF80), // Spring Green
            Color(0xFF80FFFF), // Light Cyan
            Color(0xFFFF8080), // Light Red
            Color(0xFF8080FF), // Light Blue
            Color(0xFFFFFF80), // Light Yellow
            Color(0xFFFF80FF), // Light Magenta
            Color(0xFF80FF80), // Light Green
        )

        // Use modulo to get consistent color for same identifier
        return colors[(hash % colors.size.toUInt()).toInt()]
    }

    @Test
    fun is_username_derived_color_consistent() {

        println("Testing username color function:")

        val testUsers = listOf("alice", "bob", "charlie", "diana", "eve")

        testUsers.forEach { user ->
            val color = getUsernameColor(user)
            println("User '$user' gets color: ${color.value.toString(16).uppercase()}")
        }

        val `alice'sColor` =  getUsernameColor(testUsers[0])
        val `bob'sColor` = getUsernameColor(testUsers[1])
        val `charlie'sColor` = getUsernameColor(testUsers[2])
        val `diana'sColor` = getUsernameColor(testUsers[3])
        val `eve'sColor` = getUsernameColor(testUsers[4])

        // Test consistency - same user should always get same color
        println("\nTesting consistency:")
        repeat(3) {
            val `alice's_color` = getUsernameColor(testUsers[0])
            val `bob's_color` = getUsernameColor(testUsers[1])
            val `charlie's_color` = getUsernameColor(testUsers[2])
            val `diana's_color` = getUsernameColor(testUsers[3])
            val `eve's_color` = getUsernameColor(testUsers[4])


            assertEquals(`alice'sColor`, `alice's_color`)
            assertEquals(`bob'sColor`, `bob's_color`)
            assertEquals(`charlie'sColor`, `charlie's_color`)
            assertEquals(`diana'sColor`, `diana's_color`)
            assertEquals(`eve'sColor`, `eve's_color`)

            println("Alice color (test ${it + 1}): ${`alice'sColor`.value.toString(16).uppercase()}")
        }
    }

    @Test
    fun oklchDisplayP3_gamutMapsCleanlyAcrossFullHueWheel() {
        val chromas = floatArrayOf(0.05f, 0.22f, 0.40f)
        for (hue in 0 until 360 step 15) {
            for (c in chromas) {
                val p3Color = PerceptualColorScience.oklchDisplayP3(0.62f, c, hue.toFloat())
                assertEquals(ColorSpaces.DisplayP3, p3Color.colorSpace)
                assertTrue("Red out of range for hue=$hue c=$c", p3Color.red in 0f..1f)
                assertTrue("Green out of range for hue=$hue c=$c", p3Color.green in 0f..1f)
                assertTrue("Blue out of range for hue=$hue c=$c", p3Color.blue in 0f..1f)
            }
        }
    }

    @Test
    fun apcaContrastLc_meetsWcag3AndStripeAccessibleThresholds() {
        val darkPrimaryLc = abs(
            PerceptualColorScience.apcaContrastLc(SeamlessColors.TextPrimaryDark, SeamlessColors.Obsidian)
        )
        assertTrue("Dark primary APCA Lc expected >= 85, was $darkPrimaryLc", darkPrimaryLc >= 85.0)

        val lightPrimaryLc = PerceptualColorScience.apcaContrastLc(
            SeamlessColors.TextPrimaryLight,
            SeamlessColors.OpticalLight
        )
        assertTrue("Light primary APCA Lc expected >= 85, was $lightPrimaryLc", lightPrimaryLc >= 85.0)

        val darkSecondaryLc = abs(
            PerceptualColorScience.apcaContrastLc(SeamlessColors.TextSecondaryDark, SeamlessColors.Obsidian)
        )
        assertTrue("Dark secondary APCA Lc expected >= 45, was $darkSecondaryLc", darkSecondaryLc >= 45.0)

        val selfBubbleDarkLc = abs(
            PerceptualColorScience.apcaContrastLc(SeamlessColors.BubbleSelfTextDark, SeamlessColors.BubbleSelfDark)
        )
        assertTrue("Ultramarine bubble APCA Lc expected >= 75, was $selfBubbleDarkLc", selfBubbleDarkLc >= 75.0)
    }

    @Test
    fun peerColors_maintainTsengSzafirCategoricalIsoLuminance() {
        val prevDark = ThemePreferenceManager.isCurrentlyDark
        try {
            ThemePreferenceManager.isCurrentlyDark = true
            val darkY = SeamlessColors.PeerColors.map { PerceptualColorScience.relativeLuminance(it) }
            val darkSpread = (darkY.maxOrNull() ?: 0.0) - (darkY.minOrNull() ?: 0.0)
            assertTrue("Dark peer iso-luminance spread expected < 0.08, was $darkSpread", darkSpread < 0.08)

            ThemePreferenceManager.isCurrentlyDark = false
            val lightY = SeamlessColors.PeerColors.map { PerceptualColorScience.relativeLuminance(it) }
            val lightSpread = (lightY.maxOrNull() ?: 0.0) - (lightY.minOrNull() ?: 0.0)
            assertTrue("Light peer iso-luminance spread expected < 0.06, was $lightSpread", lightSpread < 0.06)
        } finally {
            ThemePreferenceManager.isCurrentlyDark = prevDark
        }
    }
}