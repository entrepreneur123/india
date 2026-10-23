package com.yaari.chat.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the phone has a network at all (the default network, with internet capability). Feeds the offline banner;
 * the app keeps working and retrying either way. Registered once for the whole app in YaariApplication. Starts
 * online, so code without the callback (tests, previews) never shows the banner.
 */
object Connectivity {
    private val state = MutableStateFlow(true)
    val online: StateFlow<Boolean> = state.asStateFlow()
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        // Some Android 11 builds throw here (SecurityException, too many callbacks): then there is just no banner.
        runCatching {
            state.value = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    state.value = true
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    state.value = false
                }

                override fun onUnavailable() {
                    state.value = false
                }
            })
            registered = true
        }.onFailure { state.value = true }
    }
}
