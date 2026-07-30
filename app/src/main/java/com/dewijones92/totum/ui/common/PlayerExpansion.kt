package com.dewijones92.totum.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the full player. The shell owns that state, so it provides this once and anything
 * beneath can ask — no screen has to route a callback down to the row that needs it.
 *
 * Defaults to doing nothing, which is right for previews and tests: there is no player to
 * open, and a missing shell should not be a crash.
 */
internal val LocalExpandPlayer = staticCompositionLocalOf<() -> Unit> { {} }
