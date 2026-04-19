package fyi.acmc.trailkarma.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NetworkUtil(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(isOnlineNow())
    private val _networkChanged = MutableStateFlow(false)

    val isOnline: Flow<Boolean> = _isOnline
    val networkChanged: Flow<Boolean> = _networkChanged

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NetworkUtil", "Network available")
            _isOnline.value = true
            _networkChanged.value = true
        }

        override fun onLost(network: Network) {
            Log.d("NetworkUtil", "Network lost")
            _isOnline.value = false
            _networkChanged.value = true
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.d("NetworkUtil", "Network capabilities changed")
            _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _networkChanged.value = true
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun isOnlineNow(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun updateNetworkStatus() {
        _isOnline.value = isOnlineNow()
    }

    fun clearNetworkChangeFlag() {
        _networkChanged.value = false
    }
}
