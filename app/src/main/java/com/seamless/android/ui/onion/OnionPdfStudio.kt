@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seamless.android.net.TorAttachmentTransfer
import com.seamless.android.ui.privacy.PrivacyPreferenceManager
import com.seamless.android.ui.theme.concentricCornerRadius
import com.seamless.android.ui.theme.liquidGlass
import com.seamless.android.ui.theme.tactilePress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sovereign PDF Studio, Pixel-Burned Redactor, and Air-Gapped In-App PDF Reader
 * for up to 50 MB zero-server Tor P2P document sharing.
 */
internal object PdfDocumentTools {

    enum class PdfProcessPreset(val title: String, val subtitle: String) {
        ORIGINAL("Original", "Exact file & vectors"),
        CLEAN_METADATA("Strip Metadata", "Flat safe render"),
        TOR_EXPRESS("Tor Compact", "Fastest over Tor")
    }

    data class NormalizedRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    data class StagedDocument(
        val file: File,
        val fileName: String,
        val mimeType: String,
        val totalBytes: Int,
        val sha256Hex: String,
        val isPdf: Boolean,
        val pageCount: Int = 0,
        val isPasswordProtected: Boolean = false,
        val coverPreviewBytes: ByteArray? = null,
        val durationMs: Int = 0
    )

    class PdfSession private constructor(
        private val pfd: ParcelFileDescriptor,
        private val renderer: PdfRenderer
    ) {
        val pageCount: Int = renderer.pageCount
        private val mutex = Mutex()

        suspend fun renderPage(
            pageIndex: Int,
            maxDimensionPx: Int,
            redactions: List<NormalizedRect> = emptyList()
        ): Bitmap? = mutex.withLock {
            withContext(Dispatchers.IO) {
                if (pageIndex !in 0 until pageCount) return@withContext null
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val srcW = page.width.coerceAtLeast(1)
                        val srcH = page.height.coerceAtLeast(1)
                        val scale = maxDimensionPx.toFloat() / max(srcW, srcH).toFloat()
                        val targetW = (srcW * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (srcH * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (redactions.isNotEmpty()) {
                            val canvas = Canvas(bmp)
                            val paint = Paint().apply {
                                color = android.graphics.Color.BLACK
                                style = Paint.Style.FILL
                                isAntiAlias = false
                            }
                            for (rect in redactions) {
                                canvas.drawRect(
                                    rect.left.coerceIn(0f, 1f) * targetW,
                                    rect.top.coerceIn(0f, 1f) * targetH,
                                    rect.right.coerceIn(0f, 1f) * targetW,
                                    rect.bottom.coerceIn(0f, 1f) * targetH,
                                    paint
                                )
                            }
                        }
                        bmp
                    }
                }.getOrNull()
            }
        }

        fun close() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }

        companion object {
            fun open(file: File): Result<PdfSession> = runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    throw e
                }
                PdfSession(pfd, renderer)
            }
        }
    }

    private val coverBitmapCache = LruCache<String, ImageBitmap>(48)

    fun getCachedCoverBitmap(file: File): ImageBitmap? {
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        coverBitmapCache.get(key)?.let { return it }
        if (!file.exists() || !file.isFile) return null
        val lower = file.name.lowercase(Locale.US)
        val bmp = when {
            lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".png") ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            lower.endsWith(".pdf") ->
                renderFirstPageSync(file, maxDimensionPx = 220)
            else -> {
                // Also support content that starts with %PDF- regardless of extension
                val isPdfMagic = runCatching {
                    val hdr = ByteArray(5)
                    file.inputStream().use { it.read(hdr) == 5 && String(hdr, Charsets.US_ASCII) == "%PDF-" }
                }.getOrDefault(false)
                if (isPdfMagic) renderFirstPageSync(file, maxDimensionPx = 220) else null
            }
        } ?: return null
        val imageBmp = bmp.asImageBitmap()
        coverBitmapCache.put(key, imageBmp)
        return imageBmp
    }

    private fun renderFirstPageSync(file: File, maxDimensionPx: Int): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val srcW = page.width.coerceAtLeast(1)
                    val srcH = page.height.coerceAtLeast(1)
                    val scale = maxDimensionPx.toFloat() / max(srcW, srcH).toFloat()
                    val w = (srcW * scale).roundToInt().coerceAtLeast(1)
                    val h = (srcH * scale).roundToInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()

    private fun compressCoverPreviewWebp(bitmap: Bitmap): ByteArray? {
        val maxSide = max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scaled = if (maxSide > 320) {
            val scale = 320f / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val out = ByteArrayOutputStream()
        for (quality in intArrayOf(74, 58, 42, 26)) {
            out.reset()
            scaled.compress(format, quality, out)
            if (out.size() in 1..TorAttachmentTransfer.MAX_PREVIEW_BYTES) {
                if (scaled !== bitmap) scaled.recycle()
                return out.toByteArray()
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray().takeIf { it.size in 1..TorAttachmentTransfer.MAX_PREVIEW_BYTES }
    }

    suspend fun stageUriForTor(context: Context, uri: Uri): Result<StagedDocument> = withContext(Dispatchers.IO) {
        runCatching {
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
                error("File exceeds the 50 MB encrypted Tor limit")
            }
            val safeName = TorAttachmentTransfer.sanitizeFileName(fileName)
                .ifBlank { "document_${System.currentTimeMillis()}" }
            val detectedMime = resolver.getType(uri)?.takeIf { it.isNotBlank() }
                ?: safeName.substringAfterLast('.', "").lowercase(Locale.US)
                    .takeIf { it.isNotBlank() }
                    ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "application/octet-stream"
            val normMime = TorAttachmentTransfer.normalizeSupportedMimeType(detectedMime)
            val ext = safeName.substringAfterLast('.', "").lowercase(Locale.US)
                .takeIf { it.length in 1..6 && it.all(Char::isLetterOrDigit) }
                ?: if (normMime == "application/pdf") "pdf" else "tmp"
            val stageFile = File(context.cacheDir, "tor_doc_stage_${UUID.randomUUID()}.$ext")
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(32_768)
            var total = 0L
            var hasPdfMagic = false
            try {
                resolver.openInputStream(uri)?.use { input ->
                    stageFile.outputStream().use { out ->
                        var firstRead = true
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            if (firstRead) {
                                firstRead = false
                                if (read >= 5 && String(buf, 0, 5, Charsets.US_ASCII) == "%PDF-") {
                                    hasPdfMagic = true
                                }
                            }
                            total += read
                            if (total > TorAttachmentTransfer.MAX_ATTACHMENT_BYTES) {
                                error("File exceeds the 50 MB encrypted Tor limit")
                            }
                            out.write(buf, 0, read)
                            digest.update(buf, 0, read)
                        }
                    }
                } ?: error("Could not read selected file")
                if (total <= 0L) error("Selected file is empty")
            } catch (e: Exception) {
                runCatching { stageFile.delete() }
                throw e
            }

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            val isPdf = hasPdfMagic ||
                normMime == "application/pdf" ||
                safeName.lowercase(Locale.US).endsWith(".pdf")
            val effectiveMime = if (isPdf) "application/pdf" else normMime
            val isVideo = effectiveMime.startsWith("video/")

            var pageCount = 0
            var passwordProtected = false
            var coverPreview: ByteArray? = null
            var durationMs = 0

            if (isPdf) {
                PdfSession.open(stageFile).fold(
                    onSuccess = { session ->
                        try {
                            pageCount = session.pageCount
                            if (pageCount > 0) {
                                val firstBmp = session.renderPage(0, maxDimensionPx = 320)
                                if (firstBmp != null) {
                                    coverPreview = compressCoverPreviewWebp(firstBmp)
                                    firstBmp.recycle()
                                }
                            }
                        } finally {
                            session.close()
                        }
                    },
                    onFailure = { err ->
                        if (err is SecurityException) {
                            passwordProtected = true
                        }
                    }
                )
            } else if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(stageFile.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val posterBmp = retriever.getFrameAtTime(150_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L)
                    if (posterBmp != null) {
                        coverPreview = compressCoverPreviewWebp(posterBmp)
                        posterBmp.recycle()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }
            }

            StagedDocument(
                file = stageFile,
                fileName = safeName,
                mimeType = effectiveMime,
                totalBytes = total.toInt(),
                sha256Hex = shaHex,
                isPdf = isPdf,
                pageCount = pageCount,
                isPasswordProtected = passwordProtected,
                coverPreviewBytes = coverPreview,
                durationMs = durationMs
            )
        }
    }

    suspend fun buildProcessedPdf(
        context: Context,
        source: StagedDocument,
        selectedPages: List<Int>,
        redactionsByPage: Map<Int, List<NormalizedRect>>,
        preset: PdfProcessPreset
    ): Result<StagedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.isPdf && source.pageCount > 0) { "Invalid PDF document" }
            val orderedPages = selectedPages.distinct().filter { it in 0 until source.pageCount }.sorted()
            require(orderedPages.isNotEmpty()) { "Select at least one page to send" }

            val hasRedactions = orderedPages.any { !redactionsByPage[it].isNullOrEmpty() }
            val keepsAllPages = orderedPages.size == source.pageCount
            if (preset == PdfProcessPreset.ORIGINAL && keepsAllPages && !hasRedactions) {
                return@runCatching source
            }

            val outFile = File(context.cacheDir, "tor_pdf_built_${UUID.randomUUID()}.pdf")
            val pdfDoc = PdfDocument()
            val targetMaxDim = when (preset) {
                PdfProcessPreset.TOR_EXPRESS -> 1120
                PdfProcessPreset.CLEAN_METADATA,
                PdfProcessPreset.ORIGINAL -> 1580
            }
            var firstCoverPreview: ByteArray? = null

            val session = PdfSession.open(source.file).getOrThrow()
            try {
                ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { rawRenderer ->
                        orderedPages.forEachIndexed { outIdx, srcIdx ->
                            val (pagePtW, pagePtH) = rawRenderer.openPage(srcIdx).use { p ->
                                p.width.coerceAtLeast(1) to p.height.coerceAtLeast(1)
                            }
                            val rendered = session.renderPage(
                                pageIndex = srcIdx,
                                maxDimensionPx = targetMaxDim,
                                redactions = redactionsByPage[srcIdx].orEmpty()
                            ) ?: error("Unable to render page ${srcIdx + 1}")

                            val pageBmp = if (preset == PdfProcessPreset.TOR_EXPRESS) {
                                val jpegOut = ByteArrayOutputStream()
                                rendered.compress(Bitmap.CompressFormat.JPEG, 72, jpegOut)
                                rendered.recycle()
                                val jpegBytes = jpegOut.toByteArray()
                                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                    ?: error("Failed to compact page ${srcIdx + 1}")
                            } else {
                                rendered
                            }

                            if (outIdx == 0) {
                                firstCoverPreview = compressCoverPreviewWebp(pageBmp)
                            }

                            val pageInfo = PdfDocument.PageInfo.Builder(pagePtW, pagePtH, outIdx + 1).create()
                            val page = pdfDoc.startPage(pageInfo)
                            page.canvas.drawBitmap(
                                pageBmp,
                                null,
                                Rect(0, 0, pagePtW, pagePtH),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            pdfDoc.finishPage(page)
                            pageBmp.recycle()
                        }
                    }
                }
                outFile.outputStream().use { out ->
                    pdfDoc.writeTo(out)
                    out.flush()
                }
            } catch (e: Exception) {
                runCatching { outFile.delete() }
                throw e
            } finally {
                runCatching { pdfDoc.close() }
                session.close()
            }

            val totalLen = outFile.length()
            if (totalLen <= 0L || totalLen > TorAttachmentTransfer.MAX_ATTACHMENT_BYTES) {
                runCatching { outFile.delete() }
                error("Processed PDF exceeds the 50 MB encrypted Tor limit")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(32_768)
            outFile.inputStream().use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }

            StagedDocument(
                file = outFile,
                fileName = source.fileName,
                mimeType = "application/pdf",
                totalBytes = totalLen.toInt(),
                sha256Hex = shaHex,
                isPdf = true,
                pageCount = orderedPages.size,
                isPasswordProtected = false,
                coverPreviewBytes = firstCoverPreview ?: source.coverPreviewBytes
            )
        }
    }
}

private tailrec fun Context.findPdfHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPdfHostActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPreSendStudioSheet(
    staged: PdfDocumentTools.StagedDocument,
    initialBurnOnRead: Boolean,
    onDismiss: () -> Unit,
    onSendConfirmed: (
        selectedPages: List<Int>,
        redactionsByPage: Map<Int, List<PdfDocumentTools.NormalizedRect>>,
        preset: PdfDocumentTools.PdfProcessPreset,
        viewOnce: Boolean
    ) -> Unit
) {
    val selectedPages = remember(staged.file.absolutePath, staged.pageCount) {
        mutableStateMapOf<Int, Boolean>().apply {
            for (i in 0 until staged.pageCount) put(i, true)
        }
    }
    val redactionsByPage = remember(staged.file.absolutePath) {
        mutableStateMapOf<Int, List<PdfDocumentTools.NormalizedRect>>()
    }
    var preset by remember { mutableStateOf(PdfDocumentTools.PdfProcessPreset.ORIGINAL) }
    var viewOnce by remember(staged.file.absolutePath) { mutableStateOf(initialBurnOnRead) }
    var redactingPageIndex by remember { mutableStateOf<Int?>(null) }

    val sessionResult = remember(staged.file.absolutePath) { PdfDocumentTools.PdfSession.open(staged.file) }
    val session = sessionResult.getOrNull()
    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    val activeIndices by remember {
        derivedStateOf {
            (0 until staged.pageCount).filter { selectedPages[it] == true }
        }
    }
    val activeRedactionCount by remember {
        derivedStateOf {
            activeIndices.sumOf { redactionsByPage[it]?.size ?: 0 }
        }
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(OnionUi.Indigo.copy(alpha = 0.16f))
                        .border(1.dp, OnionUi.Indigo.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = OnionUi.Indigo, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = staged.fileName,
                        color = OnionUi.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val sizeText = formatByteCount(staged.totalBytes)
                    val redactionSuffix = if (activeRedactionCount > 0) " · $activeRedactionCount redacted" else ""
                    Text(
                        text = "${activeIndices.size} of ${staged.pageCount} pages · $sizeText$redactionSuffix",
                        color = OnionUi.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(OnionUi.Online.copy(alpha = 0.14f))
                        .border(1.dp, OnionUi.Online.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = OnionUi.Online, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SHA-256", color = OnionUi.Online, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pages & Redaction",
                    color = OnionUi.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val selectAll = activeIndices.size < staged.pageCount
                            for (i in 0 until staged.pageCount) selectedPages[i] = selectAll
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (activeIndices.size < staged.pageCount) "Select All" else "Clear",
                            color = OnionUi.AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (session != null && staged.pageCount > 0) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(staged.pageCount, key = { it }) { pageIdx ->
                        val isIncluded = selectedPages[pageIdx] == true
                        val redactions = redactionsByPage[pageIdx].orEmpty()
                        val thumb by produceState<ImageBitmap?>(
                            initialValue = null,
                            key1 = staged.file.absolutePath,
                            key2 = pageIdx,
                            key3 = redactions.size
                        ) {
                            value = session.renderPage(
                                pageIndex = pageIdx,
                                maxDimensionPx = 280,
                                redactions = redactions
                            )?.asImageBitmap()
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(86.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(OnionUi.Surface)
                                .border(
                                    width = if (isIncluded) 1.5.dp else 1.dp,
                                    color = if (isIncluded) OnionUi.AccentBlue else OnionUi.Hairline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedPages[pageIdx] = !isIncluded }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(concentricCornerRadius(14.dp, 6.dp)))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb!!,
                                        contentDescription = "Page ${pageIdx + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = OnionUi.AccentBlue,
                                        strokeWidth = 2.dp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isIncluded) OnionUi.AccentBlue else Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isIncluded) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${pageIdx + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (redactions.isNotEmpty()) AegisEmberColor.copy(alpha = 0.16f)
                                        else OnionUi.SurfacePressed
                                    )
                                    .clickable { redactingPageIndex = pageIdx }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    tint = if (redactions.isNotEmpty()) AegisEmberColor else OnionUi.TextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (redactions.isNotEmpty()) "Black (${redactions.size})" else "Redact",
                                    color = if (redactions.isNotEmpty()) AegisEmberColor else OnionUi.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Delivery Mode",
                color = OnionUi.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfDocumentTools.PdfProcessPreset.entries.forEach { option ->
                    val selected = preset == option
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) OnionUi.AccentBlue.copy(alpha = 0.15f) else OnionUi.Surface
                            )
                            .border(
                                1.dp,
                                if (selected) OnionUi.AccentBlue else OnionUi.Hairline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { preset = option }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            option.title,
                            color = if (selected) OnionUi.AccentBlue else OnionUi.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            option.subtitle,
                            color = OnionUi.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (viewOnce) AegisEmberColor.copy(alpha = 0.12f) else OnionUi.Surface)
                    .border(
                        1.dp,
                        if (viewOnce) AegisEmberColor.copy(alpha = 0.5f) else OnionUi.Hairline,
                        RoundedCornerShape(14.dp)
                    )
                    .tactilePress(scaleDown = 0.98f, onClick = { viewOnce = !viewOnce })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = AegisEmberColor, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("View-Once Self-Destruct", color = OnionUi.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text("Shreds PDF on recipient's device after closing", color = OnionUi.TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = viewOnce,
                    onCheckedChange = { viewOnce = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AegisEmberColor
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onSendConfirmed(activeIndices, redactionsByPage.toMap(), preset, viewOnce)
                },
                enabled = activeIndices.isNotEmpty(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewOnce) AegisEmberColor else OnionUi.PrimaryStart,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val actionText = if (viewOnce) "Send View-Once PDF" else "Send Encrypted PDF"
                Text(actionText, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    val redactTarget = redactingPageIndex
    if (redactTarget != null && session != null) {
        PdfPageRedactorDialog(
            session = session,
            pageIndex = redactTarget,
            initialRects = redactionsByPage[redactTarget].orEmpty(),
            onApply = { updatedRects ->
                if (updatedRects.isEmpty()) redactionsByPage.remove(redactTarget)
                else redactionsByPage[redactTarget] = updatedRects
                selectedPages[redactTarget] = true
                redactingPageIndex = null
            },
            onDismiss = { redactingPageIndex = null }
        )
    }
}

@Composable
private fun PdfPageRedactorDialog(
    session: PdfDocumentTools.PdfSession,
    pageIndex: Int,
    initialRects: List<PdfDocumentTools.NormalizedRect>,
    onApply: (List<PdfDocumentTools.NormalizedRect>) -> Unit,
    onDismiss: () -> Unit
) {
    val boxes = remember(pageIndex) { mutableStateListOf<PdfDocumentTools.NormalizedRect>().apply { addAll(initialRects) } }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val pageBmp by produceState<ImageBitmap?>(initialValue = null, key1 = pageIndex) {
        value = session.renderPage(pageIndex, maxDimensionPx = 1400)?.asImageBitmap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090E))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cancel redaction", tint = Color.White)
                    }
                    Column {
                        Text("Redact Page ${pageIndex + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Drag across text to permanently black out", color = Color(0xFFA7B3C8), fontSize = 11.5.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { if (boxes.isNotEmpty()) boxes.removeAt(boxes.lastIndex) },
                        enabled = boxes.isNotEmpty()
                    ) {
                        Text("Undo", color = if (boxes.isNotEmpty()) OnionUi.AccentBlue else Color(0xFF59657B))
                    }
                    Button(
                        onClick = { onApply(boxes.toList()) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AegisEmberColor, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageBmp
                if (bmp == null) {
                    CircularProgressIndicator(color = OnionUi.AccentBlue)
                } else {
                    val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .pointerInput(pageIndex) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    dragStart = down.position
                                    dragCurrent = down.position
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            val start = dragStart
                                            val end = dragCurrent
                                            if (start != null && end != null && size.width > 0 && size.height > 0) {
                                                val l = (min(start.x, end.x) / size.width).coerceIn(0f, 1f)
                                                val t = (min(start.y, end.y) / size.height).coerceIn(0f, 1f)
                                                val r = (max(start.x, end.x) / size.width).coerceIn(0f, 1f)
                                                val b = (max(start.y, end.y) / size.height).coerceIn(0f, 1f)
                                                if (r - l > 0.015f && b - t > 0.01f) {
                                                    boxes.add(PdfDocumentTools.NormalizedRect(l, t, r, b))
                                                }
                                            }
                                            dragStart = null
                                            dragCurrent = null
                                            break
                                        }
                                        change.consume()
                                        dragCurrent = change.position
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Page to redact",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (rect in boxes) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(rect.left * size.width, rect.top * size.height),
                                    size = Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height)
                                )
                            }
                            val s = dragStart
                            val c = dragCurrent
                            if (s != null && c != null) {
                                val topLeft = Offset(min(s.x, c.x), min(s.y, c.y))
                                val boxSize = Size(abs(c.x - s.x), abs(c.y - s.y))
                                drawRect(color = Color.Black.copy(alpha = 0.75f), topLeft = topLeft, size = boxSize)
                                drawRect(color = AegisEmberColor, topLeft = topLeft, size = boxSize, style = Stroke(width = 3f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131823))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Burned permanently into pixels · Underlying text destroyed",
                    color = Color(0xFFA7B3C8),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                if (boxes.isNotEmpty()) {
                    Text(
                        "Clear page",
                        color = OnionUi.Danger,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { boxes.clear() }
                            .padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmbeddedPdfReaderContent(
    file: File,
    initialPage: Int = 0,
    darkPaperMode: Boolean = false,
    onPageChanged: (currentOneBasedPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val sessionResult = remember(file.absolutePath, file.length()) { PdfDocumentTools.PdfSession.open(file) }
    val session = sessionResult.getOrNull()
    val error = sessionResult.exceptionOrNull()

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (session == null || session.pageCount <= 0) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = OnionUi.Warning, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (error is SecurityException) {
                    "This PDF is password-protected"
                } else {
                    "Unable to render PDF document"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap the download button to export and open it with a password-capable viewer.",
                color = Color(0xFFA7B3C8),
                fontSize = 13.sp
            )
        }
        return
    }

    val safeInitial = initialPage.coerceIn(0, (session.pageCount - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitial)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val pageCache = remember(file.absolutePath, file.length()) { LruCache<Int, ImageBitmap>(8) }

    val currentVisibleIdx by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, session.pageCount - 1) }
    }

    LaunchedEffect(currentVisibleIdx, session.pageCount) {
        onPageChanged(currentVisibleIdx + 1, session.pageCount)
    }

    val darkPaperFilter = remember(darkPaperMode) {
        if (!darkPaperMode) null
        else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.92f, 0f, 0f, 0f, 240f,
                    0f, -0.92f, 0f, 0f, 244f,
                    0f, 0f, -0.88f, 0f, 250f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(session.pageCount) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.15f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.0f
                        }
                    }
                )
            }
            .pointerInput(session.pageCount) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2 || zoomScale > 1.05f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val nextScale = (zoomScale * zoomChange).coerceIn(1f, 3.2f)
                            zoomScale = nextScale
                            if (nextScale <= 1.03f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                val maxX = (size.width * (nextScale - 1f)) / 2f
                                val maxY = (size.height * (nextScale - 1f)) / 2f
                                panOffset = Offset(
                                    x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            }
                            if (pressedCount >= 2 || abs(panChange.x) > abs(panChange.y)) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val pageWidthPx = constraints.maxWidth.coerceAtLeast(720)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            items(session.pageCount, key = { it }) { index ->
                val rendered by produceState<ImageBitmap?>(
                    initialValue = pageCache.get(index),
                    key1 = file.absolutePath,
                    key2 = index
                ) {
                    val cached = pageCache.get(index)
                    if (cached != null) {
                        value = cached
                    } else {
                        val bmp = session.renderPage(index, maxDimensionPx = max(1440, pageWidthPx))
                        if (bmp != null) {
                            val img = bmp.asImageBitmap()
                            pageCache.put(index, img)
                            value = img
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (darkPaperMode) Color(0xFF121620) else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (rendered != null) {
                        Image(
                            bitmap = rendered!!,
                            contentDescription = "PDF page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = darkPaperFilter,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OnionUi.AccentBlue)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${index + 1} / ${session.pageCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AegisPdfViewerDialog(
    file: File,
    fileName: String,
    sha256Hex: String = "",
    initialPage: Int = 0,
    isSecureMode: Boolean = false,
    onQuotePage: ((oneBasedPage: Int) -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findPdfHostActivity() }
    var darkPaper by remember { mutableStateOf(false) }
    var currentPage by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0) + 1) }
    var totalPages by remember { mutableIntStateOf(1) }

    if (isSecureMode) {
        DisposableEffect(activity) {
            val window = activity?.window
            val wasAlreadySecure = window != null &&
                (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (!wasAlreadySecure && !PrivacyPreferenceManager.incognitoMode.value) {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06080D))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            EmbeddedPdfReaderContent(
                file = file,
                initialPage = initialPage,
                darkPaperMode = darkPaper,
                onPageChanged = { pageOneBased, total ->
                    currentPage = pageOneBased
                    totalPages = total.coerceAtLeast(1)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = if (onQuotePage != null) 76.dp else 16.dp)
            )

            // Top Liquid Glass Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .liquidGlass(shape = RoundedCornerShape(18.dp), elevated = true)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Close reader", tint = OnionUi.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fileName.ifBlank { "Encrypted PDF" },
                        color = OnionUi.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Page $currentPage of $totalPages",
                            color = OnionUi.TextSecondary,
                            fontSize = 11.sp
                        )
                        if (sha256Hex.length >= 12) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "· Verified ${sha256Hex.take(8)}…",
                                color = OnionUi.Online,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    Toast.makeText(
                                        context,
                                        "SHA-256 verified end-to-end:\n${sha256Hex.take(32)}…",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { darkPaper = !darkPaper }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (darkPaper) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle dark paper",
                        tint = if (darkPaper) OnionUi.Warning else OnionUi.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (!isSecureMode && onExport != null) {
                    IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Save to Downloads",
                            tint = OnionUi.AccentBlue,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (onQuotePage != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .liquidGlass(shape = RoundedCornerShape(999.dp), elevated = true)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Double-tap or pinch to zoom",
                        color = OnionUi.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onQuotePage(currentPage)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OnionUi.PrimaryStart, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Quote Page $currentPage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
