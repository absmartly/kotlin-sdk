package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun evaluateInOperatorWithStringList() {
        val audience = """{"filter":[{"in":[{"var":"region"},{"value":["NORTH","SOUTH","EAST"]}]}]}"""
        val match = matcher.evaluate(audience, mapOf("region" to "NORTH"))
        assertEquals(true, match?.value, "IN operator should match 'NORTH' in ['NORTH','SOUTH','EAST']")

        val mismatch = matcher.evaluate(audience, mapOf("region" to "WEST"))
        assertEquals(false, mismatch?.value, "IN operator should not match 'WEST' in ['NORTH','SOUTH','EAST']")
    }

    @Test
    fun evaluateInOperatorWithNumericList() {
        val audience = """{"filter": [{"in": [{"var": "count"}, {"value": [1, 5, 10, 100]}]}]}"""
        val match = matcher.evaluate(audience, mapOf("count" to 10))
        assertEquals(true, match?.value, "IN operator should match 10 in [1,5,10,100]")

        val mismatch = matcher.evaluate(audience, mapOf("count" to 7))
        assertEquals(false, mismatch?.value, "IN operator should not match 7 in [1,5,10,100]")
    }

    @Test
    fun evaluateInOperatorViaContextSetAttribute() {
        val om = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val contextDataJson = """{"experiments":[{"id":1,"name":"exp_json_in","iteration":1,"unitType":"session_id","seedHi":3603515,"seedLo":233373850,"split":[0.5,0.5],"trafficSeedHi":449867249,"trafficSeedLo":455443629,"trafficSplit":[0,1],"fullOnVariant":0,"variants":[{"name":"A"},{"name":"B"}],"audience":"{\"filter\":[{\"in\":[{\"var\":\"region\"},{\"value\":[\"NORTH\",\"SOUTH\",\"EAST\"]}]}]}","audienceStrict":false}]}"""
        val contextData = om.readValue(contextDataJson, ContextData::class.java)
        val units = mutableMapOf("session_id" to "e791e240fcd3df7d238cfc285f475e8152fcc0ec")

        val exposures = mutableListOf<Exposure>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                if (type == ContextEventLogger.EventType.Exposure && data is Exposure) {
                    exposures.add(data)
                }
            }
        }

        val context = Context(contextData, units, ContextOptions(), logger)
        context.setAttribute("region", "NORTH")
        val variant = context.getTreatment("exp_json_in")
        assertEquals(1, variant, "Should get variant 1 when audience matches via IN operator")
        assertEquals(1, exposures.size, "Should have exactly 1 exposure")
        assertFalse(exposures[0].audienceMismatch, "audienceMismatch should be false when IN operator matches")
    }

    @Test
    fun evaluateInOperatorViaConvertValue() {
        val om = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val requestJson = """{"data":{"experiments":[{"id":1,"name":"exp_json_in","iteration":1,"unitType":"session_id","seedHi":3603515,"seedLo":233373850,"split":[0.5,0.5],"trafficSeedHi":449867249,"trafficSeedLo":455443629,"trafficSplit":[0,1],"fullOnVariant":0,"applications":[{"name":"website"}],"variants":[{"name":"A","config":null},{"name":"B","config":null}],"audience":"{\"filter\":[{\"in\":[{\"var\":\"region\"},{\"value\":[\"NORTH\",\"SOUTH\",\"EAST\"]}]}]}","audienceStrict":false,"customFieldValues":null}]},"units":{"session_id":"e791e240fcd3df7d238cfc285f475e8152fcc0ec"},"options":{"publishDelay":-1}}"""

        @Suppress("UNCHECKED_CAST")
        val request = om.readValue(requestJson, Map::class.java) as Map<String, Any>

        val contextData = om.convertValue(request["data"], ContextData::class.java)

        val experiment = contextData.experiments[0]
        println("audience field type: ${experiment.audience?.javaClass}")
        println("audience field value: ${experiment.audience}")

        val units = mutableMapOf("session_id" to "e791e240fcd3df7d238cfc285f475e8152fcc0ec")

        val exposures = mutableListOf<Exposure>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                if (type == ContextEventLogger.EventType.Exposure && data is Exposure) {
                    exposures.add(data)
                }
            }
        }

        val context = Context(contextData, units, ContextOptions(publishDelay = -1), logger)
        context.setAttribute("region", "NORTH")
        val variant = context.getTreatment("exp_json_in")
        assertEquals(1, variant, "Should get variant 1 when audience matches via IN operator")
        assertEquals(1, exposures.size, "Should have exactly 1 exposure")
        assertFalse(exposures[0].audienceMismatch, "audienceMismatch should be false when IN operator matches (convertValue path)")
    }

    @Test
    fun evaluateWithOrExpression() {
        val audience = """{"filter":{"or":[{"eq":[{"var":"country"},{"value":"US"}]},{"eq":[{"var":"country"},{"value":"GB"}]}]}}"""
        assertEquals(true, matcher.evaluate(audience, mapOf("country" to "US"))?.value)
        assertEquals(true, matcher.evaluate(audience, mapOf("country" to "GB"))?.value)
        assertEquals(false, matcher.evaluate(audience, mapOf("country" to "DE"))?.value)
    }
}
