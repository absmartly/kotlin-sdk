package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonExprCompareTest {

    private val matcher = AudienceMatcher()

    @Test
    fun compareNumbersWork() {
        val audience = """{"filter":[{"gt":[{"var":"age"},{"value":18}]}]}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("age" to 25))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("age" to 10))?.value)
    }

    @Test
    fun compareStringsWork() {
        val audience = """{"filter":[{"gt":[{"var":"name"},{"value":"abc"}]}]}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("name" to "def"))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("name" to "aaa"))?.value)
    }

    @Test
    fun compareBooleanVsListReturnsNull() {
        val audience = """{"filter":[{"gt":[{"value":true},{"value":[1,2,3]}]}]}"""
        val result = matcher.evaluate(audience, emptyMap())
        assertEquals(false, result?.value)
    }

    @Test
    fun compareBooleanVsStringReturnsNull() {
        val audience = """{"filter":[{"gt":[{"value":true},{"value":"abc"}]}]}"""
        val result = matcher.evaluate(audience, emptyMap())
        assertEquals(false, result?.value)
    }

    @Test
    fun compareListVsListReturnsNullInEquality() {
        val audience = """{"filter":[{"eq":[{"value":[1]},{"value":[1]}]}]}"""
        val result = matcher.evaluate(audience, emptyMap())
        assertEquals(false, result?.value)
    }

    @Test
    fun compareListVsListReturnsNullInGreaterThan() {
        val audience = """{"filter":[{"gt":[{"value":[1]},{"value":[1]}]}]}"""
        val result = matcher.evaluate(audience, emptyMap())
        assertEquals(false, result?.value)
    }

    @Test
    fun compareNumericStringsAsNumbers() {
        val audience = """{"filter":[{"gt":[{"var":"count"},{"value":"5"}]}]}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("count" to 10))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("count" to 3))?.value)
    }

    @Test
    fun compareBooleanVsBooleanAsNumbers() {
        val audience = """{"filter":[{"eq":[{"var":"flag"},{"value":true}]}]}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("flag" to true))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("flag" to false))?.value)
    }
}
