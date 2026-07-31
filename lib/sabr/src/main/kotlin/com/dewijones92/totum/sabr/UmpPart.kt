package com.dewijones92.totum.sabr

/**
 * The UMP part types, named because a hex id in a log tells nobody anything.
 *
 * Only a handful matter to a player — [MEDIA_HEADER] says which format and offset the bytes
 * that follow belong to, [MEDIA] carries them, [MEDIA_END] closes a run — but the rest are
 * named so a response can be read at a glance. [SABR_ERROR] and [RELOAD_PLAYER_RESPONSE]
 * carry a machine-readable reason and are how a malformed request announces itself: the first
 * probe of this endpoint answered `RELOAD_PLAYER_RESPONSE: sabr.malformed_config`, which is
 * what pointed at the missing config field.
 */
public object UmpPart {

    public const val ONESIE_HEADER: Int = 10
    public const val ONESIE_DATA: Int = 11
    public const val MEDIA_HEADER: Int = 20
    public const val MEDIA: Int = 21
    public const val MEDIA_END: Int = 22
    public const val LIVE_METADATA: Int = 31
    public const val STREAM_PROTECTION_STATUS: Int = 35
    public const val SABR_ERROR: Int = 42
    public const val SABR_SEEK: Int = 43
    public const val RELOAD_PLAYER_RESPONSE: Int = 44
    public const val SELECTABLE_FORMATS: Int = 49
    public const val REQUEST_CANCELLATION_POLICY: Int = 51
    public const val SABR_CONTEXT_UPDATE: Int = 55
    public const val STREAM_METADATA: Int = 56
    public const val SABR_ACK: Int = 59
    public const val END_OF_TRACK: Int = 60

    private val names = mapOf(
        ONESIE_HEADER to "ONESIE_HEADER",
        ONESIE_DATA to "ONESIE_DATA",
        12 to "ONESIE_ENCRYPTED_MEDIA",
        MEDIA_HEADER to "MEDIA_HEADER",
        MEDIA to "MEDIA",
        MEDIA_END to "MEDIA_END",
        LIVE_METADATA to "LIVE_METADATA",
        STREAM_PROTECTION_STATUS to "STREAM_PROTECTION_STATUS",
        SABR_ERROR to "SABR_ERROR",
        SABR_SEEK to "SABR_SEEK",
        RELOAD_PLAYER_RESPONSE to "RELOAD_PLAYER_RESPONSE",
        45 to "PLAYBACK_START_POLICY",
        46 to "ALLOWED_CACHED_FORMATS",
        47 to "START_BW_SAMPLING_HINT",
        48 to "PAUSE_BW_SAMPLING_HINT",
        SELECTABLE_FORMATS to "SELECTABLE_FORMATS",
        50 to "REQUEST_IDENTIFIER",
        REQUEST_CANCELLATION_POLICY to "REQUEST_CANCELLATION_POLICY",
        53 to "TIMELINE_CONTEXT",
        54 to "REQUEST_PIPELINING",
        SABR_CONTEXT_UPDATE to "SABR_CONTEXT_UPDATE",
        STREAM_METADATA to "STREAM_METADATA",
        57 to "SABR_CONTEXT_SENDING_POLICY",
        58 to "LAWNMOWER_POLICY",
        SABR_ACK to "SABR_ACK",
        END_OF_TRACK to "END_OF_TRACK",
        61 to "CACHE_LOAD_POLICY",
        63 to "PREWARM_CONNECTION",
    )

    public fun nameOf(type: Int): String = names[type] ?: "UNKNOWN_$type"
}
