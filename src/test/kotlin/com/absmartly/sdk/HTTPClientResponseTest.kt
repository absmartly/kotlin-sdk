package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HTTPClientResponseTest {

    @Test
    fun equalsComparesContentByValue() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        val r2 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        assertEquals(r1, r2)
    }

    @Test
    fun equalsReturnsFalseForDifferentContent() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        val r2 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(4, 5, 6))
        assertNotEquals(r1, r2)
    }

    @Test
    fun equalsReturnsFalseForDifferentStatusCode() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        val r2 = HTTPClient.Response(404, "OK", "text/plain", byteArrayOf(1, 2, 3))
        assertNotEquals(r1, r2)
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        val r2 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentContent() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        val r2 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(4, 5, 6))
        assertNotEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun equalsSameInstanceReturnsTrue() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        assertTrue(r1.equals(r1))
    }

    @Test
    fun equalsNullReturnsFalse() {
        val r1 = HTTPClient.Response(200, "OK", "text/plain", byteArrayOf(1, 2, 3))
        assertNotEquals<Any?>(r1, null)
    }
}
