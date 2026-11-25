@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui


import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import com.seamless.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.seamless.android.core.ui.utils.singleOrTripleClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset

/**
 * Header components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */


/**
 * Reactive helper to compute favorite state from fingerprint mapping
 * This eliminates the need for static isFavorite parameters and makes
 * the UI reactive to fingerprint manager changes
 */
@Composable
fun isFavoriteReactive(
    peerID: String,
    peerFingerprints: Map<String, String>,
    favoritePeers: Set<String>
): Boolean {
    return remember(peerID, peerFingerprints, favoritePeers) {
        val fingerprint = peerFingerprints[peerID]
        fingerprint != null && favoritePeers.contains(fingerprint)
    }
}

@Composable
fun TorStatusDot(
    modifier: Modifier = Modifier
) {
    val torStatus by com.seamless.android.net.TorManager.statusFlow.collectAsState()
    val context = LocalContext.current
    val hiddenServiceManager = remember(context) {
        com.seamless.android.net.TorHiddenServiceManager.getInstance(context)
    }
    val hiddenServiceStatus by hiddenServiceManager.statusFlow.collectAsState()
    val onionReady = hiddenServiceStatus.state ==
        com.seamless.android.net.TorHiddenServiceManager.HiddenServiceState.RUNNING &&
        hiddenServiceStatus.onionAddress.isNotBlank()
    
    if (torStatus.mode != com.seamless.android.net.TorMode.OFF || onionReady) {
        val dotColor = when {
            onionReady -> com.seamless.android.ui.theme.SeamlessColors.Emerald
            torStatus.running && torStatus.bootstrapPercent < 100 -> com.seamless.android.ui.theme.SeamlessColors.Amber
            torStatus.running && torStatus.bootstrapPercent >= 100 -> com.seamless.android.ui.theme.SeamlessColors.Emerald
            else -> com.seamless.android.ui.theme.SeamlessColors.Error
        }
        Canvas(
            modifier = modifier
        ) {
            val radius = size.minDimension / 2
            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(size.width / 2, size.height / 2)
            )
        }
    }
}

@Composable
fun NoiseSessionIcon(
    sessionState: String?,
    modifier: Modifier = Modifier
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val (icon, color, contentDescription) = when (sessionState) {
        "uninitialized" -> Triple(
            Icons.Outlined.NoEncryption,
            mutedColor,
            stringResource(R.string.cd_ready_for_handshake)
        )
        "handshaking" -> Triple(
            Icons.Outlined.Sync,
            mutedColor,
            stringResource(R.string.cd_handshake_in_progress)
        )
        "established" -> Triple(
            Icons.Filled.Lock,
            com.seamless.android.ui.theme.SeamlessColors.Emerald, // Precision system green E2EE lock
            stringResource(R.string.cd_encrypted)
        )
        else -> { // "failed" or any other state
            Triple(
                Icons.Outlined.Warning,
                com.seamless.android.ui.theme.SeamlessColors.Error, // Precision system red
                stringResource(R.string.cd_handshake_failed)
            )
        }
    }
    
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = color
    )
}

@Composable
fun NicknameEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    
    // Auto-scroll to end when text changes (simulates cursor following)
    LaunchedEffect(value) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.at_symbol),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.primary.copy(alpha = 0.8f)
        )
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.primary,
                fontFamily = FontFamily.Default
            ),
            cursorBrush = SolidColor(colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { 
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .widthIn(min = 40.dp, max = 96.dp)
                .horizontalScroll(scrollState)
        )
    }
}

@Composable
fun PeerCounter(
    connectedPeers: List<String>,
    joinedChannels: Set<String>,
    hasUnreadChannels: Map<String, Int>,
    isConnected: Boolean,
    selectedLocationChannel: com.seamless.android.geohash.ChannelID?,
    geohashPeople: List<GeoPerson>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Compute channel-aware people count and color (matches iOS logic exactly)
    val (peopleCount, countColor) = when (selectedLocationChannel) {
        is com.seamless.android.geohash.ChannelID.Location -> {
            // Geohash channel: show geohash participants
            val count = geohashPeople.size
            val green = Color(0xFF00C851) // Standard green
            Pair(count, if (count > 0) green else Color.Gray)
        }
        is com.seamless.android.geohash.ChannelID.Mesh,
        null -> {
            // Mesh channel: show Bluetooth-connected peers (excluding self)
            val count = connectedPeers.size
            val meshBlue = Color(0xFF007AFF) // iOS-style blue for mesh
            Pair(count, if (isConnected && count > 0) meshBlue else Color.Gray)
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable { onClick() }.padding(end = 8.dp) // Added right margin to match "bitchat" logo spacing
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = when (selectedLocationChannel) {
                is com.seamless.android.geohash.ChannelID.Location -> stringResource(R.string.cd_geohash_participants)
                else -> stringResource(R.string.cd_connected_peers)
            },
            modifier = Modifier.size(16.dp),
            tint = countColor
        )
        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$peopleCount",
            style = MaterialTheme.typography.bodyMedium,
            color = countColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        
        if (joinedChannels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.channel_count_prefix) + "${joinedChannels.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) Color(0xFF00C851) else Color.Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ChatHeaderContent(
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onTripleClick: () -> Unit,
    onShowAppInfo: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit,
    onChatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    when {
        selectedPrivatePeer != null -> {
            // Private chat header - Fully reactive state tracking
            val favoritePeers by viewModel.favoritePeers.observeAsState(emptySet())
            val peerFingerprints by viewModel.peerFingerprints.observeAsState(emptyMap())
            val peerSessionStates by viewModel.peerSessionStates.observeAsState(emptyMap())
            val peerNicknames by viewModel.peerNicknames.observeAsState(emptyMap())
            
            // Reactive favorite computation - no more static lookups!
            val isFavorite = isFavoriteReactive(
                peerID = selectedPrivatePeer,
                peerFingerprints = peerFingerprints,
                favoritePeers = favoritePeers
            )
            val sessionState = peerSessionStates[selectedPrivatePeer]
            
            Log.d("ChatHeader", "Header recomposing: peer=$selectedPrivatePeer, isFav=$isFavorite, sessionState=$sessionState")
            
            // Pass geohash context and people for NIP-17 chat title formatting
            val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()
            val geohashPeople by viewModel.geohashPeople.observeAsState(emptyList())

            PrivateChatHeader(
                peerID = selectedPrivatePeer,
                peerNicknames = peerNicknames,
                isFavorite = isFavorite,
                sessionState = sessionState,
                selectedLocationChannel = selectedLocationChannel,
                geohashPeople = geohashPeople,
                onBackClick = onBackClick,
                onToggleFavorite = { viewModel.toggleFavorite(selectedPrivatePeer) },
                viewModel = viewModel
            )
        }
        currentChannel != null -> {
            // Channel header
            ChannelHeader(
                channel = currentChannel,
                onBackClick = onBackClick,
                onLeaveChannel = { viewModel.leaveChannel(currentChannel) },
                onSidebarClick = onSidebarClick
            )
        }
        else -> {
            // Main header
            MainHeader(
                nickname = nickname,
                onNicknameChange = viewModel::setNickname,
                onTitleClick = onShowAppInfo,
                onTripleTitleClick = onTripleClick,
                onSidebarClick = onSidebarClick,
                onLocationChannelsClick = onLocationChannelsClick,
                onLocationNotesClick = onLocationNotesClick,
                onChatsClick = onChatsClick,
                onSettingsClick = onSettingsClick,
                onMenuClick = onMenuClick,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun PrivateChatHeader(
    peerID: String,
    peerNicknames: Map<String, String>,
    isFavorite: Boolean,
    sessionState: String?,
    selectedLocationChannel: com.seamless.android.geohash.ChannelID?,
    geohashPeople: List<GeoPerson>,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    viewModel: ChatViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val isNostrDM = peerID.startsWith("nostr_") || peerID.startsWith("nostr:")
    // Determine mutual favorite state for this peer (supports mesh ephemeral 16-hex via favorites lookup)
    val isMutualFavorite = remember(peerID, peerNicknames) {
        try {
            if (isNostrDM) return@remember false
            if (peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                val noiseKeyBytes = peerID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKeyBytes)?.isMutual == true
            } else if (peerID.length == 16 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(peerID)?.isMutual == true
            } else false
        } catch (_: Exception) { false }
    }

    // Compute title text: for NIP-17 chats show "#geohash/@username" (iOS parity)
    val titleText: String = if (isNostrDM) {
        // For geohash DMs, get the actual source geohash and proper display name
        val (conversationGeohash, baseName) = try {
            val repoField = com.seamless.android.ui.GeohashViewModel::class.java.getDeclaredField("repo")
            repoField.isAccessible = true
            val repo = repoField.get(viewModel.geohashViewModel) as com.seamless.android.nostr.GeohashRepository
            val gh = repo.getConversationGeohash(peerID) ?: "geohash"
            val fullPubkey = com.seamless.android.nostr.GeohashAliasRegistry.get(peerID) ?: ""
            val displayName = if (fullPubkey.isNotEmpty()) {
                repo.displayNameForGeohashConversation(fullPubkey, gh)
            } else {
                peerNicknames[peerID] ?: "unknown"
            }
            Pair(gh, displayName)
        } catch (e: Exception) { 
            Pair("geohash", peerNicknames[peerID] ?: "unknown")
        }
        
        "#$conversationGeohash/@$baseName"
    } else {
        // Prefer live mesh nickname; fallback to favorites nickname (supports 16-hex), finally short key
        peerNicknames[peerID] ?: run {
            val titleFromFavorites = try {
                if (peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                    val noiseKeyBytes = peerID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKeyBytes)?.peerNickname
                } else if (peerID.length == 16 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                    com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(peerID)?.peerNickname
                } else null
            } catch (_: Exception) { null }
            titleFromFavorites ?: peerID.take(12)
        }
    }

    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val glassColor = if (isDark) 
        Color(0xF009090C)
    else 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(glassColor)
            .padding(vertical = 8.dp)
    ) {
        // Tactile glassmorphic pill back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onBackClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(16.dp),
                tint = colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.chat_back),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary
            )
        }
        
        // Title - perfectly centered regardless of other elements
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.primary
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Show a globe when chatting via Nostr alias, or when mesh session not established but mutual favorite exists
            val showGlobe = isNostrDM || (sessionState != "established" && isMutualFavorite)
            if (showGlobe) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                contentDescription = stringResource(R.string.cd_nostr_reachable),
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF9B59B6) // Purple like iOS
                )
            } else {
                NoiseSessionIcon(
                    sessionState = sessionState,
                    modifier = Modifier.size(14.dp)
                )
            }

        }
        
        // Call + Favorite actions - positioned on the right
        val headerContext = LocalContext.current
        var pendingCallContact by remember { mutableStateOf<com.seamless.android.net.OnionContactManager.OnionContact?>(null) }
        val headerMicPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            val target = pendingCallContact
            pendingCallContact = null
            if (granted && target != null) {
                com.seamless.android.call.SeamlessCallManager.getInstance(headerContext)
                    .startOutgoingCall(target)
            } else if (!granted) {
                android.widget.Toast.makeText(
                    headerContext,
                    "Microphone permission is required for voice calls",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(
                onClick = {
                    val contactMgr = com.seamless.android.net.OnionContactManager.getInstance(headerContext)
                    val matchedContact = contactMgr.getAllContacts().firstOrNull { c ->
                        !c.requiresRendezvous() && (
                            c.publicKey.equals(peerID, ignoreCase = true) ||
                                c.id.equals(peerID, ignoreCase = true) ||
                                c.nickname.equals(titleText, ignoreCase = true)
                            )
                    }
                    if (matchedContact != null) {
                        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                            headerContext,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            com.seamless.android.call.SeamlessCallManager.getInstance(headerContext)
                                .startOutgoingCall(matchedContact)
                        } else {
                            pendingCallContact = matchedContact
                            headerMicPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        android.widget.Toast.makeText(
                            headerContext,
                            "Open Tor P2P Contacts to pair ${titleText}'s .onion address for long-distance voice calls",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Voice Call",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF00E676)
                )
            }

            IconButton(
                onClick = {
                    Log.d("ChatHeader", "Header toggle favorite: peerID=$peerID, currentFavorite=$isFavorite")
                    onToggleFavorite()
                }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isFavorite) stringResource(R.string.cd_remove_favorite) else stringResource(R.string.cd_add_favorite),
                    modifier = Modifier.size(18.dp), // Slightly larger than sidebar icon
                    tint = if (isFavorite) Color(0xFFFFD700) else Color(0x87878700) // Yellow or grey
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    channel: String,
    onBackClick: () -> Unit,
    onLeaveChannel: () -> Unit,
    onSidebarClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    
    val glassColor = if (isDark) 
        Color(0xF009090C)
    else 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(glassColor)
            .padding(vertical = 8.dp)
    ) {
        // Tactile glassmorphic pill back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onBackClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(16.dp),
                tint = colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.chat_back),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary
            )
        }
        
        // Title - perfectly centered regardless of other elements
        Text(
            text = stringResource(R.string.chat_channel_prefix, channel),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .clickable { onSidebarClick() }
        )
        
        // Leave button - positioned on the right
        TextButton(
            onClick = onLeaveChannel,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text(
                text = stringResource(R.string.chat_leave),
                style = MaterialTheme.typography.bodySmall,
                color = com.seamless.android.ui.theme.SeamlessColors.Error
            )
        }
    }
}

@Composable
private fun MainHeader(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onTitleClick: () -> Unit,
    onTripleTitleClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit,
    onChatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    val joinedChannels by viewModel.joinedChannels.observeAsState(emptySet())
    val hasUnreadChannels by viewModel.unreadChannelMessages.observeAsState(emptyMap())
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.observeAsState(emptySet())
    val isConnected by viewModel.isConnected.observeAsState(false)
    val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()
    val geohashPeople by viewModel.geohashPeople.observeAsState(emptyList())

    // Bookmarks store for current geohash toggle (iOS parity)
    val context = androidx.compose.ui.platform.LocalContext.current
    val bookmarksStore = remember { com.seamless.android.geohash.GeohashBookmarksStore.getInstance(context) }
    val bookmarks by bookmarksStore.bookmarks.observeAsState(emptyList())

    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val glassColor = if (isDark) 
        Color(0xF009090C)
    else 
        com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(glassColor)
            .padding(vertical = 8.dp), // Add padding for header breathing room
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hamburger menu button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.size(23.dp),
                    tint = colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(2.dp))
            
            Text(
                text = stringResource(R.string.app_brand),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.singleOrTripleClickable(
                    onSingleClick = onTitleClick,
                    onTripleClick = onTripleTitleClick
                )
            )
            
            Spacer(modifier = Modifier.width(2.dp))
            
            NicknameEditor(
                value = nickname,
                onValueChange = onNicknameChange
            )
        }
        
        // Right section with location channels button and peer counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(end = 6.dp)
        ) {
            // Network/People button with count - shows who's connected
            val peerCount = connectedPeers.filter { it != viewModel.meshServiceInstance?.myPeerID }.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (peerCount > 0) com.seamless.android.ui.theme.SeamlessColors.Emerald.copy(alpha = 0.14f)
                        else colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (peerCount > 0) com.seamless.android.ui.theme.SeamlessColors.Emerald.copy(alpha = 0.32f)
                        else colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable { onSidebarClick() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (peerCount > 0) com.seamless.android.ui.theme.SeamlessColors.Emerald
                            else colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                )
                Icon(
                    imageVector = Icons.Outlined.People,
                    contentDescription = "Network",
                    modifier = Modifier.size(15.dp),
                    tint = if (peerCount > 0) com.seamless.android.ui.theme.SeamlessColors.Emerald else colorScheme.onSurfaceVariant
                )
                Text(
                    text = peerCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (peerCount > 0) com.seamless.android.ui.theme.SeamlessColors.Emerald else colorScheme.onSurfaceVariant
                )
            }
            
            // Chats button - shows list of favorited contacts
            IconButton(
                onClick = onChatsClick,
                modifier = Modifier.size(34.dp)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = "Chats",
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    // Show badge if there are unread messages
                    if (hasUnreadPrivateMessages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(com.seamless.android.ui.theme.SeamlessColors.Error)
                        )
                    }
                }
            }
            
            // Settings button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            // Location channels button (matching iOS implementation) and bookmark grouped tightly
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 2.dp)) {
                LocationChannelsButton(
                    viewModel = viewModel,
                    onClick = onLocationChannelsClick
                )

                // Bookmark toggle for current geohash (not shown for mesh)
                val currentGeohash: String? = when (val sc = selectedLocationChannel) {
                    is com.seamless.android.geohash.ChannelID.Location -> sc.channel.geohash
                    else -> null
                }
                if (currentGeohash != null) {
                    val isBookmarked = bookmarks.contains(currentGeohash)
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp) // minimal gap between geohash and bookmark
                            .size(20.dp)
                            .clickable { bookmarksStore.toggle(currentGeohash) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_toggle_bookmark),
                            tint = if (isBookmarked) com.seamless.android.ui.theme.SeamlessColors.Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Location Notes button (extracted to separate component)
            LocationNotesButton(
                viewModel = viewModel,
                onClick = onLocationNotesClick
            )

            // Tor status dot when Tor is enabled
            TorStatusDot(
                modifier = Modifier
                    .size(8.dp)
                    .padding(start = 0.dp, end = 2.dp)
            )
            
            // PoW status indicator
            PoWStatusIndicator(
                modifier = Modifier,
                style = PoWIndicatorStyle.COMPACT
            )
        }
    }
}

@Composable
private fun LocationChannelsButton(
    viewModel: ChatViewModel,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Get current channel selection from location manager
    val selectedChannel by viewModel.selectedLocationChannel.observeAsState()
    val teleported by viewModel.isTeleported.observeAsState(false)
    
    val (badgeText, badgeColor) = when (selectedChannel) {
        is com.seamless.android.geohash.ChannelID.Mesh -> {
            "#mesh" to Color(0xFF007AFF) // iOS blue for mesh
        }
        is com.seamless.android.geohash.ChannelID.Location -> {
            val geohash = (selectedChannel as com.seamless.android.geohash.ChannelID.Location).channel.geohash
            "#$geohash" to Color(0xFF00C851) // Green for location
        }
        null -> "#mesh" to Color(0xFF007AFF) // Default to mesh
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = badgeColor.copy(alpha = 0.28f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = badgeColor,
            maxLines = 1
        )

        if (teleported) {
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.PinDrop,
                contentDescription = stringResource(R.string.cd_teleported),
                modifier = Modifier.size(12.dp),
                tint = badgeColor
            )
        }
    }
}
