package com.dewijones92.totum.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.dewijones92.totum.ui.common.LoadMoreOnScrollToEnd
import com.dewijones92.totum.ui.common.LoadingMoreFooter
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Paging must not run away when what arrives is not what shows.
 *
 * Measured on a real account: **80 requests and 1220 videos fetched at launch, to display a
 * single row**, because the "In progress" filter hid everything that arrived.
 *
 * The mechanism is subtler than "the list is short". A list shorter than the viewport is
 * trivially near its end, so the condition is true — but a `snapshotFlow` on a value that
 * STAYS true only emits once. What made it fire again and again is that `enabled` is
 * `canLoadMore && !loadingMore`, which flips false→true on every completed page, restarting
 * the effect, whose fresh collection immediately re-emits `true`. So this models a real
 * screen: an in-flight flag, a footer row, and rows that may or may not appear.
 *
 * Needs a real composition — the whole thing is `LazyListState.layoutInfo` against a real
 * viewport, which no unit test can stand in for.
 */
class LoadMoreRunawayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * @param visiblePerPage rows a page contributes to the list; 0 models a filter that hides
     *  everything arriving.
     */
    private fun pagingList(visiblePerPage: Int): Int {
        var pagesFetched = 0
        composeTestRule.setContent {
            var shown by remember { mutableIntStateOf(1) }
            var loadingMore by remember { mutableStateOf(false) }
            // Exactly the screens' shape: asking sets an in-flight flag, which disables the
            // trigger; the page landing clears it, which re-enables and restarts the effect.
            LaunchedEffect(loadingMore) {
                if (loadingMore) {
                    delay(1)
                    shown += visiblePerPage
                    loadingMore = false
                }
            }
            PagedList(shownCount = shown, loadingMore = loadingMore) {
                // Without a cap an unguarded implementation hangs the test rather than
                // failing it, which reads as a broken test instead of a caught bug.
                if (pagesFetched < RUNAWAY) {
                    pagesFetched++
                    loadingMore = true
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeTestRule.waitForIdle()
        return pagesFetched
    }

    @Test
    fun `a page that adds nothing visible stops the chain`() {
        // One ask is right — the list looked short, so asking was reasonable. A second would
        // mean it had learned nothing from the first.
        assertEquals(1, pagingList(visiblePerPage = 0))
    }

    @Test
    fun `a page that adds rows keeps paging until the screen is full`() {
        val pages = pagingList(visiblePerPage = 3)

        assertTrue("expected several pages, got $pages", pages > 1)
        assertTrue("paged away without stopping: $pages", pages < RUNAWAY)
    }

    @Composable
    private fun PagedList(shownCount: Int, loadingMore: Boolean, loadMore: () -> Unit) {
        val listState = rememberLazyListState()
        LoadMoreOnScrollToEnd(listState, enabled = !loadingMore, shownCount = shownCount, loadMore = loadMore)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(shownCount) { index -> Text("row $index") }
            if (loadingMore) item { LoadingMoreFooter() }
        }
    }

    private companion object {
        /** Far more pages than a screenful needs; reaching it is the bug this test exists for. */
        const val RUNAWAY = 30
        const val SETTLE_MS = 2_000L
    }
}
