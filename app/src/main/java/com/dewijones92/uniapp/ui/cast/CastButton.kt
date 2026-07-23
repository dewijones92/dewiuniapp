package com.dewijones92.uniapp.ui.cast

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.dewijones92.uniapp.R
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * The standard Cast button. It manages its own visibility — invisible until a
 * Cast device is on the network — and renders nothing at all when Google Play
 * Services / the Cast framework aren't available (e.g. a non-GMS device), so it's
 * safe to place unconditionally.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember {
        runCatching { CastContext.getSharedInstance(context) }.isSuccess
    }
    if (!available) return
    AndroidView(
        factory = { ctx ->
            // MediaRouteButton reads AppCompat theme attributes and computes a
            // contrast ratio against the theme's window background — the Compose
            // host theme has a translucent background, which throws ("background
            // can not be translucent"). Give it an AppCompat theme with an opaque
            // background, and fall back to an empty view if Cast still can't init.
            val themed = ContextThemeWrapper(ctx, R.style.Theme_UniApp_Cast)
            runCatching {
                MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(themed, it) }
            }.getOrElse { View(ctx) }
        },
        modifier = modifier,
    )
}
