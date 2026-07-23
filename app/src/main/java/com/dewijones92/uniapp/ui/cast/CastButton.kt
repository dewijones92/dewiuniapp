package com.dewijones92.uniapp.ui.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
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
            MediaRouteButton(ctx).also { button ->
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx, button) }
            }
        },
        modifier = modifier,
    )
}
