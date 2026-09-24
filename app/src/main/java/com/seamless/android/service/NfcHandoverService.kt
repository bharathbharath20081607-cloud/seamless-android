package com.seamless.android.service

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.util.Log
import java.nio.charset.Charset

/**
 * NFC Handover Service for HyperDrop
 * 
 * Implements Apple AirDrop / Google Quick Share style tap-to-share:
 * 1. NFC tap initiates the connection (device discovery)
 * 2. Wi-Fi Direct handles the actual high-speed file transfer
 * 
 * This provides the best UX - just tap devices together to share files.
 * 
 * Note: Android Beam was deprecated in API 29 and removed in API 31.
 * This implementation uses foreground dispatch to receive NFC tags.
 */
class NfcHandoverService(
    private val context: Context,
    private val onHandoverRequested: (String) -> Unit // Callback with device info
) {

    companion object {
        private const val TAG = "NfcHandoverService"
        
        // MIME type for HyperDrop handover messages
        const val HYPERDROP_MIME_TYPE = "application/vnd.seamless.hyperdrop"
        
        // Message types
        private const val MSG_TYPE_HANDOVER_REQUEST = "HYPERDROP_REQ"
        private const val MSG_TYPE_HANDOVER_ACCEPT = "HYPERDROP_ACK"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var isEnabled = false
    
    // Pending file share info (set before tap)
    private var pendingShareIntent: ShareIntent? = null
    
    data class ShareIntent(
        val filePath: String,
        val fileName: String,
        val fileSize: Long,
        val wifiDirectInfo: String // Wi-Fi Direct connection info
    )

    /**
     * Initialize NFC adapter and check availability
     */
    fun initialize(): Boolean {
        return try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            if (nfcAdapter == null) {
                Log.w(TAG, "NFC not available on this device")
                false
            } else if (nfcAdapter?.isEnabled != true) {
                Log.w(TAG, "NFC is disabled by user")
                false
            } else {
                Log.d(TAG, "✅ NFC initialized successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NFC: ${e.message}")
            false
        }
    }

    /**
     * Check if NFC is available and enabled
     */
    fun isNfcAvailable(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    /**
     * Register NFC callbacks for an activity (call in onResume)
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        
        try {
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                activity, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                    try {
                        addDataType(HYPERDROP_MIME_TYPE)
                    } catch (e: IntentFilter.MalformedMimeTypeException) {
                        Log.e(TAG, "MalformedMimeType: ${e.message}")
                    }
                },
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, null)
            Log.d(TAG, "✅ NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable foreground dispatch: ${e.message}")
        }
    }

    /**
     * Disable NFC foreground dispatch (call in onPause)
     */
    fun disableForegroundDispatch(activity: Activity) {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
            Log.d(TAG, "NFC foreground dispatch disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable foreground dispatch: ${e.message}")
        }
    }

    /**
     * Prepare a file for sharing via tap
     * Call this before the user taps devices together
     */
    fun prepareShare(filePath: String, fileName: String, fileSize: Long, wifiDirectInfo: String) {
        pendingShareIntent = ShareIntent(filePath, fileName, fileSize, wifiDirectInfo)
        isEnabled = true
        Log.d(TAG, "📤 Share prepared: $fileName ($fileSize bytes)")
    }

    /**
     * Cancel pending share
     */
    fun cancelShare() {
        pendingShareIntent = null
        isEnabled = false
        Log.d(TAG, "Share cancelled")
    }

    /**
     * Get the current pending share intent (used for creating NFC message)
     */
    fun getPendingShare(): ShareIntent? = pendingShareIntent

    /**
     * Create NDEF message for sharing (call when NFC tag is detected)
     */
    fun createNdefMessage(): NdefMessage? {
        val shareIntent = pendingShareIntent ?: return null
        
        // Create handover message with Wi-Fi Direct connection info
        val handoverPayload = "$MSG_TYPE_HANDOVER_REQUEST|${shareIntent.wifiDirectInfo}|${shareIntent.fileName}|${shareIntent.fileSize}"
        
        val record = NdefRecord.createMime(
            HYPERDROP_MIME_TYPE,
            handoverPayload.toByteArray(Charset.forName("UTF-8"))
        )
        
        Log.d(TAG, "📤 Creating NDEF handover message for ${shareIntent.fileName}")
        return NdefMessage(arrayOf(record))
    }

    /**
     * Handle incoming NFC intent (call from Activity.onNewIntent)
     */
    fun handleNfcIntent(intent: Intent): Boolean {
        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED && 
            action != NfcAdapter.ACTION_TAG_DISCOVERED) {
            return false
        }

        try {
            @Suppress("DEPRECATION")
            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMessages == null || rawMessages.isEmpty()) {
                Log.w(TAG, "No NDEF messages in NFC intent")
                return false
            }

            val ndefMessage = rawMessages[0] as NdefMessage
            val record = ndefMessage.records.firstOrNull() ?: return false

            val payload = String(record.payload, Charset.forName("UTF-8"))
            Log.d(TAG, "📥 Received NFC handover: $payload")

            // Parse handover message
            if (payload.startsWith(MSG_TYPE_HANDOVER_REQUEST)) {
                // Extract Wi-Fi Direct connection info from the message
                val parts = payload.split("|")
                if (parts.size >= 2) {
                    val wifiDirectInfo = parts[1]
                    Log.d(TAG, "🤝 Handover request received, connecting via Wi-Fi Direct: $wifiDirectInfo")
                    
                    // Establish trust for this session (auto-accept files)
                    val trustIntent = Intent(context, WifiDirectService::class.java).apply {
                        this.action = "ACTION_SET_NFC_TRUSTED"
                        putExtra("is_trusted", true)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(trustIntent)
                    } else {
                        context.startService(trustIntent)
                    }

                    onHandoverRequested(wifiDirectInfo)
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle NFC intent: ${e.message}")
            return false
        }
    }

    /**
     * Called when NFC message was successfully sent
     */
    fun onNfcPushComplete() {
        Log.d(TAG, "✅ NFC handover message sent successfully!")
        // The receiving device will now connect via Wi-Fi Direct
        // We can start the file server on our end
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        pendingShareIntent = null
        isEnabled = false
        nfcAdapter = null
        Log.d(TAG, "NFC handover service cleaned up")
    }
}
