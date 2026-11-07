@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Empty state displays for various contexts in the app
 */

enum class EmptyStateContext {
    NO_PEERS,
    NO_MESSAGES,
    NO_CHANNELS,
    NO_PRIVATE_CHATS,
    SEARCHING_PEERS,
    NO_CHANNEL_MESSAGES,
    NO_SEARCH_RESULTS
}

@Composable
fun EmptyStateView(
    context: EmptyStateContext,
    modifier: Modifier = Modifier,
    customTitle: String? = null,
    customMessage: String? = null,
    customIcon: ImageVector? = null,
    actionButton: @Composable (() -> Unit)? = null
) {
    val (icon, title, message) = when (context) {
        EmptyStateContext.NO_PEERS -> Triple(
            Icons.AutoMirrored.Outlined.BluetoothSearching,
            "Looking for nearby devices...",
            "Make sure Bluetooth is on and others are using Seamless nearby. You'll automatically connect when devices are in range."
        )
        EmptyStateContext.NO_MESSAGES -> Triple(
            Icons.Outlined.ChatBubbleOutline,
            "No messages yet",
            "Start chatting! Messages are end-to-end encrypted and relay through the mesh network. Type a message below to begin."
        )
        EmptyStateContext.NO_CHANNELS -> Triple(
            Icons.Outlined.Tag,
            "No channels joined",
            "Join a channel with /j #channel or create your own. Channels are topic-based group chats that anyone can join."
        )
        EmptyStateContext.NO_PRIVATE_CHATS -> Triple(
            Icons.Outlined.LockOpen,
            "No private chats",
            "Start a private encrypted chat with someone nearby. Use /m @nickname or tap on their name in the sidebar."
        )
        EmptyStateContext.SEARCHING_PEERS -> Triple(
            null, // We'll use the RadarScanner component instead
            "Searching nearby devices...",
            "Connecting securely via Bluetooth mesh. Please keep Bluetooth ON and stay nearby."
        )
        EmptyStateContext.NO_CHANNEL_MESSAGES -> Triple(
            Icons.Outlined.Forum,
            "Channel is empty",
            "Be the first to say something! Messages in this channel will be visible to all members."
        )
        EmptyStateContext.NO_SEARCH_RESULTS -> Triple(
            Icons.Outlined.SearchOff,
            "No results found",
            "Try adjusting your search terms or browse all available content."
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Show RadarScanner for searching peers, regular icon for others
        if (context == EmptyStateContext.SEARCHING_PEERS) {
            com.seamless.android.ui.components.RadarScanner(
                isScanning = true,
                title = customTitle ?: title,
                subtitle = customMessage ?: message
            )
        } else {
            // Icon for other states (if available)
            if (icon != null) {
                Icon(
                    imageVector = customIcon ?: icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp) // Larger icon
                        .padding(bottom = 24.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            
            Text(
                text = customTitle ?: title,
                style = MaterialTheme.typography.titleLarge, // Larger title
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = customMessage ?: message,
                style = MaterialTheme.typography.bodyLarge, // Larger body text
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            if (actionButton != null) {
                Spacer(modifier = Modifier.height(32.dp))
                actionButton()
            }
        }
    }
}

@Composable
fun MessageListEmptyState(
    isSearchingPeers: Boolean,
    hasPeers: Boolean,
    isPrivateChat: Boolean,
    isChannel: Boolean,
    channelName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = when {
        isSearchingPeers -> EmptyStateContext.SEARCHING_PEERS
        isPrivateChat -> EmptyStateContext.NO_MESSAGES
        isChannel -> EmptyStateContext.NO_CHANNEL_MESSAGES
        !hasPeers -> EmptyStateContext.NO_PEERS
        else -> EmptyStateContext.NO_MESSAGES
    }
    
    val customMessage = if (isChannel && channelName != null) {
        "Be the first to post in $channelName! Messages here are visible to all channel members."
    } else null
    
    EmptyStateView(
        context = context,
        customMessage = customMessage,
        modifier = modifier
    )
}

@Composable
fun SidebarEmptyState(
    showChannels: Boolean,
    modifier: Modifier = Modifier
) {
    EmptyStateView(
        context = if (showChannels) EmptyStateContext.NO_CHANNELS else EmptyStateContext.NO_PEERS,
        modifier = modifier,
        actionButton = if (showChannels) {
            {
                OutlinedButton(
                    onClick = { /* Navigate to help or show command palette */ },
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Help,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Learn about channels")
                }
            }
        } else null
    )
}

@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}
