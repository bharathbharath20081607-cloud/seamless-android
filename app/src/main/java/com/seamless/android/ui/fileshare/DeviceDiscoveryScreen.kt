@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.seamless.android.service.HyperDropIdentity

/**
 * Screen for discovering and connecting to nearby devices for file sharing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    onDeviceSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    isHosting: Boolean = false,
    viewModel: FileShareViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // Permission state
    var permissionsGranted by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Define the permissions needed based on Android version
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.all { it.value }
        if (permissionsGranted) {
            if (isHosting) {
                viewModel.startHosting()
            } else {
                viewModel.startDeviceDiscovery()
            }
        } else {
            showPermissionRationale = true
        }
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        val allPermissionsGranted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allPermissionsGranted) {
            permissionsGranted = true
            if (isHosting) {
                viewModel.startHosting()
            } else {
                viewModel.startDeviceDiscovery()
            }
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }
    
    // Determine dark mode state reliably
    val themePref by com.seamless.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState(initial = com.seamless.android.ui.theme.ThemePreference.System)
    val isDark = when (themePref) {
        com.seamless.android.ui.theme.ThemePreference.Dark -> true
        com.seamless.android.ui.theme.ThemePreference.Light -> false
        com.seamless.android.ui.theme.ThemePreference.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // Observe ViewModel state
    val isDiscovering by viewModel.isDiscovering.observeAsState(false)
    val discoveredDevices by viewModel.discoveredDevices.observeAsState(emptyList())
    val connectionInfo by viewModel.connectionInfo.observeAsState()
    val statusMessage by viewModel.statusMessage.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()
    val pendingFile by viewModel.pendingFile.observeAsState()
    val pendingFilesCount by viewModel.pendingFilesCount.observeAsState(0)
    val canRetry by viewModel.canRetry.observeAsState(false)
    val currentTransfer by viewModel.currentTransfer.observeAsState()

    // Observe incoming file requests (Secure Handover)
    val incomingRequest by viewModel.incomingRequest.observeAsState()
    
    // Observe batch transfer state
    val incomingBatchRequest by viewModel.incomingBatchRequest.observeAsState()
    val batchTransferFiles by viewModel.batchTransferFiles.observeAsState(emptyList())
    val currentBatchFileIndex by viewModel.currentBatchFileIndex.observeAsState(0)
    val isBatchTransferComplete by viewModel.isBatchTransferComplete.observeAsState(false)

    // Stop discovery when screen closes
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDeviceDiscovery()
            if (isHosting) {
                viewModel.disconnect()
                viewModel.dismissTransferScreen()
            }
        }
    }

    // Observe transfer screen state for showing progress screens
    val transferScreenState by viewModel.transferScreenState.observeAsState(TransferScreenState.NONE)
    val lastTransferResult by viewModel.lastTransferResult.observeAsState()
    
    // Determine if transfer is complete or failed
    val isComplete = lastTransferResult?.success == true
    val isFailed = lastTransferResult?.success == false

    // Step-by-step system back navigation for progress/waiting states and device discovery
    androidx.activity.compose.BackHandler {
        when (transferScreenState) {
            TransferScreenState.ANIMATING_SEND,
            TransferScreenState.SENDING -> {
                if (!isComplete && !isFailed) {
                    viewModel.cancelActiveTransfer()
                }
                viewModel.dismissTransferScreen()
            }
            TransferScreenState.ANIMATING_RECEIVE,
            TransferScreenState.RECEIVING -> {
                if (!isComplete && !isFailed && !isBatchTransferComplete) {
                    viewModel.resetBatchTransfer()
                    viewModel.cancelActiveTransfer()
                    viewModel.disconnect()
                } else {
                    viewModel.resetBatchTransfer()
                }
                viewModel.dismissTransferScreen()
                if (isHosting) {
                    onBackClick()
                }
            }
            TransferScreenState.WAITING -> {
                if (incomingBatchRequest != null) {
                    viewModel.rejectBatchTransfer()
                }
                viewModel.disconnect()
                viewModel.dismissTransferScreen()
                onBackClick()
            }
            TransferScreenState.NONE -> {
                onBackClick()
            }
        }
    }

    // Show full-screen progress screens based on transfer state
    when (transferScreenState) {
        TransferScreenState.ANIMATING_SEND -> {
            HyperDropSenderAnimation(
                onAnimationEnd = { viewModel.onAnimationCompleted() },
                progress = currentTransfer?.progress ?: 0,
                fileName = currentTransfer?.fileName ?: "",
                fileSize = currentTransfer?.totalBytes ?: 0L
            )
            return // Don't show device discovery
        }
        
        TransferScreenState.ANIMATING_RECEIVE -> {
            HyperDropReceiverAnimation(
                onAnimationEnd = { viewModel.onAnimationCompleted() },
                progress = currentTransfer?.progress ?: 0,
                fileName = currentTransfer?.fileName ?: "",
                fileSize = currentTransfer?.totalBytes ?: 0L
            )
            return // Don't show device discovery
        }

        TransferScreenState.SENDING -> {
            SendProgressScreen(
                fileName = currentTransfer?.fileName ?: lastTransferResult?.fileName ?: "Unknown",
                fileSize = currentTransfer?.totalBytes ?: lastTransferResult?.fileSize ?: 0L,
                progress = currentTransfer?.progress ?: if (isComplete) 100 else 0,
                bytesTransferred = currentTransfer?.bytesTransferred ?: 0L,
                isComplete = isComplete,
                isFailed = isFailed,
                errorMessage = lastTransferResult?.errorMessage ?: errorMessage,
                speedBytesPerSec = currentTransfer?.speedBytesPerSec ?: 0L,
                estimatedSecondsRemaining = currentTransfer?.estimatedSecondsRemaining ?: -1,
                fileIndex = currentTransfer?.fileIndex ?: 1,
                totalFiles = currentTransfer?.totalFiles ?: 1,
                onCancel = {
                    viewModel.cancelActiveTransfer()
                    viewModel.dismissTransferScreen()
                },
                onDone = {
                    viewModel.dismissTransferScreen()
                    onBackClick()
                },
                onRetry = if (canRetry) ({ viewModel.retryLastTransfer() }) else null
            )
            return // Don't show device discovery
        }
        
        TransferScreenState.RECEIVING -> {
            // Use BatchReceiveProgressScreen if we have batch files
            if (batchTransferFiles.isNotEmpty()) {
                BatchReceiveProgressScreen(
                    files = batchTransferFiles,
                    currentFileIndex = currentBatchFileIndex,
                    isBatchComplete = isBatchTransferComplete,
                    isFailed = isFailed,
                    senderName = incomingBatchRequest?.senderName ?: "Unknown Device",
                    showAcceptReject = false,
                    onCancel = {
                        viewModel.resetBatchTransfer()
                        viewModel.disconnect()
                        viewModel.dismissTransferScreen()
                    },
                    onDone = {
                        viewModel.resetBatchTransfer()
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    }
                )
            } else {
                // Fallback to single-file screen
                ReceiveProgressScreen(
                    fileName = currentTransfer?.fileName ?: lastTransferResult?.fileName ?: incomingRequest?.fileName,
                    fileSize = currentTransfer?.totalBytes ?: lastTransferResult?.fileSize ?: incomingRequest?.fileSize ?: 0L,
                    progress = currentTransfer?.progress ?: if (isComplete) 100 else 0,
                    bytesTransferred = currentTransfer?.bytesTransferred ?: 0L,
                    isWaitingForTransfer = false,
                    isReceiving = true,
                    isComplete = isComplete,
                    isFailed = isFailed,
                    errorMessage = lastTransferResult?.errorMessage ?: errorMessage,
                    showAcceptReject = false,
                    speedBytesPerSec = currentTransfer?.speedBytesPerSec ?: 0L,
                    estimatedSecondsRemaining = currentTransfer?.estimatedSecondsRemaining ?: -1,
                    fileIndex = currentTransfer?.fileIndex ?: 1,
                    totalFiles = currentTransfer?.totalFiles ?: 1,
                    onAccept = {},
                    onReject = {},
                    onCancel = {
                        viewModel.cancelActiveTransfer()
                        viewModel.dismissTransferScreen()
                    },
                    onDone = {
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    },
                    onOpenFile = {
                        val receivedName = currentTransfer?.fileName ?: lastTransferResult?.fileName ?: incomingRequest?.fileName
                        if (!receivedName.isNullOrBlank()) {
                            try {
                                val file = java.io.File(java.io.File(context.getExternalFilesDir(null), "received_files"), receivedName)
                                if (file.exists()) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val mime = android.webkit.MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mime)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(openIntent, "Open file"))
                                }
                            } catch (_: Exception) { }
                        }
                    }
                )
            }
            return // Don't show device discovery
        }
        TransferScreenState.WAITING -> {
            // Use BatchReceiveProgressScreen if we have a batch request
            if (incomingBatchRequest != null && incomingBatchRequest!!.files.size > 1) {
                BatchReceiveProgressScreen(
                    files = incomingBatchRequest!!.files,
                    currentFileIndex = 0,
                    isBatchComplete = false,
                    isFailed = false,
                    senderName = incomingBatchRequest!!.senderName,
                    showAcceptReject = true,
                    onAccept = { viewModel.acceptBatchTransfer() },
                    onReject = { viewModel.rejectBatchTransfer() },
                    onCancel = {
                        viewModel.rejectBatchTransfer()
                        viewModel.disconnect()
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    },
                    onDone = {
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    }
                )
            } else {
                // Fallback to single-file screen
                ReceiveProgressScreen(
                    fileName = incomingRequest?.fileName,
                    fileSize = incomingRequest?.fileSize ?: 0L,
                    progress = 0,
                    bytesTransferred = 0L,
                    isWaitingForTransfer = true,
                    isReceiving = false,
                    isComplete = false,
                    isFailed = false,
                    errorMessage = null,
                    showAcceptReject = incomingRequest != null,
                    onAccept = { viewModel.acceptIncomingTransfer() },
                    onReject = { viewModel.rejectIncomingTransfer() },
                    onCancel = {
                        viewModel.disconnect()
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    },
                    onDone = {
                        viewModel.dismissTransferScreen()
                        onBackClick()
                    },
                    onOpenFile = null
                )
            }
            return // Don't show device discovery
        }
        TransferScreenState.NONE -> {
            // Continue to show device discovery screen below
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show status messages as snackbars
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Show error messages as snackbars
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long,
                withDismissAction = true
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (isHosting) "Receive Files" else "Find Devices",
                            style = HyperDropTypography.DisplayMedium,
                            color = HyperDropColors.TextPrimary
                        )
                    }
                },
                actions = {
                    StatusPill(
                        label = when {
                            connectionInfo != null -> "Connected"
                            isHosting -> "Visible"
                            isDiscovering -> "Scanning"
                            else -> "Idle"
                        },
                        color = when {
                            connectionInfo != null || isHosting || isDiscovering -> HyperDropColors.SuccessGreen
                            else -> HyperDropColors.TextTertiary
                        },
                        isPulsing = isDiscovering || isHosting
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Refresh button
                    IconButton(
                        onClick = {
                            viewModel.stopDeviceDiscovery()
                            viewModel.startDeviceDiscovery()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = HyperDropColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        HyperDropBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val radarStyle = if (isHosting) com.seamless.android.ui.components.RadarStyle.RECEIVER else com.seamless.android.ui.components.RadarStyle.SENDER
                    val title = if (isHosting) stringResource(com.seamless.android.R.string.waiting_for_sender) else if (isDiscovering) stringResource(com.seamless.android.R.string.scanning_devices) else stringResource(com.seamless.android.R.string.ready_to_scan)
                    val subtitle = if (isHosting) stringResource(com.seamless.android.R.string.ask_sender) else if (isDiscovering) {
                        if (discoveredDevices.isNotEmpty()) "${discoveredDevices.size} device${if (discoveredDevices.size > 1) "s" else ""} nearby"
                        else stringResource(com.seamless.android.R.string.ensure_wifi_on)
                    } else stringResource(com.seamless.android.R.string.tap_refresh)
                    
                    com.seamless.android.ui.components.RadarScanner(
                        isScanning = isDiscovering || isHosting,
                        title = title,
                        subtitle = subtitle,
                        style = radarStyle,
                        deviceCount = discoveredDevices.size,
                        contentColor = HyperDropColors.TextPrimary,
                        secondaryContentColor = HyperDropColors.TextSecondary,
                        badgeTextColor = Color.Black
                    )
                }

                // This Device card (AirDrop/Quick Share inspired)
                if (isHosting) {
                    val deviceAlias = remember { HyperDropIdentity.getAlias(context) }
                    val deviceModel = remember { HyperDropIdentity.getDeviceModel() }
                    
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MonogramAvatar(
                                name = deviceAlias,
                                size = 52.dp,
                                isActive = true,
                                badgeIcon = Icons.Filled.Smartphone
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "This Device",
                                    style = HyperDropTypography.Caption.copy(
                                        color = HyperDropColors.TextSecondary
                                    )
                                )
                                Text(
                                    text = deviceAlias,
                                    style = HyperDropTypography.TitleMedium
                                )
                                Text(
                                    text = deviceModel,
                                    style = HyperDropTypography.BodySmall
                                )
                            }
                            
                            StatusPill(
                                label = "Visible",
                                color = HyperDropColors.SuccessGreen,
                                isPulsing = true
                            )
                        }
                    }
                }

                // Connection status (only show when connected)
                connectionInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.CardSurfaceLight
                        ),
                        border = BorderStroke(1.dp, HyperDropColors.BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = HyperDropColors.SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(com.seamless.android.R.string.connected),
                                    style = HyperDropTypography.LabelBold.copy(
                                        color = HyperDropColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = if (info.isGroupOwner) stringResource(com.seamless.android.R.string.group_owner) else stringResource(com.seamless.android.R.string.client),
                                    style = HyperDropTypography.BodySmall.copy(
                                        color = HyperDropColors.TextTertiary
                                    )
                                )
                            }
                            TextButton(onClick = { viewModel.disconnect() }) {
                                Text(stringResource(com.seamless.android.R.string.disconnect), color = HyperDropColors.ErrorRed)
                            }
                        }
                    }
                }

                // Pending File / Queue Send Action
                if (connectionInfo != null && (pendingFile != null || pendingFilesCount > 0)) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        showShimmerBorder = true
                    ) {
                        Text(
                            text = stringResource(com.seamless.android.R.string.ready_to_send),
                            style = HyperDropTypography.Caption.copy(
                                color = HyperDropColors.TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingFile?.fileName ?: "$pendingFilesCount file${if (pendingFilesCount > 1) "s" else ""} queued",
                            style = HyperDropTypography.BodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = HyperDropColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PremiumButton(
                            text = stringResource(com.seamless.android.R.string.send_now),
                            onClick = {
                                if (pendingFile != null) {
                                    viewModel.sendPendingFile()
                                } else {
                                    viewModel.processNextInQueue()
                                }
                            },
                            icon = Icons.AutoMirrored.Filled.Send,
                            color = Color.White
                        )
                    }
                }

                // Device list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (discoveredDevices.isEmpty() && !isDiscovering) {
                        item {
                            EmptyDeviceState(
                                onScanAgain = {
                                    viewModel.stopDeviceDiscovery()
                                    viewModel.startDeviceDiscovery()
                                }
                            )
                        }
                    } else {
                        items(
                            count = discoveredDevices.size,
                            key = { idx -> discoveredDevices[idx].deviceAddress.ifEmpty { idx.toString() } }
                        ) { index ->
                            val device = discoveredDevices[index]
                            DeviceListItem(
                                device = device,
                                index = index,
                                onClick = {
                                    viewModel.connectToDevice(device.deviceAddress)
                                    onDeviceSelected(device.deviceAddress)
                                },
                                isConnected = device.status == WifiP2pDevice.CONNECTED
                            )
                        }
                    }
                }
            }

            // Incoming File Request — Frosted Glass Bottom Sheet over a 65% black scrim
            val showIncomingRequest = incomingRequest != null
            if (showIncomingRequest) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                )
            }
            AnimatedVisibility(
                visible = showIncomingRequest,
                enter = HyperDropTransitions.ModalEnter,
                exit = HyperDropTransitions.ModalExit,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                incomingRequest?.let { request ->
                    val fileName = request.fileName ?: "Unknown file"
                    val fileSize = request.fileSize ?: 0L

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            showShimmerBorder = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Drag handle
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(HyperDropColors.TextDisabled)
                                )

                                Spacer(modifier = Modifier.height(HyperDropSpacing.lg))

                                // Title
                                Text(
                                    text = "Incoming File Request",
                                    style = HyperDropTypography.TitleLarge
                                )

                                Spacer(modifier = Modifier.height(HyperDropSpacing.lg))

                                // File info row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // File type icon
                                    HyperDropIconCircle(
                                        icon = HyperDropUtils.getFileTypeIcon(fileName),
                                        tint = HyperDropUtils.getFileTypeColor(fileName),
                                        backgroundColor = HyperDropUtils.getFileTypeColor(fileName).copy(alpha = 0.12f),
                                        showGlow = true
                                    )

                                    Spacer(modifier = Modifier.width(HyperDropSpacing.md))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Device wants to send you a file:",
                                            style = HyperDropTypography.BodySmall
                                        )
                                        Spacer(modifier = Modifier.height(HyperDropSpacing.xs))
                                        Text(
                                            text = HyperDropUtils.formatFileName(fileName),
                                            style = HyperDropTypography.TitleMedium
                                        )
                                        Text(
                                            text = HyperDropUtils.formatFileSize(fileSize),
                                            style = HyperDropTypography.BodySmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(HyperDropSpacing.xxl))

                                // Accept button (Optical White PremiumButton)
                                PremiumButton(
                                    text = "Accept",
                                    onClick = { viewModel.acceptIncomingTransfer() },
                                    icon = Icons.Filled.Download,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(HyperDropSpacing.sm))

                                // Decline button (TextButton)
                                TextButton(
                                    onClick = { viewModel.rejectIncomingTransfer() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Decline",
                                        color = HyperDropColors.ErrorRed,
                                        fontWeight = FontWeight.Medium
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
 * Premium empty state with pulsing radar animation
 */
@Composable
private fun EmptyDeviceState(
    onScanAgain: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_radar")

    // Pulsing radar circles
    val radarPulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar1"
    )
    val radarPulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar2"
    )
    val radarPulse3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar3"
    )

    // Animated ellipsis
    val ellipsisPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ellipsis"
    )
    val dots = ".".repeat(ellipsisPhase.toInt().coerceIn(0, 3))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pulsing radar animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Expanding and fading radar circles
                Canvas(modifier = Modifier.size(120.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.minDimension / 2

                    listOf(radarPulse1, radarPulse2, radarPulse3).forEach { pulse ->
                        val radius = maxRadius * pulse
                        val alpha = (1f - pulse) * 0.5f
                        drawCircle(
                            color = HyperDropColors.AccentBlue.copy(alpha = alpha),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Center icon
                HyperDropIconCircle(
                    icon = Icons.Outlined.Devices,
                    tint = HyperDropColors.AccentBlue,
                    backgroundColor = HyperDropColors.AccentBlue.copy(alpha = 0.12f),
                    size = 52.dp,
                    iconSize = 28.dp,
                    showGlow = true
                )
            }

            Text(
                text = "Looking for nearby devices$dots",
                style = HyperDropTypography.TitleLarge,
                color = HyperDropColors.TextPrimary
            )
            Text(
                text = "Make sure the other device has HyperDrop open",
                style = HyperDropTypography.BodyMedium,
                color = HyperDropColors.TextTertiary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            PremiumButton(
                text = stringResource(com.seamless.android.R.string.scan_again),
                onClick = onScanAgain,
                icon = Icons.Filled.Refresh,
                color = HyperDropColors.AccentBlue
            )
        }
    }
}


@Composable
private fun DeviceListItem(
    device: WifiP2pDevice,
    index: Int = 0,
    onClick: () -> Unit,
    isConnected: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "signal")
    val signalPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signalPulse"
    )

    val deviceDisplayName = device.deviceName.ifEmpty { stringResource(com.seamless.android.R.string.unknown_device) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index = index, baseDelayMs = 45),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        borderColor = if (isConnected && device.status == WifiP2pDevice.CONNECTED)
            HyperDropColors.SuccessGreen.copy(alpha = 0.5f)
        else
            HyperDropColors.BorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonogramAvatar(
                name = deviceDisplayName,
                size = 50.dp,
                isActive = isConnected && device.status == WifiP2pDevice.CONNECTED,
                badgeIcon = Icons.Filled.Smartphone
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceDisplayName,
                    style = HyperDropTypography.BodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = HyperDropColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = getDeviceStatusText(device),
                    style = HyperDropTypography.BodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAvailable = device.status == WifiP2pDevice.AVAILABLE || device.status == WifiP2pDevice.CONNECTED
                    val barColor = if (device.status == WifiP2pDevice.CONNECTED) HyperDropColors.SuccessGreen
                                   else if (isAvailable) HyperDropColors.PrimaryCyan
                                   else HyperDropColors.ErrorRed

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        for (i in 0..2) {
                            val barAlpha = if (isAvailable) {
                                if (i == 2) signalPulse * 0.8f else 1f
                            } else 0.3f
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((8 + i * 5).dp)
                                    .background(
                                        color = barColor.copy(alpha = barAlpha),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = getDeviceStatus(device),
                        style = HyperDropTypography.Caption.copy(
                            color = barColor.copy(alpha = 0.85f)
                        )
                    )
                }
            }

            if (isConnected && device.status == WifiP2pDevice.CONNECTED) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Connected",
                    tint = HyperDropColors.SuccessGreen,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Connect",
                        tint = HyperDropColors.PrimaryCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun getDeviceStatus(device: WifiP2pDevice): String {
    return when (device.status) {
        WifiP2pDevice.AVAILABLE -> stringResource(com.seamless.android.R.string.device_available)
        WifiP2pDevice.INVITED -> stringResource(com.seamless.android.R.string.device_invited)
        WifiP2pDevice.CONNECTED -> stringResource(com.seamless.android.R.string.device_connected)
        WifiP2pDevice.FAILED -> stringResource(com.seamless.android.R.string.device_failed)
        WifiP2pDevice.UNAVAILABLE -> stringResource(com.seamless.android.R.string.device_unavailable)
        else -> stringResource(com.seamless.android.R.string.unknown)
    }
}

@Composable
private fun getDeviceStatusText(device: WifiP2pDevice): String {
    // Wi-Fi P2P primaryDeviceType uses WPS format: "category-OUI-subcategory"
    // Category 10 = Phone, 7 = Display, 1 = Computer, 3 = Printer, 9 = Camera
    val category = device.primaryDeviceType?.split("-")?.firstOrNull()?.toIntOrNull()
    return when (category) {
        1 -> stringResource(com.seamless.android.R.string.device_type_computer)
        10 -> stringResource(com.seamless.android.R.string.device_type_phone)
        9 -> stringResource(com.seamless.android.R.string.device_type_tablet) // Closest match
        else -> {
            // Fallback: try matching device name patterns
            val name = device.deviceName.lowercase()
            when {
                name.contains("phone") || name.contains("pixel") || name.contains("galaxy") || name.contains("iphone") -> stringResource(com.seamless.android.R.string.device_type_phone)
                name.contains("tablet") || name.contains("pad") -> stringResource(com.seamless.android.R.string.device_type_tablet)
                name.contains("pc") || name.contains("laptop") || name.contains("computer") -> stringResource(com.seamless.android.R.string.device_type_computer)
                else -> stringResource(com.seamless.android.R.string.unknown_device)
            }
        }
    }
}
