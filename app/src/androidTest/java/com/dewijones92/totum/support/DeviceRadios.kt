package com.dewijones92.totum.support

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turning this device's radios off, and knowing when the OS agrees.
 *
 * One copy for every test that needs it, because getting it wrong looks like a pass. Three tests
 * now take a device offline and each had its own version of these six lines.
 *
 * **The radios go off, not a packet filter.** `iptables -j DROP` leaves Android reporting the
 * network as VALIDATED, so every connectivity-aware path — `NetworkCallback`, wait-for-online,
 * "retry when back" — carries on believing it is connected and is never exercised. That cost a day
 * on 2026-07-31: the filtered run looked like a successful reproduction while leaving the code
 * under test untouched. `svc wifi disable` is what makes the OS agree.
 */
object DeviceRadios {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    /** Both radios off, waiting until the OS stops reporting a validated network. */
    fun goOffline() {
        shell("svc wifi disable")
        shell("svc data disable")
        // The callbacks are asynchronous; acting before the OS has settled races the very state
        // the test is about.
        runBlocking {
            withTimeoutOrNull(OFFLINE_TIMEOUT_MS) { while (hasNetwork()) delay(POLL_MS) }
        }
    }

    /**
     * Both radios back on. Not politeness: test-class order is not guaranteed, so a leaked offline
     * device makes every later test fail for a reason nowhere near the code that looks broken.
     */
    fun goOnline() {
        shell("svc wifi enable")
        shell("svc data enable")
    }

    /** What the app itself consults, so a test can assert the device really is offline. */
    fun hasNetwork(): Boolean {
        val context: Context = instrumentation.targetContext
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private const val OFFLINE_TIMEOUT_MS = 15_000L
    private const val POLL_MS = 200L
}
