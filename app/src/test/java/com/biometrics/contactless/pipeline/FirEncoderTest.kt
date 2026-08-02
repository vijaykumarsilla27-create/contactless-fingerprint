package com.biometrics.contactless.pipeline

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Pure JVM unit tests -- no Android framework or OpenCV dependency, since
 * FirEncoder only touches ByteArrays. Run with:
 *   ./gradlew testDebugUnitTest --tests "*.FirEncoderTest"
 * or the green gutter arrow in Android Studio. No emulator/device needed.
 */
class FirEncoderTest {

    private val encoder = FirEncoder()
    private val fakePayload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

    @Test
    fun `record starts with FIR null terminator magic`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        val magic = record.sliceArray(0 until 4)
        assertArrayEquals(byteArrayOf('F'.code.toByte(), 'I'.code.toByte(), 'R'.code.toByte(), 0), magic)
    }

    @Test
    fun `version field is exactly 010 null terminated`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        val version = record.sliceArray(4 until 8)
        assertArrayEquals("010\u0000".toByteArray(Charsets.US_ASCII), version)
    }

    @Test
    fun `record length field matches actual header plus payload size, not the buggy 32-byte value`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        val lengthBytes = record.sliceArray(8 until 12)
        val declaredLength = ByteBuffer.wrap(lengthBytes).int

        // 17-byte header (4 magic + 4 version + 4 length + 2 width + 2 height + 1 depth) + payload.
        val expectedLength = 17 + fakePayload.size
        assertEquals(expectedLength, declaredLength)

        // Explicitly guard against regressing to the reference snippet's
        // literal (buggy) `32 + payload.size` formula.
        assertNotEquals(32 + fakePayload.size, declaredLength)
    }

    @Test
    fun `width and height are encoded big-endian at the correct offsets`() {
        val record = encoder.createFirRecord(fakePayload, width = 256, height = 384)
        val width = ((record[12].toInt() and 0xFF) shl 8) or (record[13].toInt() and 0xFF)
        val height = ((record[14].toInt() and 0xFF) shl 8) or (record[15].toInt() and 0xFF)
        assertEquals(256, width)
        assertEquals(384, height)
    }

    @Test
    fun `bit depth byte is 8`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        assertEquals(8, record[16].toInt())
    }

    @Test
    fun `payload is appended immediately after the 17-byte header`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        val payloadSection = record.sliceArray(17 until record.size)
        assertArrayEquals(fakePayload, payloadSection)
    }

    @Test
    fun `total record size equals header size plus payload size`() {
        val record = encoder.createFirRecord(fakePayload, 256, 384)
        assertEquals(17 + fakePayload.size, record.size)
    }

    @Test
    fun `extended record includes device id, dpi, and impression type fields`() {
        val record = encoder.createExtendedFirRecord(
            fakePayload, 256, 384,
            captureDeviceId = "TEST-DEVICE",
            dpiEstimate = 500
        )
        // extended header = 17 + 32 (device id) + 2 (dpi) + 1 (impression type) = 52
        val expectedHeaderSize = 52
        assertEquals(expectedHeaderSize + fakePayload.size, record.size)

        val impressionTypeByte = record[expectedHeaderSize - 1]
        assertEquals(FirEncoder.IMPRESSION_TYPE_CONTACTLESS_UNCONSTRAINED, impressionTypeByte)
    }

    @Test
    fun `empty payload still produces a valid header-only record`() {
        val record = encoder.createFirRecord(ByteArray(0), 100, 100)
        assertEquals(17, record.size)
    }
}
