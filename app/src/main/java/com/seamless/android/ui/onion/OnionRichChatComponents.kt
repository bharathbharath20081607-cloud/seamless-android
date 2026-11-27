@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seamless.android.features.voice.VoiceRecorder
import com.seamless.android.net.TorAttachmentTransfer
import com.seamless.android.net.TorRichChatCodec
import com.seamless.android.ui.media.ScrollingWaveformRecorder
import com.seamless.android.ui.media.WaveformPreview
import com.seamless.android.ui.privacy.PrivacyPreferenceManager
import com.seamless.android.ui.theme.concentricCornerRadius
import com.seamless.android.ui.theme.liquidGlass
import com.seamless.android.ui.theme.tactilePress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

internal val AegisEmberColor: Color
    get() = OnionUi.Ember
internal val AegisVaultGradient: Brush
    get() = Brush.linearGradient(
        if (com.seamless.android.ui.theme.ThemePreferenceManager.isCurrentlyDark) {
            listOf(Color(0xFF1D1411), Color(0xFF140E0C))
        } else {
            listOf(Color(0xFFFFF5EE), Color(0xFFFFEBDD))
        }
    )
private val DirectQuickReactions = listOf("❤️", "👍", "🔥", "😂", "😮", "🙏")

internal data class PreparedDocument(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

internal fun prepareDocumentForTor(context: Context, uri: Uri): Result<PreparedDocument> = runCatching {
    val resolver = context.contentResolver
    var fileName = ""
    var reportedSize = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx).orEmpty()
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
            }
        }
    if (reportedSize > TorAttachmentTransfer.MAX_ATTACHMENT_BYTES) {
        error("File exceeds the 1 MB encrypted Tor limit")
    }
    val safeName = TorAttachmentTransfer.sanitizeFileName(fileName)
        .ifBlank { "document_${System.currentTimeMillis()}" }

    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            total += read
            if (total > TorAttachmentTransfer.MAX_ATTACHMENT_BYTES) {
                error("File exceeds the 1 MB encrypted Tor limit")
            }
            out.write(buf, 0, read)
        }
        out.toByteArray()
    } ?: error("Could not read selected file")

    require(bytes.isNotEmpty()) { "Selected file is empty" }
    val detectedMime = resolver.getType(uri)?.takeIf { it.isNotBlank() }
        ?: safeName.substringAfterLast('.', "").lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        ?: "application/octet-stream"
    PreparedDocument(
        bytes = bytes,
        mimeType = TorAttachmentTransfer.normalizeSupportedMimeType(detectedMime),
        fileName = safeName
    )
}

internal suspend fun saveAttachmentToDownloads(
    context: Context,
    source: File,
    fileName: String,
    mimeType: String
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        require(source.exists()) { "File is no longer on device" }
        val cleanName = TorAttachmentTransfer.sanitizeFileName(fileName)
            .ifBlank { source.name }
        val safeMime = TorAttachmentTransfer.normalizeSupportedMimeType(mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                put(MediaStore.Downloads.MIME_TYPE, safeMime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Seamless")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val dest = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads storage unavailable")
            try {
                resolver.openOutputStream(dest)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Failed to write download")
                resolver.update(dest, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
            } catch (e: Exception) {
                resolver.delete(dest, null, null)
                throw e
            }
            "Downloads/Seamless/$cleanName"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, cleanName)
            source.copyTo(outFile, overwrite = true)
            outFile.absolutePath
        }
    }
}

internal fun formatByteCount(bytes: Int): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
}

@Composable
internal fun InChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnionUi.CanvasRaised)
            .border(1.dp, OnionUi.Hairline)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = OnionUi.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = OnionUi.TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(OnionUi.AccentBlue),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search encrypted messages…", color = OnionUi.TextMuted, fontSize = 14.sp)
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Text(
                "$matchCount found",
                color = OnionUi.AccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Close search", tint = OnionUi.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OmniAttachmentSheet(
    burnOnReadArmed: Boolean,
    onToggleBurnOnRead: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onStartVoiceNote: () -> Unit,
    onPickDocument: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = OnionUi.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (burnOnReadArmed) AegisVaultGradient else SolidColorBrush(OnionUi.Surface))
                    .border(
                        1.dp,
                        if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.7f) else OnionUi.Hairline,
                        RoundedCornerShape(16.dp)
                    )
                    .tactilePress(scaleDown = 0.98f, onClick = { onToggleBurnOnRead(!burnOnReadArmed) })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AegisEmberColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = AegisEmberColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "View-Once Secret Mode",
                        color = OnionUi.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Self-destructs after viewing · Blocks screenshots & wipes disk",
                        color = OnionUi.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = burnOnReadArmed,
                    onCheckedChange = onToggleBurnOnRead,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AegisEmberColor
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AttachmentTile(
                    icon = Icons.Default.Photo,
                    title = "Photo",
                    subtitle = "HD Encrypted",
                    tint = OnionUi.Info,
                    modifier = Modifier.weight(1f),
                    onClick = { onDismiss(); onPickPhoto() }
                )
                AttachmentTile(
                    icon = Icons.Default.Mic,
                    title = "Voice Note",
                    subtitle = "Crystal Audio",
                    tint = OnionUi.Online,
                    modifier = Modifier.weight(1f),
                    onClick = { onDismiss(); onStartVoiceNote() }
                )
                AttachmentTile(
                    icon = Icons.Default.Description,
                    title = "File / Doc",
                    subtitle = "Any Document",
                    tint = OnionUi.Indigo,
                    modifier = Modifier.weight(1f),
                    onClick = { onDismiss(); onPickDocument() }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun SolidColorBrush(color: Color): Brush = Brush.linearGradient(listOf(color, color))

@Composable
private fun AttachmentTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OnionUi.Surface)
            .border(1.dp, OnionUi.Hairline, RoundedCornerShape(16.dp))
            .tactilePress(scaleDown = 0.96f, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = OnionUi.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(subtitle, color = OnionUi.TextMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun SovereignChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    burnPlaceholder: String,
    canSend: Boolean,
    enabled: Boolean = true,
    mediaEnabled: Boolean = true,
    isSending: Boolean = false,
    burnOnReadArmed: Boolean,
    onToggleBurnOnRead: () -> Unit,
    isRecordingVoice: Boolean,
    onStartVoiceNote: () -> Unit,
    onCancelVoiceNote: () -> Unit,
    onSendVoiceNote: (bytes: ByteArray, durationMs: Int, fileName: String) -> Unit,
    onOpenAttachments: () -> Unit,
    onSendText: () -> Unit,
    replySenderLabel: String? = null,
    replyPreviewText: String? = null,
    replyAccentColor: Color = OnionUi.AccentBlue,
    onCancelReply: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OnionUi.Canvas.copy(alpha = 0.94f))
            .border(1.dp, OnionUi.Hairline.copy(alpha = 0.45f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(visible = replySenderLabel != null) {
            if (replySenderLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OnionUi.Surface)
                        .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(replyAccentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Replying to $replySenderLabel",
                            color = replyAccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!replyPreviewText.isNullOrBlank()) {
                            Text(
                                text = replyPreviewText,
                                color = OnionUi.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancel reply", tint = OnionUi.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = burnOnReadArmed && !isRecordingVoice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AegisEmberColor.copy(alpha = 0.14f))
                    .border(1.dp, AegisEmberColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = AegisEmberColor, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "View-Once active · Self-destructs after opening",
                    color = AegisEmberColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Disable View-Once",
                    tint = AegisEmberColor,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onToggleBurnOnRead() }
                )
            }
        }

        if (isRecordingVoice) {
            SonicWarpRecorderBar(
                burnOnReadArmed = burnOnReadArmed,
                onCancel = onCancelVoiceNote,
                onSendVoiceNote = onSendVoiceNote
            )
        } else {
            val spillTint = when {
                burnOnReadArmed -> AegisEmberColor
                isFocused -> OnionUi.AccentBlue
                else -> Color.Transparent
            }
            val pillRadius = 26.dp
            val toggleCorner by animateDpAsState(
                targetValue = if (burnOnReadArmed) 11.dp else 17.dp,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
                label = "m3e_burn_shape_morph"
            )
            val ctaCorner by animateDpAsState(
                targetValue = if (canSend || burnOnReadArmed) 15.dp else 24.dp,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
                label = "m3e_cta_shape_morph"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = if (isFocused) 5.dp else 2.dp,
                            shape = RoundedCornerShape(pillRadius),
                            spotColor = Color.Black.copy(alpha = 0.24f)
                        )
                        .liquidGlass(
                            shape = RoundedCornerShape(pillRadius),
                            tintSpill = spillTint,
                            elevated = isFocused || burnOnReadArmed
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenAttachments,
                        enabled = enabled && mediaEnabled && !isSending,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Attachments",
                            tint = if (enabled && mediaEnabled && !isSending) OnionUi.TextPrimary else OnionUi.TextMuted,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 9.dp)
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = TextStyle(
                            color = OnionUi.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(if (burnOnReadArmed) AegisEmberColor else OnionUi.AccentBlue),
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    text = if (burnOnReadArmed) burnPlaceholder else placeholder,
                                    fontSize = 15.sp,
                                    color = if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.85f) else OnionUi.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(toggleCorner))
                            .background(if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.20f) else Color.Transparent)
                            .tactilePress(scaleDown = 0.90f, onClick = onToggleBurnOnRead),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = "Toggle View-Once",
                            tint = if (burnOnReadArmed) AegisEmberColor else OnionUi.TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }

                Spacer(Modifier.width(8.dp))

                val primaryBtnBrush = if (burnOnReadArmed) {
                    Brush.linearGradient(
                        colors = listOf(AegisEmberColor, Color(0xFFC2410C)),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(OnionUi.PrimaryStart, OnionUi.PrimaryEnd),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                }
                val primaryBtnBorder = Brush.linearGradient(
                    colors = if (burnOnReadArmed) {
                        listOf(Color(0xFFFDBA74).copy(alpha = 0.44f), Color(0xFFFDBA74).copy(alpha = 0.12f))
                    } else {
                        listOf(OnionUi.PrimaryRim, OnionUi.PrimaryRim.copy(alpha = 0.12f))
                    },
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
                val primaryIconTint = OnionUi.OnPrimary
                val ctaShape = RoundedCornerShape(ctaCorner)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ctaShape)
                        .background(primaryBtnBrush, ctaShape)
                        .border(1.dp, primaryBtnBorder, ctaShape)
                        .tactilePress(
                            scaleDown = 0.91f,
                            enabled = !isSending,
                            onClick = {
                                if (canSend) onSendText() else onStartVoiceNote()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = primaryIconTint,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = canSend,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.65f,
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 500f)
                                ) + fadeIn(tween(130))).togetherWith(
                                    scaleOut(targetScale = 0.65f, animationSpec = tween(110)) +
                                        fadeOut(tween(100))
                                )
                            },
                            label = "sovereign_composer_action"
                        ) { sendMode ->
                            if (sendMode) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = primaryIconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Record voice note",
                                    tint = primaryIconTint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DateHeaderPill(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(999.dp))
        ) {
            Text(
                text = label,
                color = OnionUi.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun JumpToLatestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.45f))
            .liquidGlass(shape = CircleShape, elevated = true)
            .tactilePress(scaleDown = 0.90f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ArrowDownward,
            contentDescription = "Jump to latest",
            tint = OnionUi.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SonicWarpRecorderBar(
    burnOnReadArmed: Boolean,
    onCancel: () -> Unit,
    onSendVoiceNote: (bytes: ByteArray, durationMs: Int, fileName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    val samples = remember { mutableStateListOf<Float>() }
    var currentAmp by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var finished by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
    val recDotAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    fun finalizeAndSend() {
        if (finished) return
        finished = true
        val file = recorder.stop() ?: recordedFile
        val finalDuration = elapsedMs.coerceAtLeast(1000)
        scope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    if (file != null && file.exists() && file.length() > 0L) {
                        val b = file.readBytes()
                        runCatching { file.delete() }
                        b
                    } else null
                }.getOrNull()
            }
            if (payload == null || payload.isEmpty()) {
                Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show()
                onCancel()
            } else if (payload.size > TorAttachmentTransfer.MAX_ATTACHMENT_BYTES) {
                Toast.makeText(context, "Voice note exceeded maximum size", Toast.LENGTH_SHORT).show()
                onCancel()
            } else {
                onSendVoiceNote(payload, finalDuration, file?.name ?: "voice_note.m4a")
            }
        }
    }

    LaunchedEffect(Unit) {
        val f = withContext(Dispatchers.IO) { recorder.start() }
        if (f == null) {
            Toast.makeText(context, "Unable to start microphone recorder", Toast.LENGTH_SHORT).show()
            onCancel()
            return@LaunchedEffect
        }
        recordedFile = f
        val startedAt = System.currentTimeMillis()
        while (!finished) {
            val amp = recorder.pollAmplitude()
            currentAmp = (amp.toFloat() / 16_000f).coerceIn(0.08f, 1f)
            elapsedMs = (System.currentTimeMillis() - startedAt).toInt()
            if (elapsedMs >= 120_000) {
                finalizeAndSend()
                break
            }
            delay(80)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!finished) {
                val f = recorder.stop() ?: recordedFile
                runCatching { f?.delete() }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(OnionUi.Surface)
            .border(
                1.2.dp,
                if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.75f) else OnionUi.AccentBlue.copy(alpha = 0.55f),
                RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(OnionUi.Danger.copy(alpha = 0.14f))
                .tactilePress(scaleDown = 0.90f, onClick = {
                    finished = true
                    val f = recorder.stop() ?: recordedFile
                    runCatching { f?.delete() }
                    onCancel()
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Delete, "Cancel voice note", tint = OnionUi.Danger, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(recDotAlpha)
                .clip(CircleShape)
                .background(if (burnOnReadArmed) AegisEmberColor else OnionUi.Danger)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = TorRichChatCodec.formatDurationMs(elapsedMs),
            color = OnionUi.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        ScrollingWaveformRecorder(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .padding(horizontal = 8.dp),
            currentAmplitude = currentAmp,
            samples = samples,
            fillColor = if (burnOnReadArmed) AegisEmberColor else OnionUi.AccentBlue,
            baseColor = OnionUi.TextMuted.copy(alpha = 0.35f)
        )
        val recBtnBrush = if (burnOnReadArmed) {
            Brush.verticalGradient(listOf(Color(0xFFFF7A2F), Color(0xFFC2410C)))
        } else {
            Brush.verticalGradient(listOf(OnionUi.PrimaryStart, OnionUi.PrimaryEnd))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(recBtnBrush)
                .border(1.dp, if (burnOnReadArmed) Color(0xFFFDBA74).copy(alpha = 0.32f) else OnionUi.PrimaryRim, CircleShape)
                .tactilePress(scaleDown = 0.90f, onClick = { finalizeAndSend() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                "Send voice note",
                tint = OnionUi.OnPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun OnionVoiceNoteCard(
    filePath: String?,
    durationMsHint: Int,
    outgoing: Boolean,
    accentColor: Color = OnionUi.AccentBlue
) {
    val fileExists = remember(filePath) { filePath?.let(::File)?.exists() == true }
    var isPlaying by remember(filePath) { mutableStateOf(false) }
    var totalDurationMs by remember(filePath) { mutableIntStateOf(durationMsHint.coerceAtLeast(0)) }
    var positionMs by remember(filePath) { mutableIntStateOf(0) }
    var speed by remember(filePath) { mutableFloatStateOf(1.0f) }
    var playerRef by remember(filePath) { mutableStateOf<MediaPlayer?>(null) }

    fun applySpeedIfPlaying(mp: MediaPlayer, targetSpeed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mp.isPlaying) {
            runCatching { mp.playbackParams = mp.playbackParams.setSpeed(targetSpeed) }
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            runCatching {
                playerRef?.apply {
                    if (isPlaying) stop()
                    reset()
                    release()
                }
            }
            playerRef = null
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying, filePath) {
        while (isPlaying) {
            val mp = playerRef
            if (mp != null) {
                runCatching {
                    positionMs = mp.currentPosition.coerceAtLeast(0)
                    if (mp.duration > 0) totalDurationMs = mp.duration
                }
            }
            delay(100)
        }
    }

    fun togglePlay() {
        val path = filePath ?: return
        if (!fileExists) return
        val existing = playerRef
        if (existing == null) {
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    if (duration > 0) totalDurationMs = duration
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = 0
                        runCatching { seekTo(0) }
                    }
                    start()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                        runCatching { playbackParams = playbackParams.setSpeed(speed) }
                    }
                }
                playerRef = mp
                isPlaying = true
            }
        } else if (isPlaying) {
            runCatching { existing.pause() }
            isPlaying = false
        } else {
            runCatching {
                existing.start()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                    applySpeedIfPlaying(existing, speed)
                }
            }
            isPlaying = true
        }
    }

    val progress = if (totalDurationMs > 0) {
        (positionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val fgColor = if (outgoing) OnionUi.OutgoingText else OnionUi.TextPrimary
    val subColor = if (outgoing) OnionUi.OutgoingSubtle else OnionUi.TextSecondary
    val playBtnBrush = if (outgoing) {
        SolidColorBrush(Color(0xFFF5F8FF))
    } else if (accentColor == OnionUi.AccentBlue) {
        Brush.verticalGradient(listOf(OnionUi.PrimaryStart, OnionUi.PrimaryEnd))
    } else {
        SolidColorBrush(accentColor)
    }
    val playIconTint = if (outgoing) Color(0xFF132F63) else OnionUi.OnPrimary
    val waveColor = if (outgoing) Color(0xFFF5F8FF).copy(alpha = 0.92f) else accentColor

    Row(
        modifier = Modifier
            .widthIn(min = 216.dp, max = 252.dp)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(playBtnBrush)
                .clickable(enabled = fileExists) { togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = playIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (filePath != null && fileExists) {
                CompositionLocalProvider(LocalContentColor provides waveColor) {
                    WaveformPreview(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        path = filePath,
                        sendProgress = null,
                        playbackProgress = progress,
                        onSeek = { frac ->
                            val mp = playerRef
                            if (mp != null && totalDurationMs > 0) {
                                val target = (frac * totalDurationMs).toInt()
                                runCatching { mp.seekTo(target) }
                                positionMs = target
                            }
                        }
                    )
                }
            } else {
                Text("Audio unavailable", color = subColor, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeLabel = if (isPlaying || positionMs > 0) {
                    TorRichChatCodec.formatDurationMs(positionMs)
                } else {
                    TorRichChatCodec.formatDurationMs(totalDurationMs)
                }
                Text(timeLabel, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = when {
                        speed >= 1.9f -> "2.0×"
                        speed >= 1.4f -> "1.5×"
                        else -> "1.0×"
                    },
                    color = fgColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(fgColor.copy(alpha = 0.14f))
                        .clickable {
                            val next = when {
                                speed < 1.4f -> 1.5f
                                speed < 1.9f -> 2.0f
                                else -> 1.0f
                            }
                            speed = next
                            playerRef?.let { applySpeedIfPlaying(it, next) }
                        }
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
internal fun OnionDocumentCard(
    filePath: String?,
    fileName: String,
    totalBytes: Int,
    mimeType: String?,
    outgoing: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceFile = remember(filePath) { filePath?.let(::File)?.takeIf { it.exists() } }
    val displayName = fileName.ifBlank { sourceFile?.name ?: "Encrypted document" }
    val extBadge = displayName.substringAfterLast('.', "").uppercase(Locale.US)
        .take(4)
        .ifBlank {
            when (mimeType?.lowercase(Locale.US)) {
                "application/pdf" -> "PDF"
                "text/plain" -> "TXT"
                "application/zip" -> "ZIP"
                else -> "FILE"
            }
        }
    val typeLabel = when (extBadge) {
        "PDF" -> "PDF Document"
        "TXT", "MD", "LOG" -> "Text Document"
        "ZIP", "RAR", "7Z" -> "Archive"
        "DOC", "DOCX" -> "Word Document"
        "XLS", "XLSX", "CSV" -> "Spreadsheet"
        "MP3", "M4A", "WAV", "OGG" -> "Audio File"
        else -> "Encrypted File"
    }
    val fg = if (outgoing) OnionUi.OutgoingText else OnionUi.TextPrimary
    val sub = if (outgoing) OnionUi.OutgoingSubtle else OnionUi.TextSecondary

    Row(
        modifier = Modifier
            .widthIn(min = 212.dp, max = 256.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = sourceFile != null) {
                val fileToSave = sourceFile ?: return@clickable
                scope.launch {
                    saveAttachmentToDownloads(context, fileToSave, displayName, mimeType ?: "application/octet-stream")
                        .onSuccess { dest -> Toast.makeText(context, "Saved to $dest", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "Save failed", Toast.LENGTH_SHORT).show() }
                }
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (outgoing) Color(0xFFF5F8FF).copy(alpha = 0.14f) else OnionUi.Indigo.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                extBadge,
                color = if (outgoing) Color(0xFFF5F8FF) else OnionUi.Indigo,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = fg,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val byteLabel = formatByteCount(if (totalBytes > 0) totalBytes else (sourceFile?.length()?.toInt() ?: 0))
            Text(
                text = if (byteLabel.isNotBlank()) "$byteLabel · $typeLabel" else typeLabel,
                color = sub,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (sourceFile != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    scope.launch {
                        saveAttachmentToDownloads(context, sourceFile, displayName, mimeType ?: "application/octet-stream")
                            .onSuccess { dest -> Toast.makeText(context, "Saved to $dest", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "Save failed", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (outgoing) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = if (outgoing) "Export copy" else "Save file",
                    tint = if (outgoing) Color(0xFFF5F8FF).copy(alpha = 0.76f) else OnionUi.AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal data class ParsedCallEvent(
    val isVideo: Boolean,
    val isMissedOrFailed: Boolean,
    val title: String,
    val subtitle: String
)

internal fun parseCallEventText(rawBody: String): ParsedCallEvent? {
    val clean = rawBody.trim()
    val hasCallGlyph = clean.startsWith("📞") || clean.startsWith("📹")
    val withoutIcon = clean
        .removePrefix("📞")
        .removePrefix("📹")
        .trim()
    val lower = withoutIcon.lowercase(Locale.US)
    val looksLikeCall = hasCallGlyph ||
        lower.startsWith("incoming voice call") ||
        lower.startsWith("outgoing voice call") ||
        lower.startsWith("missed voice call") ||
        lower.startsWith("declined voice call") ||
        lower.startsWith("voice call ended") ||
        lower.startsWith("incoming video call") ||
        lower.startsWith("outgoing video call") ||
        lower.startsWith("missed video call") ||
        lower.startsWith("declined video call") ||
        lower.startsWith("video call ended")
    if (!looksLikeCall) return null

    val isVideo = clean.startsWith("📹") || lower.contains("video call")
    val detail = withoutIcon.substringAfter('(', "").substringBeforeLast(')').trim()
    val baseTitle = withoutIcon.substringBefore('(').trim()
    val isMissedOrFailed = lower.contains("missed") ||
        lower.contains("declined") ||
        detail.equals("Unreachable", ignoreCase = true) ||
        detail.equals("Busy", ignoreCase = true) ||
        detail.contains("error", ignoreCase = true) ||
        detail.contains("Lost connection", ignoreCase = true)
    val subtitle = when {
        detail.isNotEmpty() -> detail
        isMissedOrFailed -> "Tap to call back"
        else -> "Encrypted call"
    }
    return ParsedCallEvent(
        isVideo = isVideo,
        isMissedOrFailed = isMissedOrFailed,
        title = baseTitle,
        subtitle = subtitle
    )
}

@Composable
internal fun CallEventChatCard(
    event: ParsedCallEvent,
    timestampText: String,
    isFromMe: Boolean,
    onCallBack: (Boolean) -> Unit
) {
    val accentColor = when {
        event.isMissedOrFailed -> OnionUi.Danger
        event.isVideo -> OnionUi.Info
        else -> OnionUi.Online
    }
    val cardBorder = if (event.isMissedOrFailed) OnionUi.Danger.copy(alpha = 0.34f) else OnionUi.Hairline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 216.dp, max = 292.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(OnionUi.Surface)
                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                .tactilePress(scaleDown = 0.97f, onClick = { onCallBack(event.isVideo) })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        event.isMissedOrFailed -> Icons.Rounded.CallEnd
                        event.isVideo -> Icons.Rounded.Videocam
                        else -> Icons.Rounded.Call
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = if (event.isMissedOrFailed) accentColor else OnionUi.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${event.subtitle} · $timestampText",
                    color = OnionUi.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (event.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Call back",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun AegisBurnCapsuleCard(
    kindLabel: String,
    isFromMe: Boolean,
    isBurned: Boolean,
    isReady: Boolean,
    hasShredReceipt: Boolean,
    groupShredCount: Int = 0,
    onOpenCapsule: () -> Unit
) {
    if (isBurned) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = AegisEmberColor.copy(alpha = 0.78f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Opened & permanently shredded",
                color = if (isFromMe) OnionUi.OutgoingSubtle else OnionUi.TextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic
            )
        }
        return
    }

    val statusText = when {
        isFromMe && groupShredCount > 0 -> "Opened & shredded by $groupShredCount"
        isFromMe && hasShredReceipt -> "Opened & shredded on their device"
        isFromMe -> "Sealed · Waiting to be opened"
        !isReady -> "Decrypting sealed message…"
        else -> "Tap to view once · Screenshot-protected"
    }

    Row(
        modifier = Modifier
            .widthIn(min = 216.dp, max = 256.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!isFromMe && isReady) Modifier.clickable(onClick = onOpenCapsule)
                else Modifier
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AegisEmberColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isFromMe) Icons.Default.Lock else Icons.Default.LocalFireDepartment,
                null,
                tint = AegisEmberColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "View-Once $kindLabel",
                color = if (isFromMe) OnionUi.OutgoingText else OnionUi.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = statusText,
                color = if (hasShredReceipt || (!isFromMe && isReady)) AegisEmberColor
                else if (isFromMe) OnionUi.OutgoingSubtle else OnionUi.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
internal fun AegisBurnViewerDialog(
    senderLabel: String,
    secretText: String? = null,
    attachmentFile: File? = null,
    attachmentMimeType: String? = null,
    ttlSeconds: Int = 12,
    onBurnAndShred: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val isVoice = attachmentMimeType?.startsWith("audio/") == true
    val isImage = attachmentMimeType?.startsWith("image/") == true
    val effectiveTtlMs = remember(isVoice, ttlSeconds) {
        if (isVoice) 30_000L else ttlSeconds.coerceIn(5, 60) * 1000L
    }
    var remainingMs by remember { mutableIntStateOf(effectiveTtlMs.toInt()) }
    var shredded by remember { mutableStateOf(false) }
    val currentOnBurn by rememberUpdatedState(onBurnAndShred)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun triggerShredAndClose() {
        if (!shredded) {
            shredded = true
            currentOnBurn()
        }
        currentOnDismiss()
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val wasAlreadySecure = window != null &&
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasAlreadySecure && !PrivacyPreferenceManager.incognitoMode.value) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (!shredded) {
                shredded = true
                currentOnBurn()
            }
        }
    }

    LaunchedEffect(effectiveTtlMs) {
        val started = System.currentTimeMillis()
        while (true) {
            val left = (effectiveTtlMs - (System.currentTimeMillis() - started)).toInt().coerceAtLeast(0)
            remainingMs = left
            if (left <= 0) {
                triggerShredAndClose()
                break
            }
            delay(100)
        }
    }

    val photoBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = attachmentFile?.absolutePath) {
        value = if (isImage && attachmentFile?.exists() == true) {
            withContext(Dispatchers.IO) { decodeScaledFileBitmap(attachmentFile.absolutePath, maxDimensionPx = 1800) }
        } else null
    }

    Dialog(
        onDismissRequest = { triggerShredAndClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060507))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AegisEmberColor.copy(alpha = 0.16f))
                        .border(1.dp, AegisEmberColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, null, tint = AegisEmberColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Screenshot-Protected · $senderLabel",
                        color = AegisEmberColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (remainingMs.toFloat() / effectiveTtlMs.toFloat()).coerceIn(0f, 1f) },
                        color = AegisEmberColor,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        "${((remainingMs + 999) / 1000)}s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(vertical = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    secretText != null -> {
                        Surface(
                            color = Color(0xFF141118),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AegisEmberColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        ) {
                            Text(
                                text = secretText,
                                color = Color.White,
                                fontSize = 19.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(22.dp)
                            )
                        }
                    }
                    isImage && photoBitmap != null -> {
                        Image(
                            bitmap = photoBitmap!!,
                            contentDescription = "View-Once photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                    isVoice && attachmentFile != null -> {
                        Surface(
                            color = Color(0xFF141118),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .border(1.dp, AegisEmberColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                .padding(18.dp)
                        ) {
                            OnionVoiceNoteCard(
                                filePath = attachmentFile.absolutePath,
                                durationMsHint = 0,
                                outgoing = true,
                                accentColor = AegisEmberColor
                            )
                        }
                    }
                    else -> {
                        CircularProgressIndicator(color = AegisEmberColor)
                    }
                }
            }

            Button(
                onClick = { triggerShredAndClose() },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisEmberColor, contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Close & Shred Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DirectMessageActionSheet(
    onDismiss: () -> Unit,
    onReact: ((String) -> Unit)?,
    onReply: () -> Unit,
    onCopy: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = OnionUi.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            if (onReact != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(OnionUi.Surface)
                        .border(1.dp, OnionUi.Hairline, RoundedCornerShape(18.dp))
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectQuickReactions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(OnionUi.SurfacePressed)
                                .clickable { onReact(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 21.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DirectActionPill("Reply", Icons.AutoMirrored.Filled.Reply, Modifier.weight(1f), onClick = onReply)
                if (onCopy != null) {
                    DirectActionPill("Copy", Icons.Default.ContentCopy, Modifier.weight(1f), onClick = onCopy)
                }
            }
            if (onEdit != null || onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onEdit != null) {
                        DirectActionPill("Edit", Icons.Default.Edit, Modifier.weight(1f), onClick = onEdit)
                    }
                    if (onDelete != null) {
                        DirectActionPill("Delete", Icons.Default.Delete, Modifier.weight(1f), tint = OnionUi.Danger, onClick = onDelete)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun DirectActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    tint: Color = OnionUi.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(OnionUi.Surface)
            .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
