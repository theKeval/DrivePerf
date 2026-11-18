package com.driveperf.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the device has a validated internet connection, for Replay's offline fallback (F-RPL-01 AC5, EC-NET). Follows
 * the system default network; starts from the current one.
 */
class NetworkStatus(context: Context, scope: CoroutineScope) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isOnline())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        connectivity?.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(), current())

    private fun current(): Boolean =
        connectivity?.let { it.getNetworkCapabilities(it.activeNetwork)?.isOnline() } ?: false

    private fun NetworkCapabilities.isOnline(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
