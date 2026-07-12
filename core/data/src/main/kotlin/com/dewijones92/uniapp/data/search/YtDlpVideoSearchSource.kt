package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.VideoSearchResult
import com.dewijones92.uniapp.ytdlp.YtDlpEngine

/** Video search through the extraction engine (yt-dlp `ytsearch`). */
public class YtDlpVideoSearchSource(private val engine: YtDlpEngine) : SearchSource {

    override suspend fun search(query: SearchQuery, limit: Int): SearchOutcome =
        when (val result = engine.searchVideos(query.value, limit)) {
            is VideoSearchResult.Failure -> SearchOutcome.Failure(result.detail)
            is VideoSearchResult.Success -> SearchOutcome.Success(
                result.entries.map { entry ->
                    SearchHit.Video(
                        title = entry.title,
                        subtitle = entry.uploader,
                        artworkUrl = entry.thumbnailUrl?.let(HttpUrl::parse),
                        watchUrl = entry.watchUrl,
                        durationSeconds = entry.durationSeconds,
                    )
                },
            )
        }
}
