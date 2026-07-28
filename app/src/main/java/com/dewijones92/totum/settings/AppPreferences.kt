package com.dewijones92.totum.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.data.sponsorblock.SkipCategory
import com.dewijones92.totum.data.sponsorblock.SponsorBlockSegmentSource
import com.dewijones92.totum.domain.MediaFilter
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
/**
 * Whether videos play with the picture. Situational rather than per-item — wanting
 * audio is about what you're doing ("washing up"), not about a particular video — so
 * it's one global mode rather than a property remembered per video.
 */
enum class PlaybackMode {
    /** Video on Wi-Fi, audio on mobile data. The default, and why the data warning is rarely needed. */
    AUTO,

    /** Everything plays audio-only, preferring a downloaded copy. */
    AUDIO,

    /** Videos play with the picture. */
    VIDEO,
}

interface AppPreferences {
    val settings: StateFlow<Settings>
    fun setWifiMaxHeight(height: Int)
    fun setCellularMaxHeight(height: Int)
    fun setAutoPlayNext(enabled: Boolean)
    fun setAutoDownloadQueue(enabled: Boolean)
    fun setAutoDownloadWifiOnly(enabled: Boolean)
    fun setPlaybackMode(mode: PlaybackMode)
    fun setMediaFilter(filter: MediaFilter)

    /** Which SponsorBlock categories are skipped, in playback and in downloads alike. */
    fun setSkipCategories(categories: Set<SkipCategory>)

    data class Settings(
        val wifiMaxHeight: Int = DEFAULT_WIFI_MAX_HEIGHT,
        val cellularMaxHeight: Int = DEFAULT_CELLULAR_MAX_HEIGHT,
        /** Whether the queue advances when an item ends. On by default. */
        val autoPlayNext: Boolean = true,
        /** Whether queued items have their audio fetched for offline listening. */
        val autoDownloadQueue: Boolean = true,
        /** Restricts automatic downloads to Wi-Fi, so a long queue can't eat data. */
        val autoDownloadWifiOnly: Boolean = true,
        val playbackMode: PlaybackMode = PlaybackMode.AUTO,
        /**
         * Which items feeds show, by progress. Global rather than per-feed: "hide what I have
         * finished" is a preference about how you read, not about one subscription, and a
         * per-feed version would need a setting per feed for a choice nobody varies.
         */
        val mediaFilter: MediaFilter = MediaFilter.ALL,
        /** SponsorBlock categories to skip; see SponsorBlockSegmentSource.DEFAULT_CATEGORIES. */
        val skipCategories: Set<SkipCategory> = SponsorBlockSegmentSource.DEFAULT_CATEGORIES,
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

    private val prefs = context.getSharedPreferences("totum_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppPreferences.Settings(
            wifiMaxHeight = prefs.getInt(KEY_WIFI, AppPreferences.DEFAULT_WIFI_MAX_HEIGHT),
            cellularMaxHeight = prefs.getInt(KEY_CELLULAR, AppPreferences.DEFAULT_CELLULAR_MAX_HEIGHT),
            autoPlayNext = prefs.getBoolean(KEY_AUTOPLAY, true),
            autoDownloadQueue = prefs.getBoolean(KEY_AUTO_DOWNLOAD, true),
            autoDownloadWifiOnly = prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true),
            playbackMode = prefs.getString(KEY_PLAYBACK_MODE, null)
                ?.let { name -> runCatching { PlaybackMode.valueOf(name) }.getOrNull() }
                ?: PlaybackMode.AUTO,
            mediaFilter = prefs.getString(KEY_MEDIA_FILTER, null)
                ?.let { name -> runCatching { MediaFilter.valueOf(name) }.getOrNull() }
                ?: MediaFilter.ALL,
            // An unknown id (a preference written by a newer build) is dropped rather
            // than crashing; absent entirely means "never chosen", so use the defaults.
            skipCategories = prefs.getStringSet(KEY_SKIP_CATEGORIES, null)
                ?.mapNotNullTo(mutableSetOf()) { SkipCategory.fromId(it) }
                ?: SponsorBlockSegmentSource.DEFAULT_CATEGORIES,
        ),
    )
    override val settings: StateFlow<AppPreferences.Settings> = _settings.asStateFlow()

    override fun setWifiMaxHeight(height: Int): Unit =
        change("wifiMaxHeight", height, { putInt(KEY_WIFI, height) }) { it.copy(wifiMaxHeight = height) }

    override fun setCellularMaxHeight(height: Int): Unit =
        change("cellularMaxHeight", height, { putInt(KEY_CELLULAR, height) }) {
            it.copy(cellularMaxHeight = height)
        }

    override fun setAutoPlayNext(enabled: Boolean): Unit =
        change("autoPlayNext", enabled, { putBoolean(KEY_AUTOPLAY, enabled) }) { it.copy(autoPlayNext = enabled) }

    override fun setAutoDownloadQueue(enabled: Boolean): Unit =
        change("autoDownloadQueue", enabled, { putBoolean(KEY_AUTO_DOWNLOAD, enabled) }) {
            it.copy(autoDownloadQueue = enabled)
        }

    override fun setAutoDownloadWifiOnly(enabled: Boolean): Unit =
        change("autoDownloadWifiOnly", enabled, { putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled) }) {
            it.copy(autoDownloadWifiOnly = enabled)
        }

    override fun setSkipCategories(categories: Set<SkipCategory>): Unit =
        change("skipCategories", categories.map { it.id }.sorted(), {
            putStringSet(KEY_SKIP_CATEGORIES, categories.mapTo(mutableSetOf()) { it.id })
        }) { it.copy(skipCategories = categories) }

    override fun setPlaybackMode(mode: PlaybackMode): Unit =
        change("playbackMode", mode, { putString(KEY_PLAYBACK_MODE, mode.name) }) {
            it.copy(playbackMode = mode)
        }

    override fun setMediaFilter(filter: MediaFilter): Unit =
        change("mediaFilter", filter, { putString(KEY_MEDIA_FILTER, filter.name) }) {
            it.copy(mediaFilter = filter)
        }

    /**
     * One path for every setting: persist, publish, and record it. A settings change is
     * often the answer to "it started behaving differently" — a report that lists the
     * current values cannot say *when* one of them changed, or that it changed at all.
     */
    private inline fun change(
        name: String,
        value: Any,
        write: SharedPreferences.Editor.() -> Unit,
        update: (AppPreferences.Settings) -> AppPreferences.Settings,
    ) {
        prefs.edit { write() }
        _settings.update(update)
        Diag.log("settings", "$name -> $value")
    }

    private companion object {
        const val KEY_WIFI = "wifi_max_height"
        const val KEY_CELLULAR = "cellular_max_height"
        const val KEY_AUTOPLAY = "auto_play_next"
        const val KEY_AUTO_DOWNLOAD = "auto_download_queue"
        const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi_only"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_MEDIA_FILTER = "media_filter"
        const val KEY_SKIP_CATEGORIES = "skip_categories"
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
    override fun setPlaybackMode(mode: PlaybackMode) = _settings.update { it.copy(playbackMode = mode) }
    override fun setSkipCategories(categories: Set<SkipCategory>) =
        _settings.update { it.copy(skipCategories = categories) }

    override fun setMediaFilter(filter: MediaFilter) = _settings.update { it.copy(mediaFilter = filter) }
}
