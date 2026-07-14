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

    data class Settings(
        val wifiMaxHeight: Int = DEFAULT_WIFI_MAX_HEIGHT,
        val cellularMaxHeight: Int = DEFAULT_CELLULAR_MAX_HEIGHT,
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

    private companion object {
        const val KEY_WIFI = "wifi_max_height"
        const val KEY_CELLULAR = "cellular_max_height"
    }
}

/** In-memory [AppPreferences] for previews and tests. */
class InMemoryAppPreferences : AppPreferences {
    private val _settings = MutableStateFlow(AppPreferences.Settings())
    override val settings: StateFlow<AppPreferences.Settings> = _settings.asStateFlow()
    override fun setWifiMaxHeight(height: Int) = _settings.update { it.copy(wifiMaxHeight = height) }
    override fun setCellularMaxHeight(height: Int) = _settings.update { it.copy(cellularMaxHeight = height) }
}
