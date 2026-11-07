@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Database for persisting Bramble Sync Protocol state.
 *
 * Tracks:
 * - Per-message sync state with each peer (offered/requested/sent/acked)
 * - Per-peer sync session state
 * - Sync history for debugging and metrics
 *
 * This ensures that sync state survives app restarts and crash recovery.
 */
class SyncDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "SyncDatabase"
        private const val DATABASE_NAME = "seamless_sync.db"
        private const val DATABASE_VERSION = 1

        // Tables
        private const val TABLE_SYNC_STATE = "sync_state"
        private const val TABLE_SYNC_SESSIONS = "sync_sessions"
        private const val TABLE_SYNC_HISTORY = "sync_history"

        // sync_state columns
        private const val COL_PEER_ID = "peer_id"
        private const val COL_MESSAGE_ID = "message_id"
        private const val COL_STATE = "state"
        private const val COL_UPDATED_AT = "updated_at"

        // sync_sessions columns
        private const val COL_SYNC_STATE = "sync_state"
        private const val COL_LAST_SYNC_AT = "last_sync_at"
        private const val COL_OFFER_COUNT = "offer_count"
        private const val COL_ACK_COUNT = "ack_count"

        // sync_history columns
        private const val COL_DIRECTION = "direction"
        private const val COL_FRAME_TYPE = "frame_type"
        private const val COL_MESSAGE_COUNT = "message_count"
        private const val COL_TIMESTAMP = "timestamp"

        @Volatile
        private var instance: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase {
            return instance ?: synchronized(this) {
                instance ?: SyncDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.i(TAG, "Creating sync database schema v$DATABASE_VERSION")

        // Track which messages each peer has acknowledged
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_STATE (
                $COL_PEER_ID TEXT NOT NULL,
                $COL_MESSAGE_ID TEXT NOT NULL,
                $COL_STATE TEXT NOT NULL DEFAULT 'pending',
                $COL_UPDATED_AT TEXT NOT NULL DEFAULT (datetime('now')),
                PRIMARY KEY ($COL_PEER_ID, $COL_MESSAGE_ID)
            )
        """)

        // Track per-peer sync session state
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_SESSIONS (
                $COL_PEER_ID TEXT PRIMARY KEY,
                $COL_SYNC_STATE TEXT NOT NULL DEFAULT 'idle',
                $COL_LAST_SYNC_AT TEXT,
                $COL_OFFER_COUNT INTEGER DEFAULT 0,
                $COL_ACK_COUNT INTEGER DEFAULT 0
            )
        """)

        // Sync history for debugging and metrics
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PEER_ID TEXT NOT NULL,
                $COL_DIRECTION TEXT NOT NULL,
                $COL_FRAME_TYPE TEXT NOT NULL,
                $COL_MESSAGE_COUNT INTEGER DEFAULT 0,
                $COL_TIMESTAMP TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """)

        // Indexes for fast lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_state_peer ON $TABLE_SYNC_STATE($COL_PEER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_state_message ON $TABLE_SYNC_STATE($COL_MESSAGE_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_history_peer ON $TABLE_SYNC_HISTORY($COL_PEER_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Upgrading sync database from v$oldVersion to v$newVersion")
        // Future migrations go here
    }

    // ===== Sync State Operations =====

    /**
     * Set the sync state of a message for a specific peer.
     * States: 'pending', 'offered', 'requested', 'sent', 'acked'
     */
    fun setMessageSyncState(peerId: String, messageId: String, state: String) {
        val db = writableDatabase
        db.execSQL("""
            INSERT OR REPLACE INTO $TABLE_SYNC_STATE ($COL_PEER_ID, $COL_MESSAGE_ID, $COL_STATE, $COL_UPDATED_AT)
            VALUES (?, ?, ?, datetime('now'))
        """, arrayOf(peerId, messageId, state))
    }

    /**
     * Get the sync state of a message for a specific peer.
     */
    fun getMessageSyncState(peerId: String, messageId: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_STATE FROM $TABLE_SYNC_STATE WHERE $COL_PEER_ID = ? AND $COL_MESSAGE_ID = ?",
            arrayOf(peerId, messageId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    /**
     * Get all message IDs that a peer has acknowledged.
     */
    fun getAckedMessageIds(peerId: String): Set<String> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_MESSAGE_ID FROM $TABLE_SYNC_STATE WHERE $COL_PEER_ID = ? AND $COL_STATE = 'acked'",
            arrayOf(peerId)
        )
        val ids = mutableSetOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                ids.add(it.getString(0))
            }
        }
        return ids
    }

    /**
     * Get messages that haven't been synced to a specific peer.
     */
    fun getUnsyncedMessageIds(peerId: String, allMessageIds: Set<String>): Set<String> {
        val acked = getAckedMessageIds(peerId)
        return allMessageIds - acked
    }

    /**
     * Bulk mark messages as acked for a peer.
     */
    fun markMessagesAcked(peerId: String, messageIds: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (messageId in messageIds) {
                db.execSQL("""
                    INSERT OR REPLACE INTO $TABLE_SYNC_STATE ($COL_PEER_ID, $COL_MESSAGE_ID, $COL_STATE, $COL_UPDATED_AT)
                    VALUES (?, ?, 'acked', datetime('now'))
                """, arrayOf(peerId, messageId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ===== Sync Session Operations =====

    /**
     * Update the sync session state for a peer.
     */
    fun updateSessionState(peerId: String, syncState: String) {
        val db = writableDatabase
        db.execSQL("""
            INSERT OR REPLACE INTO $TABLE_SYNC_SESSIONS ($COL_PEER_ID, $COL_SYNC_STATE, $COL_LAST_SYNC_AT)
            VALUES (?, ?, datetime('now'))
        """, arrayOf(peerId, syncState))
    }

    /**
     * Get the sync session state for a peer.
     */
    fun getSessionState(peerId: String): String {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SYNC_STATE FROM $TABLE_SYNC_SESSIONS WHERE $COL_PEER_ID = ?",
            arrayOf(peerId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else "idle"
        }
    }

    /**
     * Increment offer count for a peer.
     */
    fun incrementOfferCount(peerId: String) {
        val db = writableDatabase
        db.execSQL("""
            UPDATE $TABLE_SYNC_SESSIONS SET $COL_OFFER_COUNT = $COL_OFFER_COUNT + 1
            WHERE $COL_PEER_ID = ?
        """, arrayOf(peerId))
    }

    /**
     * Increment ack count for a peer.
     */
    fun incrementAckCount(peerId: String) {
        val db = writableDatabase
        db.execSQL("""
            UPDATE $TABLE_SYNC_SESSIONS SET $COL_ACK_COUNT = $COL_ACK_COUNT + 1
            WHERE $COL_PEER_ID = ?
        """, arrayOf(peerId))
    }

    // ===== Sync History =====

    /**
     * Record a sync event for debugging and metrics.
     */
    fun recordSyncEvent(peerId: String, direction: String, frameType: String, messageCount: Int) {
        val db = writableDatabase
        db.execSQL("""
            INSERT INTO $TABLE_SYNC_HISTORY ($COL_PEER_ID, $COL_DIRECTION, $COL_FRAME_TYPE, $COL_MESSAGE_COUNT, $COL_TIMESTAMP)
            VALUES (?, ?, ?, ?, datetime('now'))
        """, arrayOf(peerId, direction, frameType, messageCount.toString()))
    }

    /**
     * Get sync history for a peer (last N events).
     */
    fun getSyncHistory(peerId: String, limit: Int = 50): List<SyncEvent> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT $COL_PEER_ID, $COL_DIRECTION, $COL_FRAME_TYPE, $COL_MESSAGE_COUNT, $COL_TIMESTAMP
            FROM $TABLE_SYNC_HISTORY
            WHERE $COL_PEER_ID = ?
            ORDER BY $COL_TIMESTAMP DESC
            LIMIT ?
        """, arrayOf(peerId, limit.toString()))

        val events = mutableListOf<SyncEvent>()
        cursor.use {
            while (it.moveToNext()) {
                events.add(SyncEvent(
                    peerId = it.getString(0),
                    direction = it.getString(1),
                    frameType = it.getString(2),
                    messageCount = it.getInt(3),
                    timestamp = it.getString(4)
                ))
            }
        }
        return events
    }

    data class SyncEvent(
        val peerId: String,
        val direction: String,  // "inbound" or "outbound"
        val frameType: String,  // "offer", "request", "batch", "ack"
        val messageCount: Int,
        val timestamp: String
    )

    // ===== Cleanup =====

    /**
     * Clean up sync state for disconnected peers.
     * Resets session state to 'idle' but preserves acked message IDs.
     */
    fun cleanupDisconnectedPeers(connectedPeerIds: Set<String>) {
        val db = writableDatabase
        // SECURITY: parameterized query (no string interpolation); also safe for an empty set
        if (connectedPeerIds.isEmpty()) {
            db.execSQL("UPDATE $TABLE_SYNC_SESSIONS SET $COL_SYNC_STATE = 'idle'")
        } else {
            val placeholders = connectedPeerIds.joinToString(",") { "?" }
            db.execSQL(
                "UPDATE $TABLE_SYNC_SESSIONS SET $COL_SYNC_STATE = 'idle' WHERE $COL_PEER_ID NOT IN ($placeholders)",
                connectedPeerIds.toTypedArray()
            )
        }
    }

    /**
     * Get total sync statistics
     */
    fun getGlobalStats(): GlobalSyncStats {
        val db = readableDatabase

        val totalAcked = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SYNC_STATE WHERE $COL_STATE = 'acked'", null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        val totalPeers = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SYNC_SESSIONS", null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        val totalEvents = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SYNC_HISTORY", null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        return GlobalSyncStats(totalAcked, totalPeers, totalEvents)
    }

    data class GlobalSyncStats(
        val totalAckedMessages: Int,
        val totalPeers: Int,
        val totalSyncEvents: Int
    )
}
