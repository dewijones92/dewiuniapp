package com.dewijones92.uniapp.di.fake

import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine

/** In-memory [AppContainer] for previews and UI tests. */
class FakeAppContainer(
    override val podcastRepository: PodcastRepository = FakePodcastRepository(),
    override val ytDlpEngine: YtDlpEngine = FakeYtDlpEngine(),
    override val playbackController: PlaybackController = FakePlaybackController(),
) : AppContainer
