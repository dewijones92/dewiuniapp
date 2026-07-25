package com.dewijones92.uniapp.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * User settings, exposed as a [StateFlow] so the UI reacts to changes.
 *
 * The quality values are *caps* (a max height): playback picks the best stream
 * that doesn't exceed the cap for the current network, so mobile data is saved
 * without forcing a lower quality than needed on Wi-Fi. [UNCAPPED] means "best".
 */
interface AppPreferences {
    val settings: StateFlow<Settings>
    fun setWifiMaxHeight(height: Int)
    fun setCellularMaxHeight(height: Int)
    fun setAutoPlayNext(enabled: Boolean)
    fun setAutoDownloadQueue(enabled: Boolean)
    fun setAutoDownloadWifiOnly(enabled: Boolean)

    data class Settings(
        val wifiMaxHeight: Int = DEFAULT_WIFI_MAX_HEIGHT,
        val cellularMaxHeight: Int = DEFAULT_CELLULAR_MAX_HEIGHT,
        /** Whether the queue advances when an item ends. On by default. */
        val autoPlayNext: Boolean = true,
        /** Whether queued items have their audio fetched for offline listening. */
        val autoDownloadQueue: Boolean = true,
        /** Restricts automatic downloads to Wi-Fi, so a long queue can't eat data. */
        val autoDownloadWifiOnly: Boolean = true,
    )

    companion object {
        /** A cap meaning "no limit — pick the best". */
        const val UNCAPPED: Int = Int.MAX_VALUE
        const val DEFAULT_WIFI_MAX_HEIGHT: Int = 1080
        const val DEFAULT_CELLULAR_MAX_HEIGHT: Int = 480
    }
}

/** SharedPreferences-backed [AppPreferences]; settings are tiny, so reads are synchronous. */
class SharedPrefsAppPreferences(context: Context) : AppPreferences {

    private val prefs = context.getSharedPreferences("uniapp_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppPreferences.Settings(
            wifiMaxHeight = prefs.getInt(KEY_WIFI, AppPreferences.DEFAULT_WIFI_MAX_HEIGHT),
            cellularMaxHeight = prefs.getInt(KEY_CELLULAR, AppPreferences.DEFAULT_CELLULAR_MAX_HEIGHT),
            autoPlayNext = prefs.getBoolean(KEY_AUTOPLAY, true),
            autoDownloadQueue = prefs.getBoolean(KEY_AUTO_DOWNLOAD, true),
            autoDownloadWifiOnly = prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true),
        ),
    )
    override val settings: StateFlow<AppPreferences.Settings> = _settings.asStateFlow()

    override fun setWifiMaxHeight(height: Int) {
        prefs.edit { putInt(KEY_WIFI, height) }
        _settings.update { it.copy(wifiMaxHeight = height) }
    }

    override fun setCellularMaxHeight(height: Int) {
        prefs.edit { putInt(KEY_CELLULAR, height) }
        _settings.update { it.copy(cellularMaxHeight = height) }
    }

    override fun setAutoPlayNext(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTOPLAY, enabled) }
        _settings.update { it.copy(autoPlayNext = enabled) }
    }

    override fun setAutoDownloadQueue(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD, enabled) }
        _settings.update { it.copy(autoDownloadQueue = enabled) }
    }

    override fun setAutoDownloadWifiOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled) }
        _settings.update { it.copy(autoDownloadWifiOnly = enabled) }
    }

    private companion object {
        const val KEY_WIFI = "wifi_max_height"
        const val KEY_CELLULAR = "cellular_max_height"
        const val KEY_AUTOPLAY = "auto_play_next"
        const val KEY_AUTO_DOWNLOAD = "auto_download_queue"
        const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi_only"
    }
}

/** In-memory [AppPreferences] for previews and tests. */
class InMemoryAppPreferences : AppPreferences {
    private val _settings = MutableStateFlow(AppPreferences.Settings())
    override val settings: StateFlow<AppPreferences.Settings> = _settings.asStateFlow()
    override fun setWifiMaxHeight(height: Int) = _settings.update { it.copy(wifiMaxHeight = height) }
    override fun setCellularMaxHeight(height: Int) = _settings.update { it.copy(cellularMaxHeight = height) }
    override fun setAutoPlayNext(enabled: Boolean) = _settings.update { it.copy(autoPlayNext = enabled) }
    override fun setAutoDownloadQueue(enabled: Boolean) = _settings.update { it.copy(autoDownloadQueue = enabled) }
    override fun setAutoDownloadWifiOnly(enabled: Boolean) =
        _settings.update { it.copy(autoDownloadWifiOnly = enabled) }
}
