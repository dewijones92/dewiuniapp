package com.dewijones92.totum.playback

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [VolumeBoostStore] backed by [android.content.SharedPreferences] — one value for everything.
 *
 * Old per-source entries are left behind rather than migrated, for the same reason as the speed
 * store: choosing one of them to become the global value would change the level on its own, which
 * is the thing being fixed.
 */
public class SharedPrefsVolumeBoostStore(context: Context) : VolumeBoostStore {

    private val prefs = context.getSharedPreferences("totum_volume_boost", Context.MODE_PRIVATE)

    override suspend fun boost(): VolumeBoost = withContext(Dispatchers.IO) {
        prefs.getString(KEY_BOOST, null)
            ?.let { name -> runCatching { VolumeBoost.valueOf(name) }.getOrNull() }
            ?: VolumeBoost.OFF
    }

    override suspend fun save(boost: VolumeBoost): Unit = withContext(Dispatchers.IO) {
        prefs.edit { putString(KEY_BOOST, boost.name) }
    }

    private companion object {
        /** Not a SourceId, so it cannot collide with the old per-source entries. */
        const val KEY_BOOST = "boost"
    }
}
