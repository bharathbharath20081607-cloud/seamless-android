@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.Locale

/**
 * Versioned rich chat wire codec and cryptographic multi-pass disk shredder
 * shared across 1-to-1 Tor P2P chats and Chrono-DAG Private Groups with zero
 * Room SQLCipher schema migrations.
 */
object TorRichChatCodec {

    const val DELETED_DIRECT_MESSAGE_TEXT = "This message was deleted"
    const val BURNED_CAPSULE_TEXT = "🔥 Burned"

    private const val REPLY_PREFIX = "⟦reply:"
    private const val REPLY_SUFFIX = "⟧\n"
    private const val BURN_TEXT_PREFIX = "⟦sburn:v1:"
    private const val ATT_DESC_PREFIX = "⟦satt:v1:"
    private const val CTRL_PREFIX = "⟦sctrl:v1:"
    private const val MARKER_SUFFIX = "⟧"

    data class ParsedReplyEnvelope(
        val body: String,
        val replyToId: String? = null,
        val replyToSender: String? = null,
        val replyToPreview: String? = null
    )

    data class BurnTextPayload(
        val secretText: String,
        val ttlSeconds: Int = 12
    )

    data class AttachmentDescriptor(
        val kind: String,
        val fileName: String = "",
        val totalBytes: Int = 0,
        val durationMs: Int = 0,
        val viewOnce: Boolean = false
    )

    data class DirectControlEvent(
        val action: String,
        val targetMessageId: String,
        val text: String = "",
        val emoji: String = "",
        val reactionAction: String = "add",
        val timestamp: Long = System.currentTimeMillis()
    )

    fun formatWithReply(
        body: String,
        replyToId: String?,
        replyToSender: String?,
        replyToPreview: String?
    ): String {
        if (replyToId.isNullOrBlank() || replyToSender.isNullOrBlank()) return body
        val cleanSender = replyToSender.replace("|", " ").replace("\n", " ").take(40)
        val cleanPreview = (replyToPreview ?: "").replace("|", " ").replace("\n", " ").take(90)
        return "$REPLY_PREFIX$replyToId|$cleanSender|$cleanPreview$REPLY_SUFFIX$body"
    }

    fun parseReply(rawText: String): ParsedReplyEnvelope {
        if (!rawText.startsWith(REPLY_PREFIX)) {
            return ParsedReplyEnvelope(body = rawText)
        }
        val endIdx = rawText.indexOf(REPLY_SUFFIX)
        if (endIdx <= REPLY_PREFIX.length) {
            return ParsedReplyEnvelope(body = rawText)
        }
        val header = rawText.substring(REPLY_PREFIX.length, endIdx)
        val parts = header.split("|", limit = 3)
        val body = rawText.substring(endIdx + REPLY_SUFFIX.length)
        return if (parts.size >= 2) {
            ParsedReplyEnvelope(
                body = body,
                replyToId = parts[0].takeIf { it.isNotBlank() },
                replyToSender = parts[1].takeIf { it.isNotBlank() },
                replyToPreview = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
            )
        } else {
            ParsedReplyEnvelope(body = rawText)
        }
    }

    fun encodeBurnText(secretText: String, ttlSeconds: Int = 12): String {
        val clampedTtl = ttlSeconds.coerceIn(5, 60)
        val encoded = Base64.encodeToString(secretText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$BURN_TEXT_PREFIX$clampedTtl:$encoded$MARKER_SUFFIX"
    }

    fun parseBurnText(rawOrCleanText: String): BurnTextPayload? {
        val body = parseReply(rawOrCleanText).body.trim()
        if (!body.startsWith(BURN_TEXT_PREFIX) || !body.endsWith(MARKER_SUFFIX)) return null
        val inner = body.substring(BURN_TEXT_PREFIX.length, body.length - MARKER_SUFFIX.length)
        val sep = inner.indexOf(':')
        if (sep <= 0 || sep >= inner.length - 1) return null
        val ttl = inner.substring(0, sep).toIntOrNull()?.coerceIn(5, 60) ?: return null
        val b64 = inner.substring(sep + 1)
        val decoded = runCatching {
            String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return BurnTextPayload(secretText = decoded, ttlSeconds = ttl)
    }

    fun isUnburnedSecretText(rawText: String): Boolean = parseBurnText(rawText) != null

    fun encodeAttachmentDescriptor(desc: AttachmentDescriptor): String {
        val json = JSONObject().apply {
            put("k", desc.kind)
            if (desc.fileName.isNotBlank()) put("n", desc.fileName)
            if (desc.totalBytes > 0) put("s", desc.totalBytes)
            if (desc.durationMs > 0) put("d", desc.durationMs)
            if (desc.viewOnce) put("v", true)
        }
        val b64 = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$ATT_DESC_PREFIX$b64$MARKER_SUFFIX"
    }

    fun parseAttachmentDescriptor(rawOrCleanText: String): AttachmentDescriptor? {
        val body = parseReply(rawOrCleanText).body.trim()
        if (!body.startsWith(ATT_DESC_PREFIX) || !body.endsWith(MARKER_SUFFIX)) return null
        val b64 = body.substring(ATT_DESC_PREFIX.length, body.length - MARKER_SUFFIX.length)
        return runCatching {
            val jsonStr = String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
            val json = JSONObject(jsonStr)
            val kind = json.optString("k", "").takeIf { it in setOf("photo", "voice", "file") }
                ?: return null
            AttachmentDescriptor(
                kind = kind,
                fileName = json.optString("n", ""),
                totalBytes = json.optInt("s", 0).coerceAtLeast(0),
                durationMs = json.optInt("d", 0).coerceAtLeast(0),
                viewOnce = json.optBoolean("v", false)
            )
        }.getOrNull()
    }

    fun inferDescriptor(
        rawText: String,
        mimeType: String?,
        filePath: String?
    ): AttachmentDescriptor? {
        parseAttachmentDescriptor(rawText)?.let { return it }
        val mime = mimeType?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: return null
        val file = filePath?.takeIf { it.isNotBlank() }?.let(::File)
        val kind = when {
            mime.startsWith("audio/") -> "voice"
            mime.startsWith("image/") -> "photo"
            else -> "file"
        }
        return AttachmentDescriptor(
            kind = kind,
            fileName = file?.name.orEmpty(),
            totalBytes = file?.takeIf { it.exists() }?.length()?.toInt() ?: 0,
            durationMs = 0,
            viewOnce = false
        )
    }

    fun encodeDirectControl(event: DirectControlEvent): String {
        val json = JSONObject().apply {
            put("a", event.action)
            put("id", event.targetMessageId)
            if (event.text.isNotEmpty()) put("t", event.text)
            if (event.emoji.isNotEmpty()) put("e", event.emoji)
            if (event.reactionAction.isNotEmpty()) put("ra", event.reactionAction)
            put("ts", event.timestamp)
        }
        val b64 = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$CTRL_PREFIX$b64$MARKER_SUFFIX"
    }

    fun parseDirectControl(rawText: String): DirectControlEvent? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith(CTRL_PREFIX) || !trimmed.endsWith(MARKER_SUFFIX)) return null
        val b64 = trimmed.substring(CTRL_PREFIX.length, trimmed.length - MARKER_SUFFIX.length)
        return runCatching {
            val json = JSONObject(String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8))
            val action = json.optString("a", "")
            val targetId = json.optString("id", "")
            if (action !in setOf("react", "edit", "delete", "shred_receipt") || targetId.isBlank()) {
                return null
            }
            DirectControlEvent(
                action = action,
                targetMessageId = targetId,
                text = json.optString("t", ""),
                emoji = json.optString("e", ""),
                reactionAction = json.optString("ra", "add"),
                timestamp = json.optLong("ts", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    fun friendlyPreviewText(rawText: String, mimeType: String? = null): String {
        val body = parseReply(rawText).body
        if (body == BURNED_CAPSULE_TEXT) return BURNED_CAPSULE_TEXT
        if (body == DELETED_DIRECT_MESSAGE_TEXT) return DELETED_DIRECT_MESSAGE_TEXT
        if (parseBurnText(body) != null) return "🔥 View-Once Capsule"
        parseAttachmentDescriptor(body)?.let { desc ->
            if (desc.viewOnce) return "🔥 View-Once Capsule"
            return when (desc.kind) {
                "voice" -> {
                    val dur = if (desc.durationMs > 0) " (${formatDurationMs(desc.durationMs)})" else ""
                    "🎤 Voice message$dur"
                }
                "file" -> "📄 ${desc.fileName.ifBlank { "Document" }}"
                else -> "📷 Photo"
            }
        }
        val lowerMime = mimeType?.lowercase(Locale.US)
        if (lowerMime?.startsWith("audio/") == true) return "🎤 Voice message"
        if (lowerMime?.startsWith("image/") == true && (body == "Photo" || body.isBlank())) return "📷 Photo"
        if (lowerMime != null && !lowerMime.startsWith("image/") && body.isBlank()) return "📄 Document"
        return body
    }

    fun formatDurationMs(durationMs: Int): String {
        val totalSeconds = (durationMs.coerceAtLeast(0) + 500) / 1000
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    /**
     * Multi-pass cryptographic disk shredder: overwrites the entire file with
     * CSPRNG bytes + hardware sync, zero-fills + syncs, truncates to 0 bytes,
     * and unlinks the file so recovered flash pages cannot expose capsule bytes.
     */
    object AegisShredder {
        private val secureRandom = SecureRandom()

        fun shredPath(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            return shredFile(File(path))
        }

        fun shredFile(file: File?): Boolean {
            if (file == null || !file.exists() || !file.isFile) return false
            return runCatching {
                val len = file.length().coerceAtLeast(64L)
                val buf = ByteArray(8192)
                RandomAccessFile(file, "rw").use { raf ->
                    // Pass 1: SecureRandom overwrite
                    raf.seek(0L)
                    var remaining = len
                    while (remaining > 0L) {
                        val step = minOf(buf.size.toLong(), remaining).toInt()
                        secureRandom.nextBytes(buf)
                        raf.write(buf, 0, step)
                        remaining -= step
                    }
                    runCatching { raf.fd.sync() }

                    // Pass 2: Deterministic zero fill + truncate
                    buf.fill(0)
                    raf.seek(0L)
                    remaining = len
                    while (remaining > 0L) {
                        val step = minOf(buf.size.toLong(), remaining).toInt()
                        raf.write(buf, 0, step)
                        remaining -= step
                    }
                    runCatching { raf.fd.sync() }
                    raf.setLength(0L)
                    runCatching { raf.fd.sync() }
                }
                file.delete()
                !file.exists()
            }.getOrElse {
                runCatching { file.delete() }
                !file.exists()
            }
        }
    }
}
