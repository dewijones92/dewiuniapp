package com.dewijones92.totum.playback

/**
 * How to handle a silent passage — and it is genuinely two different mechanisms.
 *
 * Dewi, 2026-08-04: *"make sure the skip silences thing is as smooth as other apps e.g.
 * antennapod"*. AntennaPod is smooth because it **removes the samples**: the audio simply gets
 * shorter, there is no rate change, nothing to hear at either edge of a gap.
 *
 * Totum could not do that, because it plays video too. Removing samples shortens the audio stream
 * but not the video clock, so the picture falls behind — measured at ~6s over a 20s clip. Speeding
 * up instead retimes audio and video together, so it cannot desync.
 *
 * The mistake was applying the video-safe mechanism to everything. Speeding up is audibly worse: a
 * step from 1x to 4x is heard at both edges of every gap, and each change reconfigures the audio
 * sink, which is heard as a stutter. Podcasts — the overwhelming majority of what skip-silence is
 * used on — paid that cost for a desync they could never have had.
 *
 * So the choice is made by whether there is a picture to keep in sync, not by pillar. A video
 * played in Listen mode still carries a video track, and a podcast with cover art does not.
 */
internal enum class SilenceStrategy {
    /** Leave the audio alone. */
    OFF,

    /**
     * Drop the silent samples, as AntennaPod does. Seamless, and safe only when nothing is being
     * kept in sync with the audio clock.
     */
    REMOVE_SAMPLES,

    /**
     * Play the gap faster. Audible at both edges, but it retimes the picture too, so it is the only
     * option when there is video.
     */
    SPEED_UP,
    ;

    internal companion object {
        /**
         * @param enabled whether the person has asked for skip-silence at all.
         * @param hasVideo whether a video track is being rendered against the audio clock.
         */
        fun of(enabled: Boolean, hasVideo: Boolean): SilenceStrategy = when {
            !enabled -> OFF
            hasVideo -> SPEED_UP
            else -> REMOVE_SAMPLES
        }
    }
}
