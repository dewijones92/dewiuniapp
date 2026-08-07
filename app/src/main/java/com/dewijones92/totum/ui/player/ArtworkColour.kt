package com.dewijones92.totum.ui.player

/**
 * The one colour that best represents an image — what the player tints itself with.
 *
 * Dewi, 2026-08-07: *"redesign the player screen to be more sexy??????"*, and when asked which
 * option: artwork-derived colour. It is the change that makes a player feel like a product rather
 * than a screen — a Novara episode and a tennis highlight no longer look identical.
 *
 * **Averaging the pixels does not work**, which is the trap this exists to avoid. The mean of any
 * photograph is a muddy grey-brown, so every item would come out the same dull colour and the whole
 * effect would be pointless. What people read as "the colour of this image" is its most *vivid*
 * region, even when that covers a small part of it.
 *
 * So: bucket the pixels coarsely, score each bucket on how much of the image it covers **and** how
 * vivid it is, and take the winner. Deliberately plain arithmetic over `androidx.palette` — it is
 * thirty lines, it is the part worth getting right, and being free of Android types means it can be
 * proven on the JVM rather than on a device.
 */
internal object ArtworkColour {

    /**
     * The representative colour of [pixels] (packed ARGB), or null when there is nothing to say.
     *
     * Null rather than a guess for an image with no usable colour — a black-and-white thumbnail, a
     * fully transparent one, an empty array. The caller falls back to the brand, which is a better
     * answer than a confident grey.
     */
    fun of(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val weight = HashMap<Int, Double>()
        val members = HashMap<Int, IntArray>()

        pixels.forEach { pixel ->
            if ((pixel ushr ALPHA_SHIFT and BYTE) < MIN_ALPHA) return@forEach
            val r = pixel ushr RED_SHIFT and BYTE
            val g = pixel ushr GREEN_SHIFT and BYTE
            val b = pixel and BYTE
            val vividness = vividnessOf(r, g, b) ?: return@forEach
            val key = bucketOf(r, g, b)
            // Coverage AND vividness: a large dull region and a small brilliant one should both be
            // able to win, and neither alone is what the eye picks out.
            weight[key] = (weight[key] ?: 0.0) + vividness
            val sums = members.getOrPut(key) { IntArray(SUM_FIELDS) }
            sums[RED] += r
            sums[GREEN] += g
            sums[BLUE] += b
            sums[COUNT]++
        }

        val best = weight.maxByOrNull { it.value }?.key ?: return null
        val sums = members[best] ?: return null
        val count = sums[COUNT].takeIf { it > 0 } ?: return null
        // The bucket's OWN average, not the bucket's centre: the centre would quantise every result
        // to one of a few dozen colours and two similar thumbnails would come out identical.
        return pack(sums[RED] / count, sums[GREEN] / count, sums[BLUE] / count)
    }

    /**
     * How much a colour counts, or null if it should not count at all.
     *
     * Near-black and near-white are excluded outright: almost every thumbnail has large flat areas
     * of both — letterboxing, sky, a white logo — and they would win on coverage every time while
     * saying nothing about the image. Saturation is squared so a genuinely vivid region outweighs a
     * much larger washed-out one, which is the whole reason an average fails.
     */
    private fun vividnessOf(r: Int, g: Int, b: Int): Double? {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max < MIN_LUMA || min > MAX_LUMA) return null
        val saturation = if (max == 0) 0.0 else (max - min).toDouble() / max
        return SATURATION_FLOOR + saturation * saturation
    }

    private fun bucketOf(r: Int, g: Int, b: Int): Int =
        (r shr BUCKET_SHIFT shl (BUCKET_BITS * 2)) or (g shr BUCKET_SHIFT shl BUCKET_BITS) or (b shr BUCKET_SHIFT)

    private fun pack(r: Int, g: Int, b: Int): Int =
        (BYTE shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b

    private const val BYTE = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Anything more transparent than this is not part of the picture. */
    private const val MIN_ALPHA = 128

    /** Letterboxing, shadow, sky and white logos — large, flat, and not what the image looks like. */
    private const val MIN_LUMA = 24
    private const val MAX_LUMA = 232

    /**
     * Even a grey pixel counts for something, or a genuinely monochrome image would score zero
     * everywhere and return null when it does have a usable tone.
     */
    private const val SATURATION_FLOOR = 0.05

    /** 32 levels per channel: coarse enough to group a gradient, fine enough to keep hues apart. */
    private const val BUCKET_SHIFT = 3
    private const val BUCKET_BITS = 5

    /** Indices into a bucket's running totals. */
    private const val RED = 0
    private const val GREEN = 1
    private const val BLUE = 2
    private const val COUNT = 3
    private const val SUM_FIELDS = 4
}
