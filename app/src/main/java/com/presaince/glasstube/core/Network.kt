/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The connection a stream pick is made on. Only [Cellular] is metered: everything else — Wi-Fi,
 * Ethernet, a VPN that reports no underlying transport, or no network at all — reads the Wi-Fi
 * setting, because the metered link is the one the user is protecting.
 */
enum class Transport { Wifi, Cellular, Ethernet, Other }

fun activeTransport(context: Context?): Transport {
    if (context == null) return Transport.Other
    return runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching Transport.Other
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching Transport.Other
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.Ethernet
            else -> Transport.Other
        }
    }.getOrDefault(Transport.Other)
}

/** The same fact as one word, for the player log. */
fun transportLabel(context: Context?): String = when (activeTransport(context)) {
    Transport.Wifi -> "wifi"
    Transport.Cellular -> "lte"
    Transport.Ethernet -> "eth"
    Transport.Other -> "other"
}

/**
 * Whether the phone can reach the internet, as a value the player can read instead of inferring
 * it from a failed request. A DNS drop used to arrive as a stream error, so the two quiet retries
 * were spent on a phone that had nothing to retry, and the video was dead by the time the
 * connection came back. Lives for the process, like the screen receiver: one registration, never
 * taken down, because the thing that needs it outlives every screen.
 *
 * `VALIDATED` is the capability that matters. `INTERNET` alone is granted to a Wi-Fi network with
 * a captive portal or no uplink, which is the case this has to tell apart from a working one.
 */
class NetworkMonitor(context: Context) {

    private val _online = MutableStateFlow(readOnline(context))

    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            _online.value = caps.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                            )
                        }

                        override fun onLost(network: Network) {
                            _online.value = false
                        }
                    },
                )
        }
    }

    private companion object {
        fun readOnline(context: Context): Boolean = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return@runCatching true
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
                ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }
}
