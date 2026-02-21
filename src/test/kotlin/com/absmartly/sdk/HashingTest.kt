package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class HashingTest {

    @Test
    fun hashUnitProducesExpectedBase64() {
        val testCases = listOf(
            "e791e240fcd3df7d238cfc285f475e8152fcc0ec" to "pAE3a1i5Drs5mKRNq56adA",
            "123456789" to "JfnnlDI7RTiF9RgfG2JNCw",
            "bleh@absmartly.com" to "IuqYkNRfEx5yClel4j3NbA",
        )
        for ((input, expected) in testCases) {
            val hash = Hashing.hashUnit(input)
            assertEquals(expected, String(hash, Charsets.US_ASCII), "hashUnit failed for '$input'")
        }
    }

    @Test
    fun hashUnitIsDeterministic() {
        val hash1 = Hashing.hashUnit("test_user_123")
        val hash2 = Hashing.hashUnit("test_user_123")
        assertEquals(String(hash1, Charsets.US_ASCII), String(hash2, Charsets.US_ASCII))
    }

    @Test
    fun hashUnitDifferentInputsProduceDifferentHashes() {
        val hash1 = String(Hashing.hashUnit("user_a"), Charsets.US_ASCII)
        val hash2 = String(Hashing.hashUnit("user_b"), Charsets.US_ASCII)
        assert(hash1 != hash2) { "Different inputs should produce different hashes" }
    }
}
