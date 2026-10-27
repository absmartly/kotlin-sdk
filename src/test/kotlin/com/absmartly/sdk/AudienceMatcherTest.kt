package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudienceMatcherTest {

    private val matcher = AudienceMatcher()

    @Test
    fun evaluateReturnsNullOnEmptyAudience() {
        assertNull(matcher.evaluate("", emptyMap()))
        assertNull(matcher.evaluate("{}", emptyMap()))
        assertNull(matcher.evaluate("null", emptyMap()))
    }

    @Test
    fun evaluateReturnsNullIfFilterNotObjectOrArray() {
        assertNull(matcher.evaluate("{\"filter\":null}", emptyMap()))
        assertNull(matcher.evaluate("{\"filter\":false}", emptyMap()))
        assertNull(matcher.evaluate("{\"filter\":5}", emptyMap()))
        assertNull(matcher.evaluate("{\"filter\":\"a\"}", emptyMap()))
    }

    @Test
    fun evaluateReturnsBooleanForValue() {
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"value\":5}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"value\":true}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"value\":1}]}", emptyMap())?.value)
        assertEquals(false, matcher.evaluate("{\"filter\":[{\"value\":null}]}", emptyMap())?.value)
        assertEquals(false, matcher.evaluate("{\"filter\":[{\"value\":0}]}", emptyMap())?.value)
    }

    @Test
    fun evaluateNotOperatorWithVar() {
        val resultTrue = matcher.evaluate(
            "{\"filter\":[{\"not\":{\"var\":\"returning\"}}]}",
            mapOf("returning" to true)
        )
        assertEquals(false, resultTrue?.value)

        val resultFalse = matcher.evaluate(
            "{\"filter\":[{\"not\":{\"var\":\"returning\"}}]}",
            mapOf("returning" to false)
        )
        assertEquals(true, resultFalse?.value)
    }

    @Test
    fun evaluateWithComplexAndExpression() {
        val audience = """{"filter":[{"gt":[{"var":"age"},{"value":18}]},{"eq":[{"var":"country"},{"value":"US"}]}]}"""
        val match = matcher.evaluate(audience, mapOf("age" to 25, "country" to "US"))
        assertEquals(true, match?.value)

        val mismatch = matcher.evaluate(audience, mapOf("age" to 15, "country" to "US"))
        assertEquals(false, mismatch?.value)
    }

    @Test
    fun evaluateWithOrExpression() {
        val audience = """{"filter":{"or":[{"eq":[{"var":"country"},{"value":"US"}]},{"eq":[{"var":"country"},{"value":"GB"}]}]}}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("country" to "US"))?.value)
        assertEquals(true, matcher.evaluate(audience, mapOf("country" to "GB"))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("country" to "DE"))?.value)
    }

    @Test
    fun numberToStringMatchesJavaScript() {
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"1e+21\"},{\"value\":1e21}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"0\"},{\"value\":-0.0}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"0.1\"},{\"value\":0.1}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"-1.5e+3\"},{\"value\":-1.5e300}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"1e+23\"},{\"value\":1e23}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"1e+3\"},{\"value\":1e30}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"-0\"},{\"value\":-1e-16}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":\"100\"},{\"value\":100}]}]}", emptyMap())?.value)
    }

    @Test
    fun deepEqualityComparesNestedNumbersByValue() {
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":[1]},{\"value\":[1.0]}]}]}", emptyMap())?.value)
        assertEquals(true, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":{\"a\":[2]}},{\"value\":{\"a\":[2.0]}}]}]}", emptyMap())?.value)
        assertEquals(false, matcher.evaluate("{\"filter\":[{\"eq\":[{\"value\":[1]},{\"value\":[2]}]}]}", emptyMap())?.value)
    }
}
