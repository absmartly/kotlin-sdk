package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssignmentRulesTest {

    private val matcher = AudienceMatcher()

    private fun rules(vararg rule: String): String = """{"rules":[${rule.joinToString(",")}]}"""

    private fun experiment(
        assignmentRules: String?,
        variants: Int = 2,
        fullOnVariant: Int = 0,
        audience: String? = null,
        audienceStrict: Boolean = false,
        trafficSplit: DoubleArray = doubleArrayOf(0.0, 1.0)
    ) = Experiment(
        id = 1,
        name = "exp_rules",
        iteration = 1,
        unitType = "session_id",
        seedHi = 3603515,
        seedLo = 233373850,
        split = DoubleArray(variants) { 1.0 / variants },
        trafficSeedHi = 449867249,
        trafficSeedLo = 455443629,
        trafficSplit = trafficSplit,
        fullOnVariant = fullOnVariant,
        variants = List(variants) { ExperimentVariant("v$it", null) },
        audience = audience,
        audienceStrict = audienceStrict,
        assignmentRules = assignmentRules
    )

    private class Capture : ContextEventLogger {
        val exposures = mutableListOf<Exposure>()
        override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
            if (type == ContextEventLogger.EventType.Exposure && data is Exposure) exposures.add(data)
        }
    }

    private fun context(
        vararg experiments: Experiment,
        units: Map<String, String> = mapOf("session_id" to "e791e240fcd3df7d238cfc285f475e8152fcc0ec"),
        environment: String = "production",
        logger: ContextEventLogger? = null,
        configure: (ContextConfig) -> kotlin.Unit = {}
    ): Context {
        val config = ContextConfig.create().setPublishDelay(-1)
        units.forEach { (k, v) -> config.setUnit(k, v) }
        configure(config)
        val context = Context.create(
            config = config,
            dataFuture = CompletableFuture.completedFuture(ContextData(experiments.toList())),
            dataProvider = null,
            eventHandler = null,
            eventLogger = logger,
            scheduler = null,
            application = "website",
            environment = environment
        )
        context.waitUntilReady()
        return context
    }

    // --- AudienceMatcher.evaluateRules ---

    @Test
    fun firstMatchingRuleWins() {
        val json = rules(
            """{"type":"assign","variant":1,"conditions":{"eq":[{"var":"country"},{"value":"US"}]}}""",
            """{"type":"assign","variant":2}"""
        )
        assertEquals(1, matcher.evaluateRules(json, "production", mapOf("country" to "US"))?.variant)
        assertEquals(2, matcher.evaluateRules(json, "production", mapOf("country" to "DE"))?.variant)
    }

    @Test
    fun environmentsRestrictARule() {
        val json = rules("""{"type":"assign","variant":1,"environments":["staging"]}""")
        assertNull(matcher.evaluateRules(json, "production", emptyMap()))
        assertEquals(1, matcher.evaluateRules(json, "staging", emptyMap())?.variant)
        // An empty list matches every environment; a missing environment never matches a non-empty list.
        assertEquals(1, matcher.evaluateRules(rules("""{"type":"assign","variant":1,"environments":[]}"""), null, emptyMap())?.variant)
        assertNull(matcher.evaluateRules(json, null, emptyMap()))
    }

    @Test
    fun malformedRulesAreSkippedNotFatal() {
        val json = rules(
            """"not an object"""",
            """{"type":"unknown","variant":1}""",
            """{"type":"assign","variant":1.5}""",
            """{"type":"assign","variant":1,"environments":"production"}""",
            """{"type":"assign","variant":1,"conditions":[{"value":true}]}""",
            """{"type":"split","percentages":"NaN/100"}""",
            """{"type":"assign","variant":3}"""
        )
        assertEquals(3, matcher.evaluateRules(json, "production", emptyMap())?.variant)
        assertNull(matcher.evaluateRules("not json", "production", emptyMap()))
        assertNull(matcher.evaluateRules("""{"rules":"nope"}""", "production", emptyMap()))
    }

    @Test
    fun splitPercentagesFollowTheBackendCheck() {
        fun split(p: String) = matcher.evaluateRules(rules("""{"type":"split","percentages":"$p"}"""), null, emptyMap())?.split
        assertContentEquals(doubleArrayOf(0.2, 0.3, 0.5), split("20/30/50"))
        // parseFloat semantics: whitespace and trailing garbage are tolerated, like the JavaScript SDK.
        assertContentEquals(doubleArrayOf(0.5, 0.5), split(" 50/50abc"))
        // A gap of 0.01 is tolerated, more is not.
        assertNotNull(split("33.33/33.33/33.33"))
        assertNull(split("33/33/33"))
        assertNull(split("-10/110"))
        assertNull(split("abc/100"))
    }

    // --- Context ---

    @Test
    fun assignRuleWinsAndIsExposedWithRuleOverride() {
        val logger = Capture()
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}""")), logger = logger)
        assertEquals(1, ctx.getTreatment("exp_rules"))
        val exposure = logger.exposures.single()
        assertTrue(exposure.ruleOverride)
        assertFalse(exposure.assigned)
        assertFalse(exposure.custom)
    }

    @Test
    fun ruleWinsOverStrictAudienceTrafficFullOnAndCustomAssignment() {
        val strictMismatch = experiment(
            rules("""{"type":"assign","variant":1}"""),
            audience = """{"filter":[{"value":false}]}""", audienceStrict = true
        )
        assertEquals(1, context(strictMismatch).peekTreatment("exp_rules"))

        val noTraffic = experiment(rules("""{"type":"assign","variant":1}"""), trafficSplit = doubleArrayOf(1.0, 0.0))
        assertEquals(1, context(noTraffic).peekTreatment("exp_rules"))

        val fullOn = experiment(rules("""{"type":"assign","variant":0}"""), fullOnVariant = 1)
        assertEquals(0, context(fullOn).peekTreatment("exp_rules"))

        val custom = context(experiment(rules("""{"type":"assign","variant":1}"""))) { it.setCustomAssignment("exp_rules", 0) }
        assertEquals(1, custom.peekTreatment("exp_rules"))
    }

    @Test
    fun overrideStillWinsOverARule() {
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}"""))) { it.setOverride("exp_rules", 0) }
        assertEquals(0, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun outOfRangeVariantAndMismatchedSplitAreIgnored() {
        val outOfRange = experiment(rules("""{"type":"assign","variant":5}""", """{"type":"assign","variant":1}"""))
        // The first matching rule decides; an out-of-range variant falls back to normal assignment.
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertEquals(normal, context(outOfRange).peekTreatment("exp_rules"))

        val threeWaySplit = experiment(rules("""{"type":"split","percentages":"20/30/50"}"""))
        assertEquals(normal, context(threeWaySplit).peekTreatment("exp_rules"))
    }

    @Test
    fun splitRuleIsDeterministicPerUnitAndUsesTheExperimentSeed() {
        val exp = experiment(rules("""{"type":"split","percentages":"0/100"}"""))
        assertEquals(1, context(exp).peekTreatment("exp_rules"))
        val even = experiment(rules("""{"type":"split","percentages":"50/50"}"""))
        val seen = (1..40).map { context(even, units = mapOf("session_id" to "user-$it")).peekTreatment("exp_rules") }.toSet()
        assertEquals(setOf(0, 1), seen)
        val again = (1..40).map { context(even, units = mapOf("session_id" to "user-$it")).peekTreatment("exp_rules") }.toSet()
        assertEquals(seen, again)
    }

    @Test
    fun splitWithoutItsUnitUsesFullOnAndIsReRandomizedOnceTheUnitIsSet() {
        val exp = experiment(rules("""{"type":"split","percentages":"0/100"}"""), fullOnVariant = 0)
        val ctx = context(exp, units = emptyMap())
        assertEquals(0, ctx.peekTreatment("exp_rules"))
        ctx.setUnit("session_id", "e791e240fcd3df7d238cfc285f475e8152fcc0ec")
        assertEquals(1, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun exposedSplitWithoutItsUnitStaysPinned() {
        val exp = experiment(rules("""{"type":"split","percentages":"0/100"}"""), fullOnVariant = 0)
        val ctx = context(exp, units = emptyMap())
        assertEquals(0, ctx.getTreatment("exp_rules"))
        ctx.setUnit("session_id", "e791e240fcd3df7d238cfc285f475e8152fcc0ec")
        ctx.setAttribute("unrelated", "x")
        assertEquals(0, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun attributeChangesStartAndStopARuleMatching() {
        val exp = experiment(rules("""{"type":"assign","variant":1,"conditions":{"eq":[{"var":"tier"},{"value":"gold"}]}}"""))
        val ctx = context(exp)
        val normal = ctx.peekTreatment("exp_rules")
        ctx.setAttribute("tier", "gold")
        assertEquals(1, ctx.peekTreatment("exp_rules"))
        ctx.setAttribute("tier", "silver")
        assertEquals(normal, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun unrelatedAttributeDoesNotReExposeARuleAssignment() {
        val logger = Capture()
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}""")), logger = logger)
        ctx.getTreatment("exp_rules")
        ctx.setAttribute("unrelated", "x")
        ctx.getTreatment("exp_rules")
        assertEquals(1, logger.exposures.size)
    }

    @Test
    fun ruleEnvironmentUsesTheConfiguredEnvironment() {
        val exp = experiment(rules("""{"type":"assign","variant":1,"environments":["staging"]}"""), fullOnVariant = 0)
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertEquals(normal, context(exp).peekTreatment("exp_rules"))
        assertEquals(1, context(exp, environment = "staging").peekTreatment("exp_rules"))
    }

    @Test
    fun ruleWonExperimentServesVariables() {
        val exp = experiment(rules("""{"type":"assign","variant":1}""")).apply {
            variants = listOf(ExperimentVariant("A", """{"color":"red"}"""), ExperimentVariant("B", """{"color":"blue"}"""))
        }
        assertEquals("blue", context(exp).peekVariableValue("color", null))
    }

    @Test
    fun refreshRemovingTheExperimentDropsTheRuleVariant() {
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}""")))
        assertEquals(1, ctx.peekTreatment("exp_rules"))
        ctx.setData(ContextData(emptyList()))
        assertEquals(0, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun refreshChangingTheRulesReassigns() {
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}"""), fullOnVariant = 0))
        assertEquals(1, ctx.peekTreatment("exp_rules"))
        ctx.setData(ContextData(listOf(experiment(rules("""{"type":"assign","variant":0}""")))))
        assertEquals(0, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun semverConditionsSelectRules() {
        val json = rules("""{"type":"assign","variant":1,"conditions":{"semver_gte":[{"var":"app_version"},{"value":"2.4.0"}]}}""")
        val exp = experiment(json)
        val ctx = context(exp)
        val normal = ctx.peekTreatment("exp_rules")
        ctx.setAttribute("app_version", "2.3.9")
        assertEquals(normal, ctx.peekTreatment("exp_rules"))
        ctx.setAttribute("app_version", "v2.10.0")
        assertEquals(1, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun objectOrArrayAssignmentRulesAreIgnoredWithoutFailingTheContext() {
        val json = """{"experiments":[{"id":1,"name":"a","assignmentRules":{"rules":[]}},{"id":2,"name":"b","assignmentRules":[1]},{"id":3,"name":"c","assignmentRules":"{\"rules\":[]}"}]}"""
        val data = DefaultContextDataDeserializer().deserialize(json.toByteArray(), 0, json.length)
        assertNotNull(data)
        assertNull(data.experiments[0].assignmentRules)
        assertNull(data.experiments[1].assignmentRules)
        assertEquals("""{"rules":[]}""", data.experiments[2].assignmentRules)
    }

    @Test
    fun ruleOverrideSerializes() {
        val json = String(DefaultContextEventSerializer().serialize(PublishEvent(exposures = listOf(Exposure(ruleOverride = true))))!!)
        assertTrue(json.contains("\"ruleOverride\":true"), json)
        assertNotEquals(-1, json.indexOf("\"audienceMismatch\""))
    }
}
