package com.seamless.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks network connectivity status (Internet availability)
 */
class NetworkStatusTracker(private val appContext: Context) {

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            checkInternetAvailability()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            checkInternetAvailability()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            checkInternetAvailability()
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            checkInternetAvailability()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun checkInternetAvailability() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (_isInternetAvailable.value != isAvailable) {
            Log.i(TAG, "Internet availability changed: $isAvailable")
            _isInternetAvailable.value = isAvailable
            
            // PRODUCTION FIX: When internet becomes available, notify CensorshipDetector
            // to clear any stale false-positive cache from when there was no internet
            if (isAvailable) {
                try {
                    CensorshipDetector.getInstance(appContext).onNetworkAvailable()
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not notify CensorshipDetector: ${e.message}")
                }
            }
        }
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    companion object {
        private const val TAG = "NetworkStatusTracker"
        
        @Volatile
        private var INSTANCE: NetworkStatusTracker? = null

        fun getInstance(context: Context): NetworkStatusTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkStatusTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
