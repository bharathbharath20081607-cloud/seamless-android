@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.seamless.android.features.file.FileUtils
import java.io.File

/**
 * Business logic for file sharing operations
 */
object FileSharingManager {

    private const val TAG = "FileSharingManager"

    /**
     * Create a file packet from URI for sending
     */
    fun createFilePacketFromUri(
        context: Context,
        uri: Uri,
        originalName: String? = null
    ): BitchatFilePacket? {
        return try {
            // Get file name from URI or use original name
            val fileName = originalName ?: getFileNameFromUri(context, uri) ?: "unknown_file"

            // Copy file to our temp storage for sending
            val localPath = FileUtils.copyFileForSending(context, uri) ?: return null

            // Determine MIME type
            val mimeType = FileUtils.getMimeTypeFromExtension(fileName)

            // Read file content
            val file = File(localPath)
            val content = file.readBytes()
            val fileSize = file.length()

            // Clean up temp file
            file.delete()

            val packet = BitchatFilePacket(
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                content = content
            )

            Log.d(TAG, "Created file packet: name=$fileName, size=${FileUtils.formatFileSize(fileSize)}, mime=$mimeType")
            packet

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file packet from URI", e)
            null
        }
    }

    /**
     * Extract filename from URI
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get filename from URI", e)
            uri.lastPathSegment
        }
    }

    /**
     * Process a received file packet and return file info
     */
    data class ReceivedFileInfo(
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val content: ByteArray
    )

    fun processReceivedFile(packet: BitchatFilePacket): ReceivedFileInfo {
        return ReceivedFileInfo(
            fileName = packet.fileName,
            fileSize = packet.fileSize,
            mimeType = packet.mimeType,
            content = packet.content
        )
    }
}
