@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.media

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily

@Composable
fun VoiceNotePlayer(
    path: String,
    progressOverride: Float? = null,
    progressColor: Color? = null
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableStateOf(0) }
    val player = remember { MediaPlayer() }

    // Seek function - position is a fraction from 0.0 to 1.0
    val seekTo: (Float) -> Unit = { position ->
        if (isPrepared && durationMs > 0) {
            val seekMs = (position * durationMs).toInt().coerceIn(0, durationMs)
            try {
                player.seekTo(seekMs)
                progress = position  // Update progress immediately for UI responsiveness
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(path) {
        isPrepared = false
        isError = false
        progress = 0f
        durationMs = 0
        isPlaying = false
        try {
            player.reset()
            player.setOnPreparedListener {
                isPrepared = true
                durationMs = try { player.duration } catch (_: Exception) { 0 }
            }
            player.setOnCompletionListener {
                isPlaying = false
                progress = 1f
            }
            player.setOnErrorListener { _, _, _ ->
                isError = true
                isPlaying = false
                true
            }
            player.setDataSource(path)
            player.prepareAsync()
        } catch (_: Exception) {
            isError = true
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        try {
            if (isPlaying && isPrepared) {
                if (progress >= 0.99f || (durationMs > 0 && player.currentPosition >= durationMs - 150)) {
                    player.seekTo(0)
                    progress = 0f
                }
                player.start()
            } else if (isPrepared && player.isPlaying) {
                player.pause()
            }
        } catch (_: Exception) {}
    }
    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            progress = try { player.currentPosition.toFloat() / (player.duration.toFloat().coerceAtLeast(1f)) } catch (_: Exception) { 0f }
            kotlinx.coroutines.delay(60)
        }
    }
    DisposableEffect(path) { onDispose { try { player.release() } catch (_: Exception) {} } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Disable play/pause while showing send progress override
        val controlsEnabled = isPrepared && !isError && progressOverride == null
        val contentColor = androidx.compose.material3.LocalContentColor.current
        val iconTint = if (contentColor.red > 0.5f) Color.Black else Color.White
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = if (controlsEnabled) contentColor else contentColor.copy(alpha = 0.25f),
                    shape = CircleShape
                )
                .clickable(enabled = controlsEnabled) { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        com.seamless.android.ui.media.WaveformPreview(
            modifier = Modifier
                .height(28.dp)
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            path = path,
            sendProgress = progressOverride,
            playbackProgress = if (progressOverride == null) progress else null,
            onSeek = seekTo
        )
        val displayMs = if (isPlaying && durationMs > 0) {
            (progress * durationMs).toInt().coerceIn(0, durationMs)
        } else {
            durationMs
        }
        val durText = if (durationMs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d", (displayMs / 1000) / 60, (displayMs / 1000) % 60)
        } else if (isError) {
            "00:00"
        } else {
            "--:--"
        }
        Text(
            text = durText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = contentColor.copy(alpha = 0.78f)
        )
    }
}

