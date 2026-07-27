package com.dewijones92.totum.data.sponsorblock

/**
 * A SponsorBlock segment category, and the one place its wire id is written.
 *
 * An enum rather than the raw strings the API takes, so a typo is a compile error and the
 * settings screen can offer the full set without duplicating the list. Which of these are
 * actually skipped is the user's choice — see
 * [SponsorBlockSegmentSource.DEFAULT_CATEGORIES] for what is on by default and why.
 */
public enum class SkipCategory(public val id: String) {
    SPONSOR("sponsor"),
    SELF_PROMO("selfpromo"),
    INTERACTION("interaction"),
    INTRO("intro"),
    OUTRO("outro"),
    PREVIEW("preview"),
    MUSIC_OFFTOPIC("music_offtopic"),
    FILLER("filler"),
    ;

    public companion object {
        /** Null for an unknown id, so a stored preference from a newer build cannot crash an older one. */
        public fun fromId(id: String): SkipCategory? = entries.firstOrNull { it.id == id }
    }
}
