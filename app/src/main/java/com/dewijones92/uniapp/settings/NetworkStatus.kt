package com.dewijones92.uniapp.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Tells whether the active connection is metered (mobile data / hotspot) vs
 * unmetered (Wi-Fi), so playback can apply the right quality cap. Errs toward
 * "metered" (the data-saving side) when the state is unknown.
 */
class NetworkStatus(private val context: Context) {

    fun isMetered(): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
