package com.seamless.android.core

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Global exception handler for catching and logging unhandled exceptions
 * Prevents app crashes when possible and logs errors for debugging
 */
class GlobalExceptionHandler private constructor(
    private val application: Application,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    
    companion object {
        private const val TAG = "GlobalExceptionHandler"
        private const val MAX_ERROR_LOGS = 50
        
        @Volatile
        private var INSTANCE: GlobalExceptionHandler? = null
        
        /**
         * Initialize and install the global exception handler
         */
        fun install(application: Application) {
            synchronized(this) {
                if (INSTANCE == null) {
                    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                    val handler = GlobalExceptionHandler(application, defaultHandler)
                    Thread.setDefaultUncaughtExceptionHandler(handler)
                    INSTANCE = handler
                    
                    // Also install coroutine exception handler
                    installCoroutineHandler()
                    
                    Log.i(TAG, "✅ Global exception handler installed")
                }
            }
        }
        
        /**
         * Install coroutine exception handler for async code
         */
        private fun installCoroutineHandler() {
            val handler = CoroutineExceptionHandler { context, throwable ->
                Log.e(TAG, "⚠️ Coroutine exception caught", throwable)
                logError(throwable)
            }
            // Note: This handler is for reference - individual coroutine scopes
            // should use this handler when created
        }
        
        /**
         * Get the coroutine exception handler for safe async operations
         */
        val safeCoroutineHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "⚠️ Coroutine exception caught", throwable)
            logError(throwable)
        }
        
        // Error log storage
        private val errorLogs = ConcurrentLinkedQueue<ErrorLog>()
        
        /**
         * Log an error for debugging
         */
        fun logError(throwable: Throwable, context: String? = null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            
            val errorLog = ErrorLog(
                timestamp = System.currentTimeMillis(),
                context = context ?: "Unknown",
                message = throwable.message ?: "No message",
                stackTrace = sw.toString().take(500),
                className = throwable.javaClass.simpleName
            )
            
            errorLogs.add(errorLog)
            
            // Keep only recent errors
            while (errorLogs.size > MAX_ERROR_LOGS) {
                errorLogs.poll()
            }
            
            Log.e(TAG, "📝 Error logged: ${errorLog.className} - ${errorLog.message}")
        }
        
        /**
         * Get recent error logs for debugging
         */
        fun getRecentErrors(): List<ErrorLog> = errorLogs.toList()
        
        /**
         * Clear error logs
         */
        fun clearLogs() = errorLogs.clear()
    }
    
    /**
     * Error log data class
     */
    data class ErrorLog(
        val timestamp: Long,
        val context: String,
        val message: String,
        val stackTrace: String,
        val className: String
    ) {
        fun toReadableString(): String {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
            return "[$time] $className: $message"
        }
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "🔴 UNCAUGHT EXCEPTION on thread: ${thread.name}", throwable)
            
            // Log the error
            logError(throwable, "UncaughtException on ${thread.name}")
            
            // Check if this is a recoverable error
            // Never swallow exceptions on the main thread: if the main Looper dies without the
            // process exiting, the app freezes in a zombie state. Only background threads may recover.
            val isMainThread = thread === android.os.Looper.getMainLooper().thread
            if (!isMainThread && isRecoverableError(throwable)) {
                Log.w(TAG, "⚠️ Recoverable error detected, attempting to continue...")
                // For recoverable errors, log and don't crash
                return
            }
            
            // For fatal errors, pass to default handler
            Log.e(TAG, "💀 Fatal error, passing to default handler")
            defaultHandler?.uncaughtException(thread, throwable)
            
        } catch (e: Exception) {
            // Last resort - pass to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    /**
     * Check if an error is potentially recoverable
     */
    private fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            // Common recoverable exceptions
            is IllegalStateException -> true
            is NullPointerException -> {
                // Only recover from NPE in specific known contexts
                val stackTrace = throwable.stackTrace.firstOrNull()?.className ?: ""
                stackTrace.contains("Compose") || 
                stackTrace.contains("UI") ||
                stackTrace.contains("View")
            }
            is IndexOutOfBoundsException -> true
            is ConcurrentModificationException -> true
            is SecurityException -> false // Security issues should crash
            is OutOfMemoryError -> false // OOM is fatal
            else -> false
        }
    }
}

/**
 * Extension to safely run code with error logging
 */
inline fun <T> safeRun(
    context: String = "Unknown",
    fallback: T? = null,
    block: () -> T
): T? {
    return try {
        block()
    } catch (e: Exception) {
        Log.e("SafeRun", "Error in $context: ${e.message}")
        GlobalExceptionHandler.logError(e, context)
        fallback
    }
}

/**
 * Extension for coroutines with safe error handling
 */
fun CoroutineScope.launchSafe(
    context: String = "Unknown",
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(GlobalExceptionHandler.safeCoroutineHandler) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("SafeCoroutine", "Error in $context: ${e.message}")
            GlobalExceptionHandler.logError(e, context)
        }
    }
}
