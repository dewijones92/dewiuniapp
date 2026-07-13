package com.dewijones92.uniapp

import android.app.Application
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.DefaultAppContainer

class UniAppApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Fetch the latest yt-dlp in the background so YouTube-breaking changes
        // self-heal; the download applies on the next start.
        container.refreshExtractorEngine()
    }
}
