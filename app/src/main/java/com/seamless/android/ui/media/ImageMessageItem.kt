@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.seamless.android.mesh.BluetoothMeshService
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.BitchatMessageType
import androidx.compose.material3.ColorScheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMessageItem(
    message: BitchatMessage,
    messages: List<BitchatMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    onNicknameClick: ((String) -> Unit)?,
    onMessageLongPress: ((BitchatMessage) -> Unit)?,
    onCancelTransfer: ((BitchatMessage) -> Unit)?,
    onImageClick: ((String, List<String>, Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val path = message.content.trim()
    val haptic = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        val bmp = remember(path) { try { android.graphics.BitmapFactory.decodeFile(path) } catch (_: Exception) { null } }

        // Collect all image paths from messages for swipe navigation
        val imagePaths = remember(messages) {
            messages.filter { it.type == BitchatMessageType.Image }
                .map { it.content.trim() }
        }

        if (bmp != null) {
            val img = bmp.asImageBitmap()
            val aspect = (bmp.width.toFloat() / bmp.height.toFloat()).takeIf { it.isFinite() && it > 0 } ?: 1f
            val progressFraction: Float? = when (val st = message.deliveryStatus) {
                is com.seamless.android.model.DeliveryStatus.PartiallyDelivered -> if (st.total > 0) st.reached.toFloat() / st.total.toFloat() else 0f
                else -> null
            }
            val imageShape = RoundedCornerShape(14.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (progressFraction != null && progressFraction < 1f && message.sender == currentUserNickname) {
                        // Cyberpunk block-reveal while sending
                        BlockRevealImage(
                            bitmap = img,
                            progress = progressFraction,
                            blocksX = 24,
                            blocksY = 16,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .aspectRatio(aspect)
                                .clip(imageShape)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), imageShape)
                                .combinedClickable(
                                    onClick = {
                                        val currentIndex = imagePaths.indexOf(path).coerceAtLeast(0)
                                        onImageClick?.invoke(path, imagePaths, currentIndex)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMessageLongPress?.invoke(message)
                                    }
                                )
                        )
                    } else {
                        // Fully revealed image
                        Image(
                            bitmap = img,
                            contentDescription = stringResource(com.seamless.android.R.string.cd_image),
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .aspectRatio(aspect)
                                .clip(imageShape)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), imageShape)
                                .combinedClickable(
                                    onClick = {
                                        val currentIndex = imagePaths.indexOf(path).coerceAtLeast(0)
                                        onImageClick?.invoke(path, imagePaths, currentIndex)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMessageLongPress?.invoke(message)
                                    }
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Cancel button overlay during sending
                    val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is com.seamless.android.model.DeliveryStatus.PartiallyDelivered)
                    if (showCancel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { onCancelTransfer?.invoke(message) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(com.seamless.android.R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        } else {
            Text(text = stringResource(com.seamless.android.R.string.image_unavailable), fontFamily = FontFamily.Default, color = Color.Gray)
        }
    }
}
