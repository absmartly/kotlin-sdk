package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class Murmur3Test {

    private fun stringToBytes(s: String): ByteArray {
        val buf = ByteArray(s.length * 3)
        val len = Buffers.encodeUTF8(buf, 0, s)
        return buf.copyOf(len)
    }

    @Test
    fun digestMatchesKnownHashesSeed0() {
        val testCases = listOf(
            "" to 0x00000000,
            " " to 0x7ef49b98.toInt(),
            "t" to 0xca87df4d.toInt(),
            "te" to 0xedb8ee1b.toInt(),
            "tes" to 0x0bb90e5a,
            "test" to 0xba6bd213.toInt(),
            "testy" to 0x44af8342,
            "testy1" to 0x8a1a243a.toInt(),
            "testy12" to 0x845461b9.toInt(),
            "testy123" to 0x47628ac4,
            "special characters a\u00E7b\u2193c" to 0xbe83b140.toInt(),
            "The quick brown fox jumps over the lazy dog" to 0x2e4ff723,
        )
        for ((input, expected) in testCases) {
            val bytes = stringToBytes(input)
            assertEquals(expected, Murmur3.digest(bytes, 0), "Murmur3 failed for input '$input' with seed 0")
        }
    }

    @Test
    fun digestMatchesKnownHashesSeedDeadbeef() {
        val seed = 0xdeadbeef.toInt()
        val testCases = listOf(
            "" to 0x0de5c6a9,
            " " to 0x25acce43,
            "t" to 0x3b15dcf8,
            "te" to 0xac981332.toInt(),
            "tes" to 0xc1c78dda.toInt(),
            "test" to 0xaa22d41a.toInt(),
            "testy" to 0x84f5f623.toInt(),
            "testy1" to 0x09ed28e9,
            "testy12" to 0x22467835,
            "testy123" to 0xd633060d.toInt(),
            "special characters a\u00E7b\u2193c" to 0xf7fdd8a2.toInt(),
            "The quick brown fox jumps over the lazy dog" to 0x3a7b3f4d,
        )
        for ((input, expected) in testCases) {
            val bytes = stringToBytes(input)
            assertEquals(expected, Murmur3.digest(bytes, seed), "Murmur3 failed for input '$input' with seed 0xdeadbeef")
        }
    }

    @Test
    fun digestMatchesKnownHashesSeed1() {
        val seed = 0x00000001
        val testCases = listOf(
            "" to 0x514e28b7,
            " " to 0x4f0f7132,
            "t" to 0x5db1831e,
            "te" to 0xd248bb2e.toInt(),
            "tes" to 0xd432eb74.toInt(),
            "test" to 0x99c02ae2.toInt(),
            "testy" to 0xc5b2dc1e.toInt(),
            "testy1" to 0x33925ceb,
            "testy12" to 0xd92c9f23.toInt(),
            "testy123" to 0x3bc1712d,
            "special characters a\u00E7b\u2193c" to 0x293327b5,
            "The quick brown fox jumps over the lazy dog" to 0x78e69e27,
        )
        for ((input, expected) in testCases) {
            val bytes = stringToBytes(input)
            assertEquals(expected, Murmur3.digest(bytes, seed), "Murmur3 failed for input '$input' with seed 1")
        }
    }
}
