package com.dewijones92.uniapp.playback

import android.content.Context
import androidx.core.content.edit
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [VolumeBoostStore] backed by [android.content.SharedPreferences], one entry per source. */
public class SharedPrefsVolumeBoostStore(context: Context) : VolumeBoostStore {

    private val prefs = context.getSharedPreferences("uniapp_volume_boost", Context.MODE_PRIVATE)

    override suspend fun boostFor(source: SourceId): VolumeBoost = withContext(Dispatchers.IO) {
        prefs.getString(source.value, null)
            ?.let { name -> runCatching { VolumeBoost.valueOf(name) }.getOrNull() }
            ?: VolumeBoost.OFF
    }

    override suspend fun save(source: SourceId, boost: VolumeBoost): Unit = withContext(Dispatchers.IO) {
        prefs.edit { putString(source.value, boost.name) }
    }
}
