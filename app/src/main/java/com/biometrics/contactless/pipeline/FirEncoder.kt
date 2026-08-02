package com.biometrics.contactless.pipeline

import java.io.ByteArrayOutputStream

/**
 * Step 5: FIR (Fingerprint Image Record) Template Encoding  (target < 500ms)
 *
 * createFirRecord() matches reference Snippet D's structure and field order
 * exactly: "FIR\0" magic, "010\0" version string, 4-byte record length,
 * width, height, 1-byte bit depth, then the payload.
 *
 * ONE DELIBERATE FIX vs. the literal snippet, flagged here rather than
 * silently carried over: the reference code computes
 * `totalLength = 32 + jp2Data.size`, but the header it actually writes is
 * only 17 bytes (4 magic + 4 version + 4 length + 2 width + 2 height +
 * 1 depth), not 32. Copying that literally would make the Record Length
 * field wrong by 15 bytes on every record, which breaks any downstream
 * parser that trusts that field to seek to the payload start. Fixed to the
 * correct header size below -- worth confirming on the call whether "32"
 * was meant to reserve room for fields that were dropped from this
 * simplified example (which is exactly what the spec's prose header list
 * -- device ID, DPI, impression type -- would fill).
 *
 * FLAG FOR THE WALKTHROUGH CALL -- same pattern as ImageRectifier: Step 5's
 * written spec lists Capture Device ID, Resolution/DPI, and Impression
 * Type (0x09 = Contactless Unconstrained) as required header fields, but
 * none of them appear in Snippet D's actual code. createFirRecord() below
 * matches the example exactly (none of those fields). A separate
 * createExtendedFirRecord() is provided with the full spec-prose field set,
 * NOT called by default -- same opt-in pattern as the rectifier, so you can
 * demo either version depending on what they ask for.
 */
class FirEncoder {

    companion object {
        private const val HEADER_SIZE_BASIC = 4 + 4 + 4 + 2 + 2 + 1 // 17 bytes, not 32
        const val IMPRESSION_TYPE_CONTACTLESS_UNCONSTRAINED: Byte = 0x09
    }

    /**
     * Matches reference Snippet D exactly (field-for-field), with only the
     * record-length arithmetic corrected as documented above.
     */
    fun createFirRecord(jp2Data: ByteArray, width: Int, height: Int): ByteArray {
        val stream = ByteArrayOutputStream()

        // Magic Identifier: "FIR\0"
        stream.write("FIR\u0000".toByteArray(Charsets.US_ASCII))

        // Version Number: "010\0"
        stream.write("010\u0000".toByteArray(Charsets.US_ASCII))

        // Record Length (4 bytes) -- corrected to the true header size, see class doc.
        val totalLength = HEADER_SIZE_BASIC + jp2Data.size
        stream.write(intToByteArray(totalLength))

        // Image Dimensions (Width x Height)
        stream.write(shortToByteArray(width.toShort()))
        stream.write(shortToByteArray(height.toShort()))

        // Image Depth (8 bits grayscale)
        stream.write(8)

        // Image Payload
        stream.write(jp2Data)

        return stream.toByteArray()
    }

    /**
     * OPT-IN extended variant implementing the spec prose's full header
     * field list (device ID, DPI/resolution, impression type) that Snippet
     * D's example omits. Not called by default.
     */
    fun createExtendedFirRecord(
        jp2Data: ByteArray,
        width: Int,
        height: Int,
        captureDeviceId: String = "YSENSE-CONTACTLESS-01",
        dpiEstimate: Int = 500,
        impressionType: Byte = IMPRESSION_TYPE_CONTACTLESS_UNCONSTRAINED
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        stream.write("FIR\u0000".toByteArray(Charsets.US_ASCII))
        stream.write("010\u0000".toByteArray(Charsets.US_ASCII))

        val deviceIdBytes = ByteArray(32)
        val idBytes = captureDeviceId.toByteArray(Charsets.UTF_8)
        System.arraycopy(idBytes, 0, deviceIdBytes, 0, minOf(idBytes.size, 32))

        val extendedHeaderSize = HEADER_SIZE_BASIC + 32 /*device id*/ + 2 /*dpi*/ + 1 /*impression type*/
        val totalLength = extendedHeaderSize + jp2Data.size
        stream.write(intToByteArray(totalLength))

        stream.write(shortToByteArray(width.toShort()))
        stream.write(shortToByteArray(height.toShort()))
        stream.write(8) // bit depth

        stream.write(deviceIdBytes)
        stream.write(shortToByteArray(dpiEstimate.toShort()))
        stream.write(impressionType.toInt())

        stream.write(jp2Data)

        return stream.toByteArray()
    }

    private fun intToByteArray(value: Int): ByteArray =
        byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )

    private fun shortToByteArray(value: Short): ByteArray =
        byteArrayOf(
            (value.toInt() shr 8).toByte(),
            value.toByte()
        )
}
