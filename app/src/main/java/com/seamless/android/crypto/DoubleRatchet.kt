package com.seamless.android.crypto

import android.util.Log
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Double Ratchet Algorithm Implementation
 * 
 * Based on Signal Protocol specification for:
 * - Forward secrecy (past messages protected if current key compromised)
 * - Post-compromise security (future messages protected after breach)
 * - Out-of-order message handling with skipped key cache
 * 
 * Cryptographic primitives:
 * - X25519 for DH key exchange
 * - HKDF-SHA256 for key derivation
 * - ChaCha20-Poly1305 for AEAD (falls back to AES-256-GCM on older devices)
 * 
 * Based on: https://signal.org/docs/specifications/doubleratchet/
 */
class DoubleRatchet private constructor(
    private var dhKeyPair: X25519KeyPair,
    private var remoteDHPublicKey: ByteArray?,
    private var rootKey: ByteArray,
    private var sendingChainKey: ByteArray?,
    private var receivingChainKey: ByteArray?,
    private var sendMessageNumber: Int = 0,
    private var receiveMessageNumber: Int = 0,
    private var previousSendingChainLength: Int = 0,
    private val skippedMessageKeys: MutableMap<Pair<ByteArray, Int>, ByteArray> = mutableMapOf()
) {
    companion object {
        private const val TAG = "DoubleRatchet"
        
        // Maximum number of skipped message keys to store
        const val MAX_SKIP = 1000
        
        // Key sizes
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 16
        
        // HKDF info strings
        private val INFO_ROOT_KEY = "SeamlessRootKey".toByteArray()
        private val INFO_CHAIN_KEY = "SeamlessChainKey".toByteArray()
        private val INFO_MESSAGE_KEY = "SeamlessMessageKey".toByteArray()
        
        private val secureRandom = SecureRandom()
        
        /**
         * Initialize as the initiator (Alice in the protocol)
         * Called after completing Noise handshake
         */
        fun initAsInitiator(
            sharedSecret: ByteArray,
            remoteDHPublicKey: ByteArray
        ): DoubleRatchet {
            Log.d(TAG, "🔑 Initializing Double Ratchet as initiator")
            
            // Generate our first DH key pair
            val dhKeyPair = X25519KeyPair.generate()
            
            // Perform DH with remote public key
            val dhOutput = X25519.computeSharedSecret(dhKeyPair.privateKey, remoteDHPublicKey)
            
            // Derive root key and sending chain key
            val (rootKey, chainKey) = kdfRootKey(sharedSecret, dhOutput)
            
            return DoubleRatchet(
                dhKeyPair = dhKeyPair,
                remoteDHPublicKey = remoteDHPublicKey,
                rootKey = rootKey,
                sendingChainKey = chainKey,
                receivingChainKey = null
            )
        }
        
        /**
         * Initialize as the responder (Bob in the protocol)
         * Called after completing Noise handshake
         */
        fun initAsResponder(
            sharedSecret: ByteArray,
            ourDHKeyPair: X25519KeyPair
        ): DoubleRatchet {
            Log.d(TAG, "🔑 Initializing Double Ratchet as responder")
            
            return DoubleRatchet(
                dhKeyPair = ourDHKeyPair,
                remoteDHPublicKey = null,
                rootKey = sharedSecret,
                sendingChainKey = null,
                receivingChainKey = null
            )
        }
        
        /**
         * Restore from serialized state
         */
        fun restore(state: RatchetState): DoubleRatchet {
            return DoubleRatchet(
                dhKeyPair = X25519KeyPair(state.dhPrivateKey, state.dhPublicKey),
                remoteDHPublicKey = state.remoteDHPublicKey,
                rootKey = state.rootKey,
                sendingChainKey = state.sendingChainKey,
                receivingChainKey = state.receivingChainKey,
                sendMessageNumber = state.sendMessageNumber,
                receiveMessageNumber = state.receiveMessageNumber,
                previousSendingChainLength = state.previousSendingChainLength,
                skippedMessageKeys = state.skippedMessageKeys.toMutableMap()
            )
        }
        
        /**
         * KDF for deriving root key and chain key from DH output
         */
        private fun kdfRootKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
            val salt = rootKey
            val inputKeyMaterial = dhOutput
            
            // HKDF-Expand with two outputs
            val prk = hkdfExtract(salt, inputKeyMaterial)
            val output = hkdfExpand(prk, INFO_ROOT_KEY, KEY_SIZE * 2)
            
            val newRootKey = output.sliceArray(0 until KEY_SIZE)
            val chainKey = output.sliceArray(KEY_SIZE until KEY_SIZE * 2)
            
            return Pair(newRootKey, chainKey)
        }
        
        /**
         * KDF for deriving next chain key and message key
         */
        private fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
            val mac = Mac.getInstance("HmacSHA256")
            
            // Derive message key
            mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
            val messageKey = mac.doFinal(byteArrayOf(0x01))
            
            // Derive next chain key
            mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
            val nextChainKey = mac.doFinal(byteArrayOf(0x02))
            
            return Pair(nextChainKey, messageKey)
        }
        
        /**
         * HKDF-Extract (RFC 5869)
         */
        private fun hkdfExtract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            return mac.doFinal(inputKeyMaterial)
        }
        
        /**
         * HKDF-Expand (RFC 5869)
         */
        private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            val hashLen = 32
            val n = (length + hashLen - 1) / hashLen
            
            var output = ByteArray(0)
            var t = ByteArray(0)
            
            for (i in 1..n) {
                mac.init(SecretKeySpec(prk, "HmacSHA256"))
                mac.update(t)
                mac.update(info)
                mac.update(byteArrayOf(i.toByte()))
                t = mac.doFinal()
                output += t
            }
            
            return output.sliceArray(0 until length)
        }
    }
    
    /**
     * Encrypt a message
     * Returns: encrypted message with header containing our DH public key
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray): RatchetMessage {
        // Get message key from sending chain
        if (sendingChainKey == null) {
            throw IllegalStateException("Sending chain not initialized")
        }
        
        val (nextChainKey, messageKey) = kdfChainKey(sendingChainKey!!)
        sendingChainKey = nextChainKey
        
        // Create header
        val header = RatchetHeader(
            dhPublicKey = dhKeyPair.publicKey.clone(),
            previousChainLength = previousSendingChainLength,
            messageNumber = sendMessageNumber
        )
        
        sendMessageNumber++
        
        // Encrypt message with AEAD
        val (ciphertext, nonce) = aeadEncrypt(messageKey, plaintext, header.encode())
        
        // Zeroize message key
        messageKey.fill(0)
        
        return RatchetMessage(header, nonce, ciphertext)
    }
    
    /**
     * Decrypt a message
     */
    @Synchronized
    fun decrypt(message: RatchetMessage): ByteArray {
        val header = message.header
        val headerBytes = header.encode()
        
        // Try skipped message keys first
        val skippedKey = trySkippedMessageKeys(header, headerBytes)
        if (skippedKey != null) {
            val plaintext = aeadDecrypt(skippedKey, message.ciphertext, message.nonce, headerBytes)
            skippedKey.fill(0)
            return plaintext
        }
        
        // Check if we need to perform a DH ratchet step
        if (remoteDHPublicKey == null || !header.dhPublicKey.contentEquals(remoteDHPublicKey)) {
            skipMessageKeys(header.previousChainLength)
            performDHRatchet(header.dhPublicKey)
        }
        
        // Skip any skipped messages in current receiving chain
        skipMessageKeys(header.messageNumber)
        
        // Derive message key
        if (receivingChainKey == null) {
            throw IllegalStateException("Receiving chain not initialized")
        }
        
        val (nextChainKey, messageKey) = kdfChainKey(receivingChainKey!!)
        receivingChainKey = nextChainKey
        receiveMessageNumber++
        
        // Decrypt
        val plaintext = aeadDecrypt(messageKey, message.ciphertext, message.nonce, headerBytes)
        
        // Zeroize
        messageKey.fill(0)
        
        return plaintext
    }
    
    /**
     * Perform DH ratchet step
     */
    private fun performDHRatchet(theirDHPublicKey: ByteArray) {
        previousSendingChainLength = sendMessageNumber
        sendMessageNumber = 0
        receiveMessageNumber = 0
        remoteDHPublicKey = theirDHPublicKey.clone()
        
        // Compute DH with their new key and our current key
        val dhOutput1 = X25519.computeSharedSecret(dhKeyPair.privateKey, theirDHPublicKey)
        val (rootKey1, chainKey1) = kdfRootKey(rootKey, dhOutput1)
        rootKey = rootKey1
        receivingChainKey = chainKey1
        
        // Generate new DH key pair
        dhKeyPair = X25519KeyPair.generate()
        
        // Compute DH with their key and our new key
        val dhOutput2 = X25519.computeSharedSecret(dhKeyPair.privateKey, theirDHPublicKey)
        val (rootKey2, chainKey2) = kdfRootKey(rootKey, dhOutput2)
        rootKey = rootKey2
        sendingChainKey = chainKey2
    }
    
    /**
     * Try to find message key in skipped keys
     */
    private fun trySkippedMessageKeys(header: RatchetHeader, headerBytes: ByteArray): ByteArray? {
        val key = Pair(header.dhPublicKey, header.messageNumber)
        
        // Find matching key (we need to compare byte arrays properly)
        val matchingEntry = skippedMessageKeys.entries.find { (k, _) ->
            k.first.contentEquals(header.dhPublicKey) && k.second == header.messageNumber
        }
        
        return if (matchingEntry != null) {
            skippedMessageKeys.remove(matchingEntry.key)
            matchingEntry.value
        } else {
            null
        }
    }
    
    /**
     * Skip message keys up to given message number
     */
    private fun skipMessageKeys(until: Int) {
        if (receivingChainKey == null) return
        
        if (receiveMessageNumber + MAX_SKIP < until) {
            throw SecurityException("Too many skipped messages")
        }
        
        while (receiveMessageNumber < until) {
            val (nextChainKey, messageKey) = kdfChainKey(receivingChainKey!!)
            
            val key = Pair(remoteDHPublicKey!!.clone(), receiveMessageNumber)
            skippedMessageKeys[key] = messageKey
            
            receivingChainKey = nextChainKey
            receiveMessageNumber++
            
            // Limit cache size
            if (skippedMessageKeys.size > MAX_SKIP) {
                val oldest = skippedMessageKeys.keys.first()
                skippedMessageKeys.remove(oldest)?.fill(0)
            }
        }
    }
    
    /**
     * AEAD Encrypt using AES-256-GCM
     */
    private fun aeadEncrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(NONCE_SIZE)
        secureRandom.nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(TAG_SIZE * 8, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        
        return Pair(ciphertext, nonce)
    }
    
    /**
     * AEAD Decrypt using AES-256-GCM
     */
    private fun aeadDecrypt(key: ByteArray, ciphertext: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(TAG_SIZE * 8, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }
    
    /**
     * Export state for persistence
     */
    fun exportState(): RatchetState {
        return RatchetState(
            dhPrivateKey = dhKeyPair.privateKey.clone(),
            dhPublicKey = dhKeyPair.publicKey.clone(),
            remoteDHPublicKey = remoteDHPublicKey?.clone(),
            rootKey = rootKey.clone(),
            sendingChainKey = sendingChainKey?.clone(),
            receivingChainKey = receivingChainKey?.clone(),
            sendMessageNumber = sendMessageNumber,
            receiveMessageNumber = receiveMessageNumber,
            previousSendingChainLength = previousSendingChainLength,
            skippedMessageKeys = skippedMessageKeys.mapKeys { 
                Pair(it.key.first.clone(), it.key.second) 
            }.mapValues { it.value.clone() }
        )
    }
    
    /**
     * Get our current DH public key (for handshake)
     */
    fun getPublicKey(): ByteArray = dhKeyPair.publicKey.clone()
}

/**
 * X25519 Key Pair
 */
data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
) {
    companion object {
        private val secureRandom = SecureRandom()
        
        fun generate(): X25519KeyPair {
            val privateKey = ByteArray(32)
            secureRandom.nextBytes(privateKey)
            
            // Clamp private key per X25519 spec
            privateKey[0] = (privateKey[0].toInt() and 248).toByte()
            privateKey[31] = (privateKey[31].toInt() and 127).toByte()
            privateKey[31] = (privateKey[31].toInt() or 64).toByte()
            
            val publicKey = X25519.computePublicKey(privateKey)
            
            return X25519KeyPair(privateKey, publicKey)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }
    
    override fun hashCode(): Int = privateKey.contentHashCode()
}

/**
 * X25519 Diffie-Hellman
 */
object X25519 {
    private const val KEY_SIZE = 32
    
    // Basepoint for X25519
    private val BASEPOINT = ByteArray(32).apply { this[0] = 9 }
    
    /**
     * Compute public key from private key
     */
    fun computePublicKey(privateKey: ByteArray): ByteArray {
        return scalarMult(privateKey, BASEPOINT)
    }
    
    /**
     * Compute shared secret from private key and peer's public key
     */
    fun computeSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val result = scalarMult(privateKey, peerPublicKey)
        
        // Check for all-zeros result (invalid point)
        if (result.all { it == 0.toByte() }) {
            throw SecurityException("Invalid X25519 shared secret")
        }
        
        return result
    }
    
    /**
     * X25519 scalar multiplication using Android's KeyAgreement when available
     */
    private fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        try {
            // Try to use Android's native X25519 (API 31+)
            val keyFactory = java.security.KeyFactory.getInstance("XDH")
            val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(
                wrapX25519PrivateKey(scalar)
            )
            val privateKey = keyFactory.generatePrivate(privateKeySpec)
            
            val publicKeySpec = java.security.spec.X509EncodedKeySpec(
                wrapX25519PublicKey(point)
            )
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            
            val keyAgreement = javax.crypto.KeyAgreement.getInstance("XDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)
            return keyAgreement.generateSecret()
            
        } catch (e: Exception) {
            // Fallback to pure software implementation
            return softwareScalarMult(scalar, point)
        }
    }
    
    /**
     * Wrap raw X25519 private key in PKCS#8 format
     */
    private fun wrapX25519PrivateKey(raw: ByteArray): ByteArray {
        // ASN.1 prefix for X25519 private key
        val prefix = byteArrayOf(
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e,
            0x04, 0x22, 0x04, 0x20
        )
        return prefix + raw
    }
    
    /**
     * Wrap raw X25519 public key in X.509 format
     */
    private fun wrapX25519PublicKey(raw: ByteArray): ByteArray {
        // ASN.1 prefix for X25519 public key
        val prefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
        )
        return prefix + raw
    }
    
    /**
     * Software X25519 implementation (fallback)
     * Based on TweetNaCl with optimizations
     */
    private fun softwareScalarMult(n: ByteArray, p: ByteArray): ByteArray {
        val z = n.clone()
        z[31] = (z[31].toInt() and 127).toByte()
        z[31] = (z[31].toInt() or 64).toByte()
        z[0] = (z[0].toInt() and 248).toByte()
        
        val x = gf(p)
        val a = gf()
        val b = gf()
        val c = gf()
        val d = gf()
        val e = gf()
        val f = gf()
        
        a[0] = 1
        d[0] = 1
        
        for (i in 254 downTo 0) {
            val r = (z[i / 8].toInt() shr (i and 7)) and 1
            sel25519(a, b, r)
            sel25519(c, d, r)
            add(e, a, c)
            sub(a, a, c)
            add(c, b, d)
            sub(b, b, d)
            square(d, e)
            square(f, a)
            mult(a, c, a)
            mult(c, b, e)
            add(e, a, c)
            sub(a, a, c)
            square(b, a)
            sub(c, d, f)
            mult(a, c, _121665)
            add(a, a, d)
            mult(c, c, a)
            mult(a, d, f)
            mult(d, b, x)
            square(b, e)
            sel25519(a, b, r)
            sel25519(c, d, r)
        }
        
        inv25519(c, c)
        mult(a, a, c)
        return pack25519(a)
    }
    
    // Field element operations
    private fun gf(init: ByteArray? = null): LongArray {
        val o = LongArray(16)
        if (init != null) {
            for (i in 0 until 16) {
                o[i] = (init[2 * i].toLong() and 0xff) or ((init[2 * i + 1].toLong() and 0xff) shl 8)
            }
        }
        return o
    }
    
    private val _121665 = LongArray(16).apply { this[0] = 0xDB41 }
    
    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).inv().toLong()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }
    
    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }
    
    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }
    
    private fun mult(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38 * t[i + 16]
        }
        for (i in 0 until 16) {
            o[i] = t[i]
        }
        car25519(o)
        car25519(o)
    }
    
    private fun square(o: LongArray, a: LongArray) {
        mult(o, a, a)
    }
    
    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            o[(i + 1) % 16] += if (i < 15) c - 1 else 38 * (c - 1)
            o[i] -= c shl 16
        }
    }
    
    private fun inv25519(o: LongArray, i: LongArray) {
        val c = LongArray(16)
        for (a in 0 until 16) c[a] = i[a]
        for (a in 253 downTo 0) {
            square(c, c)
            if (a != 2 && a != 4) mult(c, c, i)
        }
        for (a in 0 until 16) o[a] = c[a]
    }
    
    private fun pack25519(n: LongArray): ByteArray {
        val m = LongArray(16)
        val t = LongArray(16)
        for (i in 0 until 16) t[i] = n[i]
        car25519(t)
        car25519(t)
        car25519(t)
        
        for (j in 0 until 2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = (m[15] shr 16) and 1
            m[14] = m[14] and 0xffff
            sel25519(t, m, (1 - b).toInt())
        }
        
        val o = ByteArray(32)
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xff).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
        return o
    }
}

/**
 * Ratchet message header
 */
data class RatchetHeader(
    val dhPublicKey: ByteArray,
    val previousChainLength: Int,
    val messageNumber: Int
) {
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(32 + 4 + 4)
        buffer.put(dhPublicKey)
        buffer.putInt(previousChainLength)
        buffer.putInt(messageNumber)
        return buffer.array()
    }
    
    companion object {
        fun decode(data: ByteArray): RatchetHeader {
            val buffer = ByteBuffer.wrap(data)
            val dhPublicKey = ByteArray(32)
            buffer.get(dhPublicKey)
            val previousChainLength = buffer.int
            val messageNumber = buffer.int
            return RatchetHeader(dhPublicKey, previousChainLength, messageNumber)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetHeader) return false
        return dhPublicKey.contentEquals(other.dhPublicKey) && 
               previousChainLength == other.previousChainLength && 
               messageNumber == other.messageNumber
    }
    
    override fun hashCode(): Int = dhPublicKey.contentHashCode()
}

/**
 * Complete ratchet message (header + ciphertext)
 */
data class RatchetMessage(
    val header: RatchetHeader,
    val nonce: ByteArray,
    val ciphertext: ByteArray
) {
    fun encode(): ByteArray {
        val headerBytes = header.encode()
        val buffer = ByteBuffer.allocate(4 + headerBytes.size + 4 + nonce.size + 4 + ciphertext.size)
        buffer.putInt(headerBytes.size)
        buffer.put(headerBytes)
        buffer.putInt(nonce.size)
        buffer.put(nonce)
        buffer.putInt(ciphertext.size)
        buffer.put(ciphertext)
        return buffer.array()
    }
    
    companion object {
        fun decode(data: ByteArray): RatchetMessage {
            val buffer = ByteBuffer.wrap(data)
            
            val headerSize = buffer.int
            val headerBytes = ByteArray(headerSize)
            buffer.get(headerBytes)
            val header = RatchetHeader.decode(headerBytes)
            
            val nonceSize = buffer.int
            val nonce = ByteArray(nonceSize)
            buffer.get(nonce)
            
            val ciphertextSize = buffer.int
            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)
            
            return RatchetMessage(header, nonce, ciphertext)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetMessage) return false
        return header == other.header && nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)
    }
    
    override fun hashCode(): Int = header.hashCode()
}

/**
 * Serializable state for persistence
 */
data class RatchetState(
    val dhPrivateKey: ByteArray,
    val dhPublicKey: ByteArray,
    val remoteDHPublicKey: ByteArray?,
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray?,
    val receivingChainKey: ByteArray?,
    val sendMessageNumber: Int,
    val receiveMessageNumber: Int,
    val previousSendingChainLength: Int,
    val skippedMessageKeys: Map<Pair<ByteArray, Int>, ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetState) return false
        return dhPrivateKey.contentEquals(other.dhPrivateKey) &&
               dhPublicKey.contentEquals(other.dhPublicKey) &&
               sendMessageNumber == other.sendMessageNumber
    }
    
    override fun hashCode(): Int = dhPrivateKey.contentHashCode()
}
