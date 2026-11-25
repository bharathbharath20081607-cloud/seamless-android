@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui
import com.seamless.android.ui.media.FilePickerButton
import androidx.compose.ui.focus.focusRequester
// [Goose] TODO: Replace inline file attachment stub with FilePickerButton abstraction that dispatches via FileShareDispatcher


import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.seamless.android.ui.theme.tactilePress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.withStyle
import com.seamless.android.ui.theme.BASE_FONT_SIZE
import com.seamless.android.features.voice.normalizeAmplitudeSample
import com.seamless.android.features.voice.AudioWaveformExtractor
import com.seamless.android.ui.media.RealtimeScrollingWaveform
import com.seamless.android.ui.media.ImagePickerButton

/**
 * Input components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

/**
 * VisualTransformation that styles slash commands with background and color
 * while preserving cursor positioning and click handling
 */
class SlashCommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val slashCommandRegex = Regex("(/\\w+)(?=\\s|$)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0

            slashCommandRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }

                // Add the styled slash command
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF60A5FA), // Precision Cobalt-Blue
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        background = Color(0xFF162235)
                    )
                ) {
                    append(match.value)
                }

                lastIndex = match.range.last + 1
            }

            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }

        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that styles mentions with background and color
 * while preserving cursor positioning and click handling
 */
class MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mentionRegex = Regex("@([a-zA-Z0-9_]+)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            
            mentionRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }
                
                // Add the styled mention
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF38BDF8), // Precision System Sky
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(match.value)
                }
                
                lastIndex = match.range.last + 1
            }
            
            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }
        
        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that combines multiple visual transformations
 */
class CombinedVisualTransformation(private val transformations: List<VisualTransformation>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        var resultText = text
        
        // Apply each transformation in order
        transformations.forEach { transformation ->
            resultText = transformation.filter(resultText).text
        }
        
        return TransformedText(
            text = resultText,
            offsetMapping = OffsetMapping.Identity
        )
    }
}





@Composable
fun MessageInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    showMediaButtons: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isFocused = remember { mutableStateOf(false) }
    val hasText = value.text.isNotBlank() // Check if there's text for send button state
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }

    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f

    // Modern clean input container — Obsidian glass pill with specular focus border
    val glassColor = if (isDark) 
        Color(0xFF121217)
    else 
        Color.White.copy(alpha = 0.97f)

    val borderBrush = if (isFocused.value) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.22f))
            } else {
                listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.2f))
            }
        )
    } else {
        SolidColor(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f))
    }

    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(
                elevation = if (isFocused.value) 4.dp else 1.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .background(
                color = glassColor,
                shape = RoundedCornerShape(28.dp)
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(28.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Text input with placeholder OR visualizer when recording
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
        ) {
            // Always keep the text field mounted to retain focus and avoid IME collapse
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(if (isRecording) Color.Transparent else colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { 
                    if (hasText) onSend() // Only send if there's text
                }),
                visualTransformation = CombinedVisualTransformation(
                    listOf(SlashCommandVisualTransformation(), MentionVisualTransformation())
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused.value = focusState.isFocused
                    }
            )

            // Show placeholder when there's no text and not recording
            if (value.text.isEmpty() && !isRecording) {
                Text(
                    text = "Message...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Overlay the real-time scrolling waveform while recording
            if (isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RealtimeScrollingWaveform(
                        modifier = Modifier.weight(1f).height(32.dp),
                        amplitudeNorm = normalizeAmplitudeSample(amplitude),
                        barColor = com.seamless.android.ui.theme.SeamlessColors.BrandGradientStart,
                        baseColor = colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.width(20.dp))
                    val secs = (elapsedMs / 1000).toInt()
                    val mm = secs / 60
                    val ss = secs % 60
                    val maxSecs = 60 // 60 second max recording time
                    val maxMm = maxSecs / 60
                    val maxSs = maxSecs % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%02d:%02d / %02d:%02d", mm, ss, maxMm, maxSs),
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.primary,
                        fontSize = (BASE_FONT_SIZE - 4).sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Expressive Spring-Morphing Action Cluster (Media/Mic <-> Send Button)
        val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
        val latestChannel = rememberUpdatedState(currentChannel)
        val latestOnSendVoiceNote = rememberUpdatedState(onSendVoiceNote)

        androidx.compose.animation.AnimatedContent(
            targetState = hasText,
            transitionSpec = {
                (androidx.compose.animation.scaleIn(
                    initialScale = 0.72f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.66f,
                        stiffness = 480f
                    )
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(150)
                )).togetherWith(
                    androidx.compose.animation.scaleOut(
                        targetScale = 0.72f,
                        animationSpec = androidx.compose.animation.core.tween(130)
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(120)
                    )
                )
            },
            label = "InputActionMorph"
        ) { showingSendButton ->
            if (showingSendButton) {
                // High-contrast Optical White / Obsidian Send Button with tactile spring + haptic tick
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White else Color(0xFF09090B))
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .tactilePress(
                            scaleDown = 0.90f,
                            onClick = { onSend() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (isDark) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (showMediaButtons) {
                val bg = if (isDark) Color(0xFF26262E) else Color(0xFFE4E4E9)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording) {
                        ImagePickerButton(
                            onImageReady = { outPath ->
                                onSendImageNote(latestSelectedPeer.value, latestChannel.value, outPath)
                            }
                        )
                    }
                    VoiceRecordButton(
                        backgroundColor = bg,
                        onStart = {
                            isRecording = true
                            elapsedMs = 0L
                            if (isFocused.value) {
                                try { focusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        },
                        onAmplitude = { amp, ms ->
                            amplitude = amp
                            elapsedMs = ms
                        },
                        onFinish = { path ->
                            isRecording = false
                            AudioWaveformExtractor.extractAsync(path, sampleCount = 120) { arr ->
                                if (arr != null) {
                                    try { com.seamless.android.features.voice.VoiceWaveformCache.put(path, arr) } catch (_: Exception) {}
                                }
                            }
                            latestOnSendVoiceNote.value(
                                latestSelectedPeer.value,
                                latestChannel.value,
                                path
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CommandSuggestionsBox(
    suggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val boxShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(boxShape)
            .background(
                brush = Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color(0xFF17171F), Color(0xFF101016))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFF6F6FA))
                    }
                ),
                shape = boxShape
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
                shape = boxShape
            )
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        suggestions.forEach { suggestion: CommandSuggestion ->
            CommandSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun CommandSuggestionItem(
    suggestion: CommandSuggestion,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.04f)
                else Color.Black.copy(alpha = 0.03f)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }

        // Command Pill Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.primary.copy(alpha = 0.12f))
                .border(
                    width = 1.dp,
                    color = colorScheme.primary.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = allCommands.joinToString(", "),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = colorScheme.primary
            )
        }

        suggestion.syntax?.let { syntax ->
            Text(
                text = syntax,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MentionSuggestionsBox(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val boxShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(boxShape)
            .background(
                brush = Brush.verticalGradient(
                    if (isDark) {
                        listOf(Color(0xFF17171F), Color(0xFF101016))
                    } else {
                        listOf(Color(0xFFFFFFFF), Color(0xFFF6F6FA))
                    }
                ),
                shape = boxShape
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
                shape = boxShape
            )
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        suggestions.forEach { suggestion: String ->
            MentionSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun MentionSuggestionItem(
    suggestion: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.04f)
                else Color.Black.copy(alpha = 0.03f)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mention_suggestion_at, suggestion),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.mention),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}
