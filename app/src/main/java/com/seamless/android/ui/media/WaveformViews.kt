@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.seamless.android.features.voice.AudioWaveformExtractor
import com.seamless.android.features.voice.VoiceWaveformCache
import com.seamless.android.features.voice.resampleWave

@Composable
fun ScrollingWaveformRecorder(
    modifier: Modifier = Modifier,
    currentAmplitude: Float,
    samples: SnapshotStateList<Float>,
    maxSamples: Int = 120,
    fillColor: Color = Color(0xFF10B981),
    baseColor: Color = Color(0xFF475569)
) {
    // Append samples at a fixed cadence while visible
    val latestAmp by rememberUpdatedState(currentAmplitude)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { _: Long -> }
            val v = latestAmp.coerceIn(0f, 1f)
            samples.add(v)
            val overflow = samples.size - maxSamples
            if (overflow > 0) repeat(overflow) { if (samples.isNotEmpty()) samples.removeAt(0) }
            kotlinx.coroutines.delay(80)
        }
    }
    WaveformCanvas(modifier = modifier, samples = samples, fillProgress = 1f, baseColor = baseColor, fillColor = fillColor)
}

@Composable
fun WaveformPreview(
    modifier: Modifier = Modifier,
    path: String,
    sendProgress: Float?,
    playbackProgress: Float?,
    onLoaded: ((FloatArray) -> Unit)? = null,
    onSeek: ((Float) -> Unit)? = null
) {
    val cached = remember(path) { VoiceWaveformCache.get(path) }
    val stateSamples = remember(path) {
        mutableStateListOf<Float>().apply {
            if (cached != null) {
                val normalized = if (cached.size != 64) resampleWave(cached, 64) else cached
                addAll(normalized.toList())
            } else {
                // Deterministic fallback waveform while extracting
                val hash = path.hashCode()
                for (i in 0 until 64) {
                    val v = 0.22f + 0.58f * kotlin.math.abs(kotlin.math.sin((i * 0.43f) + (hash % 17)))
                    add(v.coerceIn(0.18f, 0.85f))
                }
            }
        }
    }
    val progress = (sendProgress ?: playbackProgress)?.coerceIn(0f, 1f) ?: 0f
    LaunchedEffect(path, cached) {
        if (cached != null) {
            val normalized = if (cached.size != 64) resampleWave(cached, 64) else cached
            stateSamples.clear(); stateSamples.addAll(normalized.toList())
        } else {
            AudioWaveformExtractor.extractAsync(path, sampleCount = 64) { arr ->
                if (arr != null && arr.isNotEmpty()) {
                    VoiceWaveformCache.put(path, arr)
                    stateSamples.clear(); stateSamples.addAll(arr.toList())
                    onLoaded?.invoke(arr)
                }
            }
        }
    }
    val contentColor = androidx.compose.material3.LocalContentColor.current
    WaveformCanvas(
        modifier = modifier,
        samples = stateSamples,
        fillProgress = if (stateSamples.isEmpty()) 0f else progress,
        baseColor = contentColor.copy(alpha = 0.28f),
        fillColor = when {
            sendProgress != null -> Color(0xFF0EA5E9) // electric cyan while sending
            else -> contentColor
        },
        onSeek = onSeek
    )
}

@Composable
private fun WaveformCanvas(
    modifier: Modifier,
    samples: List<Float>,
    fillProgress: Float,
    baseColor: Color,
    fillColor: Color,
    onSeek: ((Float) -> Unit)? = null
) {
    val seekModifier = if (onSeek != null) {
        modifier.pointerInput(onSeek) {
            detectTapGestures { offset ->
                // Calculate the seek position as a fraction (0.0 to 1.0)
                val position = offset.x / size.width.toFloat()
                val clampedPosition = position.coerceIn(0f, 1f)
                onSeek(clampedPosition)
            }
        }
    } else {
        modifier
    }

    Canvas(modifier = seekModifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val n = samples.size
        if (n <= 0) return@Canvas
        val stepX = w / n
        val midY = h / 2f
        val radius = 2.dp.toPx()
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val filledUntil = (n * fillProgress).toInt()
        for (i in 0 until n) {
            val amp = samples[i].coerceIn(0f, 1f)
            val lineH = (amp * (h * 0.8f)).coerceAtLeast(2f)
            val x = i * stepX + stepX / 2f
            val yTop = midY - lineH / 2f
            val yBot = midY + lineH / 2f
            drawLine(
                color = if (i <= filledUntil) fillColor else baseColor,
                start = Offset(x, yTop),
                end = Offset(x, yBot),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}
