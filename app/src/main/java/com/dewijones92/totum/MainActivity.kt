package com.dewijones92.totum

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.canonicalWatchUrl
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.AppShell
import kotlinx.coroutines.launch

/**
 * A [FragmentActivity], not a bare `ComponentActivity`, purely so Cast works.
 *
 * `MediaRouteButton` shows its device picker as a **DialogFragment**, so tapping it
 * against a plain ComponentActivity throws `IllegalStateException: The activity must be a
 * subclass of FragmentActivity` and takes the app down. Two crash reports from real use
 * (0.1.143 and 0.1.149) are exactly this, and nothing in a Compose-only app otherwise
 * needs fragments — which is why the requirement is invisible until someone taps Cast.
 */
class MainActivity : FragmentActivity() {

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
        // Logged because this path was completely silent: a shared link that misbehaved left
        // nothing in a report tying the playback to the share (0.1.228).
        Diag.log("share", "shared link -> $url")
        // Consumed, so it plays ONCE. The activity's intent outlives a recreation, and
        // onCreate reads it — so a rotation, a theme change or a process restart replayed
        // the shared video over whatever was playing, days after it was shared.
        setIntent(Intent())
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
        // Canonicalised at the door. The URL becomes this video's identity everywhere —
        // MediaItemId, the resolve cache key, what the queue dedupes on — so a share link's
        // `?si=` tracking parameter would make it a DIFFERENT video from the same one
        // already queued, which is exactly what went wrong.
        return url.takeIf { candidate -> WATCH_MARKERS.any { it in candidate.value } }
            ?.canonicalWatchUrl()
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
