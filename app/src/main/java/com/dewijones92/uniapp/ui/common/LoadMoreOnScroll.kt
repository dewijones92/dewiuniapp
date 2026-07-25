package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Asks for the next page as the list nears its end — written once, for every paged list.
 *
 * Fires while there are still [PREFETCH_ITEMS] rows below the fold, so the next page is
 * usually there before the user reaches the bottom; waiting for the true end makes
 * scrolling stutter on every page boundary. The caller's `loadMore` is expected to be
 * idempotent and self-guarding (the view models are), so a repeat fire is harmless.
 */
@Composable
internal fun LoadMoreOnScrollToEnd(
    listState: LazyListState,
    enabled: Boolean,
    loadMore: () -> Unit,
) {
    val shouldLoad by remember(listState) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            total > 0 && lastVisible >= total - 1 - PREFETCH_ITEMS
        }
    }
    LaunchedEffect(listState, enabled) {
        snapshotFlow { shouldLoad }
            .collect { near -> if (near && enabled) loadMore() }
    }
}

/** Footer spinner for a list that is fetching its next page. */
@Composable
internal fun LoadingMoreFooter(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(FOOTER_SPINNER))
    }
}

private const val PREFETCH_ITEMS = 4
private val FOOTER_SPINNER = 28.dp
