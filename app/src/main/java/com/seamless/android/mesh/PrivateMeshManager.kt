@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Private Mesh functionality
 * Allows users to create and join controlled mesh groups with host approval
 */
class PrivateMeshManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PrivateMeshManager"
        
        @Volatile
        private var instance: PrivateMeshManager? = null
        
        fun getInstance(context: Context): PrivateMeshManager {
            return instance ?: synchronized(this) {
                instance ?: PrivateMeshManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State flows
    private val _state = MutableStateFlow(PrivateMeshState.IDLE)
    val state: StateFlow<PrivateMeshState> = _state.asStateFlow()
    
    private val _currentMeshName = MutableStateFlow<String?>(null)
    val currentMeshName: StateFlow<String?> = _currentMeshName.asStateFlow()
    
    private val _currentMeshId = MutableStateFlow<String?>(null)
    val currentMeshId: StateFlow<String?> = _currentMeshId.asStateFlow()
    
    private val _members = MutableStateFlow<List<MeshMember>>(emptyList())
    val members: StateFlow<List<MeshMember>> = _members.asStateFlow()
    
    private val _pendingRequests = MutableStateFlow<List<JoinRequest>>(emptyList())
    val pendingRequests: StateFlow<List<JoinRequest>> = _pendingRequests.asStateFlow()
    
    private val _discoveredMeshes = MutableStateFlow<List<DiscoveredMesh>>(emptyList())
    val discoveredMeshes: StateFlow<List<DiscoveredMesh>> = _discoveredMeshes.asStateFlow()
    
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    // Mesh chat messages
    private val _messages = MutableStateFlow<List<PrivateMeshMessage>>(emptyList())
    val messages: StateFlow<List<PrivateMeshMessage>> = _messages.asStateFlow()
    
    // Track approved members for auto-rejoin
    private val approvedMembers = ConcurrentHashMap<String, String>() // peerID -> nickname
    
    // Deduplicate incoming mesh messages within a short window
    private val recentMessageFingerprints = ConcurrentHashMap<String, Long>()
    
    // Discovery job
    private var discoveryJob: Job? = null
    
    // Join request retry job
    private var joinRequestJob: Job? = null
    
    // Heartbeat job for pruning stale members
    private var heartbeatJob: Job? = null
    
    // Delegate for mesh service integration
    var delegate: PrivateMeshDelegate? = null
    
    // Host info
    private var myPeerID: String = ""
    private var myNickname: String = ""
    
    fun setIdentity(peerID: String, nickname: String) {
        myPeerID = peerID
        myNickname = nickname
    }
    
    // ==================== MESSAGING ====================
    
    /**
     * Send a message to the private mesh
     */
    fun sendMessage(content: String) {
        if (_state.value != PrivateMeshState.HOSTING && _state.value != PrivateMeshState.CONNECTED) {
            Log.w(TAG, "Cannot send message - not in a mesh")
            return
        }
        
        val message = PrivateMeshMessage(
            senderPeerID = myPeerID,
            senderNickname = myNickname,
            content = content,
            isFromMe = true
        )
        
        // Cap messages to prevent unbounded memory growth
        val updated = _messages.value + message
        _messages.value = if (updated.size > 500) updated.takeLast(500) else updated
        
        // Send via delegate to broadcast to mesh (include message.id so reactions match across devices)
        delegate?.sendMeshMessage(_currentMeshId.value ?: "", content, message.id)
    }
    
    /**
     * Send a voice note to the private mesh
     */
    fun sendVoiceNote(filePath: String) {
        if (_state.value != PrivateMeshState.HOSTING && _state.value != PrivateMeshState.CONNECTED) {
            Log.w(TAG, "Cannot send voice note - not in a mesh")
            return
        }
        
        Log.i(TAG, "🎤 Sending voice note to mesh: $filePath")
        
        // Add local message for voice note
        val message = PrivateMeshMessage(
            senderPeerID = myPeerID,
            senderNickname = myNickname,
            content = filePath,
            isFromMe = true,
            type = PrivateMeshMessageType.AUDIO
        )
        val updated2 = _messages.value + message
        _messages.value = if (updated2.size > 500) updated2.takeLast(500) else updated2
        
        // Send via delegate
        delegate?.sendMeshVoiceNote(_currentMeshId.value ?: "", filePath, message.id)
    }
    
    /**
     * Send an image to the private mesh
     */
    fun sendImage(filePath: String) {
        if (_state.value != PrivateMeshState.HOSTING && _state.value != PrivateMeshState.CONNECTED) {
            Log.w(TAG, "Cannot send image - not in a mesh")
            return
        }
        
        Log.i(TAG, "🖼️ Sending image to mesh: $filePath")
        
        // Add local message for image
        val message = PrivateMeshMessage(
            senderPeerID = myPeerID,
            senderNickname = myNickname,
            content = filePath,
            isFromMe = true,
            type = PrivateMeshMessageType.IMAGE
        )
        val updated3 = _messages.value + message
        _messages.value = if (updated3.size > 500) updated3.takeLast(500) else updated3
        
        // Send via delegate
        delegate?.sendMeshImage(_currentMeshId.value ?: "", filePath, message.id)
    }
    
    /**
     * Called when a message is received from the mesh
     */
    fun onMessageReceived(senderPeerID: String, senderNickname: String, content: String, messageId: String? = null) {
        if (_state.value != PrivateMeshState.HOSTING && _state.value != PrivateMeshState.CONNECTED) {
            return
        }
        
        // Ignore our own messages (they're already added in sendMessage)
        if (senderPeerID.equals(myPeerID, ignoreCase = true)) return
        
        updateMemberLastSeen(senderPeerID)
        
        // Deduplicate identical messages received within 3 seconds
        val now = System.currentTimeMillis()
        val dedupKey = "${senderPeerID.lowercase()}:${messageId ?: content}"
        val prevTimestamp = recentMessageFingerprints.put(dedupKey, now)
        if (prevTimestamp != null && (now - prevTimestamp) < 3_000L) {
            return
        }
        if (recentMessageFingerprints.size > 200) {
            val cutoff = now - 15_000L
            recentMessageFingerprints.entries.removeIf { it.value < cutoff }
        }
        
        val resolvedNickname = if (senderNickname.isBlank() || senderNickname.equals("Unknown", ignoreCase = true)) {
            _members.value.firstOrNull { it.peerID.equals(senderPeerID, ignoreCase = true) }?.nickname ?: senderNickname
        } else {
            senderNickname
        }
        
        val message = PrivateMeshMessage(
            id = messageId ?: UUID.randomUUID().toString(),
            senderPeerID = senderPeerID,
            senderNickname = resolvedNickname,
            content = content,
            isFromMe = false
        )
        
        val updated4 = _messages.value + message
        _messages.value = if (updated4.size > 500) updated4.takeLast(500) else updated4
    }
    
    /**
     * Called when media (audio/image) is received from the mesh
     */
    fun onMediaReceived(senderPeerID: String, senderNickname: String, filePath: String, type: PrivateMeshMessageType, messageId: String? = null) {
        if (_state.value != PrivateMeshState.HOSTING && _state.value != PrivateMeshState.CONNECTED) {
            return
        }
        
        // Ignore our own media (they're already added in sendVoiceNote/sendImage)
        if (senderPeerID.equals(myPeerID, ignoreCase = true)) return
        
        updateMemberLastSeen(senderPeerID)
        
        val resolvedNickname = if (senderNickname.isBlank() || senderNickname.equals("Unknown", ignoreCase = true)) {
            _members.value.firstOrNull { it.peerID.equals(senderPeerID, ignoreCase = true) }?.nickname ?: senderNickname
        } else {
            senderNickname
        }
        
        Log.i(TAG, "📥 Received mesh media from $resolvedNickname: $type")
        
        val message = PrivateMeshMessage(
            id = messageId ?: UUID.randomUUID().toString(),
            senderPeerID = senderPeerID,
            senderNickname = resolvedNickname,
            content = filePath,
            isFromMe = false,
            type = type
        )
        
        val updated5 = _messages.value + message
        _messages.value = if (updated5.size > 500) updated5.takeLast(500) else updated5
    }
    
    // ==================== HOST FUNCTIONS ====================
    
    /**
     * Create and host a new private mesh
     */
    fun createMesh(name: String) {
        if (_state.value != PrivateMeshState.IDLE) {
            Log.w(TAG, "Cannot create mesh - already in state: ${_state.value}")
            return
        }
        
        val meshId = UUID.randomUUID().toString().replace("-", "").take(16).lowercase()
        
        Log.i(TAG, "🌐 Creating private mesh: $name (ID: $meshId, length: ${meshId.length})")
        
        _currentMeshId.value = meshId
        _currentMeshName.value = name
        _state.value = PrivateMeshState.HOSTING
        
        // Add self as host member
        val selfMember = MeshMember(
            peerID = myPeerID,
            nickname = myNickname,
            isHost = true
        )
        _members.value = listOf(selfMember)
        
        // Start broadcasting mesh availability
        startMeshBroadcast()
        
        delegate?.onMeshCreated(meshId, name)
        
        // Start heartbeat to monitor member freshness
        startHeartbeat()
    }
    
    /**
     * Stop hosting the private mesh
     */
    fun stopHosting() {
        if (_state.value != PrivateMeshState.HOSTING) return
        
        Log.i(TAG, "🛑 Stopping private mesh: ${_currentMeshName.value}")
        
        // Notify members that mesh is closing
        delegate?.onMeshClosing(_currentMeshId.value ?: "")
        
        approvedMembers.clear()
        reset()
    }
    
    /**
     * Accept a join request
     */
    fun acceptJoinRequest(requestId: String) {
        val request = _pendingRequests.value.find { it.requestId == requestId } ?: return
        
        Log.i(TAG, "✅ Accepting join request from: ${request.requesterNickname}")
        
        // Add to approved members for auto-rejoin
        approvedMembers[request.requesterPeerID.lowercase()] = request.requesterNickname
        
        // Add to members list (avoiding duplicates)
        val newMember = MeshMember(
            peerID = request.requesterPeerID,
            nickname = request.requesterNickname,
            isHost = false,
            lastSeen = System.currentTimeMillis()
        )
        _members.value = _members.value.filterNot { it.peerID.equals(request.requesterPeerID, ignoreCase = true) } + newMember
        
        // Remove from pending
        _pendingRequests.value = _pendingRequests.value.filterNot {
            it.requestId == requestId || it.requesterPeerID.equals(request.requesterPeerID, ignoreCase = true)
        }
        
        // Notify the requester
        delegate?.onJoinRequestAccepted(request.requesterPeerID, _currentMeshId.value ?: "", _currentMeshName.value ?: "")
        
        // Broadcast updated member list
        delegate?.onMemberListChanged(_members.value)
    }
    
    /**
     * Reject a join request
     */
    fun rejectJoinRequest(requestId: String) {
        val request = _pendingRequests.value.find { it.requestId == requestId } ?: return
        
        Log.i(TAG, "❌ Rejecting join request from: ${request.requesterNickname}")
        
        // Remove from pending
        _pendingRequests.value = _pendingRequests.value.filter { it.requestId != requestId }
        
        // Notify the requester
        delegate?.onJoinRequestRejected(request.requesterPeerID)
    }
    
    /**
     * Kick a member from the mesh
     */
    fun kickMember(peerID: String) {
        if (_state.value != PrivateMeshState.HOSTING) return
        
        Log.i(TAG, "🚫 Kicking member: $peerID")
        
        // Remove from approved members
        approvedMembers.remove(peerID.lowercase())
        approvedMembers.remove(peerID)
        
        // Remove from members list
        _members.value = _members.value.filterNot { it.peerID.equals(peerID, ignoreCase = true) }
        
        // Notify the kicked member
        delegate?.onMemberKicked(peerID)
        
        // Broadcast updated member list
        delegate?.onMemberListChanged(_members.value)
    }
    
    // ==================== CLIENT FUNCTIONS ====================
    
    /**
     * Start discovering nearby private meshes
     */
    fun startDiscovery() {
        if (_isDiscovering.value) return
        
        Log.i(TAG, "🔍 Starting private mesh discovery...")
        _isDiscovering.value = true
        _discoveredMeshes.value = emptyList()
        
        discoveryJob = scope.launch {
            while (isActive) {
                // Discovery happens via BluetoothMeshService packets
                // We just need to keep the flag active
                delay(10_000) // Cleanup stale meshes every 10 seconds
                
                val now = System.currentTimeMillis()
                val activeMeshes = _discoveredMeshes.value.filter { 
                    now - it.lastSeen < 30_000 // Remove meshes not seen in 30 seconds
                }
                _discoveredMeshes.value = activeMeshes
            }
        }
        
        delegate?.onStartDiscovery()
    }
    
    /**
     * Stop discovering meshes
     */
    fun stopDiscovery() {
        Log.i(TAG, "🛑 Stopping private mesh discovery")
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscovering.value = false
    }
    
    /**
     * Request to join a discovered mesh
     */
    fun requestToJoin(meshId: String) {
        val mesh = _discoveredMeshes.value.find { it.meshId == meshId } ?: return
        
        if (_state.value != PrivateMeshState.IDLE) {
            Log.w(TAG, "Cannot join - already in state: ${_state.value}")
            return
        }
        
        Log.i(TAG, "📤 Requesting to join mesh: ${mesh.name}")
        
        _state.value = PrivateMeshState.JOINING
        _currentMeshId.value = meshId
        _currentMeshName.value = mesh.name
        
        stopDiscovery()
        
        // Retry join request periodically while waiting for host approval / BLE delivery
        joinRequestJob?.cancel()
        joinRequestJob = scope.launch {
            var attempts = 0
            while (isActive && _state.value == PrivateMeshState.JOINING && attempts < 6) {
                delegate?.onJoinRequested(meshId, mesh.hostPeerID)
                attempts++
                delay(4_000L)
            }
        }
    }
    
    /**
     * Leave the current private mesh
     */
    fun leaveMesh() {
        if (_state.value == PrivateMeshState.IDLE) return
        
        Log.i(TAG, "👋 Leaving mesh: ${_currentMeshName.value}")
        
        delegate?.onLeavingMesh(_currentMeshId.value ?: "")
        
        reset()
    }
    
    // ==================== PACKET HANDLERS ====================
    
    /**
     * Called when a mesh announcement is received
     */
    fun onMeshAnnounceReceived(meshId: String, name: String, hostPeerID: String, hostNickname: String, memberCount: Int, rssi: Int = Int.MIN_VALUE) {
        updateMemberLastSeen(hostPeerID)
        if (_state.value == PrivateMeshState.HOSTING && _currentMeshId.value == meshId) {
            // This is our own announcement, ignore
            return
        }
        
        val mesh = DiscoveredMesh(
            meshId = meshId,
            name = name,
            hostPeerID = hostPeerID,
            hostNickname = hostNickname,
            memberCount = memberCount,
            rssi = rssi,
            lastSeen = System.currentTimeMillis()
        )
        
        // Update or add to discovered meshes
        val currentMeshes = _discoveredMeshes.value.toMutableList()
        val existingIndex = currentMeshes.indexOfFirst { it.meshId == meshId }
        if (existingIndex >= 0) {
            currentMeshes[existingIndex] = mesh
        } else {
            currentMeshes.add(mesh)
            Log.i(TAG, "🎯 Discovered private mesh: $name (Host: $hostNickname)")
        }
        _discoveredMeshes.value = currentMeshes
    }
    
    /**
     * Called when a heartbeat ping is received for a private mesh
     */
    fun onHeartbeatReceived(peerID: String, meshId: String) {
        updateMemberLastSeen(peerID)
        // Also refresh discovered mesh timestamp if this peer is a known host
        val currentMeshes = _discoveredMeshes.value
        if (currentMeshes.any { it.meshId == meshId || it.hostPeerID.equals(peerID, ignoreCase = true) }) {
            _discoveredMeshes.value = currentMeshes.map { mesh ->
                if (mesh.meshId == meshId || mesh.hostPeerID.equals(peerID, ignoreCase = true)) {
                    mesh.copy(lastSeen = System.currentTimeMillis())
                } else {
                    mesh
                }
            }
        }
    }
    
    /**
     * Called when a member explicitly leaves the mesh
     */
    fun onMemberLeft(peerID: String) {
        if (_state.value != PrivateMeshState.HOSTING) return
        val beforeSize = _members.value.size
        _members.value = _members.value.filterNot { it.peerID.equals(peerID, ignoreCase = true) }
        if (_members.value.size != beforeSize) {
            Log.i(TAG, "👋 Member $peerID left the mesh; broadcasting updated member list")
            delegate?.onMemberListChanged(_members.value)
        }
    }
    
    /**
     * Called when a join request is received (host only)
     */
    fun onJoinRequestReceived(requestId: String, requesterPeerID: String, requesterNickname: String) {
        if (_state.value != PrivateMeshState.HOSTING) return
        
        updateMemberLastSeen(requesterPeerID)
        
        // Check if this is an approved member (auto-rejoin)
        if (approvedMembers.containsKey(requesterPeerID.lowercase()) || approvedMembers.containsKey(requesterPeerID)) {
            Log.i(TAG, "🔄 Auto-accepting rejoining member: $requesterNickname")
            
            // Prevent duplicate members on re-join
            if (_members.value.any { it.peerID.equals(requesterPeerID, ignoreCase = true) }) {
                Log.d(TAG, "Member $requesterNickname already in list, re-sending acceptance & member list")
                updateMemberLastSeen(requesterPeerID)
                delegate?.onJoinRequestAccepted(requesterPeerID, _currentMeshId.value ?: "", _currentMeshName.value ?: "")
                delegate?.onMemberListChanged(_members.value)
                return
            }
            
            // Auto-accept
            val newMember = MeshMember(
                peerID = requesterPeerID,
                nickname = requesterNickname,
                isHost = false,
                lastSeen = System.currentTimeMillis()
            )
            _members.value = _members.value + newMember
            
            delegate?.onJoinRequestAccepted(requesterPeerID, _currentMeshId.value ?: "", _currentMeshName.value ?: "")
            delegate?.onMemberListChanged(_members.value)
            return
        }
        
        // Deduplicate pending requests from the same peer
        if (_pendingRequests.value.any { it.requesterPeerID.equals(requesterPeerID, ignoreCase = true) }) {
            Log.d(TAG, "📥 Duplicate join request from $requesterNickname ignored (already pending)")
            return
        }
        
        Log.i(TAG, "📥 Received join request from: $requesterNickname")
        
        val request = JoinRequest(
            requestId = requestId,
            requesterPeerID = requesterPeerID,
            requesterNickname = requesterNickname
        )
        
        _pendingRequests.value = _pendingRequests.value + request
    }
    
    /**
     * Called when join request is accepted (client)
     */
    fun onJoinAccepted(meshId: String, meshName: String, members: List<MeshMember>) {
        if (_state.value != PrivateMeshState.JOINING && _state.value != PrivateMeshState.CONNECTED) return
        
        joinRequestJob?.cancel()
        joinRequestJob = null
        
        if (_state.value == PrivateMeshState.CONNECTED && _currentMeshId.value == meshId) {
            return
        }
        
        Log.i(TAG, "✅ Join request accepted for mesh: $meshName")
        
        _currentMeshId.value = meshId
        _currentMeshName.value = meshName
        if (members.isNotEmpty() || _members.value.isEmpty()) {
            _members.value = members
        }
        _state.value = PrivateMeshState.CONNECTED
        
        // Start heartbeat as connected member so host doesn't prune us
        startHeartbeat()
    }
    
    /**
     * Called when join request is rejected (client)
     */
    fun onJoinRejected() {
        if (_state.value != PrivateMeshState.JOINING) return
        
        Log.i(TAG, "❌ Join request rejected")
        
        reset()
    }
    
    /**
     * Called when member list is updated
     */
    fun onMemberListUpdated(members: List<MeshMember>) {
        val now = System.currentTimeMillis()
        _members.value = members.map { it.copy(lastSeen = now) }
    }
    
    /**
     * Called when host closes the mesh
     */
    fun onMeshClosed() {
        if (_state.value == PrivateMeshState.CONNECTED || _state.value == PrivateMeshState.JOINING) {
            Log.i(TAG, "🚫 Mesh was closed by host")
            reset()
        }
    }
    
    /**
     * Called when we are kicked from the mesh
     */
    fun onKicked() {
        Log.i(TAG, "🚫 We were kicked from the mesh")
        reset()
    }
    
    // ==================== HEARTBEAT & MEMBER TRACKING ====================
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000L)
                val currentState = _state.value
                if (currentState == PrivateMeshState.HOSTING || currentState == PrivateMeshState.CONNECTED) {
                    // Send heartbeat ping (both host and members send heartbeats)
                    delegate?.sendHeartbeat(_currentMeshId.value ?: "")
                    
                    // Only host prunes stale members
                    if (currentState == PrivateMeshState.HOSTING) {
                        val now = System.currentTimeMillis()
                        val staleThreshold = 45000L // 45 seconds without any heartbeat or packet
                        val currentMembers = _members.value
                        val staleMembers = currentMembers.filter { 
                            !it.isHost && (now - it.lastSeen) > staleThreshold 
                        }
                        if (staleMembers.isNotEmpty()) {
                            Log.i(TAG, "💓 Heartbeat: pruning ${staleMembers.size} stale members: ${staleMembers.map { it.nickname }}")
                            _members.value = currentMembers.filter { member ->
                                member.isHost || (now - member.lastSeen) <= staleThreshold
                            }
                            delegate?.onMemberListChanged(_members.value)
                        }
                    }
                }
            }
        }
    }
    
    fun updateMemberLastSeen(peerID: String) {
        val current = _members.value
        if (current.none { it.peerID.equals(peerID, ignoreCase = true) }) return
        val now = System.currentTimeMillis()
        _members.value = current.map { member ->
            if (member.peerID.equals(peerID, ignoreCase = true)) member.copy(lastSeen = now)
            else member
        }
    }
    
    // ==================== PRIVATE HELPERS ====================
    
    private fun startMeshBroadcast() {
        scope.launch {
            while (_state.value == PrivateMeshState.HOSTING) {
                delegate?.broadcastMeshAnnounce(
                    meshId = _currentMeshId.value ?: "",
                    name = _currentMeshName.value ?: "",
                    memberCount = _members.value.size
                )
                delay(4_000) // Broadcast every 4 seconds for fast discovery
            }
        }
    }
    
    private fun reset() {
        joinRequestJob?.cancel()
        joinRequestJob = null
        _state.value = PrivateMeshState.IDLE
        _currentMeshId.value = null
        _currentMeshName.value = null
        _members.value = emptyList()
        _pendingRequests.value = emptyList()
        _messages.value = emptyList()
        recentMessageFingerprints.clear()
        heartbeatJob?.cancel()
        heartbeatJob = null
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscovering.value = false
    }
    
    fun shutdown() {
        scope.cancel()
        reset()
    }
}

/**
 * Delegate interface for PrivateMeshManager to communicate with BluetoothMeshService
 */
interface PrivateMeshDelegate {
    fun onMeshCreated(meshId: String, name: String)
    fun onMeshClosing(meshId: String)
    fun onStartDiscovery()
    fun onJoinRequested(meshId: String, hostPeerID: String)
    fun onJoinRequestAccepted(requesterPeerID: String, meshId: String, meshName: String)
    fun onJoinRequestRejected(requesterPeerID: String)
    fun onMemberListChanged(members: List<MeshMember>)
    fun onMemberKicked(peerID: String)
    fun onLeavingMesh(meshId: String)
    fun broadcastMeshAnnounce(meshId: String, name: String, memberCount: Int)
    fun sendMeshMessage(meshId: String, content: String, messageId: String = "")
    fun sendMeshVoiceNote(meshId: String, filePath: String, messageId: String = "")
    fun sendMeshImage(meshId: String, filePath: String, messageId: String = "")
    fun sendVoiceStream(chunk: ByteArray)  // Real-time walkie-talkie
    fun sendHeartbeat(meshId: String)       // Periodic heartbeat ping
}

