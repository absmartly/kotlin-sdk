package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class MD5Test {

    private fun stringToBytes(s: String): ByteArray {
        val buf = ByteArray(s.length * 3)
        val len = Buffers.encodeUTF8(buf, 0, s)
        return buf.copyOf(len)
    }

    @Test
    fun digestBase64UrlNoPaddingMatchesKnownHashes() {
        val testCases = listOf(
            "" to "1B2M2Y8AsgTpgAmY7PhCfg",
            " " to "chXunH2dwinSkhpA6JnsXw",
            "t" to "41jvpIn1gGLxDdcxa2Vkng",
            "te" to "Vp73JkK-D63XEdakaNaO4Q",
            "tes" to "KLZi2IO212_Zbk3cXpungA",
            "test" to "CY9rzUYh03PK3k6DJie09g",
            "testy" to "K5I_V6RgP8c6sYKz-TVn8g",
            "testy1" to "8fT8xGipOhPkZ2DncKU-1A",
            "testy12" to "YqRAtOz000gIu61ErEH18A",
            "testy123" to "pfV2H07L6WvdqlY0zHuYIw",
            "special characters a\u00E7b\u2193c" to "4PIrO7lKtTxOcj2eMYlG7A",
            "The quick brown fox jumps over the lazy dog" to "nhB9nTcrtoJr2B01QqQZ1g",
            "The quick brown fox jumps over the lazy dog and eats a pie" to "iM-8ECRrLUQzixl436y96A",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum." to "24m7XOq4f5wPzCqzbBicLA",
        )
        for ((input, expected) in testCases) {
            val bytes = stringToBytes(input)
            val result = MD5.digestBase64UrlNoPadding(bytes, 0, bytes.size)
            assertEquals(expected, String(result, Charsets.US_ASCII), "MD5 failed for input '$input'")
        }
    }
}
