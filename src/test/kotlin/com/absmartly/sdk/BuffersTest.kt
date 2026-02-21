package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class BuffersTest {

    @Test
    fun putUInt32AndGetUInt32RoundTrips() {
        val buf = ByteArray(8)
        Buffers.putUInt32(buf, 0, 0x01020304)
        assertEquals(0x01020304, Buffers.getUInt32(buf, 0))

        Buffers.putUInt32(buf, 4, 0xDEADBEEF.toInt())
        assertEquals(0xDEADBEEF.toInt(), Buffers.getUInt32(buf, 4))
    }

    @Test
    fun putUInt32LittleEndian() {
        val buf = ByteArray(4)
        Buffers.putUInt32(buf, 0, 0x04030201)
        assertEquals(0x01, buf[0].toInt() and 0xFF)
        assertEquals(0x02, buf[1].toInt() and 0xFF)
        assertEquals(0x03, buf[2].toInt() and 0xFF)
        assertEquals(0x04, buf[3].toInt() and 0xFF)
    }

    @Test
    fun getUInt24() {
        val buf = byteArrayOf(0x01, 0x02, 0x03)
        assertEquals(0x030201, Buffers.getUInt24(buf, 0))
    }

    @Test
    fun getUInt16() {
        val buf = byteArrayOf(0x01, 0x02)
        assertEquals(0x0201, Buffers.getUInt16(buf, 0))
    }

    @Test
    fun getUInt8() {
        val buf = byteArrayOf(0xFF.toByte())
        assertEquals(0xFF, Buffers.getUInt8(buf, 0))
    }

    @Test
    fun encodeUTF8Ascii() {
        val buf = ByteArray(16)
        val len = Buffers.encodeUTF8(buf, 0, "test")
        assertEquals(4, len)
        assertEquals('t'.code.toByte(), buf[0])
        assertEquals('e'.code.toByte(), buf[1])
        assertEquals('s'.code.toByte(), buf[2])
        assertEquals('t'.code.toByte(), buf[3])
    }

    @Test
    fun encodeUTF8TwoByte() {
        val buf = ByteArray(16)
        val len = Buffers.encodeUTF8(buf, 0, "\u00E7")
        assertEquals(2, len)
    }

    @Test
    fun encodeUTF8ThreeByte() {
        val buf = ByteArray(16)
        val len = Buffers.encodeUTF8(buf, 0, "\u2193")
        assertEquals(3, len)
    }

    @Test
    fun encodeUTF8WithOffset() {
        val buf = ByteArray(16)
        val len = Buffers.encodeUTF8(buf, 5, "ab")
        assertEquals(2, len)
        assertEquals('a'.code.toByte(), buf[5])
        assertEquals('b'.code.toByte(), buf[6])
    }
}
