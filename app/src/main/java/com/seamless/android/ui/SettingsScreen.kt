@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui
import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.seamless.android.service.MeshForegroundService
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.debug.DebugSettingsSheet
import com.seamless.android.mesh.BluetoothMeshService
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorMode
import com.seamless.android.net.TorPreferenceManager
import com.seamless.android.net.TorHiddenServiceManager

/**
 * Settings screen for app configuration
 * Currently includes background service toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    meshService: BluetoothMeshService? = null,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    // State for background service toggle - check preference periodically
    var isBackgroundEnabled by remember { 
        mutableStateOf(MeshForegroundService.isBackgroundEnabled(context))
    }
    
    // Debug settings sheet state
    var showDebugSettings by remember { mutableStateOf(false) }
    
    // Nostr settings sheet state
    var showNostrSettings by remember { mutableStateOf(false) }

    // Tor State
    val torStatus by TorManager.statusFlow.collectAsState()
    val torMode by TorPreferenceManager.modeFlow.collectAsState()
    val hiddenServiceManager = remember(context) { TorHiddenServiceManager.getInstance(context) }
    val hiddenServiceStatus by hiddenServiceManager.statusFlow.collectAsState()
    val torReachable = hiddenServiceStatus.state == TorHiddenServiceManager.HiddenServiceState.RUNNING &&
        hiddenServiceStatus.onionAddress.isNotBlank()
    val scope = rememberCoroutineScope()

    // Privacy state
    val privacyManager = com.seamless.android.ui.privacy.PrivacyPreferenceManager
    LaunchedEffect(Unit) { privacyManager.init(context) }
    var isAppLockEnabled by remember { mutableStateOf(privacyManager.appLockEnabled.value) }
    var isIncognitoEnabled by remember { mutableStateOf(privacyManager.incognitoMode.value) }
    
    // Poll for preference changes to sync with notification stop action
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500) // Check every 500ms
            val currentState = MeshForegroundService.isBackgroundEnabled(context)
            if (currentState != isBackgroundEnabled) {
                isBackgroundEnabled = currentState
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
            val switchColors = SwitchDefaults.colors(
                checkedThumbColor = if (isDark) Color.Black else Color.White,
                checkedTrackColor = if (isDark) Color.White else Color.Black,
                uncheckedThumbColor = colorScheme.outline,
                uncheckedTrackColor = if (isDark) Color(0xFF26262E) else colorScheme.surfaceVariant
            )

            // Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                ),
                onClick = onProfileClick
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    SeamlessColors.BrandGradientStart.copy(alpha = 0.06f),
                                    SeamlessColors.BrandGradientEnd.copy(alpha = 0.02f)
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Manage your identity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Service Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Background Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Keep mesh active",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Relay messages in the background to extend network range",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Switch(
                            checked = isBackgroundEnabled,
                            onCheckedChange = { enabled ->
                                isBackgroundEnabled = enabled
                                try {
                                    MeshForegroundService.setBackgroundEnabled(context, enabled)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = switchColors
                        )
                    }
                    
                    if (isBackgroundEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Info box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "A notification will appear while the service is active. This uses more battery but enables long-distance messaging through multi-hop relay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery optimization info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Battery Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Tips for better battery life:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BulletPoint("Enable background service only when needed")
                        BulletPoint("The app uses Bluetooth Low Energy which is power efficient")
                        BulletPoint("Disable battery optimization for Seamless if messages are delayed")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Privacy & Long Distance (Tor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Privacy & Long Distance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Long Distance Mode (Tor)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Keep a private onion address available and retry queued delivery while Tor is enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = torMode == TorMode.ON,
                            onCheckedChange = { enabled ->
                                val newMode = if (enabled) TorMode.ON else TorMode.OFF
                                TorPreferenceManager.set(context, newMode)
                            },
                            colors = switchColors
                        )
                    }

                    if (torMode == TorMode.ON) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        torReachable -> SeamlessColors.Success.copy(alpha = 0.12f)
                                        else -> when (torStatus.state) {
                                        TorManager.TorState.RUNNING -> SeamlessColors.Success.copy(alpha = 0.12f)
                                        TorManager.TorState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    }
                                )
                                .padding(12.dp)
                        ) {
                            // Status Dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            torReachable -> SeamlessColors.Success
                                            else -> when (torStatus.state) {
                                            TorManager.TorState.RUNNING -> SeamlessColors.Success
                                            TorManager.TorState.ERROR -> MaterialTheme.colorScheme.error
                                            else -> SeamlessColors.Warning
                                            }
                                        }
                                    )
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = when {
                                        torReachable -> "Onion service ready"
                                        else -> when (torStatus.state) {
                                        TorManager.TorState.RUNNING -> "Connected to Tor Network"
                                        TorManager.TorState.BOOTSTRAPPING -> "Bootstrapping (${torStatus.bootstrapPercent}%)..."
                                        TorManager.TorState.STARTING -> "Starting Tor..."
                                        TorManager.TorState.STOPPING -> "Stopping..."
                                        TorManager.TorState.OFF -> "Tor is Off"
                                        TorManager.TorState.ERROR -> "Tor Error"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                if (torStatus.state == TorManager.TorState.BOOTSTRAPPING || torStatus.state == TorManager.TorState.STARTING) {
                                    val cleanLog = remember(torStatus.lastLogLine) {
                                        torStatus.lastLogLine.replace(Regex("\\u001B\\[[;\\d]*m"), "")
                                    }
                                    Text(
                                        text = cleanLog.take(60),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Battery Warning (fully visible and unclipped)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SeamlessColors.Warning.copy(alpha = 0.12f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = SeamlessColors.Warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keeping Tor active uses battery. Turn it off only when you do not need remote delivery or incoming Tor messages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Nostr Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Nostr Network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showNostrSettings = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Relays & Identity", fontWeight = FontWeight.SemiBold)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Configure relays for global reach beyond the local mesh",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Security & Privacy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Security & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "App Lock",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Require fingerprint or PIN to open the app",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { enabled ->
                                isAppLockEnabled = enabled
                                com.seamless.android.ui.privacy.PrivacyPreferenceManager
                                    .setAppLockEnabled(context, enabled)
                            },
                            colors = switchColors
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Incognito Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Incognito Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Block screenshots and screen recording",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isIncognitoEnabled,
                            onCheckedChange = { enabled ->
                                isIncognitoEnabled = enabled
                                com.seamless.android.ui.privacy.PrivacyPreferenceManager
                                    .setIncognitoMode(context, enabled)
                            },
                            colors = switchColors
                        )
                    }

                    if (isIncognitoEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorScheme.primary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Screenshots and screen recordings are now blocked system-wide while the app is open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Panic Button Card
            var showPanicConfirm by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D1010)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Panic Button",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Instantly wipe all messages and sign out. Use in an emergency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showPanicConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Panic — Wipe & Exit", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showPanicConfirm) {
                AlertDialog(
                    onDismissRequest = { showPanicConfirm = false },
                    title = { Text("⚠️ Confirm Panic", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold) },
                    text = { Text("This will permanently delete ALL messages and contacts. This cannot be undone.", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = {
                            showPanicConfirm = false
                            // Wipe the database on a background thread then restart
                            scope.launch {
                                try {
                                    val db = com.seamless.android.data.AppDatabase.getDatabase(context)
                                    db.clearAllTables()
                                } catch (_: Exception) {}
                                // Delete the database file entirely
                                context.deleteDatabase("seamless_database")
                                // Restart app to clear state
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }
                        }) {
                            Text("WIPE EVERYTHING", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPanicConfirm = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1A1A2E)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Debug Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Developer Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showDebugSettings = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Debug Settings")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Access GATT server/client controls, connection limits, and diagnostic tools",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Nostr Settings Sheet
        if (showNostrSettings) {
            com.seamless.android.ui.nostr.NostrSettingsSheet(
                isPresented = showNostrSettings,
                onDismiss = { showNostrSettings = false }
            )
        }
        
        // Debug Settings Sheet
        if (showDebugSettings && meshService != null) {
            DebugSettingsSheet(
                isPresented = showDebugSettings,
                onDismiss = { showDebugSettings = false },
                meshService = meshService
            )
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = SeamlessCyan,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
