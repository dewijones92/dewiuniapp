package com.dewijones92.uniapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.AppShell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as UniAppApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            UniAppTheme { AppShell(container) }
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
        lifecycleScope.launch { container.videoPlaybackLauncher.play(url, SHARED_SOURCE) }
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
