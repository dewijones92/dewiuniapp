package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Keeps the [DownloadManager] on one strategy while letting each pillar bring
 * its own mechanics: the first [routes] entry whose predicate matches wins,
 * otherwise [fallback] handles it. This is the single place pillar-specific
 * routing lives — everything downstream sees one [DownloadStrategy].
 */
public class RoutedDownloadStrategy(
    private val routes: List<Pair<(MediaItem) -> Boolean, DownloadStrategy>>,
    private val fallback: DownloadStrategy,
) : DownloadStrategy {

    override fun download(item: MediaItem, target: File): Flow<DownloadState> =
        (routes.firstOrNull { (matches, _) -> matches(item) }?.second ?: fallback)
            .download(item, target)
}
