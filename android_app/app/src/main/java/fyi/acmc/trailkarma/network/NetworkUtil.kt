package fyi.acmc.trailkarma.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NetworkUtil(context: Context) {
    companion object {
        private const val TAG = "NetworkUtil"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(isOnlineNow())
    private val _networkChanged = MutableStateFlow(false)

    val isOnline: Flow<Boolean> = _isOnline
    val networkChanged: Flow<Boolean> = _networkChanged

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            _isOnline.value = true
            _networkChanged.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            _isOnline.value = false
            _networkChanged.value = true
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.d(
                TAG,
                "Network capabilities changed: internet=${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
                    "validated=${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
            )
            _isOnline.value = hasUsableNetwork(networkCapabilities)
            _networkChanged.value = true
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun isOnlineNow(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return hasUsableNetwork(capabilities)
    }

    fun updateNetworkStatus() {
        _isOnline.value = isOnlineNow()
    }

    fun clearNetworkChangeFlag() {
        _networkChanged.value = false
    }

    private fun hasUsableNetwork(capabilities: NetworkCapabilities): Boolean {
        // `VALIDATED` is too strict for our dev flows because emulator traffic to 10.0.2.2 and
        // device traffic forwarded via adb reverse can still succeed without network validation.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
