package com.dewijones92.totum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.AppShell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as TotumApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            TotumTheme { AppShell(container) }
        }
        handleShareIntent(intent)
    }

    /** A YouTube link shared to us (share sheet or opened directly) plays here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val url = intent.sharedWatchUrl() ?: return
        // Resolved first so the queue entry carries a real title rather than a URL; a
        // shared link is a deliberate, occasional action, so the extra resolve is cheap.
        lifecycleScope.launch {
            val item = container.videoPlaybackLauncher.describe(url, SHARED_SOURCE) ?: return@launch
            container.playbackQueue.playNow(PlayableItem(item, PlayHandle.Video(url)))
        }
    }

    /** The YouTube watch URL from a VIEW (link) or SEND (share text) intent, if any. */
    private fun Intent.sharedWatchUrl(): HttpUrl? {
        val raw = when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        val match = URL_PATTERN.find(raw)?.value ?: return null
        val url = HttpUrl.parse(match) ?: return null
        return url.takeIf { candidate -> WATCH_MARKERS.any { it in candidate.value } }
    }

    private companion object {
        val SHARED_SOURCE = SourceId("shared")
        val URL_PATTERN = Regex("""https?://\S+""")
        val WATCH_MARKERS = listOf(
            "youtube.com/watch",
            "m.youtube.com/watch",
            "youtu.be/",
            "youtube.com/shorts/",
        )
    }
}
