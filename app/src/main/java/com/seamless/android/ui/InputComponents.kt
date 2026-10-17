@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui
import com.seamless.android.ui.media.FilePickerButton
import androidx.compose.ui.focus.focusRequester
// [Goose] TODO: Replace inline file attachment stub with FilePickerButton abstraction that dispatches via FileShareDispatcher


import androidx.compose.foundation.*
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
                        color = Color(0xFF00FF7F), // Bright green
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        background = Color(0xFF2D2D2D) // Dark gray background
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
                        color = Color(0xFFFF9500), // Orange
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
        
        // Voice and image buttons when no text (only visible in Mesh chat)
        if (value.text.isEmpty() && showMediaButtons) {
            // Hold-to-record microphone
            val bg = if (isDark) Color(0xFF26262E) else Color(0xFFE4E4E9)

            // Ensure latest values are used when finishing recording
            val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
            val latestChannel = rememberUpdatedState(currentChannel)
            val latestOnSendVoiceNote = rememberUpdatedState(onSendVoiceNote)

            // Image button (image picker) - keep layout space reserved during recording so mic button doesn't shift under finger
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isRecording) {
                    ImagePickerButton(
                        onImageReady = { outPath ->
                            onSendImageNote(latestSelectedPeer.value, latestChannel.value, outPath)
                        }
                    )
                }
            }

            Spacer(Modifier.width(1.dp))

            VoiceRecordButton(
                backgroundColor = bg,
                onStart = {
                    isRecording = true
                    elapsedMs = 0L
                    // Keep existing focus to avoid IME collapse, but do not force-show keyboard
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
                    // Extract and cache waveform from the actual audio file to match receiver rendering
                    AudioWaveformExtractor.extractAsync(path, sampleCount = 120) { arr ->
                        if (arr != null) {
                            try { com.seamless.android.features.voice.VoiceWaveformCache.put(path, arr) } catch (_: Exception) {}
                        }
                    }
                    // BLE path (private or public) — use latest values to avoid stale captures
                    latestOnSendVoiceNote.value(
                        latestSelectedPeer.value,
                        latestChannel.value,
                        path
                    )
                }
            )
        } else if (hasText) {
            // Optical White Send Button (#FFFFFF circle with #000000 arrow icon in dark mode)
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White else Color.Black)
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Send",
                    tint = if (isDark) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
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

    // Glassmorphic suggestion box
    val glassColor = if (isDark) 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceDark 
    else 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(glassColor, RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show all aliases together
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }

        Text(
            text = allCommands.joinToString(", "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.primary,
            fontSize = (BASE_FONT_SIZE - 4).sp
        )

        // Show syntax if any
        suggestion.syntax?.let { syntax ->
            Text(
                text = syntax,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = (BASE_FONT_SIZE - 5).sp
            )
        }

        // Show description
        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    
    // Glassmorphic suggestion box
    val glassColor = if (isDark) 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceDark 
    else 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight
        
    Column(
        modifier = modifier
            .background(glassColor, RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp)
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mention_suggestion_at, suggestion),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFFFF9500), // Orange like mentions
            fontSize = (BASE_FONT_SIZE - 4).sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = stringResource(R.string.mention),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp
        )
    }
}
