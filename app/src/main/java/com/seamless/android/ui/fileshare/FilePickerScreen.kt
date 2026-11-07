@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource

/**
 * Screen for selecting files to share
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    onFileSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: FileShareViewModel = viewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFilesToQueue(uris.take(20))
            // Trigger navigation to DeviceDiscoveryScreen immediately
            onFileSelected("queue")
        }
    }

    // Observe ViewModel state
    val currentTransfer by viewModel.currentTransfer.observeAsState()
    val connectionInfo by viewModel.connectionInfo.observeAsState()
    val statusMessage by viewModel.statusMessage.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()

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
                            text = "Select Files to Share",
                            style = HyperDropTypography.TitleLarge,
                            color = HyperDropColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        HyperDropBackground(intensity = 0.6f) { // Slightly dimmed for utility screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // File type category selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HyperDropSpacing.lg, vertical = HyperDropSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(HyperDropSpacing.sm)
                ) {
                    val categories = listOf(
                        "All" to "*/*",
                        "Photos" to "image/*",
                        "Videos" to "video/*",
                        "Audio" to "audio/*",
                        "Docs" to "application/*"
                    )
                    var selectedCategory by remember { mutableStateOf("All") }

                    categories.forEach { (label, mimeType) ->
                        FilterChip(
                            selected = selectedCategory == label,
                            onClick = {
                                selectedCategory = label
                                filePickerLauncher.launch(mimeType)
                            },
                            label = { Text(label, style = HyperDropTypography.BodySmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = HyperDropColors.CardSurface.copy(alpha = 0.5f),
                                selectedContainerColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.2f),
                                labelColor = HyperDropColors.TextSecondary,
                                selectedLabelColor = HyperDropColors.PrimaryCyan
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == label,
                                borderColor = HyperDropColors.BorderSubtle,
                                selectedBorderColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Connection status / guidance
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    backgroundColor = if (connectionInfo != null) 
                        HyperDropColors.PrimaryCyan.copy(alpha = 0.1f) 
                    else 
                        HyperDropColors.CardSurface.copy(alpha = 0.4f)
                ) {
                    if (connectionInfo != null) {
                        val isGroupOwner = connectionInfo?.isGroupOwner == true
                        val role = if (isGroupOwner) stringResource(com.seamless.android.R.string.group_owner) else stringResource(com.seamless.android.R.string.client)
                        Text(
                            text = stringResource(com.seamless.android.R.string.connected_via_wifi, role),
                            style = HyperDropTypography.BodyMedium,
                            color = HyperDropColors.TextPrimary
                        )
                        Text(
                            text = stringResource(com.seamless.android.R.string.can_send_now),
                            style = HyperDropTypography.BodySmall,
                            color = HyperDropColors.TextSecondary
                        )
                    } else {
                        Text(
                            text = stringResource(com.seamless.android.R.string.not_connected),
                            style = HyperDropTypography.BodyMedium,
                            color = HyperDropColors.TextPrimary
                        )
                        Text(
                            text = stringResource(com.seamless.android.R.string.go_receive_first),
                            style = HyperDropTypography.BodySmall,
                            color = HyperDropColors.TextSecondary
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = HyperDropTypography.BodySmall,
                        color = HyperDropColors.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = HyperDropSpacing.lg)
                    )
                }

                errorMessage?.let { err ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        backgroundColor = HyperDropColors.ErrorRed.copy(alpha = 0.2f),
                        borderColor = HyperDropColors.ErrorRed.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = err,
                            style = HyperDropTypography.BodySmall,
                            color = HyperDropColors.ErrorRed.copy(alpha = 0.7f)
                        )
                    }
                }

                // Current transfer progress
                currentTransfer?.let { transfer ->
                    val animatedProgress by animateFloatAsState(
                        targetValue = (transfer.progress / 100f).coerceIn(0f, 1f),
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        ),
                        label = "fileTransferProgress"
                    )
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        backgroundColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = stringResource(com.seamless.android.R.string.sending_file, transfer.fileName),
                            style = HyperDropTypography.BodyLarge,
                            color = HyperDropColors.PrimaryCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = HyperDropColors.PrimaryCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${transfer.progress}% • ${HyperDropUtils.formatFileSize(transfer.bytesTransferred)} / ${HyperDropUtils.formatFileSize(transfer.totalBytes)}",
                            style = HyperDropTypography.BodySmall,
                            color = HyperDropColors.TextTertiary
                        )
                    }
                }

                // Header info
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (currentTransfer != null) 0.dp else 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = HyperDropColors.PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(com.seamless.android.R.string.select_file_header),
                            style = HyperDropTypography.BodyMedium,
                            color = HyperDropColors.TextSecondary
                        )
                    }
                }

                // File list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth(),
                            onClick = { filePickerLauncher.launch("*/*") },
                            backgroundColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.15f),
                            borderColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Browse files",
                                    tint = HyperDropColors.PrimaryCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = stringResource(com.seamless.android.R.string.browse_files),
                                        style = HyperDropTypography.BodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = HyperDropColors.TextPrimary
                                    )
                                    Text(
                                        text = stringResource(com.seamless.android.R.string.browse_files_desc),
                                        style = HyperDropTypography.BodySmall,
                                        color = HyperDropColors.TextTertiary
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


