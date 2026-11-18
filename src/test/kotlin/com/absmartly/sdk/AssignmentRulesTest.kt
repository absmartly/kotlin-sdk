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
        audienceStrict = audienceStrict
    ).also { it.assignmentRules = assignmentRules }

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

    private class ThrowingMap : AbstractMap<String, Any?>() {
        override val entries: Set<Map.Entry<String, Any?>> get() = emptySet()
        override fun get(key: String): Any? = throw IllegalStateException("boom")
    }

    @Test
    fun conditionThatThrowsSkipsOnlyItsRule() {
        val json = rules(
            """{"type":"assign","variant":1,"conditions":{"eq":[{"var":"bad/x"},{"value":1}]}}""",
            """{"type":"assign","variant":2}"""
        )
        assertEquals(2, matcher.evaluateRules(json, "production", mapOf("bad" to ThrowingMap()))?.variant)
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

    // Reference vector from the JavaScript SDK (VariantAssigner over hashUnit("user-N"), split 0.2/0.3/0.5). Rows
    // with the experiment seed (3603515, 233373850) and the traffic seed (449867249, 455443629) differ, so hashing
    // with the wrong seed, or not deterministically, fails.
    @Test
    fun splitRuleMatchesTheJavaScriptVectorWithTheExperimentSeed() {
        val withExperimentSeed = listOf(2, 2, 1, 0, 2, 2, 1, 2, 2, 0, 2, 0, 2, 0, 1, 2, 2, 1, 2, 2, 2, 2, 2, 0)
        val withTrafficSeed = listOf(0, 1, 2, 1, 2, 2, 1, 1, 2, 0, 0, 2, 1, 1, 1, 0, 2, 2, 0, 2, 1, 1, 2, 1)
        assertNotEquals(withExperimentSeed, withTrafficSeed)
        val exp = experiment(rules("""{"type":"split","percentages":"20/30/50"}"""), variants = 3)
        val contexts = (1..24).map { context(exp, units = mapOf("session_id" to "user-$it")) }
        repeat(2) {
            assertEquals(withExperimentSeed, contexts.map { it.peekTreatment("exp_rules") })
        }
        assertEquals(withExperimentSeed, (1..24).map { context(exp, units = mapOf("session_id" to "user-$it")).peekTreatment("exp_rules") })
    }

    @Test
    fun splitRuleAllOnOneVariant() {
        assertEquals(1, context(experiment(rules("""{"type":"split","percentages":"0/100"}"""))).peekTreatment("exp_rules"))
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

    // Rule scope is the configured environment: a user "environment" attribute feeds conditions, never the scope.
    @Test
    fun ruleScopeUsesTheConfiguredEnvironmentNotTheUserAttribute() {
        val stagingCondition = """"conditions":{"eq":[{"var":"environment"},{"value":"staging"}]}"""
        val productionScope = experiment(rules("""{"type":"assign","variant":0,"environments":["production"],$stagingCondition}"""))
        val stagingScope = experiment(rules("""{"type":"assign","variant":0,"environments":["staging"],$stagingCondition}"""))
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)

        val production = context(productionScope) { it.setAttribute("environment", "staging") }
        assertEquals(0, production.peekTreatment("exp_rules"))
        val staging = context(stagingScope) { it.setAttribute("environment", "staging") }
        assertEquals(normal, staging.peekTreatment("exp_rules"))
    }

    @Test
    fun scopedRuleNeverMatchesWithoutAnEnvironment() {
        assertNull(matcher.evaluateRules(rules("""{"type":"assign","variant":1,"environments":[null]}"""), null, emptyMap()))
        assertNull(matcher.evaluateRules(rules("""{"type":"assign","variant":1,"environments":[1,true,{}]}"""), "1", emptyMap()))
    }

    // Like the JavaScript SDK, an integral variant outside the int range still matches its rule, which is then
    // rejected by the bounds check instead of falling through to a later rule.
    @Test
    fun hugeIntegralAssignVariantStopsAtItsRule() {
        val exp = experiment(rules("""{"type":"assign","variant":1e12}""", """{"type":"assign","variant":0}"""))
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)
        assertEquals(normal, context(exp).peekTreatment("exp_rules"))
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
    fun harmlessRuleRefreshKeepsTheExposedAssignment() {
        val logger = Capture()
        val ctx = context(experiment(rules("""{"name":"a","type":"assign","variant":1}""")), logger = logger)
        assertEquals(1, ctx.getTreatment("exp_rules"))
        ctx.setData(ContextData(listOf(experiment(rules("""{"name":"b","future":true,"type":"assign","variant":1}""")))))
        repeat(3) { assertEquals(1, ctx.getTreatment("exp_rules")) }
        assertEquals(1, logger.exposures.size)
        assertEquals(1, ctx.pendingCount)
    }

    @Test
    fun harmlessRuleRefreshKeepsAnExposedNonRuleAssignment() {
        val logger = Capture()
        val never = """"conditions":{"eq":[{"var":"tier"},{"value":"gold"}]}"""
        val ctx = context(experiment(rules("""{"type":"assign","variant":1,$never}"""), fullOnVariant = 0), logger = logger)
        val normal = ctx.getTreatment("exp_rules")
        ctx.setData(ContextData(listOf(experiment(rules("""{"name":"x","type":"assign","variant":1,$never}""")))))
        assertEquals(normal, ctx.getTreatment("exp_rules"))
        assertEquals(1, logger.exposures.size)
    }

    @Test
    fun ruleRefreshChangingTheOutcomeReExposes() {
        val logger = Capture()
        val ctx = context(experiment(rules("""{"type":"assign","variant":1}""")), logger = logger)
        assertEquals(1, ctx.getTreatment("exp_rules"))
        ctx.setData(ContextData(listOf(experiment(rules("""{"type":"assign","variant":0}""")))))
        assertEquals(0, ctx.getTreatment("exp_rules"))
        ctx.setData(ContextData(listOf(experiment(null))))
        val normal = ctx.getTreatment("exp_rules")
        assertEquals(context(experiment(null)).peekTreatment("exp_rules"), normal)
        assertEquals(3, logger.exposures.size)
        assertFalse(logger.exposures.last().ruleOverride)
    }

    private class CountingMap(private val value: String) : AbstractMap<String, Any?>() {
        var reads = 0
        override val entries: Set<Map.Entry<String, Any?>> get() = mapOf("tier" to value).entries
        override fun get(key: String): Any? { reads++; return if (key == "tier") value else null }
    }

    @Test
    fun sameOutcomeRevalidationIsDoneOnce() {
        val probe = CountingMap("gold")
        val logger = Capture()
        val condition = """"conditions":{"eq":[{"var":"probe/tier"},{"value":"gold"}]}"""
        val ctx = context(experiment(rules("""{"type":"assign","variant":1,$condition}""")), logger = logger) {
            it.setAttribute("probe", probe)
        }
        assertEquals(1, ctx.getTreatment("exp_rules"))
        val afterFirst = probe.reads

        ctx.setAttribute("unrelated", "x")
        repeat(100) { assertEquals(1, ctx.peekTreatment("exp_rules")) }
        assertEquals(afterFirst + 1, probe.reads)

        ctx.setData(ContextData(listOf(experiment(rules("""{"name":"renamed","type":"assign","variant":1,$condition}""")))))
        repeat(100) { assertEquals(1, ctx.getTreatment("exp_rules")) }
        assertEquals(afterFirst + 2, probe.reads)
        assertEquals(1, logger.exposures.size)
    }

    @Test
    fun sameOutcomeAudienceRevalidationIsDoneOnce() {
        val probe = CountingMap("gold")
        val audience = """{"filter":[{"eq":[{"var":"probe/tier"},{"value":"gold"}]}]}"""
        val ctx = context(experiment(rules("""{"type":"assign","variant":1,"environments":["staging"]}"""), audience = audience)) {
            it.setAttribute("probe", probe)
        }
        val variant = ctx.peekTreatment("exp_rules")
        val afterFirst = probe.reads
        ctx.setAttribute("unrelated", "x")
        repeat(100) { assertEquals(variant, ctx.peekTreatment("exp_rules")) }
        assertEquals(afterFirst + 1, probe.reads)
    }

    // Runs onFirstRead on its first lookup, i.e. while an assignment is being evaluated.
    private class ReassigningMap(private val onFirstRead: () -> kotlin.Unit) : AbstractMap<String, Any?>() {
        private var fired = false
        override val entries: Set<Map.Entry<String, Any?>> get() = mapOf("tier" to "gold").entries
        override fun get(key: String): Any? {
            if (!fired) { fired = true; onFirstRead() }
            return if (key == "tier") "gold" else null
        }
    }

    private val goldRule = rules("""{"type":"assign","variant":0,"conditions":{"eq":[{"var":"probe/tier"},{"value":"gold"}]}}""")

    @Test
    fun attributeSetDuringEvaluationIsSeenAsNewer() {
        lateinit var ctx: Context
        val probe = ReassigningMap { ctx.setAttribute("probe", mapOf("tier" to "silver")) }
        ctx = context(experiment(goldRule)) { it.setAttribute("probe", probe) }
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)

        assertEquals(0, ctx.peekTreatment("exp_rules"))
        assertEquals(normal, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun attributeSetDuringRevalidationIsSeenAsNewer() {
        lateinit var ctx: Context
        val probe = ReassigningMap { ctx.setAttribute("probe", mapOf("tier" to "silver")) }
        ctx = context(experiment(goldRule))
        ctx.setAttribute("probe", mapOf("tier" to "gold"))
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)

        assertEquals(0, ctx.peekTreatment("exp_rules"))
        ctx.setAttribute("probe", probe)
        assertEquals(0, ctx.peekTreatment("exp_rules"))
        assertEquals(normal, ctx.peekTreatment("exp_rules"))
    }

    private val goldAudience = """{"filter":[{"eq":[{"var":"probe/tier"},{"value":"gold"}]}]}"""

    @Test
    fun attributeSetDuringAudienceEvaluationIsSeenAsNewer() {
        lateinit var ctx: Context
        val probe = ReassigningMap { ctx.setAttribute("probe", mapOf("tier" to "silver")) }
        ctx = context(experiment(null, audience = goldAudience, audienceStrict = true)) { it.setAttribute("probe", probe) }
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)

        assertEquals(normal, ctx.peekTreatment("exp_rules"))
        assertEquals(0, ctx.peekTreatment("exp_rules"))
    }

    @Test
    fun attributeSetDuringAudienceRevalidationIsSeenAsNewer() {
        lateinit var ctx: Context
        val probe = ReassigningMap { ctx.setAttribute("probe", mapOf("tier" to "silver")) }
        ctx = context(experiment(null, audience = goldAudience, audienceStrict = true))
        ctx.setAttribute("probe", mapOf("tier" to "gold"))
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        assertNotEquals(0, normal)

        assertEquals(normal, ctx.peekTreatment("exp_rules"))
        ctx.setAttribute("probe", probe)
        assertEquals(normal, ctx.peekTreatment("exp_rules"))
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

    // Unknown scalar, object and array keys at every payload level, an unknown rule type and operator, and every
    // non-rule assignmentRules shape are ignored: the context loads and both experiments still assign.
    @Test
    fun unknownPayloadFieldsAreIgnoredAtEveryLevel() {
        val unknown = """"futureScalar":1,"futureObject":{"a":[1,{"b":2}]},"futureArray":[{"c":null}]"""
        fun experimentJson(id: Int, name: String, rules: String) = """{"id":$id,"name":"$name","unitType":"session_id",
            "iteration":1,"seedHi":3603515,"seedLo":233373850,"split":[0.5,0.5],"trafficSeedHi":449867249,
            "trafficSeedLo":455443629,"trafficSplit":[0.0,1.0],"fullOnVariant":0,"assignmentRules":$rules,
            "applications":[{"name":"website",$unknown}],
            "variants":[{"name":"A","config":null,$unknown},{"name":"B","config":null,$unknown}],
            "customFieldValues":[{"name":"f","type":"string","value":"v",$unknown}],
            "holdouts":[{"id":9,$unknown}],"switchback":{$unknown},$unknown}"""
        val ruleJson = """{"rules":[{"type":"future","variant":0},{"type":"assign","variant":1,"conditions":{"future_op":[1]},$unknown},{"type":"assign","variant":1,$unknown}],$unknown}"""
        val quoted = "\"" + ruleJson.replace("\"", "\\\"") + "\""
        val normal = context(experiment(null)).peekTreatment("exp_rules")
        for (other in listOf("null", "\"\"", "\"{}\"", "\"not json\"", "{}", "[]", "7", "true")) {
            val json = """{"experiments":[${experimentJson(1, "exp_rules", quoted)},${experimentJson(2, "exp_other", other)}],$unknown}"""
            val data = DefaultContextDataDeserializer().deserialize(json.toByteArray(), 0, json.length)!!
            val ctx = context(*data.experiments.toTypedArray())
            assertEquals(1, ctx.peekTreatment("exp_rules"), other)
            assertEquals(normal, ctx.peekTreatment("exp_other"), other)
            assertEquals("v", ctx.getCustomFieldValue("exp_rules", "f"))
        }
    }

    @Test
    fun ruleOverrideSerializes() {
        val json = String(DefaultContextEventSerializer().serialize(PublishEvent(exposures = listOf(Exposure().apply { ruleOverride = true })))!!)
        assertTrue(json.contains("\"ruleOverride\":true"), json)
        assertNotEquals(-1, json.indexOf("\"audienceMismatch\""))
    }
}
