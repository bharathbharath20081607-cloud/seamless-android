package com.seamless.android.ui.events

/**
 * UI Events for user feedback via Snackbar and other UI responses
 */
sealed class UiEvent {
    data class ShowSnackbar(
        val message: String,
        val action: String? = null,
        val actionLabel: String? = null,
        val duration: SnackbarDuration = SnackbarDuration.SHORT
    ) : UiEvent()
    
    data class ShowError(
        val message: String,
        val retry: (() -> Unit)? = null
    ) : UiEvent()
    
    object MessageSent : UiEvent()
    object MessageDelivered : UiEvent()
    data class ConnectionEstablished(val peerName: String) : UiEvent()
    data class ConnectionLost(val peerName: String) : UiEvent()
    data class JoinedChannel(val channelName: String) : UiEvent()
    data class LeftChannel(val channelName: String) : UiEvent()
    data class FileSent(val fileName: String) : UiEvent()
    data class FileReceived(val fileName: String) : UiEvent()
    object CopiedToClipboard : UiEvent()
}

enum class SnackbarDuration {
    SHORT,  // 4 seconds
    LONG,   // 10 seconds
    INDEFINITE
}
