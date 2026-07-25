package com.dewijones92.totum.diagnostics

import android.util.Log
import com.dewijones92.totum.common.Diag

/**
 * Sends [Diag] output to logcat under the one `dewidebug` tag. Installed once at startup;
 * this is the only place in the app that touches `android.util.Log` directly.
 */
internal fun installAndroidLogSink() {
    Diag.sink = Diag.Sink { level, tag, message, error ->
        when (level) {
            Diag.Level.INFO -> Log.i(Diag.LOGCAT_TAG, "[$tag] $message")
            Diag.Level.WARN -> Log.w(Diag.LOGCAT_TAG, "[$tag] $message", error)
        }
    }
}
