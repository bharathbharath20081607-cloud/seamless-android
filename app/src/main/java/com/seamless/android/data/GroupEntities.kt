@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for Private Groups (Briar-style).
 * Only the creator can invite new members.
 */
@Entity(
    tableName = "private_groups",
    indices = [Index(value = ["createdAt"])]
)
data class PrivateGroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val creatorOnionAddress: String,
    val creatorNickname: String,
    val isCreatedByMe: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Group membership mapping.
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "memberOnionAddress"],
    indices = [Index(value = ["groupId"])]
)
data class GroupMemberEntity(
    val groupId: String,
    val memberOnionAddress: String,
    val memberNickname: String,
    val joinedAt: Long = System.currentTimeMillis()
)

/**
 * Messages within private groups.
 */
@Entity(
    tableName = "group_messages",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["timestamp"])
    ]
)
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val senderOnionAddress: String,
    val senderNickname: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val deliveryState: String = "SENT",
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null
)

/**
 * Database entity for Forums (Briar-style).
 * Any subscriber can invite their own contacts.
 */
@Entity(
    tableName = "forums",
    indices = [Index(value = ["createdAt"])]
)
data class ForumEntity(
    @PrimaryKey val forumId: String,
    val name: String,
    val creatorOnionAddress: String,
    val creatorNickname: String,
    val isCreatedByMe: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Forum messages with threading (parentMessageId for replies).
 */
@Entity(
    tableName = "forum_messages",
    indices = [
        Index(value = ["forumId"]),
        Index(value = ["parentMessageId"]),
        Index(value = ["timestamp"])
    ]
)
data class ForumMessageEntity(
    @PrimaryKey val id: String,
    val forumId: String,
    val parentMessageId: String? = null,   // null = top-level post
    val senderOnionAddress: String,
    val senderNickname: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean
)
