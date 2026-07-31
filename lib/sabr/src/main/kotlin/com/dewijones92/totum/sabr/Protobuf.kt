package com.dewijones92.totum.sabr

/**
 * Just enough protobuf to write a `VideoPlaybackAbrRequest` by hand.
 *
 * Hand-rolled rather than generated, deliberately. The schema is Google's private one: it has
 * no public `.proto` we can depend on, its field names are largely unknown (`field6`,
 * `field21`, `field1000` in the reverse-engineered version), and a code generator plus its
 * runtime would be a build-time dependency and an APK cost for what turns out to be a handful
 * of length-delimited fields.
 *
 * NOT to be confused with [UmpVarint], which sits inches away in the same response and encodes
 * differently — protobuf varints are 7 bits per byte with a continuation flag, UMP's are
 * width-prefixed and little-endian. Mixing them up produces plausible garbage rather than an
 * error, which is exactly the kind of bug that costs a day.
 */
internal object Protobuf {

    private const val WIRE_VARINT = 0
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val CONTINUATION = 0x80
    private const val SEVEN_BITS = 0x7F
    private const val SHIFT = 7

    /** Protobuf packs the field number above the 3-bit wire type. */
    private const val WIRE_TYPE_BITS = 3

    /** Protobuf's own varint: 7 bits per byte, low first, high bit set while more follow. */
    fun varint(value: Long): ByteArray {
        var remaining = value
        val out = ArrayList<Byte>()
        do {
            val chunk = (remaining and SEVEN_BITS.toLong()).toInt()
            remaining = remaining ushr SHIFT
            out += (if (remaining != 0L) chunk or CONTINUATION else chunk).toByte()
        } while (remaining != 0L)
        return out.toByteArray()
    }

    private fun tag(field: Int, wireType: Int) = varint(((field shl WIRE_TYPE_BITS) or wireType).toLong())

    /** A length-delimited field: bytes, a string, or a nested message. */
    fun bytes(field: Int, value: ByteArray): ByteArray =
        tag(field, WIRE_LENGTH_DELIMITED) + varint(value.size.toLong()) + value

    fun number(field: Int, value: Long): ByteArray = tag(field, WIRE_VARINT) + varint(value)
}
