@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * HyperDrop Utilities 2.0
 * Shared formatting, file type, magic-byte inspection, media preview, and display utilities for HyperDrop.
 */
object HyperDropUtils {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "3g2", "ts")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus")

    // ======================== FILE SIZE FORMATTING ========================

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    // ======================== SPEED FORMATTING ========================

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "—"
        val kbps = bytesPerSec / 1024.0
        val mbps = kbps / 1024.0
        return when {
            mbps >= 1.0 -> String.format("%.1f MB/s", mbps)
            kbps >= 1.0 -> String.format("%.0f KB/s", kbps)
            else -> "$bytesPerSec B/s"
        }
    }

    // ======================== TIME FORMATTING ========================

    fun formatEta(seconds: Int): String {
        return when {
            seconds < 0 -> ""
            seconds == 0 -> "Almost done"
            seconds < 5 -> "A few seconds"
            seconds < 60 -> "~${seconds}s remaining"
            seconds < 120 -> "About a minute"
            seconds < 3600 -> "~${seconds / 60} min remaining"
            else -> "~${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    // ======================== BATCH FORMATTING ========================

    fun formatBatchSummary(fileCount: Int, totalBytes: Long): String {
        val sizeStr = formatFileSize(totalBytes)
        return when (fileCount) {
            0 -> "No files"
            1 -> "1 file • $sizeStr"
            else -> "$fileCount files • $sizeStr"
        }
    }

    // ======================== FILE NAME FORMATTING ========================

    /**
     * Smart truncation that keeps extension visible.
     * "my_very_long_document_name.pdf" → "my_very_long_d…e.pdf"
     */
    fun formatFileName(name: String, maxLength: Int = 28): String {
        if (name.length <= maxLength) return name
        val extIndex = name.lastIndexOf('.')
        return if (extIndex > 0) {
            val ext = name.substring(extIndex) // ".pdf"
            val baseName = name.substring(0, extIndex)
            val availableChars = maxLength - ext.length - 1 // -1 for ellipsis
            if (availableChars > 4) {
                baseName.take(availableChars) + "…" + ext
            } else {
                name.take(maxLength - 1) + "…"
            }
        } else {
            name.take(maxLength - 1) + "…"
        }
    }

    // ======================== FILE TYPE ICONS ========================

    fun getFileTypeIcon(fileName: String): ImageVector {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            in IMAGE_EXTENSIONS -> Icons.Filled.Image
            in VIDEO_EXTENSIONS -> Icons.Filled.VideoFile
            in AUDIO_EXTENSIONS -> Icons.Filled.AudioFile
            "pdf" -> Icons.Filled.PictureAsPdf
            "doc", "docx", "txt", "rtf", "odt" -> Icons.Filled.Description
            "xls", "xlsx", "csv", "ods" -> Icons.Filled.TableChart
            "ppt", "pptx", "odp" -> Icons.Filled.Slideshow
            "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.FolderZip
            "apk" -> Icons.Filled.Android
            else -> Icons.Filled.InsertDriveFile
        }
    }

    /**
     * Returns a color that represents the file type category.
     */
    fun getFileTypeColor(fileName: String): Color {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            in IMAGE_EXTENSIONS -> Color(0xFF42A5F5) // Blue
            in VIDEO_EXTENSIONS -> Color(0xFFAB47BC) // Purple
            in AUDIO_EXTENSIONS -> Color(0xFFFF7043) // Orange
            "pdf" -> Color(0xFFEF5350) // Red
            "doc", "docx", "txt", "rtf", "odt" -> Color(0xFF29B6F6) // Light Blue
            "zip", "rar", "7z", "tar", "gz" -> Color(0xFFFFCA28) // Amber
            "apk" -> Color(0xFF66BB6A) // Green
            else -> HyperDropColors.PrimaryCyan
        }
    }

    /**
     * Check if the file is an image type that Coil can display.
     */
    fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in IMAGE_EXTENSIONS
    }

    fun isImageFile(file: File): Boolean {
        if (isImageFile(file.name)) return true
        return resolveMimeType(file).startsWith("image/")
    }

    /**
     * Check if the file is a video type.
     */
    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS
    }

    fun isVideoFile(file: File): Boolean {
        if (isVideoFile(file.name)) return true
        return resolveMimeType(file).startsWith("video/")
    }

    fun isAudioFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in AUDIO_EXTENSIONS
    }

    // ======================== MIME & MAGIC-BYTE DETECTION ========================

    fun extensionFromMimeType(mimeType: String?): String? {
        if (mimeType.isNullOrBlank()) return null
        val cleanMime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        val explicit = when (cleanMime) {
            "video/mp4", "video/mp4v-es", "application/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/x-matroska", "video/matroska" -> "mkv"
            "video/webm" -> "webm"
            "video/x-msvideo", "video/avi", "video/msvideo" -> "avi"
            "video/3gpp" -> "3gp"
            "video/3gpp2" -> "3g2"
            "video/x-flv" -> "flv"
            "video/x-ms-wmv" -> "wmv"
            "video/mp2t" -> "ts"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/x-wav", "audio/wav" -> "wav"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/aac" -> "aac"
            "application/pdf" -> "pdf"
            "application/zip" -> "zip"
            "application/vnd.android.package-archive" -> "apk"
            "text/plain" -> "txt"
            else -> null
        }
        if (explicit != null) return explicit
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime)
    }

    fun mimeTypeFromExtension(ext: String): String? {
        val cleanExt = ext.trim().trimStart('.').lowercase(Locale.ROOT)
        if (cleanExt.isBlank()) return null
        val explicit = when (cleanExt) {
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "3g2" -> "video/3gpp2"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "ts" -> "video/mp2t"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/x-wav"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            else -> null
        }
        return explicit ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt)
    }

    /**
     * Inspect magic bytes (up to 64 bytes) to identify container extension and MIME type
     * even when a file has no extension or a stripped display name.
     */
    fun detectExtensionAndMimeFromBytes(bytes: ByteArray, length: Int): Pair<String, String>? {
        if (length < 4) return null

        // ISO Base Media File Format (MP4, MOV, 3GP, M4A, HEIC): bytes 4..7 == "ftyp"
        if (length >= 12 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII)
            return when {
                brand == "qt  " -> "mov" to "video/quicktime"
                brand.startsWith("3gp") || brand.startsWith("3g2") -> "3gp" to "video/3gpp"
                brand == "M4A " || brand == "M4B " -> "m4a" to "audio/mp4"
                brand in listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") -> "heic" to "image/heic"
                else -> "mp4" to "video/mp4"
            }
        }

        // Matroska / WebM EBML header: 0x1A 0x45 0xDF 0xA3
        if (bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            val headerAscii = String(bytes, 0, minOf(length, 64), Charsets.ISO_8859_1).lowercase(Locale.ROOT)
            return if (headerAscii.contains("webm")) {
                "webm" to "video/webm"
            } else {
                "mkv" to "video/x-matroska"
            }
        }

        // RIFF containers (AVI, WEBP, WAV)
        if (length >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            val subType = String(bytes, 8, 4, Charsets.US_ASCII)
            return when (subType) {
                "AVI " -> "avi" to "video/x-msvideo"
                "WEBP" -> "webp" to "image/webp"
                "WAVE" -> "wav" to "audio/x-wav"
                else -> null
            }
        }

        // FLV video: "FLV"
        if (bytes[0] == 'F'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'V'.code.toByte()
        ) {
            return "flv" to "video/x-flv"
        }

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "jpg" to "image/jpeg"
        }

        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "png" to "image/png"
        }

        // GIF: "GIF8"
        if (bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()
        ) {
            return "gif" to "image/gif"
        }

        // PDF: "%PDF"
        if (bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            return "pdf" to "application/pdf"
        }

        // MP3: "ID3" or MPEG audio sync word
        if ((bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) ||
            (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0)
        ) {
            return "mp3" to "audio/mpeg"
        }

        // OGG: "OggS"
        if (bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) {
            return "ogg" to "audio/ogg"
        }

        // FLAC: "fLaC"
        if (bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()
        ) {
            return "flac" to "audio/flac"
        }

        // ZIP / APK: 50 4B 03 04
        if (bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            return "zip" to "application/zip"
        }

        return null
    }

    fun detectExtensionAndMimeFromHeader(file: File): Pair<String, String>? {
        if (!file.exists() || !file.isFile || file.length() < 4L) return null
        return runCatching {
            val buffer = ByteArray(64)
            val read = FileInputStream(file).use { it.read(buffer) }
            if (read >= 4) detectExtensionAndMimeFromBytes(buffer, read) else null
        }.getOrNull()
    }

    /**
     * Ensures a received file on disk has a valid file extension matching its content.
     * If the file was sent without an extension (common with Android Photo Picker URIs),
     * this inspects its magic bytes and renames the file on disk to include the extension
     * so FileProvider and external media players recognize its container format.
     */
    fun ensureFileExtension(file: File): File {
        if (!file.exists() || !file.isFile) return file
        val currentExt = file.extension.lowercase(Locale.ROOT)
        val knownMime = mimeTypeFromExtension(currentExt)
        if (currentExt.isNotBlank() && knownMime != null) {
            return file
        }

        val detected = detectExtensionAndMimeFromHeader(file) ?: return file
        val detectedExt = detected.first
        if (currentExt == detectedExt) return file

        val parent = file.parentFile ?: return file
        var targetFile = File(parent, "${file.name}.$detectedExt")
        var counter = 1
        while (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
            targetFile = File(parent, "${file.name} ($counter).$detectedExt")
            counter++
        }

        return if (runCatching { file.renameTo(targetFile) }.getOrDefault(false)) {
            targetFile
        } else {
            file
        }
    }

    /**
     * Resolves the accurate MIME type for a file using extension mapping first
     * and magic-byte header inspection as fallback. Never returns application/octet-stream
     * for recognizable video, audio, or image containers.
     */
    fun resolveMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        mimeTypeFromExtension(ext)?.let { return it }
        detectExtensionAndMimeFromHeader(file)?.second?.let { return it }
        return "*/*"
    }

    /**
     * Finds a received file in `received_files/` even if `ensureFileExtension` appended
     * an extension (e.g. `1000012345` -> `1000012345.mp4`).
     */
    fun findReceivedFile(context: Context, fileName: String): File? {
        val dir = File(context.getExternalFilesDir(null), "received_files")
        if (!dir.exists()) return null
        val direct = File(dir, fileName)
        if (direct.exists()) return ensureFileExtension(direct)

        val match = dir.listFiles()?.firstOrNull { candidate ->
            candidate.name == fileName ||
                candidate.nameWithoutExtension == fileName ||
                candidate.name.startsWith("$fileName.")
        }
        return match?.let { ensureFileExtension(it) }
    }

    /**
     * Opens a file using an external Android application with an explicit MIME type
     * and full FileProvider read permissions (including ClipData for strict video players).
     */
    fun openFileExternally(context: Context, rawFile: File) {
        val file = ensureFileExtension(rawFile)
        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val resolvedMime = resolveMimeType(file)
            val effectiveMime = when {
                isVideoFile(file) && !resolvedMime.startsWith("video/") -> "video/mp4"
                isImageFile(file) && !resolvedMime.startsWith("image/") -> "image/jpeg"
                else -> resolvedMime
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, effectiveMime)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No app found to open this file: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Shares a received file using the system Share sheet with an accurate MIME type.
     */
    fun shareFile(context: Context, rawFile: File) {
        val file = ensureFileExtension(rawFile)
        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = resolveMimeType(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                clipData = ClipData.newRawUri(file.name, fileUri)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Built-in Fullscreen Video & Image Media Preview Dialog for HyperDrop.
 * Plays received videos directly inside Seamless with hardware-accelerated VideoView + MediaController,
 * avoiding OEM Gallery "Video files not supported" errors when opening FileProvider URIs.
 * Also provides one-tap "Open in External App" and "Share" actions in the top bar.
 */
@Composable
fun HyperDropMediaPreviewDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ensuredFile = remember(file.absolutePath) { HyperDropUtils.ensureFileExtension(file) }
    val isVideo = remember(ensuredFile.absolutePath) { HyperDropUtils.isVideoFile(ensuredFile) }
    var isLoading by remember { mutableStateOf(isVideo) }
    var playbackError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isVideo) {
                if (!playbackError) {
                    AndroidView(
                        factory = { ctx ->
                            val container = FrameLayout(ctx).apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                            val videoView = VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.view.Gravity.CENTER
                                )
                            }
                            container.addView(videoView)

                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(container)
                            videoView.setMediaController(mediaController)
                            videoView.setVideoPath(ensuredFile.absolutePath)

                            videoView.setOnPreparedListener { mp ->
                                isLoading = false
                                mp.isLooping = false
                                videoView.start()
                                mediaController.show(3000)
                            }

                            videoView.setOnErrorListener { _, _, _ ->
                                isLoading = false
                                playbackError = true
                                true
                            }

                            container
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isLoading && !playbackError) {
                    CircularProgressIndicator(
                        color = HyperDropColors.PrimaryCyan,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (playbackError) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoFile,
                            contentDescription = null,
                            tint = HyperDropColors.PrimaryCyan,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = ensuredFile.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Button(
                            onClick = {
                                HyperDropUtils.openFileExternally(context, ensuredFile)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HyperDropColors.PrimaryCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in External Video Player", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ensuredFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = ensuredFile.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top bar overlay with file title, Share, Open Externally, and Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ensuredFile.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = HyperDropUtils.formatFileSize(ensuredFile.length()),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = { HyperDropUtils.shareFile(context, ensuredFile) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = HyperDropColors.PrimaryCyan
                    )
                }

                IconButton(
                    onClick = { HyperDropUtils.openFileExternally(context, ensuredFile) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Open in external app",
                        tint = HyperDropColors.PrimaryCyan
                    )
                }
            }
        }
    }
}
