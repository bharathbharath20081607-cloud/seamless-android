@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorGroupMessaging
import com.seamless.android.net.TorMessagingService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ───────────────────────── Shared helpers ─────────────────────────

// M3 HCT Iso-Luminant Sender Palette (Dark T76 / Light T42) — zero Bezold-Brücke shift
private val SenderPaletteDark = listOf(
    Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFF818CF8),
    Color(0xFFFBBF24), Color(0xFFFB7185), Color(0xFF22D3EE)
)
private val SenderPaletteLight = listOf(
    Color(0xFF1D4ED8), Color(0xFF047857), Color(0xFF4338CA),
    Color(0xFFB45309), Color(0xFFBE123C), Color(0xFF0E7490)
)

private val AvatarGradients = listOf(
    listOf(Color(0xFF2563EB), Color(0xFF1E3A8A)),
    listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)),
    listOf(Color(0xFF6366F1), Color(0xFF312E81)),
    listOf(Color(0xFF10B981), Color(0xFF065F46)),
    listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)),
    listOf(Color(0xFFEC4899), Color(0xFF831843))
)

/** Stable, polarity-calibrated iso-luminant sender tint so members stay legible in Light & Dark modes. */
internal fun senderColor(key: String): Color {
    val palette = if (com.seamless.android.ui.theme.ThemePreferenceManager.isCurrentlyDark) {
        SenderPaletteDark
    } else {
        SenderPaletteLight
    }
    return palette[(key.hashCode() and 0x7fffffff) % palette.size]
}

internal fun formatChatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

internal fun formatDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

internal fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@Composable
internal fun GroupAvatar(
    name: String,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
    onlineCount: Int = 0
) {
    val gradient = remember(name) {
        if (name.isBlank()) listOf(Color(0xFF2E2E38), Color(0xFF1A1A21))
        else AvatarGradients[(name.trim().lowercase().hashCode() and 0x7fffffff) % AvatarGradients.size]
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient))
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val initials = when {
                words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> null
            }
            if (initials != null) {
                Text(
                    initials,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * if (initials.length > 1) 0.34f else 0.40f).sp
                )
            } else {
                Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (onlineCount > 0) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.28f).coerceIn(11f, 16f).dp)
                    .clip(CircleShape)
                    .background(OnionUi.Canvas)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnionUi.Online)
            )
        }
    }
}

/** Multi-select list of contacts with a circular check indicator. */
@Composable
internal fun ContactPickerList(
    candidates: List<OnionContactManager.OnionContact>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(candidates, key = { it.id }) { contact ->
            val isOn = contact.id in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isOn) OnionUi.TextPrimary.copy(alpha = 0.08f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isOn) OnionUi.TextPrimary.copy(alpha = 0.24f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggle(contact.id) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupAvatar(contact.nickname, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.nickname, color = OnionUi.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(contact.onionAddress.take(18) + "…", color = OnionUi.TextMuted, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isOn) OnionUi.Online else Color.Transparent)
                        .border(1.5.dp, if (isOn) OnionUi.Online else OnionUi.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOn) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
internal fun onionFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnionUi.TextPrimary,
    unfocusedTextColor = OnionUi.TextPrimary,
    focusedBorderColor = OnionUi.TextPrimary.copy(alpha = 0.6f),
    unfocusedBorderColor = OnionUi.Outline,
    focusedLabelColor = OnionUi.TextPrimary.copy(alpha = 0.8f),
    unfocusedLabelColor = OnionUi.TextSecondary,
    cursorColor = OnionUi.TextPrimary,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

// ───────────────────────── Groups tab ─────────────────────────

@Composable
internal fun PrivateGroupsTab(onGroupClick: (PrivateGroupEntity) -> Unit) {
    val context = LocalContext.current
    val groups by remember { AppDatabase.getDatabase(context).groupDao().getGroupsOrderedByRecentActivity() }
        .collectAsState(initial = emptyList())

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                GroupAvatar("", size = 84.dp)
                Spacer(Modifier.height(20.dp))
                Text("No private groups yet", color = OnionUi.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a private group and invite your Tor contacts. Every message is end-to-end encrypted peer-to-peer with zero central servers.",
                    color = OnionUi.TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups, key = { it.groupId }) { group -> GroupRow(group) { onGroupClick(group) } }
    }
}

@Composable
internal fun GroupRow(group: PrivateGroupEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).groupDao() }
    val service = remember { TorGroupMessaging.getInstance(context) }
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val lastReadMap by service.lastReadAtFlow.collectAsState()
    val lastRead = lastReadMap[group.groupId] ?: service.lastReadAt(group.groupId)
    val last by remember(group.groupId) { dao.getLastMessage(group.groupId) }.collectAsState(initial = null)
    val unread by remember(group.groupId, lastRead) { dao.getUnreadCount(group.groupId, lastRead) }
        .collectAsState(initial = 0)
    val members by remember(group.groupId) { dao.getMembersForGroup(group.groupId) }
        .collectAsState(initial = emptyList())

    val onlineCount = remember(members, connectedPeers) {
        members.count { member ->
            connectedPeers.any { peer ->
                OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peer) ==
                    OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OnionUi.Corner)
            .background(OnionUi.Surface)
            .border(1.dp, if (unread > 0) OnionUi.Online.copy(alpha = 0.35f) else OnionUi.Hairline, OnionUi.Corner)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(group.name, onlineCount = onlineCount)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    color = OnionUi.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (group.isCreatedByMe) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Creator",
                        tint = Color(0xFFFFD166),
                        modifier = Modifier.size(14.dp)
                    )
                }
                last?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatChatTime(it.timestamp),
                        color = if (unread > 0) OnionUi.Online else OnionUi.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previewText = remember(last, members.size, onlineCount) {
                    val msg = last
                    if (msg != null) {
                        val cleanBody = com.seamless.android.net.TorRichChatCodec.friendlyPreviewText(
                            msg.text,
                            msg.attachmentMimeType
                        )
                        if (msg.senderOnionAddress == TorGroupMessaging.SYSTEM_SENDER) {
                            cleanBody
                        } else {
                            val prefix = if (msg.isFromMe) "You: " else "${msg.senderNickname}: "
                            prefix + cleanBody
                        }
                    } else {
                        val onlineSuffix = if (onlineCount > 0) " · $onlineCount online" else ""
                        "${members.size} member${if (members.size == 1) "" else "s"}$onlineSuffix · Encrypted group"
                    }
                }
                Text(
                    text = previewText,
                    color = if (unread > 0) OnionUi.TextPrimary else OnionUi.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(OnionUi.Online)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unread > 99) "99+" else unread.toString(),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── Create group sheet ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateGroupSheet(
    contacts: List<OnionContactManager.OnionContact>,
    onDismiss: () -> Unit,
    onCreate: (name: String, invitees: Set<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val eligible = remember(contacts) {
        contacts.filter {
            !it.requiresRendezvous() &&
                it.effectiveTrustState() != TorContactTrustState.BLOCKED &&
                it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED
        }
    }
    val filtered = remember(eligible, searchQuery) {
        if (searchQuery.isBlank()) eligible
        else eligible.filter {
            it.nickname.contains(searchQuery.trim(), ignoreCase = true) ||
                it.onionAddress.contains(searchQuery.trim(), ignoreCase = true)
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
                GroupAvatar(name.ifBlank { "G" }, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("New Private Group", color = OnionUi.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = OnionUi.Online, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "End-to-end encrypted peer-to-peer group",
                            color = OnionUi.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Group name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = onionFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Invite Tor contacts",
                    color = OnionUi.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (eligible.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val maxSelect = eligible.take(31).map { it.id }.toSet()
                            selected = if (selected.size == maxSelect.size) emptySet() else maxSelect
                        }
                    ) {
                        val capCount = minOf(eligible.size, 31)
                        Text(
                            if (selected.size == capCount) "Clear" else "Select all (${selected.size}/31)",
                            color = OnionUi.Online,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (eligible.size > 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts…", color = OnionUi.TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = OnionUi.TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = onionFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            if (eligible.isEmpty()) {
                Text(
                    "No eligible Tor contacts with verified .onion addresses yet. You can create the group now and invite members anytime.",
                    color = OnionUi.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                ContactPickerList(
                    candidates = filtered,
                    selected = selected,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id
                        else if (selected.size < 31) selected + id else selected
                    },
                    modifier = Modifier.height(250.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onCreate(name.trim(), selected) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnionUi.Accent,
                    contentColor = OnionUi.Canvas,
                    disabledContainerColor = OnionUi.TextPrimary.copy(alpha = 0.14f),
                    disabledContentColor = OnionUi.TextPrimary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                val suffix = if (selected.isNotEmpty()) " & Invite ${selected.size}" else ""
                Text("Create Group$suffix", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
