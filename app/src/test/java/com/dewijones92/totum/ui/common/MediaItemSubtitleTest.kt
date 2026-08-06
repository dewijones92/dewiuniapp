package com.dewijones92.totum.ui.common

import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.formatViewCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The line under every video title, everywhere: "author · views · date".
 *
 * Dewi, 2026-08-06: *"i want things like videoviews, datestuff, datepublished always visible
 * whenever videos are listed"*. Every list already routes through this one function, and so — as of
 * the same day — does the video page, so this is the single place that decides what those facts look
 * like anywhere in the app.
 *
 * Testable on the JVM only because `@Composable` came off both functions: neither ever called
 * anything composable, and the annotation was the only thing holding the app's most-seen piece of
 * formatting behind an instrumented test.
 */
class MediaItemSubtitleTest {

    private fun video(
        author: String? = "Novara Media",
        viewsText: String? = "1.2M views",
        publishedText: String? = "2 days ago",
        publishedAt: Instant? = null,
    ) = MediaItem(
        id = MediaItemId("abc"),
        sourceId = SourceId("s"),
        title = "a video",
        publishedAt = publishedAt,
        publishedText = publishedText,
        duration = null,
        author = author,
        viewsText = viewsText,
    )

    @Test
    fun `a video shows author then views then date, in that order`() {
        assertEquals("Novara Media · 1.2M views · 2 days ago", mediaItemSubtitle(video()))
    }

    /**
     * Order is not cosmetic: this line is the part that truncates, so the least essential fact goes
     * last. Duration is deliberately absent — it rides on the thumbnail where an ellipsis cannot
     * reach it.
     */
    @Test
    fun `views come before the date so a truncation loses the date first`() {
        val subtitle = mediaItemSubtitle(video())!!
        assertTrue(subtitle.indexOf("1.2M views") < subtitle.indexOf("2 days ago"))
    }

    @Test
    fun `a missing view count leaves no empty separator`() {
        assertEquals("Novara Media · 2 days ago", mediaItemSubtitle(video(viewsText = null)))
    }

    @Test
    fun `a video with nothing to say has no subtitle at all rather than a blank line`() {
        assertNull(mediaItemSubtitle(video(author = null, viewsText = null, publishedText = null)))
    }

    // ---- the date rule ---------------------------------------------------------------------------

    /**
     * The source's own wording wins. YouTube says "2 days ago" and deriving that from a timestamp
     * would drift from the site — and would be wrong about a video published in a different zone.
     */
    @Test
    fun `the sources own relative wording is preferred over a timestamp`() {
        assertEquals("2 days ago", mediaDateText("2 days ago", Instant.parse("2020-01-01T00:00:00Z")))
    }

    /** Podcasts give an absolute date and no wording, which is the other half of the same line. */
    @Test
    fun `an absolute date is formatted when there is no wording`() {
        val at = Instant.parse("2026-08-01T09:00:00Z")
        val expected = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(at)
        assertEquals(expected, mediaDateText(null, at))
    }

    @Test
    fun `no date at all is null rather than a placeholder`() {
        assertNull(mediaDateText(null, null))
    }

    // ---- the two sources of a view count read identically ---------------------------------------

    /**
     * A yt-dlp row gives a NUMBER and an InnerTube row gives TEXT, and both end up in the same list.
     * [formatViewCount] exists so they cannot look like different apps; this pins that they don't.
     */
    @Test
    fun `a counted and a quoted view figure render the same way`() {
        assertEquals(
            mediaItemSubtitle(video(viewsText = "1.2M views")),
            mediaItemSubtitle(video(viewsText = formatViewCount(1_234_567))),
        )
    }

    /** Truncating, never rounding up, so "1M views" is never a lie about a 1,999,999 video. */
    @Test
    fun `a view count never rounds up`() {
        assertEquals("1.9M views", formatViewCount(1_999_999))
        assertEquals("999 views", formatViewCount(999))
        assertEquals("1K views", formatViewCount(1_000))
    }

    // ---- the video page uses the same formatter --------------------------------------------------

    /**
     * The player omits the author because the artist line sits directly above it, and this is the
     * shape it asks for. Pinned here so the page and the row cannot drift apart: they are the same
     * call.
     */
    @Test
    fun `the video page line is the same facts without the repeated author`() {
        assertEquals(
            "1.2M views · 2 days ago",
            mediaSubtitle(author = null, dateText = mediaDateText("2 days ago", null), viewsText = "1.2M views"),
        )
    }

    @Test
    fun `the video page shows nothing when the source said nothing`() {
        assertNull(mediaSubtitle(author = null, dateText = mediaDateText(null, null), viewsText = null))
    }
}
