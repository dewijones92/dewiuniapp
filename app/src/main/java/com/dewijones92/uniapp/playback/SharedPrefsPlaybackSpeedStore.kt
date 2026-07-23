package com.dewijones92.uniapp.playback

import android.content.Context
import androidx.core.content.edit
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [PlaybackSpeedStore] backed by [android.content.SharedPreferences], one entry per source. */
public class SharedPrefsPlaybackSpeedStore(context: Context) : PlaybackSpeedStore {

    private val prefs = context.getSharedPreferences("uniapp_playback_speed", Context.MODE_PRIVATE)

    override suspend fun speedFor(source: SourceId): Float = withContext(Dispatchers.IO) {
        prefs.getFloat(source.value, DEFAULT_PLAYBACK_SPEED)
    }

    override suspend fun save(source: SourceId, speed: Float): Unit = withContext(Dispatchers.IO) {
        prefs.edit { putFloat(source.value, speed) }
    }
}
