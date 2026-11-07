@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.services

import com.seamless.android.ui.ChatState

object ConversationAliasResolver {

    fun resolveCanonicalPeerID(
        selectedPeerID: String,
        connectedPeers: List<String>,
        meshNoiseKeyForPeer: (String) -> ByteArray?,
        meshHasPeer: (String) -> Boolean,
        nostrPubHexForAlias: (String) -> String?,
        findNoiseKeyForNostr: (String) -> ByteArray?
    ): String {
        var peer = selectedPeerID
        try {
            if (peer.startsWith("nostr_")) {
                val pubHex = nostrPubHexForAlias(peer)
                if (pubHex != null) {
                    val noiseKey = findNoiseKeyForNostr(pubHex)
                    if (noiseKey != null) {
                        val noiseHex = noiseKey.joinToString("") { b -> "%02x".format(b) }
                        // Prefer a connected mesh peer that matches this noise key
                        val meshPeer = connectedPeers.firstOrNull { pid ->
                            meshNoiseKeyForPeer(pid)?.contentEquals(noiseKey) == true
                        }
                        peer = meshPeer ?: noiseHex
                    }
                }
            } else if (peer.length == 64 && peer.matches(Regex("^[0-9a-fA-F]+$"))) {
                // Peer is full noise key hex: upgrade to active mesh peer if available
                val noiseKey = peer.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val meshPeer = connectedPeers.firstOrNull { pid ->
                    meshNoiseKeyForPeer(pid)?.contentEquals(noiseKey) == true
                }
                if (meshPeer != null) {
                    peer = meshPeer
                }
            }
        } catch (_: Exception) { /* no-op */ }
        return peer
    }

    fun unifyChatsIntoPeer(
        state: ChatState,
        targetPeerID: String,
        keysToMerge: List<String>
    ) {
        if (keysToMerge.isEmpty()) return
        val currentChats = state.getPrivateChatsValue().toMutableMap()
        val targetList = currentChats[targetPeerID]?.toMutableList() ?: mutableListOf()
        var didMerge = false
        keysToMerge.distinct().forEach { key ->
            if (key == targetPeerID) return@forEach
            val list = currentChats[key]
            if (!list.isNullOrEmpty()) {
                targetList.addAll(list)
                currentChats.remove(key)
                didMerge = true
            }
        }
        if (didMerge) {
            // Preserve arrival order; do not sort by timestamp
            currentChats[targetPeerID] = targetList
            state.setPrivateChats(currentChats)

            // Move unread flags
            val unread = state.getUnreadPrivateMessagesValue().toMutableSet()
            var hadUnread = false
            keysToMerge.forEach { key -> if (unread.remove(key)) hadUnread = true }
            if (hadUnread) unread.add(targetPeerID)
            state.setUnreadPrivateMessages(unread)

            // Switch selection if currently viewing an alias that got merged
            val selected = state.getSelectedPrivateChatPeerValue()
            if (selected != null && keysToMerge.contains(selected)) {
                state.setSelectedPrivateChatPeer(targetPeerID)
            }
        }
    }
}

