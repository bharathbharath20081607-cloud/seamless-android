@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.nostr

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import com.seamless.android.ui.ChatState
import com.seamless.android.ui.GeoPerson
import java.util.Date

/**
 * GeohashRepository
 * - Owns geohash participant tracking and nickname caching
 * - Maintains lightweight state for geohash-related UI
 */
class GeohashRepository(
    private val application: Application,
    private val state: ChatState,
    private val dataManager: com.seamless.android.ui.DataManager
) {
    companion object { private const val TAG = "GeohashRepository" }

    // geohash -> (participant pubkeyHex -> lastSeen)
    private val geohashParticipants: MutableMap<String, MutableMap<String, Date>> = mutableMapOf()


    // pubkeyHex(lowercase) -> nickname (without #hash)
    private val geoNicknames: MutableMap<String, String> = mutableMapOf()

    // conversation key (e.g., "nostr_<pub16>") -> source geohash it belongs to
    private val conversationGeohash: MutableMap<String, String> = mutableMapOf()

    fun setConversationGeohash(convKey: String, geohash: String) {
        if (geohash.isNotEmpty()) {
            conversationGeohash[convKey] = geohash
        }
    }

    fun getConversationGeohash(convKey: String): String? = conversationGeohash[convKey]

    fun findPubkeyByNickname(targetNickname: String): String? {
        return geoNicknames.entries.firstOrNull { (_, nickname) ->
            val base = nickname.split("#").firstOrNull() ?: nickname
            base == targetNickname
        }?.key
    }

    // peerID alias -> nostr pubkey mapping for geohash DMs and temp aliases
    private val nostrKeyMapping: MutableMap<String, String> = mutableMapOf()

    // Current geohash in view
    private var currentGeohash: String? = null

    fun setCurrentGeohash(geo: String?) { currentGeohash = geo }
    fun getCurrentGeohash(): String? = currentGeohash

    fun clearAll() {
        geohashParticipants.clear()
        geoNicknames.clear()
        nostrKeyMapping.clear()
        state.setGeohashPeople(emptyList())
        state.setTeleportedGeo(emptySet())
        state.setGeohashParticipantCounts(emptyMap())
        currentGeohash = null
    }

    fun cacheNickname(pubkeyHex: String, nickname: String) {
        val lower = pubkeyHex.lowercase()
        val previous = geoNicknames[lower]
        geoNicknames[lower] = nickname
        if (previous != nickname && currentGeohash != null) {
            refreshGeohashPeople()
        }
    }

    fun getCachedNickname(pubkeyHex: String): String? = geoNicknames[pubkeyHex.lowercase()]

    fun markTeleported(pubkeyHex: String) {
        val set = state.getTeleportedGeoValue().toMutableSet()
        val key = pubkeyHex.lowercase()
        if (!set.contains(key)) {
            set.add(key)
            // Background safe update
            state.postTeleportedGeo(set)
        }
    }

    fun isPersonTeleported(pubkeyHex: String): Boolean {
        return state.getTeleportedGeoValue().contains(pubkeyHex.lowercase())
    }

    fun updateParticipant(geohash: String, participantId: String, lastSeen: Date) {
        val participants = geohashParticipants.getOrPut(geohash) { mutableMapOf() }
        participants[participantId] = lastSeen
        if (currentGeohash == geohash) refreshGeohashPeople()
        updateReactiveParticipantCounts()
    }

    fun geohashParticipantCount(geohash: String): Int {
        val cutoff = Date(System.currentTimeMillis() - 5 * 60 * 1000)
        val participants = geohashParticipants[geohash] ?: return 0
        // prune expired
        val it = participants.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value.before(cutoff)) it.remove()
        }
        // exclude blocked users
        return participants.keys.count { !dataManager.isGeohashUserBlocked(it) }
    }

    fun refreshGeohashPeople() {
        val geohash = currentGeohash
        if (geohash == null) {
            // Use postValue for thread safety - this can be called from background threads
            state.postGeohashPeople(emptyList())
            return
        }
        val cutoff = Date(System.currentTimeMillis() - 5 * 60 * 1000)
        val participants = geohashParticipants[geohash] ?: mutableMapOf()
        // prune expired
        val it = participants.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value.before(cutoff)) it.remove()
        }
        geohashParticipants[geohash] = participants
        // exclude blocked users from people list
        val people = participants.filterKeys { !dataManager.isGeohashUserBlocked(it) }
            .map { (pubkeyHex, lastSeen) ->
            // Use our actual nickname for self; otherwise use cached nickname or anon
            val base = try {
                val myHex = currentGeohash?.let { NostrIdentityBridge.deriveIdentity(it, application).publicKeyHex }
                if (myHex != null && myHex.equals(pubkeyHex, true)) {
                    state.getNicknameValue() ?: "anon"
                } else {
                    getCachedNickname(pubkeyHex) ?: "anon"
                }
            } catch (_: Exception) { getCachedNickname(pubkeyHex) ?: "anon" }
            GeoPerson(
                id = pubkeyHex.lowercase(),
                displayName = base, // UI can add #hash if necessary
                lastSeen = lastSeen
            )
        }.sortedByDescending { it.lastSeen }
        // Use postValue for thread safety - this can be called from background threads
        state.postGeohashPeople(people)
    }

    fun updateReactiveParticipantCounts() {
        val cutoff = Date(System.currentTimeMillis() - 5 * 60 * 1000)
        val counts = mutableMapOf<String, Int>()
        for ((gh, participants) in geohashParticipants) {
            val active = participants.filterKeys { !dataManager.isGeohashUserBlocked(it) }
                .values.count { !it.before(cutoff) }
            counts[gh] = active
        }
        // Use postValue for thread safety - this can be called from background threads  
        state.postGeohashParticipantCounts(counts)
    }

    fun putNostrKeyMapping(tempKeyOrPeer: String, pubkeyHex: String) {
        nostrKeyMapping[tempKeyOrPeer] = pubkeyHex
    }

    fun getNostrKeyMapping(): Map<String, String> = nostrKeyMapping.toMap()

    fun displayNameForNostrPubkey(pubkeyHex: String): String {
        val suffix = pubkeyHex.takeLast(4)
        val lower = pubkeyHex.lowercase()
        // Self nickname if matches current identity of current geohash
        val current = currentGeohash
        if (current != null) {
            try {
                val my = NostrIdentityBridge.deriveIdentity(current, application)
                if (my.publicKeyHex.equals(lower, true)) {
                    return "${state.getNicknameValue()}#$suffix"
                }
            } catch (_: Exception) {}
        }
        val nick = geoNicknames[lower] ?: "anon"
        return "$nick#$suffix"
    }

    fun displayNameForNostrPubkeyUI(pubkeyHex: String): String {
        val lower = pubkeyHex.lowercase()
        val suffix = pubkeyHex.takeLast(4)
        val current = currentGeohash
        val base: String = try {
            if (current != null) {
                val my = NostrIdentityBridge.deriveIdentity(current, application)
                if (my.publicKeyHex.equals(lower, true)) {
                    state.getNicknameValue() ?: "anon"
                } else geoNicknames[lower] ?: "anon"
            } else geoNicknames[lower] ?: "anon"
        } catch (_: Exception) { geoNicknames[lower] ?: "anon" }
        if (current == null) return base
        return try {
            val cutoff = Date(System.currentTimeMillis() - 5 * 60 * 1000)
            val participants = geohashParticipants[current] ?: emptyMap()
            var count = 0
            for ((k, t) in participants) {
                if (dataManager.isGeohashUserBlocked(k)) continue
                if (t.before(cutoff)) continue
                val name = if (k.equals(lower, true)) base else (geoNicknames[k.lowercase()] ?: "anon")
                if (name.equals(base, true)) { count++; if (count > 1) break }
            }
            if (!participants.containsKey(lower)) count += 1
            if (count > 1) "$base#$suffix" else base
        } catch (_: Exception) { base }
    }

    /**
     * Get display name for any geohash (not just current one) for header titles
     */
    fun displayNameForGeohashConversation(pubkeyHex: String, sourceGeohash: String): String {
        val lower = pubkeyHex.lowercase()
        val suffix = pubkeyHex.takeLast(4)
        val base = geoNicknames[lower] ?: "anon"
        return try {
            val cutoff = Date(System.currentTimeMillis() - 5 * 60 * 1000)
            val participants = geohashParticipants[sourceGeohash] ?: emptyMap()
            var count = 0
            for ((k, t) in participants) {
                if (dataManager.isGeohashUserBlocked(k)) continue
                if (t.before(cutoff)) continue
                val name = if (k.equals(lower, true)) base else (geoNicknames[k.lowercase()] ?: "anon")
                if (name.equals(base, true)) { count++; if (count > 1) break }
            }
            if (!participants.containsKey(lower)) count += 1
            if (count > 1) "$base#$suffix" else base
        } catch (_: Exception) { base }
    }
}
