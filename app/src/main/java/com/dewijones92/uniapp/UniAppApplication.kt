package com.dewijones92.uniapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.DefaultAppContainer

class UniAppApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { DefaultAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Fetch the latest yt-dlp in the background so YouTube-breaking changes
        // self-heal; the download applies on the next start.
        container.refreshExtractorEngine()
    }

    /**
     * The one image loader the whole app shares (used by every [MediaThumbnail]).
     * Network fetching goes through OkHttp — the coil-network-okhttp artifact
     * registers it automatically — so thumbnails ride the same HTTP stack the
     * rest of the app uses. Crossfade so images fade in rather than pop.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
}
