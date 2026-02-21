package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelsTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun contextDataDefaultValues() {
        val data = ContextData()
        assertEquals(emptyList(), data.experiments)
    }

    @Test
    fun contextDataDeserializesFromJson() {
        val json = """{"experiments":[{"id":1,"name":"test","unitType":"session_id","iteration":1,"seedHi":100,"seedLo":200,"split":[0.5,0.5],"trafficSeedHi":300,"trafficSeedLo":400,"trafficSplit":[0.0,1.0],"fullOnVariant":0,"variants":[{"name":"A"},{"name":"B","config":"{\"key\":\"value\"}"}]}]}"""
        val data = objectMapper.readValue(json, ContextData::class.java)
        assertEquals(1, data.experiments.size)
        assertEquals("test", data.experiments[0].name)
        assertEquals(1, data.experiments[0].id)
        assertEquals("session_id", data.experiments[0].unitType)
        assertEquals(2, data.experiments[0].variants.size)
        assertNull(data.experiments[0].variants[0].config)
        assertEquals("{\"key\":\"value\"}", data.experiments[0].variants[1].config)
    }

    @Test
    fun contextDataIgnoresUnknownProperties() {
        val json = """{"experiments":[],"unknownField":"value"}"""
        val data = objectMapper.readValue(json, ContextData::class.java)
        assertEquals(emptyList(), data.experiments)
    }

    @Test
    fun experimentDefaultValues() {
        val exp = Experiment()
        assertEquals(0, exp.id)
        assertEquals("", exp.name)
        assertNull(exp.unitType)
        assertEquals(0, exp.iteration)
        assertEquals(0, exp.fullOnVariant)
        assertNull(exp.audience)
        assertNull(exp.customFieldValues)
        assertEquals(false, exp.audienceStrict)
    }

    @Test
    fun experimentVariantDefaultValues() {
        val variant = ExperimentVariant()
        assertEquals("", variant.name)
        assertNull(variant.config)
    }

    @Test
    fun customFieldValueDefaultValues() {
        val field = CustomFieldValue()
        assertEquals("", field.name)
        assertNull(field.type)
        assertNull(field.value)
    }

    @Test
    fun exposureDefaultValues() {
        val exposure = Exposure()
        assertEquals(0, exposure.id)
        assertEquals("", exposure.name)
        assertNull(exposure.unit)
        assertEquals(0, exposure.variant)
        assertEquals(false, exposure.assigned)
        assertEquals(false, exposure.eligible)
        assertEquals(false, exposure.overridden)
        assertEquals(false, exposure.fullOn)
        assertEquals(false, exposure.custom)
        assertEquals(false, exposure.audienceMismatch)
    }

    @Test
    fun goalAchievementDefaultValues() {
        val goal = GoalAchievement()
        assertEquals("", goal.name)
        assertEquals(0, goal.achievedAt)
        assertNull(goal.properties)
    }

    @Test
    fun publishEventDefaultValues() {
        val event = PublishEvent()
        assertEquals(false, event.hashed)
        assertNull(event.units)
        assertEquals(0, event.publishedAt)
        assertNull(event.exposures)
        assertNull(event.goals)
        assertNull(event.attributes)
    }

    @Test
    fun unitDefaultValues() {
        val unit = Unit()
        assertEquals("", unit.type)
        assertEquals("", unit.uid)
    }

    @Test
    fun attributeDefaultValues() {
        val attr = Attribute()
        assertEquals("", attr.name)
        assertNull(attr.value)
        assertEquals(0, attr.setAt)
    }

    @Test
    fun contextOptionsDefaultValues() {
        val opts = ContextOptions()
        assertEquals(-1L, opts.publishDelay)
        assertEquals(0L, opts.refreshPeriod)
    }

    @Test
    fun contextOptionsDataClass() {
        val opts1 = ContextOptions(publishDelay = 100, refreshPeriod = 200)
        val opts2 = ContextOptions(publishDelay = 100, refreshPeriod = 200)
        assertEquals(opts1, opts2)
        assertEquals(100L, opts1.publishDelay)
        assertEquals(200L, opts1.refreshPeriod)
    }

    @Test
    fun exposureSerializesToJson() {
        val exposure = Exposure(
            id = 1,
            name = "test",
            unit = "session_id",
            variant = 1,
            exposedAt = 1234567890,
            assigned = true,
            eligible = true,
            overridden = false,
            fullOn = false,
            custom = false,
            audienceMismatch = false
        )
        val json = objectMapper.writeValueAsString(exposure)
        assertTrue(json.contains("\"id\":1"))
        assertTrue(json.contains("\"name\":\"test\""))
        assertTrue(json.contains("\"unit\":\"session_id\""))
        assertTrue(json.contains("\"variant\":1"))
    }

    @Test
    fun goalAchievementSerializesToJson() {
        val goal = GoalAchievement(
            name = "purchase",
            achievedAt = 1234567890,
            properties = mapOf("amount" to 100, "currency" to "USD")
        )
        val json = objectMapper.writeValueAsString(goal)
        assertTrue(json.contains("\"name\":\"purchase\""))
        assertTrue(json.contains("\"amount\":100"))
    }

    @Test
    fun experimentWithCustomFieldsDeserializes() {
        val json = """{"id":1,"name":"test","variants":[],"customFieldValues":[{"name":"country","type":"string","value":"US"}]}"""
        val exp = objectMapper.readValue(json, Experiment::class.java)
        assertEquals(1, exp.customFieldValues?.size)
        assertEquals("country", exp.customFieldValues!![0].name)
        assertEquals("string", exp.customFieldValues!![0].type)
        assertEquals("US", exp.customFieldValues!![0].value)
    }

    @Test
    fun contextDataRoundTrip() {
        val original = ContextData(
            experiments = listOf(
                Experiment(
                    id = 1,
                    name = "test_exp",
                    unitType = "session_id",
                    iteration = 1,
                    seedHi = 100,
                    seedLo = 200,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 300,
                    trafficSeedLo = 400,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"key":"value"}""")
                    )
                )
            )
        )
        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(json, ContextData::class.java)
        assertEquals(1, deserialized.experiments.size)
        assertEquals("test_exp", deserialized.experiments[0].name)
        assertEquals(1, deserialized.experiments[0].id)
        assertEquals(2, deserialized.experiments[0].variants.size)
    }
}
