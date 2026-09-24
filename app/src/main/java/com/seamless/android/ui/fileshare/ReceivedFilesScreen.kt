package com.seamless.android.ui.fileshare

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Screen showing received files from file sharing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedFilesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var receivedFiles by remember { mutableStateOf<List<ReceivedFile>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Track file pending permanent deletion after swipe
    var pendingDeleteFile by remember { mutableStateOf<ReceivedFile?>(null) }

    // Auto-refresh when navigating back to this screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshKey) {
        val dir = File(context.getExternalFilesDir(null), "received_files")
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        receivedFiles = files.map { file ->
            val sizeBytes = file.length()
            val sizeLabel = HyperDropUtils.formatFileSize(sizeBytes)
            val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(file.lastModified()))
            val typeLabel = inferFileType(file)
            ReceivedFile(
                id = file.absolutePath,
                name = file.name,
                size = sizeLabel,
                dateReceived = dateLabel,
                type = typeLabel,
                path = file.absolutePath,
                lastModifiedMillis = file.lastModified()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = HyperDropColors.TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Received Files",
                            style = HyperDropTypography.TitleLarge,
                            color = HyperDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        HyperDropBackground(intensity = 0.6f) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)

            ) {
                HorizontalDivider(color = HyperDropColors.BorderSubtle)

                // Content
                if (receivedFiles.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = null,
                                tint = HyperDropColors.TextDisabled,
                                modifier = Modifier.size(80.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "No received files yet",
                                style = HyperDropTypography.TitleLarge,
                                color = HyperDropColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Files you receive through Share Things will appear here",
                                style = HyperDropTypography.BodyMedium,
                                color = HyperDropColors.TextTertiary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Group files by date
                    val today = LocalDate.now()
                    val yesterday = today.minusDays(1)
                    val groupedFiles = receivedFiles.groupBy { file ->
                        val fileDate = Instant.ofEpochMilli(file.lastModifiedMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        when (fileDate) {
                            today -> "Today"
                            yesterday -> "Yesterday"
                            else -> file.dateReceived
                        }
                    }

                    // File list with date sections
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(HyperDropSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                    ) {
                        groupedFiles.forEach { (dateLabel, filesInGroup) ->
                            // Date section header
                            item(key = "header_$dateLabel") {
                                Text(
                                    text = dateLabel,
                                    style = HyperDropTypography.LabelBold,
                                    color = HyperDropColors.TextTertiary,
                                    modifier = Modifier.padding(
                                        top = HyperDropSpacing.sm,
                                        bottom = HyperDropSpacing.xs
                                    )
                                )
                            }

                            items(
                                items = filesInGroup,
                                key = { it.id }
                            ) { file ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            pendingDeleteFile = file
                                            receivedFiles = receivedFiles.filter { it.id != file.id }
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "${file.name} deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                when (result) {
                                                    SnackbarResult.ActionPerformed -> {
                                                        // Undo: restore file to list
                                                        pendingDeleteFile?.let { restored ->
                                                            receivedFiles = (receivedFiles + restored)
                                                                .sortedByDescending { it.lastModifiedMillis }
                                                        }
                                                        pendingDeleteFile = null
                                                    }
                                                    SnackbarResult.Dismissed -> {
                                                        // Actually delete the file from disk
                                                        pendingDeleteFile?.let { toDelete ->
                                                            val f = File(toDelete.path)
                                                            if (f.exists()) f.delete()
                                                        }
                                                        pendingDeleteFile = null
                                                    }
                                                }
                                            }
                                            true
                                        } else false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        // Red delete background
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    HyperDropColors.ErrorRed.copy(alpha = 0.3f),
                                                    RoundedCornerShape(HyperDropSpacing.cardCorner)
                                                )
                                                .padding(horizontal = HyperDropSpacing.xl),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete",
                                                tint = HyperDropColors.ErrorRed
                                            )
                                        }
                                    }
                                ) {
                                    ReceivedFileItem(
                                        file = file,
                                        onFileClick = {
                                            openReceivedFile(context, file)
                                        },
                                        onDownloadClick = {
                                            saveToDownloads(context, file)
                                        },
                                        onShareClick = {
                                            val f = File(file.path)
                                            if (f.exists()) {
                                                try {
                                                    val fileUri = FileProvider.getUriForFile(
                                                        context,
                                                        context.packageName + ".fileprovider",
                                                        f
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "*/*"
                                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(shareIntent, "Share via")
                                                    )
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Error sharing file: ${e.message}",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data class for received files
 */
data class ReceivedFile(
    val id: String,
    val name: String,
    val size: String,
    val dateReceived: String,
    val type: String,
    val path: String,
    val lastModifiedMillis: Long = 0L
)

/**
 * Individual file item in the received files list
 */
@Composable
private fun ReceivedFileItem(
    file: ReceivedFile,
    onFileClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current

    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.94f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "receivedFileScale"
    )

    LaunchedEffect(file.id) {
        appeared = true
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        onClick = onFileClick,
        backgroundColor = HyperDropColors.CardSurface.copy(alpha = 0.4f),
        borderColor = HyperDropColors.BorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File icon or image thumbnail
            if (HyperDropUtils.isImageFile(file.name)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(file.path))
                        .crossfade(true)
                        .size(96) // Thumbnail size
                        .build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                HyperDropIconCircle(
                    icon = HyperDropUtils.getFileTypeIcon(file.name),
                    tint = HyperDropUtils.getFileTypeColor(file.name),
                    backgroundColor = HyperDropUtils.getFileTypeColor(file.name).copy(alpha = 0.12f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // File details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = HyperDropUtils.formatFileName(file.name),
                    style = HyperDropTypography.BodyLarge,
                    color = HyperDropColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${file.size} • ${file.dateReceived}",
                    style = HyperDropTypography.BodySmall,
                    color = HyperDropColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Share button
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Download to device button
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Save to Downloads",
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun getFileIcon(fileType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (fileType.lowercase()) {
        "image" -> Icons.Outlined.Image
        "video" -> Icons.Outlined.VideoLibrary
        "document", "pdf" -> Icons.AutoMirrored.Outlined.Article
        "audio" -> Icons.Outlined.LibraryMusic
        "archive", "zip" -> Icons.Outlined.Archive
        else -> Icons.Outlined.Description
    }
}

private fun openReceivedFile(context: android.content.Context, file: ReceivedFile) {
    val f = File(file.path)
    if (!f.exists()) {
        android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            f
        )

        val ext = f.extension.lowercase(Locale.getDefault())
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Check if any app can handle this intent before starting
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } else {
            // No app can handle this file type
            android.widget.Toast.makeText(
                context, 
                "No app found to open this file type", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(
            context, 
            "No app found to open this file", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    } catch (e: IllegalArgumentException) {
        // FileProvider couldn't find a valid path
        android.widget.Toast.makeText(
            context, 
            "Cannot access this file", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context, 
            "Error opening file: ${e.message}", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Save a received file to the public Downloads folder using MediaStore
 * This makes the file accessible in the system Files app
 */
private fun saveToDownloads(context: android.content.Context, file: ReceivedFile) {
    try {
        val sourceFile = File(file.path)
        if (!sourceFile.exists()) {
            android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val ext = sourceFile.extension.lowercase(Locale.getDefault())
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses MediaStore for Downloads
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Seamless")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                android.widget.Toast.makeText(
                    context,
                    "Saved to Downloads/Seamless",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(context, "Failed to save file", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 and below - direct file copy
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Seamless"
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)

            // Notify media scanner
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(mime),
                null
            )

            android.widget.Toast.makeText(
                context,
                "Saved to Downloads/Seamless",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Error saving file: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}



private fun inferFileType(file: File): String {
    val ext = file.extension.lowercase(Locale.getDefault())
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "Image"
        "mp4", "mkv", "webm", "avi", "mov" -> "Video"
        "mp3", "wav", "flac", "m4a", "ogg" -> "Audio"
        "pdf" -> "Document"
        "doc", "docx", "txt", "rtf", "odt" -> "Document"
        "xls", "xlsx", "csv" -> "Document"
        "ppt", "pptx" -> "Document"
        "zip", "rar", "7z" -> "Archive"
        "apk" -> "App"
        else -> "File"
    }
}
