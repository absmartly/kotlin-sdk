package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class UTF8EncodingTest {

    @Test
    fun encodeUTF8HandlesAscii() {
        val buf = ByteArray(20)
        val len = Buffers.encodeUTF8(buf, 0, "hello")
        assertEquals(5, len)
        assertEquals("hello", String(buf, 0, len, Charsets.UTF_8))
    }

    @Test
    fun encodeUTF8HandlesTwoByteChars() {
        val buf = ByteArray(20)
        val input = "\u00e9\u00f1"
        val len = Buffers.encodeUTF8(buf, 0, input)
        assertEquals(input, String(buf, 0, len, Charsets.UTF_8))
    }

    @Test
    fun encodeUTF8HandlesThreeByteChars() {
        val buf = ByteArray(20)
        val input = "\u4e16\u754c"
        val len = Buffers.encodeUTF8(buf, 0, input)
        assertEquals(6, len)
        assertEquals(input, String(buf, 0, len, Charsets.UTF_8))
    }

    @Test
    fun encodeUTF8HandlesSurrogatePairs() {
        val buf = ByteArray(20)
        val input = "\uD83D\uDE00"
        val len = Buffers.encodeUTF8(buf, 0, input)
        assertEquals(4, len)
        assertEquals(input, String(buf, 0, len, Charsets.UTF_8))
    }

    @Test
    fun encodeUTF8HandlesOffset() {
        val buf = ByteArray(30)
        buf[0] = 0xFF.toByte()
        val len = Buffers.encodeUTF8(buf, 1, "abc")
        assertEquals(3, len)
        assertEquals(0xFF.toByte(), buf[0])
        assertEquals("abc", String(buf, 1, len, Charsets.UTF_8))
    }

    @Test
    fun hashUnitHandlesCJKCharacters() {
        val hash1 = Hashing.hashUnit("\u4e16\u754c\u4f60\u597d")
        val hash2 = Hashing.hashUnit("\u4e16\u754c\u4f60\u597d")
        assertEquals(String(hash1, Charsets.US_ASCII), String(hash2, Charsets.US_ASCII))
    }

    @Test
    fun hashUnitHandlesEmoji() {
        val hash1 = Hashing.hashUnit("\uD83D\uDE00\uD83D\uDE01")
        val hash2 = Hashing.hashUnit("\uD83D\uDE00\uD83D\uDE01")
        assertEquals(String(hash1, Charsets.US_ASCII), String(hash2, Charsets.US_ASCII))
    }

    @Test
    fun hashUnitHandlesMixedAsciiAndUnicode() {
        val hash = Hashing.hashUnit("user_\u4e16\u754c_123")
        assertEquals(22, hash.size)
    }

    @Test
    fun hashUnitBufferSizeHandlesLargeUnicode() {
        val input = "\u4e16".repeat(500)
        val hash = Hashing.hashUnit(input)
        assertEquals(22, hash.size)
    }
}
