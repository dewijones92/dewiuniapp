package com.dewijones92.uniapp.di

import android.content.Context
import com.dewijones92.uniapp.data.podcast.DefaultPodcastRepository
import com.dewijones92.uniapp.data.podcast.OkHttpFeedFetcher
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.database.RoomPodcastStore
import com.dewijones92.uniapp.database.UniAppDatabase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's dependency graph. Manual DI: construction is code, errors are compile-time. */
interface AppContainer {
    val podcastRepository: PodcastRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val database: UniAppDatabase = UniAppDatabase.build(context)

    override val podcastRepository: PodcastRepository by lazy {
        DefaultPodcastRepository(
            fetcher = OkHttpFeedFetcher(httpClient),
            store = RoomPodcastStore(database.podcastDao()),
        )
    }

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 20L
    }
}
