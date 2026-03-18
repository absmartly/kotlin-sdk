package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ContextTest {

    private val units = mutableMapOf(
        "session_id" to "e791e240fcd3df7d238cfc285f475e8152fcc0ec",
        "user_id" to "123456789",
        "email" to "bleh@absmartly.com"
    )

    private val expectedVariants = mapOf(
        "exp_test_ab" to 1,
        "exp_test_abc" to 2,
        "exp_test_not_eligible" to 0,
        "exp_test_fullon" to 2,
        "exp_test_custom_fields" to 1
    )

    private fun createContextData(): ContextData {
        return ContextData(
            experiments = listOf(
                Experiment(
                    id = 1,
                    name = "exp_test_ab",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 3603515,
                    seedLo = 233373850,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 449867249,
                    trafficSeedLo = 455443629,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"banner.border":1,"banner.size":"large"}""")
                    ),
                    audience = null
                ),
                Experiment(
                    id = 2,
                    name = "exp_test_abc",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 55006150,
                    seedLo = 47189152,
                    split = doubleArrayOf(0.34, 0.33, 0.33),
                    trafficSeedHi = 705671872,
                    trafficSeedLo = 212903484,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"button.color":"blue"}"""),
                        ExperimentVariant("C", """{"button.color":"red"}""")
                    ),
                    audience = "",
                    customFieldValues = listOf(
                        CustomFieldValue("country", "string", "US,PT,ES,DE,FR"),
                        CustomFieldValue("json_object", "json", """{"123":1,"456":0}"""),
                        CustomFieldValue("json_array", "json", """["hello", "world"]"""),
                        CustomFieldValue("json_number", "json", "123"),
                        CustomFieldValue("json_string", "json", "\"hello\""),
                        CustomFieldValue("json_boolean", "json", "true"),
                        CustomFieldValue("json_null", "json", "null"),
                        CustomFieldValue("json_invalid", "json", "invalid")
                    )
                ),
                Experiment(
                    id = 3,
                    name = "exp_test_not_eligible",
                    iteration = 1,
                    unitType = "user_id",
                    seedHi = 503266407,
                    seedLo = 144942754,
                    split = doubleArrayOf(0.34, 0.33, 0.33),
                    trafficSeedHi = 87768905,
                    trafficSeedLo = 511357582,
                    trafficSplit = doubleArrayOf(0.99, 0.01),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"card.width":"80%"}"""),
                        ExperimentVariant("C", """{"card.width":"75%"}""")
                    ),
                    audience = "{}"
                ),
                Experiment(
                    id = 4,
                    name = "exp_test_fullon",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 856061641,
                    seedLo = 990838475,
                    split = doubleArrayOf(0.25, 0.25, 0.25, 0.25),
                    trafficSeedHi = 360868579,
                    trafficSeedLo = 330937933,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 2,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"submit.color":"red","submit.shape":"circle"}"""),
                        ExperimentVariant("C", """{"submit.color":"blue","submit.shape":"rect"}"""),
                        ExperimentVariant("D", """{"submit.color":"green","submit.shape":"square"}""")
                    ),
                    audience = "null"
                ),
                Experiment(
                    id = 5,
                    name = "exp_test_custom_fields",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 9372617,
                    seedLo = 121364805,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 318746944,
                    trafficSeedLo = 359812364,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"submit.size":"sm"}""")
                    ),
                    audience = null,
                    customFieldValues = listOf(
                        CustomFieldValue("country", "string", "US,PT,ES"),
                        CustomFieldValue("languages", "string", "en-US,en-GB,pt-PT,pt-BR,es-ES,es-MX"),
                        CustomFieldValue("text_field", "text", "hello text"),
                        CustomFieldValue("string_field", "string", "hello string"),
                        CustomFieldValue("number_field", "number", "123"),
                        CustomFieldValue("boolean_field", "boolean", "true"),
                        CustomFieldValue("false_boolean_field", "boolean", "false"),
                        CustomFieldValue("invalid_type_field", "invalid", "invalid")
                    )
                )
            )
        )
    }

    private fun createRefreshContextData(): ContextData {
        return ContextData(
            experiments = listOf(
                Experiment(
                    id = 6,
                    name = "exp_test_new",
                    iteration = 2,
                    unitType = "session_id",
                    seedHi = 934590467,
                    seedLo = 714771373,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 940553836,
                    trafficSeedLo = 270705624,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 1,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"show-modal":true}""")
                    ),
                    audience = null
                )
            )
        )
    }

    private fun createAudienceContextData(): ContextData {
        return ContextData(
            experiments = listOf(
                Experiment(
                    id = 1,
                    name = "exp_test_ab",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 3603515,
                    seedLo = 233373850,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 449867249,
                    trafficSeedLo = 455443629,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"banner.border":1,"banner.size":"large"}""")
                    ),
                    audience = """{"filter":[{"not":{"var":"returning"}}]}""",
                    audienceStrict = false
                )
            )
        )
    }

    private fun createAudienceStrictContextData(): ContextData {
        return ContextData(
            experiments = listOf(
                Experiment(
                    id = 1,
                    name = "exp_test_ab",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 3603515,
                    seedLo = 233373850,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 449867249,
                    trafficSeedLo = 455443629,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    applications = listOf(ExperimentApplication("website")),
                    variants = listOf(
                        ExperimentVariant("A", null),
                        ExperimentVariant("B", """{"banner.border":1,"banner.size":"large"}""")
                    ),
                    audience = """{"filter":[{"not":{"var":"returning"}}]}""",
                    audienceStrict = true
                )
            )
        )
    }

    private fun createContext(
        data: ContextData = createContextData(),
        units: MutableMap<String, String> = this.units.toMutableMap(),
        options: ContextOptions = ContextOptions(),
        eventLogger: ContextEventLogger? = null
    ): Context {
        return Context(data, units, options, eventLogger)
    }

    private fun createContextWithProvider(
        data: ContextData = createContextData(),
        refreshData: ContextData,
        eventLogger: ContextEventLogger? = null
    ): Context {
        val provider = object : ContextDataProvider {
            override fun getContextData(): java.util.concurrent.CompletableFuture<ContextData> =
                java.util.concurrent.CompletableFuture.completedFuture(refreshData)
        }
        val config = ContextConfig.create().setUnits(units)
        eventLogger?.let { config.setEventLogger(it) }
        return Context.create(config, java.util.concurrent.CompletableFuture.completedFuture(data), provider, null, eventLogger, null)
    }

    // --- State Tests ---

    @Test
    fun contextIsReadyAfterConstruction() {
        val context = createContext()
        assertTrue(context.isReady)
        assertFalse(context.isFailed)
        assertFalse(context.isClosed)
    }

    @Test
    fun pendingCountStartsAtZero() {
        val context = createContext()
        assertEquals(0, context.pendingCount)
    }

    // --- Experiments ---

    @Test
    fun experimentsReturnsExperimentNames() {
        val context = createContext()
        val names = context.experiments
        assertEquals(5, names.size)
        assertTrue(names.contains("exp_test_ab"))
        assertTrue(names.contains("exp_test_abc"))
        assertTrue(names.contains("exp_test_not_eligible"))
        assertTrue(names.contains("exp_test_fullon"))
        assertTrue(names.contains("exp_test_custom_fields"))
    }

    // --- Treatment ---

    @Test
    fun getTreatmentReturnsExpectedVariants() {
        val context = createContext()
        for ((name, variant) in expectedVariants) {
            assertEquals(variant, context.getTreatment(name), "Treatment mismatch for $name")
        }
    }

    @Test
    fun getTreatmentReturnsZeroForUnknownExperiment() {
        val context = createContext()
        assertEquals(0, context.getTreatment("non_existent_experiment"))
    }

    @Test
    fun getTreatmentQueuesExposure() {
        val context = createContext()
        assertEquals(0, context.pendingCount)
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
    }

    @Test
    fun getTreatmentDoesNotQueueDuplicateExposure() {
        val context = createContext()
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
    }

    // --- Peek Treatment ---

    @Test
    fun peekTreatmentReturnsExpectedVariants() {
        val context = createContext()
        for ((name, variant) in expectedVariants) {
            assertEquals(variant, context.peekTreatment(name), "Peek treatment mismatch for $name")
        }
    }

    @Test
    fun peekTreatmentDoesNotQueueExposure() {
        val context = createContext()
        context.peekTreatment("exp_test_ab")
        assertEquals(0, context.pendingCount)
    }

    @Test
    fun peekTreatmentReturnsZeroForUnknownExperiment() {
        val context = createContext()
        assertEquals(0, context.peekTreatment("non_existent_experiment"))
    }

    // --- Variable Value ---

    @Test
    fun getVariableValueReturnsExpectedValues() {
        val context = createContext()
        assertEquals(1, context.getVariableValue("banner.border", 0))
        assertEquals("large", context.getVariableValue("banner.size", "small"))
        assertEquals("red", context.getVariableValue("button.color", "green"))
        assertEquals("blue", context.getVariableValue("submit.color", "white"))
        assertEquals("rect", context.getVariableValue("submit.shape", "circle"))
        assertEquals("sm", context.getVariableValue("submit.size", "lg"))
    }

    @Test
    fun getVariableValueReturnsDefaultForUnknownKey() {
        val context = createContext()
        assertEquals("default_val", context.getVariableValue("unknown_key", "default_val"))
        assertNull(context.getVariableValue("unknown_key", null))
    }

    @Test
    fun getVariableValueQueuesExposure() {
        val context = createContext()
        context.getVariableValue("banner.border", 0)
        assertEquals(1, context.pendingCount)
    }

    @Test
    fun getVariableValueDoesNotQueueExposureForUnknownKey() {
        val context = createContext()
        context.getVariableValue("unknown_key", "default")
        assertEquals(0, context.pendingCount)
    }

    // --- Peek Variable Value ---

    @Test
    fun peekVariableValueReturnsExpectedValues() {
        val context = createContext()
        assertEquals(1, context.peekVariableValue("banner.border", 0))
        assertEquals("large", context.peekVariableValue("banner.size", "small"))
        assertEquals("red", context.peekVariableValue("button.color", "green"))
    }

    @Test
    fun peekVariableValueDoesNotQueueExposure() {
        val context = createContext()
        context.peekVariableValue("banner.border", 0)
        assertEquals(0, context.pendingCount)
    }

    @Test
    fun peekVariableValueReturnsDefaultForUnknownKey() {
        val context = createContext()
        assertEquals("default_val", context.peekVariableValue("unknown_key", "default_val"))
    }

    // --- Variable Keys ---

    @Test
    fun variableKeysReturnsExpectedMapping() {
        val context = createContext()
        val keys = context.variableKeys
        assertTrue(keys.containsKey("banner.border"))
        assertTrue(keys.containsKey("banner.size"))
        assertTrue(keys.containsKey("button.color"))
        assertTrue(keys.containsKey("card.width"))
        assertTrue(keys.containsKey("submit.color"))
        assertTrue(keys.containsKey("submit.shape"))
        assertTrue(keys.containsKey("submit.size"))

        assertEquals(listOf("exp_test_ab"), keys["banner.border"])
        assertEquals(listOf("exp_test_ab"), keys["banner.size"])
        assertEquals(listOf("exp_test_abc"), keys["button.color"])
    }

    // --- Units ---

    @Test
    fun setUnitAddsUnit() {
        val context = createContext(units = mutableMapOf("session_id" to "test_session"))
        assertNull(context.getUnit("user_id"))
        context.setUnit("user_id", "12345")
        assertEquals("12345", context.getUnit("user_id"))
    }

    @Test
    fun setUnitTrimsWhitespace() {
        val context = createContext(units = mutableMapOf())
        context.setUnit("session_id", "  test  ")
        assertEquals("test", context.getUnit("session_id"))
    }

    @Test
    fun setUnitThrowsIfAlreadySetWithDifferentValue() {
        val context = createContext()
        assertFailsWith<IllegalArgumentException> {
            context.setUnit("session_id", "different_session")
        }
    }

    @Test
    fun setUnitAllowsSameValue() {
        val context = createContext()
        context.setUnit("session_id", "e791e240fcd3df7d238cfc285f475e8152fcc0ec")
    }

    @Test
    fun setUnitThrowsOnBlankUid() {
        val context = createContext()
        assertFailsWith<IllegalArgumentException> {
            context.setUnit("new_type", "  ")
        }
    }

    @Test
    fun getUnitReturnsNullForUnknownType() {
        val context = createContext()
        assertNull(context.getUnit("unknown_type"))
    }

    // --- Attributes ---

    @Test
    fun setAndGetAttribute() {
        val context = createContext()
        context.setAttribute("attr1", "value1")
        assertEquals("value1", context.getAttribute("attr1"))
    }

    @Test
    fun getAttributeReturnsLatestValue() {
        val context = createContext()
        context.setAttribute("attr1", "value1")
        context.setAttribute("attr1", "value2")
        assertEquals("value2", context.getAttribute("attr1"))
    }

    @Test
    fun getAttributeReturnsNullForUnknown() {
        val context = createContext()
        assertNull(context.getAttribute("unknown_attr"))
    }

    @Test
    fun setAttributeSupportsNullValue() {
        val context = createContext()
        context.setAttribute("attr1", null)
        assertNull(context.getAttribute("attr1"))
    }

    // --- Overrides ---

    @Test
    fun setOverrideChangesVariant() {
        val context = createContext()
        context.setOverride("exp_test_ab", 0)
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun setOverrideForUnknownExperiment() {
        val context = createContext()
        context.setOverride("non_existent", 3)
        assertEquals(3, context.getTreatment("non_existent"))
    }

    @Test
    fun setOverrideQueuesExposure() {
        val context = createContext()
        context.setOverride("exp_test_ab", 0)
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
    }

    // --- Custom Assignments ---

    @Test
    fun setCustomAssignmentChangesVariant() {
        val context = createContext()
        context.setCustomAssignment("exp_test_ab", 0)
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun setCustomAssignmentDoesNotOverrideFullOn() {
        val context = createContext()
        context.setCustomAssignment("exp_test_fullon", 0)
        assertEquals(2, context.getTreatment("exp_test_fullon"))
    }

    @Test
    fun setCustomAssignmentQueuesExposure() {
        val context = createContext()
        context.setCustomAssignment("exp_test_ab", 0)
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
    }

    // --- Track ---

    @Test
    fun trackIncreasesPendingCount() {
        val context = createContext()
        context.track("goal1", null)
        assertEquals(1, context.pendingCount)
    }

    @Test
    fun trackWithProperties() {
        val context = createContext()
        context.track("purchase", mapOf("amount" to 100, "currency" to "USD"))
        assertEquals(1, context.pendingCount)
    }

    @Test
    fun trackMultipleGoals() {
        val context = createContext()
        context.track("goal1", null)
        context.track("goal2", null)
        context.track("goal3", null)
        assertEquals(3, context.pendingCount)
    }

    // --- Publish ---

    @Test
    fun publishResetsAfterFlush() {
        val context = createContext()
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
        context.publish()
        assertEquals(0, context.pendingCount)
    }

    @Test
    fun publishWithNoPendingIsNoOp() {
        val context = createContext()
        assertEquals(0, context.pendingCount)
        context.publish()
        assertEquals(0, context.pendingCount)
    }

    // --- Close ---

    @Test
    fun closeMarksContextAsClosed() {
        val context = createContext()
        assertFalse(context.isClosed)
        context.close()
        assertTrue(context.isClosed)
    }

    @Test
    fun closeFlushesBeforeClosing() {
        val context = createContext()
        context.getTreatment("exp_test_ab")
        assertEquals(1, context.pendingCount)
        context.close()
        assertEquals(0, context.pendingCount)
        assertTrue(context.isClosed)
    }

    @Test
    fun closedContextReturnsZeroForGetTreatment() {
        val context = createContext()
        context.close()
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun closedContextThrowsOnTrack() {
        val context = createContext()
        context.close()
        assertFailsWith<IllegalStateException> {
            context.track("goal", null)
        }
    }

    @Test
    fun closedContextThrowsOnPublish() {
        val context = createContext()
        context.close()
        assertFailsWith<IllegalStateException> {
            context.publish()
        }
    }

    @Test
    fun closedContextThrowsOnSetAttribute() {
        val context = createContext()
        context.close()
        assertFailsWith<IllegalStateException> {
            context.setAttribute("attr", "value")
        }
    }

    @Test
    fun closedContextThrowsOnSetUnit() {
        val context = createContext()
        context.close()
        assertFailsWith<IllegalStateException> {
            context.setUnit("new_type", "uid")
        }
    }

    @Test
    fun closedContextAllowsSetOverride() {
        val context = createContext()
        context.close()
        context.setOverride("exp", 1)
    }

    @Test
    fun closedContextThrowsOnSetCustomAssignment() {
        val context = createContext()
        context.close()
        assertFailsWith<IllegalStateException> {
            context.setCustomAssignment("exp", 1)
        }
    }

    @Test
    fun doubleCloseIsNoOp() {
        val context = createContext()
        context.close()
        context.close()
        assertTrue(context.isClosed)
    }

    @Test
    fun finalizeIsAliasForClose() {
        val context = createContext()
        assertFalse(context.isFinalized)
        assertFalse(context.isFinalizing)
        @Suppress("DEPRECATION")
        context.finalize()
        assertTrue(context.isFinalized)
        assertTrue(context.isClosed)
    }

    @Test
    fun isFinalizedReflectsClosedState() {
        val context = createContext()
        assertFalse(context.isFinalized)
        context.close()
        assertTrue(context.isFinalized)
    }

    // --- Refresh ---

    @Test
    fun refreshUpdatesData() {
        val refreshData = createRefreshContextData()
        val context = createContextWithProvider(refreshData = refreshData)
        context.refresh().get()
        assertEquals(listOf("exp_test_new"), context.experiments)
    }

    @Test
    fun refreshClearsOldAssignments() {
        val context = createContextWithProvider(refreshData = createRefreshContextData())
        assertEquals(1, context.getTreatment("exp_test_ab"))
        context.refresh().get()
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun refreshNewExperimentTreatment() {
        val refreshData = createRefreshContextData()
        val context = createContextWithProvider(refreshData = refreshData)
        context.refresh().get()
        assertEquals(1, context.getTreatment("exp_test_new"))
    }

    // --- Full-on Experiment ---

    @Test
    fun fullOnExperimentReturnsFullOnVariant() {
        val context = createContext()
        assertEquals(2, context.getTreatment("exp_test_fullon"))
    }

    @Test
    fun fullOnVariableValues() {
        val context = createContext()
        assertEquals("blue", context.getVariableValue("submit.color", "default"))
        assertEquals("rect", context.getVariableValue("submit.shape", "default"))
    }

    // --- Not Eligible ---

    @Test
    fun notEligibleReturnsZero() {
        val context = createContext()
        assertEquals(0, context.getTreatment("exp_test_not_eligible"))
    }

    // --- Custom Field Values ---

    @Test
    fun getCustomFieldValueReturnsStringValue() {
        val context = createContext()
        assertEquals("US,PT,ES,DE,FR", context.getCustomFieldValue("exp_test_abc", "country"))
    }

    @Test
    fun getCustomFieldValueReturnsJsonObjectAsMap() {
        val context = createContext()
        val value = context.getCustomFieldValue("exp_test_abc", "json_object")
        assertTrue(value is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        val map = value as Map<String, Any?>
        assertEquals(1, map["123"])
        assertEquals(0, map["456"])
    }

    @Test
    fun getCustomFieldValueReturnsJsonArray() {
        val context = createContext()
        val value = context.getCustomFieldValue("exp_test_abc", "json_array")
        assertTrue(value is List<*>)
        assertEquals(listOf("hello", "world"), value)
    }

    @Test
    fun getCustomFieldValueReturnsJsonNumber() {
        val context = createContext()
        assertEquals(123, context.getCustomFieldValue("exp_test_abc", "json_number"))
    }

    @Test
    fun getCustomFieldValueReturnsJsonString() {
        val context = createContext()
        assertEquals("hello", context.getCustomFieldValue("exp_test_abc", "json_string"))
    }

    @Test
    fun getCustomFieldValueReturnsJsonBoolean() {
        val context = createContext()
        assertEquals(true, context.getCustomFieldValue("exp_test_abc", "json_boolean"))
    }

    @Test
    fun getCustomFieldValueReturnsJsonNull() {
        val context = createContext()
        assertNull(context.getCustomFieldValue("exp_test_abc", "json_null"))
    }

    @Test
    fun getCustomFieldValueReturnsFallbackForInvalidJson() {
        val context = createContext()
        assertEquals("invalid", context.getCustomFieldValue("exp_test_abc", "json_invalid"))
    }

    @Test
    fun getCustomFieldValueReturnsNumberField() {
        val context = createContext()
        assertEquals(123.0, context.getCustomFieldValue("exp_test_custom_fields", "number_field"))
    }

    @Test
    fun getCustomFieldValueReturnsBooleanField() {
        val context = createContext()
        assertEquals(true, context.getCustomFieldValue("exp_test_custom_fields", "boolean_field"))
        assertEquals(false, context.getCustomFieldValue("exp_test_custom_fields", "false_boolean_field"))
    }

    @Test
    fun getCustomFieldValueReturnsNullForUnknownField() {
        val context = createContext()
        assertNull(context.getCustomFieldValue("exp_test_abc", "unknown_field"))
    }

    @Test
    fun getCustomFieldValueReturnsNullForUnknownExperiment() {
        val context = createContext()
        assertNull(context.getCustomFieldValue("non_existent", "country"))
    }

    @Test
    fun getCustomFieldValueReturnsStringForInvalidType() {
        val context = createContext()
        assertEquals("invalid", context.getCustomFieldValue("exp_test_custom_fields", "invalid_type_field"))
    }

    // --- Custom Field Value Type ---

    @Test
    fun getCustomFieldValueTypeReturnsType() {
        val context = createContext()
        assertEquals("string", context.getCustomFieldValueType("exp_test_abc", "country"))
        assertEquals("json", context.getCustomFieldValueType("exp_test_abc", "json_object"))
        assertEquals("number", context.getCustomFieldValueType("exp_test_custom_fields", "number_field"))
        assertEquals("boolean", context.getCustomFieldValueType("exp_test_custom_fields", "boolean_field"))
    }

    @Test
    fun getCustomFieldValueTypeReturnsNullForUnknown() {
        val context = createContext()
        assertNull(context.getCustomFieldValueType("exp_test_abc", "unknown_field"))
        assertNull(context.getCustomFieldValueType("non_existent", "country"))
    }

    // --- Custom Field Keys ---

    @Test
    fun customFieldKeysReturnsAllFieldNames() {
        val context = createContext()
        val keys = context.customFieldKeys
        assertTrue(keys.contains("country"))
        assertTrue(keys.contains("json_object"))
        assertTrue(keys.contains("languages"))
        assertTrue(keys.contains("number_field"))
        assertTrue(keys.contains("boolean_field"))
    }

    // --- Audience ---

    @Test
    fun audienceMatchReturnsExpectedVariant() {
        val data = createAudienceContextData()
        val context = createContext(data = data)
        context.setAttribute("returning", false)
        assertEquals(1, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun audienceMismatchReturnsExpectedVariantNonStrict() {
        val data = createAudienceContextData()
        val context = createContext(data = data)
        context.setAttribute("returning", true)
        assertEquals(1, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun audienceStrictMismatchReturnsZero() {
        val data = createAudienceStrictContextData()
        val context = createContext(data = data)
        context.setAttribute("returning", true)
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    @Test
    fun audienceStrictMatchReturnsExpectedVariant() {
        val data = createAudienceStrictContextData()
        val context = createContext(data = data)
        context.setAttribute("returning", false)
        assertEquals(1, context.getTreatment("exp_test_ab"))
    }

    // --- Event Logger ---

    @Test
    fun eventLoggerReceivesExposureEvent() {
        val events = mutableListOf<Pair<ContextEventLogger.EventType, Any?>>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                events.add(type to data)
            }
        }
        val context = createContext(eventLogger = logger)
        context.getTreatment("exp_test_ab")
        assertTrue(events.any { it.first == ContextEventLogger.EventType.Exposure })
    }

    @Test
    fun eventLoggerReceivesGoalEvent() {
        val events = mutableListOf<Pair<ContextEventLogger.EventType, Any?>>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                events.add(type to data)
            }
        }
        val context = createContext(eventLogger = logger)
        context.track("test_goal", null)
        assertTrue(events.any { it.first == ContextEventLogger.EventType.Goal })
    }

    @Test
    fun eventLoggerReceivesPublishEvent() {
        val events = mutableListOf<Pair<ContextEventLogger.EventType, Any?>>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                events.add(type to data)
            }
        }
        val context = createContext(eventLogger = logger)
        context.track("test_goal", null)
        context.publish()
        assertTrue(events.any { it.first == ContextEventLogger.EventType.Publish })
    }

    @Test
    fun eventLoggerReceivesCloseEvent() {
        val events = mutableListOf<Pair<ContextEventLogger.EventType, Any?>>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                events.add(type to data)
            }
        }
        val context = createContext(eventLogger = logger)
        context.close()
        assertTrue(events.any { it.first == ContextEventLogger.EventType.Close })
    }

    @Test
    fun eventLoggerReceivesRefreshEvent() {
        val events = mutableListOf<Pair<ContextEventLogger.EventType, Any?>>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                events.add(type to data)
            }
        }
        val context = createContextWithProvider(refreshData = createRefreshContextData(), eventLogger = logger)
        context.refresh().get()
        assertTrue(events.any { it.first == ContextEventLogger.EventType.Refresh })
    }

    @Test
    fun eventLoggerExceptionsDoNotPropagate() {
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                throw RuntimeException("logger error")
            }
        }
        val context = createContext(eventLogger = logger)
        context.getTreatment("exp_test_ab")
        context.track("goal", null)
        context.publish()
        context.close()
    }

    // --- Empty context data ---

    @Test
    fun emptyContextDataReturnsZeroForTreatment() {
        val context = createContext(data = ContextData())
        assertEquals(0, context.getTreatment("any_experiment"))
    }

    @Test
    fun emptyContextDataReturnsDefaultForVariable() {
        val context = createContext(data = ContextData())
        assertEquals("default", context.getVariableValue("any_key", "default"))
    }

    @Test
    fun emptyContextDataReturnsEmptyExperiments() {
        val context = createContext(data = ContextData())
        assertEquals(emptyList(), context.experiments)
    }

    @Test
    fun emptyContextDataReturnsEmptyVariableKeys() {
        val context = createContext(data = ContextData())
        assertEquals(emptyMap(), context.variableKeys)
    }

    // --- Missing unit type ---

    @Test
    fun treatmentReturnsZeroWhenUnitTypeMissing() {
        val context = createContext(units = mutableMapOf())
        assertEquals(0, context.getTreatment("exp_test_ab"))
    }

    // --- Publish error handling ---

    @Test
    fun publishKeepsEventsPendingOnFailure() {
        val failure = RuntimeException("PUBLISH_FAILED")
        val eventHandler = object : ContextPublisher {
            override fun publish(context: Context, event: PublishEvent): java.util.concurrent.CompletableFuture<Void> {
                val future = java.util.concurrent.CompletableFuture<Void>()
                future.completeExceptionally(failure)
                return future
            }
        }

        val config = ContextConfig.create().setUnits(units)
        val dataFuture = java.util.concurrent.CompletableFuture.completedFuture(createContextData())
        val context = Context.create(config, dataFuture, null, eventHandler, null, null)
        context.waitUntilReady()

        context.track("goal1", mapOf("amount" to 125))
        assertEquals(1, context.pendingCount)

        val future = context.publish()
        try {
            future.get()
        } catch (_: Exception) {}

        assertEquals(1, context.pendingCount)
    }

    // --- Override after close ---

    @Test
    fun setOverrideSucceedsAfterClose() {
        val context = createContext()
        context.close()
        assertTrue(context.isClosed)
        context.setOverride("exp_test_ab", 2)
    }

    // --- readyError ---

    @Test
    fun readyErrorReturnsNullOnSuccess() {
        val context = createContext()
        assertNull(context.readyError())
    }

    @Test
    fun readyErrorReturnsExceptionOnDataFailure() {
        val exception = RuntimeException("data failed")
        val f = java.util.concurrent.CompletableFuture<ContextData>()
        f.completeExceptionally(exception)
        val config = ContextConfig.create().setUnits(units)
        val ctx = Context.create(config, f, null, null, null, null)
        ctx.waitUntilReady()
        assertTrue(ctx.isFailed)
        assertNotNull(ctx.readyError())
    }

    // --- isClosing ---

    @Test
    fun isClosingReturnsFalseWhenNotClosing() {
        val context = createContext()
        assertFalse(context.isClosing)
    }

    @Test
    fun isClosingReturnsFalseAfterClose() {
        val context = createContext()
        context.close()
        assertFalse(context.isClosing)
        assertTrue(context.isClosed)
    }

    // --- getUnits ---

    @Test
    fun getUnitsReturnsAllUnits() {
        val context = createContext()
        val result = context.getUnits()
        assertEquals(units["session_id"], result["session_id"])
        assertEquals(units["user_id"], result["user_id"])
        assertEquals(units["email"], result["email"])
    }

    // --- getAttributes ---

    @Test
    fun getAttributesReturnsAllAttributes() {
        val context = createContext()
        context.setAttribute("key1", "val1")
        context.setAttribute("key2", 42)
        val result = context.getAttributes()
        assertEquals("val1", result["key1"])
        assertEquals(42, result["key2"])
    }

    @Test
    fun getAttributesReturnsEmptyMapWhenNoAttributes() {
        val context = createContext(units = mutableMapOf())
        val result = context.getAttributes()
        assertTrue(result.isEmpty())
    }

    // --- setUnits bulk setter ---

    @Test
    fun setUnitsBulkSetsAllUnits() {
        val context = createContext(units = mutableMapOf())
        context.setUnits(mapOf("user_id" to "abc", "email" to "test@test.com"))
        assertEquals("abc", context.getUnit("user_id"))
        assertEquals("test@test.com", context.getUnit("email"))
    }

    // --- setAttributes bulk setter ---

    @Test
    fun setAttributesBulkSetsAllAttributes() {
        val context = createContext()
        context.setAttributes(mapOf("k1" to "v1", "k2" to 99))
        assertEquals("v1", context.getAttribute("k1"))
        assertEquals(99, context.getAttribute("k2"))
    }

    // --- setOverrides bulk setter ---

    @Test
    fun setOverridesBulkSetsAllOverrides() {
        val context = createContext()
        context.setOverrides(mapOf("exp_test_ab" to 2, "exp_test_abc" to 1))
        assertEquals(2, context.getTreatment("exp_test_ab"))
        assertEquals(1, context.getTreatment("exp_test_abc"))
    }

    // --- setCustomAssignments bulk setter ---

    @Test
    fun setCustomAssignmentsBulkSetsAll() {
        val context = createContext()
        context.setCustomAssignments(mapOf("exp_test_ab" to 2))
        assertEquals(2, context.getTreatment("exp_test_ab"))
    }

    // --- Returns defaults when not ready ---

    @Test
    fun returnsDefaultsWhenNotReady() {
        val dataFuture = java.util.concurrent.CompletableFuture<ContextData>()
        val config = ContextConfig.create().setUnits(units)
        val context = Context.create(config, dataFuture, null, null, null, null)
        assertFalse(context.isReady)

        assertEquals(0, context.getTreatment("exp_test_ab"))
        assertEquals(0, context.peekTreatment("exp_test_ab"))
        assertEquals("default", context.getVariableValue("banner.border", "default"))
        assertEquals("default", context.peekVariableValue("banner.border", "default"))
        assertEquals(emptyList<String>(), context.experiments)
        assertEquals(emptyMap<String, List<String>>(), context.variableKeys)
        assertEquals(emptySet<String>(), context.customFieldKeys)
        assertNull(context.getCustomFieldValue("exp_test_ab", "key"))
        assertNull(context.getCustomFieldValueType("exp_test_ab", "key"))
    }

    // --- Returns defaults when closed ---

    @Test
    fun returnsDefaultsWhenClosed() {
        val context = createContext()
        context.close()
        assertTrue(context.isClosed)

        assertEquals(0, context.getTreatment("exp_test_ab"))
        assertEquals(0, context.peekTreatment("exp_test_ab"))
        assertEquals("default", context.getVariableValue("banner.border", "default"))
        assertEquals("default", context.peekVariableValue("banner.border", "default"))
        assertEquals(emptyList<String>(), context.experiments)
        assertEquals(emptyMap<String, List<String>>(), context.variableKeys)
        assertEquals(emptySet<String>(), context.customFieldKeys)
        assertNull(context.getCustomFieldValue("exp_test_ab", "key"))
        assertNull(context.getCustomFieldValueType("exp_test_ab", "key"))
    }
}
