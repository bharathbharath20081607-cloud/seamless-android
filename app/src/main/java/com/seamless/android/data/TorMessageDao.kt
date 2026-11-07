@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Tor P2P messages
 * 
 * Provides database operations for chat persistence
 */
@Dao
interface TorMessageDao {
    
    /**
     * Insert a new message
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: TorMessageEntity)
    
    /**
     * Insert multiple messages
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<TorMessageEntity>)
    
    /**
     * Get all messages for a contact
     */
    @Query("SELECT * FROM tor_messages WHERE contactOnionAddress = :onionAddress ORDER BY timestamp ASC")
    fun getMessagesForContact(onionAddress: String): Flow<List<TorMessageEntity>>
    
    /**
     * Get all messages for a contact (one-shot)
     */
    @Query("SELECT * FROM tor_messages WHERE contactOnionAddress = :onionAddress ORDER BY timestamp ASC")
    suspend fun getMessagesForContactOnce(onionAddress: String): List<TorMessageEntity>

    @Query("""
        SELECT * FROM tor_messages
        WHERE contactId = :contactId OR contactOnionAddress IN (:addressAliases)
        ORDER BY timestamp ASC
    """)
    fun getMessagesForContactIdentity(
        contactId: String,
        addressAliases: List<String>
    ): Flow<List<TorMessageEntity>>
    
    /**
     * Get recent conversations (last message per contact)
     */
    @Query("""
        SELECT * FROM tor_messages 
        WHERE id IN (
            SELECT id FROM tor_messages 
            GROUP BY contactOnionAddress 
            HAVING timestamp = MAX(timestamp) OR id = (SELECT m2.id FROM tor_messages m2 WHERE m2.contactOnionAddress = tor_messages.contactOnionAddress ORDER BY m2.timestamp DESC LIMIT 1)
        )
        ORDER BY timestamp DESC
    """)
    fun getRecentConversations(): Flow<List<TorMessageEntity>>
    
    /**
     * Get unread message count for a contact
     */
    @Query("SELECT COUNT(*) FROM tor_messages WHERE contactOnionAddress = :onionAddress AND isFromMe = 0 AND readAt IS NULL")
    suspend fun getUnreadCount(onionAddress: String): Int
    
    /**
     * Get total unread message count
     */
    @Query("SELECT COUNT(*) FROM tor_messages WHERE isFromMe = 0 AND readAt IS NULL")
    fun getTotalUnreadCount(): Flow<Int>
    
    /**
     * Mark messages as read
     */
    @Query("UPDATE tor_messages SET readAt = :readAt WHERE contactOnionAddress = :onionAddress AND isFromMe = 0 AND readAt IS NULL")
    suspend fun markAsRead(onionAddress: String, readAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE tor_messages SET readAt = :readAt
        WHERE (contactId = :contactId OR contactOnionAddress IN (:addressAliases))
          AND isFromMe = 0 AND readAt IS NULL
    """)
    suspend fun markAsReadIdentity(
        contactId: String,
        addressAliases: List<String>,
        readAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Update delivery state
     */
    @Query("UPDATE tor_messages SET deliveryState = :deliveryState WHERE id = :messageId")
    suspend fun updateDeliveryState(messageId: String, deliveryState: String)

    @Query("UPDATE tor_messages SET deliveryRoute = :deliveryRoute WHERE id = :messageId")
    suspend fun updateDeliveryRoute(messageId: String, deliveryRoute: String)

    @Query("UPDATE tor_messages SET deliveryState = :deliveryState, deliveryRoute = :deliveryRoute WHERE id = :messageId")
    suspend fun updateDelivery(messageId: String, deliveryState: String, deliveryRoute: String)
    
    /**
     * Get a specific message
     */
    @Query("SELECT * FROM tor_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): TorMessageEntity?
    
    /**
     * Delete all messages for a contact
     */
    @Query("DELETE FROM tor_messages WHERE contactOnionAddress = :onionAddress")
    suspend fun deleteMessagesForContact(onionAddress: String)
    
    /**
     * Delete old messages (older than specified timestamp)
     */
    @Query("DELETE FROM tor_messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)
    
    /**
     * Get message count
     */
    @Query("SELECT COUNT(*) FROM tor_messages")
    suspend fun getMessageCount(): Int

    // ===== Bramble Sync Protocol (BSP) queries =====

    /**
     * BSP: Get messages newer than a timestamp for a contact.
     * Used during the OFFER phase to find messages the peer hasn't seen.
     */
    @Query("SELECT * FROM tor_messages WHERE contactOnionAddress = :contactOnionAddress AND timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesAfterTimestamp(contactOnionAddress: String, afterTimestamp: Long): List<TorMessageEntity>

    /**
     * BSP: Get message IDs newer than a timestamp for a contact.
     * Lightweight version for offer generation — avoids loading full message payloads.
     */
    @Query("SELECT id FROM tor_messages WHERE contactOnionAddress = :contactOnionAddress AND timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getMessageIdsAfterTimestamp(contactOnionAddress: String, afterTimestamp: Long): List<String>

    /**
     * BSP: Get messages by their IDs for batch retrieval.
     * Used during the BATCH phase to package requested messages.
     */
    @Query("SELECT * FROM tor_messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<String>): List<TorMessageEntity>
}
