package com.dewijones92.uniapp.innertube.actions

import java.util.Base64

/**
 * Builds the `createCommentParams` token the create-comment endpoint expects.
 * It is a protobuf carrying the target video id in field 2 (length-delimited),
 * base64url-encoded without padding. Verified to post successfully against a
 * real video (2026-07-13).
 */
internal object CreateCommentParams {

    private const val FIELD_2_LEN_DELIMITED = 0x12

    fun forVideo(videoId: String): String {
        val id = videoId.toByteArray(Charsets.UTF_8)
        val proto = ByteArray(id.size + 2)
        proto[0] = FIELD_2_LEN_DELIMITED.toByte()
        proto[1] = id.size.toByte()
        id.copyInto(proto, destinationOffset = 2)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(proto)
    }
}
