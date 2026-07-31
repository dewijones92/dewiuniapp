package com.dewijones92.totum.sabr

/**
 * The body of a SABR media request.
 *
 * **Proven minimal**: on 2026-07-31 a POST to `serverAbrStreamingUrl` carrying nothing but
 * [ustreamerConfig] returned 212246 bytes of UMP containing WebM and fMP4 initialisation
 * segments and `moof` fragments for audio and video at once. Sending an EMPTY body instead
 * answers `RELOAD_PLAYER_RESPONSE: sabr.malformed_config`, which is how the required field was
 * identified in the first place.
 *
 * Field numbers are from the reverse-engineered schema; only the ones we have a use for are
 * here, and each is named for what it does rather than for its number.
 */
public class VideoPlaybackAbrRequest(
    /**
     * `playerConfig.mediaCommonConfig.mediaUstreamerRequestConfig.videoPlaybackUstreamerConfig`
     * from the player response, base64url-DECODED. It is the one field the server insists on.
     */
    private val ustreamerConfig: ByteArray,
    /** Where playback is, in milliseconds — what the server picks the next segments around. */
    private val playerTimeMs: Long? = null,
) {
    public fun encode(): ByteArray {
        var body = ByteArray(0)
        if (playerTimeMs != null) body += Protobuf.number(FIELD_PLAYER_TIME_MS, playerTimeMs)
        body += Protobuf.bytes(FIELD_USTREAMER_CONFIG, ustreamerConfig)
        return body
    }

    private companion object {
        const val FIELD_PLAYER_TIME_MS = 4
        const val FIELD_USTREAMER_CONFIG = 5
    }
}
