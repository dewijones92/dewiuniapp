package com.dewijones92.totum.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayState

/**
 * Play state for every item that has one, provided once at the app root.
 *
 * Every list in the app shows played/part-way status, and every list renders through
 * one [MediaItemRow] — so this is provided in one place and read as that row's default
 * rather than threaded through ten screens and their view models. Passing it explicitly
 * still works, which is what previews and tests do.
 */
internal val LocalPlayStates = staticCompositionLocalOf<Map<MediaItemId, PlayState>> { emptyMap() }

/** Marks an item played or unplayed; null where no store is wired (previews, tests). */
internal val LocalSetPlayed = staticCompositionLocalOf<((MediaItemId, Boolean) -> Unit)?> { null }
