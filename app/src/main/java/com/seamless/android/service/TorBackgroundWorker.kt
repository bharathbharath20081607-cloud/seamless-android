package com.seamless.android.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.seamless.android.net.MailboxClient
import com.seamless.android.net.OnionMessageQueue
import com.seamless.android.net.TorMessagingService
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * TorBackgroundWorker - Essential for a mature, production-ready P2P app.
 * 
 * Works like Briar/Cwtch to bypass Android Doze mode and battery optimizations.
 * Periodically wakes the device, starts the Tor daemon, flushes the offline
 * message queue, polls registered mailboxes for offline messages, and listens
 * for incoming messages for a few minutes before sleeping.
 */
class TorBackgroundWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TorBackgroundWorker"
        const val WORK_NAME = "TorBackgroundSync"

        /**
         * Schedules the periodic Tor background sync.
         * Runs approximately every 15 minutes (Android minimum limit) when network is connected.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TorBackgroundWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Scheduled PeriodicWorkRequest for Tor background sync")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "⏰ Waking up for resilient Tor background sync...")
        
        try {
            val queue = OnionMessageQueue.getInstance(appContext)
            val messagingService = TorMessagingService.getInstance(appContext)
            val mailboxClient = MailboxClient.getInstance(appContext)

            val pending = queue.getAllPendingMessages()
            Log.d(TAG, "Found ${pending.values.sumOf { it.size }} pending messages to flush over Tor.")

            // Start the TorMessagingService (includes mailbox polling)
            messagingService.start()

            // Keep the worker alive for 3 minutes to allow Tor to bootstrap, send all queued messages,
            // poll mailboxes for offline messages, and receive any inbound messages/receipts.
            Log.i(TAG, "⏳ Waiting 3 minutes for Tor P2P sync + mailbox poll to complete...")
            delay(3 * 60 * 1000L)

            // Force a final mailbox poll before sleeping
            if (mailboxClient.isInitialized() && mailboxClient.getMailboxes().isNotEmpty()) {
                Log.i(TAG, "📬 Final mailbox poll before sleep...")
                try {
                    val fetched = mailboxClient.pollAllMailboxes()
                    Log.i(TAG, "📬 Fetched ${fetched.size} offline messages from mailboxes")
                } catch (e: Exception) {
                    Log.w(TAG, "Mailbox poll failed: ${e.message}")
                }
            }

            // Only stop the service if the Foreground Service is NOT running.
            // If TorForegroundService is active, the messaging service must stay alive!
            val isForegroundServiceRunning = TorForegroundService.isRunning(appContext)
            if (!isForegroundServiceRunning) {
                messagingService.stop()
                Log.i(TAG, "✅ Background sync complete — messaging service stopped (no foreground service).")
            } else {
                Log.i(TAG, "✅ Background sync complete — messaging service kept alive (foreground service active).")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync failed: ${e.message}")
            return Result.retry()
        }
    }
}
