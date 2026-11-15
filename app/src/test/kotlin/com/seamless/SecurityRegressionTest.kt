@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless

import com.seamless.android.crypto.DoubleRatchet
import com.seamless.android.crypto.RatchetMessage
import com.seamless.android.crypto.X25519KeyPair
import com.seamless.android.model.BitchatFilePacket
import com.seamless.android.model.FragmentPayload
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.nostr.NostrCrypto
import com.seamless.android.nostr.NostrEvent
import com.seamless.android.nostr.NostrProofOfWork
import com.seamless.android.noise.southernstorm.protocol.Noise
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Security regression tests for the audit fixes.
 * Run with:  .\gradlew testDebugUnitTest --console=plain
 */
class SecurityRegressionTest {

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // ---- Nostr: BIP-340 official test vector #0 proves signature verification is correct ----
    // (needed because relay events are now rejected when their signature is invalid)
    @Test
    fun schnorrVerify_acceptsOfficialBip340Vector() {
        val pub = "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9".lowercase()
        val sig = ("E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA8215" +
            "25F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0").lowercase()
        assertTrue(NostrCrypto.schnorrVerify(ByteArray(32), sig, pub))
    }

    @Test
    fun schnorrVerify_rejectsTamperedSignatureAndMessage() {
        val pub = "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9".lowercase()
        val sig = ("E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA8215" +
            "25F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0").lowercase()
        val tampered = sig.dropLast(1) + (if (sig.last() == '0') "1" else "0")
        assertFalse(NostrCrypto.schnorrVerify(ByteArray(32), tampered, pub))
        val otherMessage = ByteArray(32).also { it[0] = 1 }
        assertFalse(NostrCrypto.schnorrVerify(otherMessage, sig, pub))
    }

    @Test
    fun schnorr_signThenVerifyRoundTrip() {
        val (priv, pub) = NostrCrypto.generateKeyPair()
        val msg = ByteArray(32) { (it * 7).toByte() }
        val sig = NostrCrypto.schnorrSign(msg, priv)
        assertTrue(NostrCrypto.schnorrVerify(msg, sig, pub))
    }

    // ---- Noise: low-order Curve25519 point must not yield an all-zero shared secret ----
    @Test
    fun curve25519_rejectsLowOrderPublicKey() {
        val mine = Noise.createDH("25519")
        mine.generateKeyPair()
        val attacker = Noise.createDH("25519")
        attacker.setPublicKey(ByteArray(32), 0) // all-zero = low-order point
        try {
            mine.calculate(ByteArray(32), 0, attacker)
            fail("Expected rejection of all-zero shared secret")
        } catch (expected: IllegalStateException) {
            // good
        }
    }

    // ---- Protocol parsers: hostile length fields must be rejected, not allocated ----
    @Test
    fun syncOffer_hugeLengthIsRejected() {
        val hostile = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 0)
        assertNull(OnionMessageProtocol.SyncOffer.fromBytes(hostile))
    }

    @Test
    fun syncBatch_hugeCountIsRejected() {
        val hostile = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 1)
        assertNull(OnionMessageProtocol.SyncBatch.fromBytes(hostile))
    }

    @Test
    fun ratchetMessage_hugeHeaderSizeIsRejected() {
        val hostile = hex("7FFFFFFF0000000000000000")
        try {
            RatchetMessage.decode(hostile)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // good
        }
    }

    @Test
    fun nostrPoW_rejectsSpoofedEventId() {
        val spoofed = NostrEvent(
            id = "0".repeat(64),
            pubkey = "a".repeat(64),
            createdAt = 1700000000,
            kind = 1,
            tags = listOf(listOf("nonce", "12345", "16")),
            content = "hello",
            sig = "b".repeat(128)
        )
        assertFalse(NostrProofOfWork.validateDifficulty(spoofed, 8))
    }

    @Test
    fun fragmentPayload_rejectsExcessiveFragmentCount() {
        val id = FragmentPayload.generateFragmentID()
        val valid = FragmentPayload(id, 0, 512, 1u, byteArrayOf(1, 2, 3))
        val excessive = FragmentPayload(id, 0, 5000, 1u, byteArrayOf(1, 2, 3))
        assertTrue(valid.isValid())
        assertFalse(excessive.isValid())
    }

    @Test
    fun bitchatFilePacket_handlesUnsigned32BitSize() {
        val threeGb = 3_000_000_000L
        val pkt = BitchatFilePacket(
            fileName = "clip.mp4",
            fileSize = threeGb,
            mimeType = "video/mp4",
            content = byteArrayOf(1, 2, 3, 4)
        )
        val encoded = pkt.encode()!!
        val decoded = BitchatFilePacket.decode(encoded)!!
        assertEquals(threeGb, decoded.fileSize)
    }

    @Test
    fun doubleRatchet_rollsBackStateOnTamperedCiphertext() {
        val sharedSecret = ByteArray(32) { (it + 1).toByte() }
        val bobKeyPair = X25519KeyPair.generate()
        val alice = DoubleRatchet.initAsInitiator(sharedSecret, bobKeyPair.publicKey)
        val bob = DoubleRatchet.initAsResponder(sharedSecret, bobKeyPair)

        val msg1 = alice.encrypt("first".toByteArray())
        assertArrayEquals("first".toByteArray(), bob.decrypt(msg1))

        val msg2 = alice.encrypt("second".toByteArray())
        val badCiphertext = msg2.ciphertext.clone()
        badCiphertext[0] = (badCiphertext[0].toInt() xor 0xFF).toByte()
        val tampered = RatchetMessage(
            header = msg2.header,
            nonce = msg2.nonce,
            ciphertext = badCiphertext
        )
        try {
            bob.decrypt(tampered)
            fail("Expected decryption failure on tampered ciphertext")
        } catch (_: Exception) {
            // Expected; Bob's ratchet state must remain intact for genuine msg2
        }

        assertArrayEquals("second".toByteArray(), bob.decrypt(msg2))
    }
}
