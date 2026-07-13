package com.dewijones92.uniapp.innertube.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateCommentParamsTest {

    @Test
    fun `builds the exact param verified to post against a real video`() {
        // Captured live: video UCA2RD4bZ3E -> this token posted successfully.
        assertEquals("EgtVQ0EyUkQ0YlozRQ", CreateCommentParams.forVideo("UCA2RD4bZ3E"))
    }

    @Test
    fun `encodes the video id in protobuf field 2, url-safe and unpadded`() {
        val token = CreateCommentParams.forVideo("abcdefghijk")
        assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(token.decodeToProto()), token)
    }

    // Decodes back to verify the field-2 layout: 0x12, length, then the id bytes.
    private fun String.decodeToProto(): ByteArray {
        val bytes = java.util.Base64.getUrlDecoder().decode(this)
        assertEquals(0x12.toByte(), bytes[0])
        assertEquals("abcdefghijk".length.toByte(), bytes[1])
        assertEquals("abcdefghijk", String(bytes.copyOfRange(2, bytes.size)))
        return bytes
    }
}
