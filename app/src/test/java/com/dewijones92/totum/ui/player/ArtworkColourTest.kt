package com.dewijones92.totum.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picking the colour the player tints itself with.
 *
 * The whole effect depends on this being right. Get it wrong and every item comes out the same
 * muddy grey — which is exactly what averaging the pixels produces, and why this is not that. The
 * cases below are the specific ways an image can defeat a naive picker: letterboxing, a white logo,
 * a small vivid subject on a dull background.
 *
 * On the JVM, deliberately. The function takes packed ARGB ints and touches no Android type, so the
 * part worth proving is provable without a device.
 */
class ArtworkColourTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun image(vararg runs: Pair<Int, Int>): IntArray =
        runs.flatMap { (colour, count) -> List(count) { colour } }.toIntArray()

    private fun Int.red() = this ushr 16 and 0xFF
    private fun Int.green() = this ushr 8 and 0xFF
    private fun Int.blue() = this and 0xFF

    private val red = argb(220, 30, 30)
    private val teal = argb(20, 150, 150)
    private val black = argb(2, 2, 2)
    private val white = argb(252, 252, 252)
    private val grey = argb(128, 128, 128)

    @Test
    fun `a solid colour is itself`() {
        val picked = ArtworkColour.of(image(red to 100))!!

        assertEquals(220, picked.red())
        assertEquals(30, picked.green())
        assertEquals(30, picked.blue())
    }

    /**
     * THE POINT. A small vivid subject beats a large dull background.
     *
     * An average of this image is a murky olive that appears nowhere in it. What a person would call
     * "the colour of this picture" is the red.
     */
    @Test
    fun `a small vivid area beats a large dull one`() {
        val picked = ArtworkColour.of(image(grey to 900, red to 100))!!

        assertTrue(
            "expected the red to win, got ${picked.red()},${picked.green()},${picked.blue()}",
            picked.red() > 150
        )
        assertTrue(picked.green() < 100)
    }

    /**
     * Letterboxing must not win, and it would win every time on coverage alone.
     *
     * Almost every video thumbnail has black bars; a picker that counted them would return black for
     * most of the library.
     */
    @Test
    fun `letterboxing is ignored`() {
        val picked = ArtworkColour.of(image(black to 2_000, teal to 200))!!

        assertTrue("black bars won: ${picked.red()},${picked.green()},${picked.blue()}", picked.green() > 100)
        assertTrue(picked.blue() > 100)
    }

    /** Same for a big white area — a logo, a sky, a blown-out background. */
    @Test
    fun `a large white area is ignored`() {
        val picked = ArtworkColour.of(image(white to 2_000, red to 200))!!

        assertTrue("white won: ${picked.red()},${picked.green()},${picked.blue()}", picked.red() > 150)
        assertTrue(picked.green() < 100)
    }

    /**
     * Nearly-identical pixels average within their bucket rather than snapping to its centre.
     *
     * Without this two similar thumbnails would come out as literally the same colour, which is the
     * quantised look that gives the whole effect away.
     */
    @Test
    fun `the result is the areas own average, not a quantised bucket centre`() {
        val a = ArtworkColour.of(image(argb(200, 40, 40) to 100))!!
        val b = ArtworkColour.of(image(argb(206, 44, 44) to 100))!!

        assertTrue("two similar images must not collapse to the same colour", a != b)
    }

    @Test
    fun `transparent pixels do not count`() {
        val transparentRed = (0x10 shl 24) or (220 shl 16) or (30 shl 8) or 30
        val picked = ArtworkColour.of(image(transparentRed to 1_000, teal to 50))!!

        assertTrue(picked.green() > 100)
    }

    // ---- when there is nothing to say ------------------------------------------------------------

    @Test
    fun `an empty image has no colour`() {
        assertNull(ArtworkColour.of(IntArray(0)))
    }

    /**
     * Pure black, pure white and full transparency yield null rather than a confident grey — the
     * caller then falls back to the brand, which is a better answer than a wrong one.
     */
    @Test
    fun `an image with nothing usable in it has no colour`() {
        assertNull(ArtworkColour.of(image(black to 100, white to 100)))
        assertNull(ArtworkColour.of(image(((0x00 shl 24) or 0xFFFFFF) to 100)))
    }

    /** A genuinely monochrome photograph still has a usable mid-tone; it is not "nothing usable". */
    @Test
    fun `a greyscale image still yields its mid-tone`() {
        val picked = ArtworkColour.of(image(grey to 100))!!

        assertEquals(128, picked.red())
        assertEquals(picked.red(), picked.blue())
    }

    /** Whatever comes back is opaque: it is used as a background and must not be see-through. */
    @Test
    fun `the colour is always fully opaque`() {
        val picked = ArtworkColour.of(image(red to 10, teal to 10))!!

        assertEquals(0xFF, picked ushr 24 and 0xFF)
    }
}
