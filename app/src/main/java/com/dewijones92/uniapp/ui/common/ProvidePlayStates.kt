package com.dewijones92.uniapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.launch

/**
 * Makes play state available to every row in the app, from one place — installed around
 * the whole shell in `MainActivity`. This is what lets [MediaItemRow] default to it: no
 * screen, and no view model, has to know that lists show played/part-way status.
 */
@Composable
internal fun ProvidePlayStates(container: AppContainer, content: @Composable () -> Unit) {
    val store = container.playbackProgressStore
    val states by remember(store) { store.observeStates() }.collectAsStateWithLifecycle(emptyMap())
    val scope = rememberCoroutineScope()
    val setPlayed = remember(store, scope) {
        {
                id: MediaItemId, played: Boolean ->
            scope.launch { store.setPlayed(id, played) }
            Unit
        }
    }
    CompositionLocalProvider(
        LocalPlayStates provides states,
        LocalSetPlayed provides setPlayed,
        content = content,
    )
}
