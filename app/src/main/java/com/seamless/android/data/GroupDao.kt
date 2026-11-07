@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Private Groups and Forums
 */
@Dao
interface GroupDao {
    // ── Private Groups ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: PrivateGroupEntity)

    @Query("SELECT * FROM private_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<PrivateGroupEntity>>

    @Query("SELECT * FROM private_groups WHERE groupId = :groupId")
    suspend fun getGroup(groupId: String): PrivateGroupEntity?

    @Delete
    suspend fun deleteGroup(group: PrivateGroupEntity)

    // ── Group Members ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun getMembersForGroup(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembersForGroupNow(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND memberOnionAddress = :onionAddress LIMIT 1")
    suspend fun getMember(groupId: String, onionAddress: String): GroupMemberEntity?

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND memberOnionAddress = :onionAddress")
    suspend fun removeMember(groupId: String, onionAddress: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: String)

    @Query("DELETE FROM private_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("UPDATE private_groups SET name = :name WHERE groupId = :groupId")
    suspend fun renameGroup(groupId: String, name: String)

    // ── Group Messages ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(message: GroupMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupMessageIfAbsent(message: GroupMessageEntity): Long

    @Query("SELECT * FROM group_messages WHERE id = :id LIMIT 1")
    suspend fun getGroupMessage(id: String): GroupMessageEntity?

    @Query("UPDATE group_messages SET deliveryState = :state WHERE id = :id")
    suspend fun updateGroupMessageState(id: String, state: String)

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun deleteMessagesForGroup(groupId: String)

    @Query("SELECT COUNT(*) FROM group_messages WHERE groupId = :groupId AND isFromMe = 0 AND timestamp > :since")
    fun getUnreadCount(groupId: String, since: Long): Flow<Int>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(groupId: String): Flow<GroupMessageEntity?>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getGroupMessages(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("""
        SELECT * FROM group_messages 
        WHERE id IN (
            SELECT id FROM group_messages 
            GROUP BY groupId 
            HAVING timestamp = MAX(timestamp) OR id = (SELECT g2.id FROM group_messages g2 WHERE g2.groupId = group_messages.groupId ORDER BY g2.timestamp DESC LIMIT 1)
        )
        ORDER BY timestamp DESC
    """)
    fun getRecentGroupConversations(): Flow<List<GroupMessageEntity>>

    // ── Forums ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForum(forum: ForumEntity)

    @Query("SELECT * FROM forums ORDER BY createdAt DESC")
    fun getAllForums(): Flow<List<ForumEntity>>

    @Query("SELECT * FROM forums WHERE forumId = :forumId")
    suspend fun getForum(forumId: String): ForumEntity?

    @Delete
    suspend fun deleteForum(forum: ForumEntity)

    // ── Forum Messages ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForumMessage(message: ForumMessageEntity)

    @Query("SELECT * FROM forum_messages WHERE forumId = :forumId ORDER BY timestamp ASC")
    fun getForumMessages(forumId: String): Flow<List<ForumMessageEntity>>

    @Query("SELECT * FROM forum_messages WHERE forumId = :forumId AND parentMessageId IS NULL ORDER BY timestamp DESC")
    fun getTopLevelForumMessages(forumId: String): Flow<List<ForumMessageEntity>>

    @Query("SELECT * FROM forum_messages WHERE parentMessageId = :parentId ORDER BY timestamp ASC")
    fun getReplies(parentId: String): Flow<List<ForumMessageEntity>>
}
