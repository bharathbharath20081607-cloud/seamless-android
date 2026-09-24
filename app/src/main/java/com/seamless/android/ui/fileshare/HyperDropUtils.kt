package com.seamless.android.ui.fileshare

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * HyperDrop Utilities 2.0
 * Shared formatting, file type, and display utilities for the HyperDrop feature.
 */
object HyperDropUtils {

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
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> Icons.Filled.Image
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> Icons.Filled.VideoFile
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma" -> Icons.Filled.AudioFile
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
    fun getFileTypeColor(fileName: String): androidx.compose.ui.graphics.Color {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" ->
                androidx.compose.ui.graphics.Color(0xFF42A5F5) // Blue
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" ->
                androidx.compose.ui.graphics.Color(0xFFAB47BC) // Purple
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma" ->
                androidx.compose.ui.graphics.Color(0xFFFF7043) // Orange
            "pdf" ->
                androidx.compose.ui.graphics.Color(0xFFEF5350) // Red
            "doc", "docx", "txt", "rtf", "odt" ->
                androidx.compose.ui.graphics.Color(0xFF29B6F6) // Light Blue
            "zip", "rar", "7z", "tar", "gz" ->
                androidx.compose.ui.graphics.Color(0xFFFFCA28) // Amber
            "apk" ->
                androidx.compose.ui.graphics.Color(0xFF66BB6A) // Green
            else ->
                HyperDropColors.PrimaryCyan
        }
    }

    /**
     * Check if the file is an image type that Coil can display.
     */
    fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    }

    /**
     * Check if the file is a video type.
     */
    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")
    }
}
