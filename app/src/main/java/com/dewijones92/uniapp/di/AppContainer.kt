package com.dewijones92.uniapp.di

import android.content.Context
import com.dewijones92.uniapp.data.download.DefaultDownloadManager
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.download.HttpDownloadStrategy
import com.dewijones92.uniapp.data.net.OkHttpTextFetcher
import com.dewijones92.uniapp.data.podcast.DefaultPodcastRepository
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.search.ItunesPodcastSearchSource
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.data.sponsorblock.SponsorBlockSegmentSource
import com.dewijones92.uniapp.database.RoomDownloadStore
import com.dewijones92.uniapp.database.RoomPodcastStore
import com.dewijones92.uniapp.database.UniAppDatabase
import com.dewijones92.uniapp.playback.Media3PlaybackController
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.chaquopy.ChaquopyYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's dependency graph. Manual DI: construction is code, errors are compile-time. */
interface AppContainer {
    val podcastRepository: PodcastRepository
    val ytDlpEngine: YtDlpEngine
    val playbackController: PlaybackController
    val podcastSearchSource: SearchSource
    val videoSearchSource: SearchSource
    val skipSegmentSource: SkipSegmentSource
    val downloadManager: DownloadManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val database: UniAppDatabase = UniAppDatabase.build(context)

    override val podcastRepository: PodcastRepository by lazy {
        DefaultPodcastRepository(
            fetcher = OkHttpTextFetcher(httpClient),
            store = RoomPodcastStore(database.podcastDao()),
        )
    }

    override val ytDlpEngine: YtDlpEngine by lazy { ChaquopyYtDlpEngine(context) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val playbackController: PlaybackController by lazy {
        Media3PlaybackController(context, applicationScope)
    }

    private val textFetcher by lazy { OkHttpTextFetcher(httpClient) }

    override val podcastSearchSource: SearchSource by lazy {
        ItunesPodcastSearchSource(textFetcher)
    }

    override val videoSearchSource: SearchSource by lazy {
        YtDlpVideoSearchSource(ytDlpEngine)
    }

    override val skipSegmentSource: SkipSegmentSource by lazy {
        SponsorBlockSegmentSource(textFetcher)
    }

    override val downloadManager: DownloadManager by lazy {
        DefaultDownloadManager(
            downloadDir = File(context.filesDir, "downloads"),
            store = RoomDownloadStore(database.downloadDao()),
            strategy = HttpDownloadStrategy(httpClient),
            scope = applicationScope,
        )
    }

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 20L
    }
}
