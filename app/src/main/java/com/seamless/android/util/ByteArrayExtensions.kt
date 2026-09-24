package com.seamless.android.util

/**
 * Extension function to convert a ByteArray to a hexadecimal string.
 */
fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02x".format(it) }
}

/**
 * Extension function to convert a hexadecimal string to a ByteArray.
 */
fun String.hexToByteArray(): ByteArray {
    val hex = this.replace(" ", "")
    return ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
