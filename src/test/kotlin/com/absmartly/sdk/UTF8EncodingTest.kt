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
        assertEquals(22, hash1.size)

        // Characters outside the BMP are stored as UTF-16 surrogate pairs and must
        // encode to 4-byte UTF-8; these canonical hashes are shared across all SDKs.
        assertEquals("v2CJG7YcjjWncKOSCzF2GA", String(hash1, Charsets.US_ASCII))
        assertEquals(
            "SCgk4OzXlFMvo1UMsP88fA",
            String(Hashing.hashUnit("user_\u4e16\u754c_123"), Charsets.US_ASCII),
        )
    }

    @Test
    fun hashUnitHandlesEmoji() {
        val hash1 = Hashing.hashUnit("\uD83D\uDE00\uD83D\uDE01")
        val hash2 = Hashing.hashUnit("\uD83D\uDE00\uD83D\uDE01")
        assertEquals(String(hash1, Charsets.US_ASCII), String(hash2, Charsets.US_ASCII))
        assertEquals(22, hash1.size)

        // Characters outside the BMP are stored as UTF-16 surrogate pairs and must
        // encode to 4-byte UTF-8; these canonical hashes are shared across all SDKs.
        assertEquals(
            "KgLqw51xanDs83V5GFkntg",
            String(Hashing.hashUnit("\uD83D\uDE00"), Charsets.US_ASCII),
        )
        assertEquals("ZJuDalvUWRJnVtkspj-2bQ", String(hash1, Charsets.US_ASCII))
    }

    @Test
    fun hashUnitHandlesMixedAsciiAndUnicode() {
        val hash = Hashing.hashUnit("user_\u4e16\u754c_123")
        assertEquals(22, hash.size)
    }

    // Shared cross-SDK vectors (validated against the JavaScript SDK's stringToUint8Array/hashUnit).
    private val vectors = listOf(
        Triple("user😀id", intArrayOf(117, 115, 101, 114, 240, 159, 152, 128, 105, 100), "nnPMxN6wY36LGRnuJ6jZrw"),
        Triple("😀", intArrayOf(240, 159, 152, 128), "KgLqw51xanDs83V5GFkntg"),
        Triple("𠀋", intArrayOf(240, 160, 128, 139), "8mWvgvNGn-DZqb6Ft0HVjA"),
        Triple("a€𝄞z", intArrayOf(97, 226, 130, 172, 240, 157, 132, 158, 122), "AGGq8R9K1XjO77q33izhLg"),
        Triple("🇵🇹", intArrayOf(240, 159, 135, 181, 240, 159, 135, 185), "1RRpVphazpblARsm5DaSkg"),
        // A lone surrogate encodes as U+FFFD (EF BF BD), as TextEncoder does.
        Triple("ab\uD83Dcd", intArrayOf(97, 98, 239, 191, 189, 99, 100), "jvDxBUzS9Tdsml3QQStWSg"),
    )

    @Test
    fun encodeUTF8MatchesCrossSdkVectors() {
        for ((value, expected, _) in vectors) {
            val buf = ByteArray(value.length * 4)
            val len = Buffers.encodeUTF8(buf, 0, value)
            val actual = buf.copyOf(len).map { it.toInt() and 0xff }
            assertEquals(expected.toList(), actual, "utf8 bytes for ${value.map { "%04x".format(it.code) }}")
        }
    }

    @Test
    fun hashUnitMatchesCrossSdkVectors() {
        for ((value, _, hash) in vectors) {
            assertEquals(hash, String(Hashing.hashUnit(value), Charsets.US_ASCII))
        }
    }

    @Test
    fun encodeUTF8ReplacesLoneLowSurrogateAndTrailingHighSurrogate() {
        val buf = ByteArray(32)
        val len = Buffers.encodeUTF8(buf, 0, "a\uDC00b\uD800")
        assertEquals(listOf(97, 239, 191, 189, 98, 239, 191, 189), buf.copyOf(len).map { it.toInt() and 0xff })
    }

    @Test
    fun assignsNonBmpUnitsLikeJavaScript() {
        val split = doubleArrayOf(0.5, 0.5)
        val expected = mapOf("😀" to 1, "🇵🇹" to 0, "a€𝄞z" to 1, "user😀id" to 0)
        for ((unit, variant) in expected) {
            assertEquals(variant, VariantAssigner(Hashing.hashUnit(unit)).assign(split, 3603583, 233373918), unit)
        }
    }

    @Test
    fun hashUnitBufferSizeHandlesLargeUnicode() {
        val input = "\u4e16".repeat(500)
        val hash = Hashing.hashUnit(input)
        assertEquals(22, hash.size)
    }
}
