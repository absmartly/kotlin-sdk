package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers §4 of the "SDKs must evaluate audiences with application + environment" brief.
 *
 * The configured application and environment names are always available to audience filters, with
 * user-set attributes overriding them. app_version is never provided. Nothing extra is published; the
 * collector adds application/environment server-side.
 *
 * The Kotlin SDK evaluates audiences only through the experiment `audience` field (there is no separate
 * assignment-rule `conditions` feature), so §4 test 5 (rule-level conditions) does not apply here.
 */
class ContextSystemAttributesTest {

    private val units = mutableMapOf(
        "session_id" to "e791e240fcd3df7d238cfc285f475e8152fcc0ec"
    )

    private fun experiment(audience: String?, audienceStrict: Boolean = true, fullOnVariant: Int = 1): Experiment =
        Experiment(
            id = 1,
            name = "exp_system",
            iteration = 1,
            unitType = "session_id",
            seedHi = 3603515,
            seedLo = 233373850,
            split = doubleArrayOf(0.5, 0.5),
            trafficSeedHi = 449867249,
            trafficSeedLo = 455443629,
            trafficSplit = doubleArrayOf(0.0, 1.0),
            fullOnVariant = fullOnVariant,
            applications = listOf(ExperimentApplication("website")),
            variants = listOf(
                ExperimentVariant("A", null),
                ExperimentVariant("B", null)
            ),
            audienceStrict = audienceStrict,
            audience = audience
        )

    private fun contextWith(
        audience: String?,
        application: String? = "website",
        environment: String? = "production",
        audienceStrict: Boolean = true,
        fullOnVariant: Int = 1
    ): Context {
        val data = ContextData(experiments = listOf(experiment(audience, audienceStrict, fullOnVariant)))
        return Context(
            data,
            units.toMutableMap(),
            ContextOptions(),
            application = application,
            environment = environment
        )
    }

    private fun eqFilter(variable: String, value: String): String =
        """{"filter":[{"eq":[{"var":"$variable"},{"value":"$value"}]}]}"""

    // §4.1 Strict audience on the environment matches.
    @Test
    fun strictEnvironmentAudienceMatches() {
        val context = contextWith(eqFilter("environment", "production"))
        assertEquals(1, context.peekTreatment("exp_system"))
        context.getTreatment("exp_system")
        context.publish()
    }

    // §4.2 Strict audience on the application matches.
    @Test
    fun strictApplicationAudienceMatches() {
        val context = contextWith(eqFilter("application", "website"))
        assertEquals(1, context.peekTreatment("exp_system"))
    }

    // §4.3 No match -> variant 0, audienceMismatch true (observed on the published exposure).
    @Test
    fun noMatchGivesVariantZeroAndMismatch() {
        val context = contextWith(eqFilter("environment", "staging"))
        assertEquals(0, context.peekTreatment("exp_system"))

        val captured = mutableListOf<Exposure>()
        val logger = object : ContextEventLogger {
            override fun handleEvent(ctx: Context, type: ContextEventLogger.EventType, data: Any?) {
                if (type == ContextEventLogger.EventType.Exposure && data is Exposure) captured.add(data)
            }
        }
        val data = ContextData(experiments = listOf(experiment(eqFilter("environment", "staging"))))
        val ctx = Context(data, units.toMutableMap(), ContextOptions(), logger,
            application = "website", environment = "production")
        assertEquals(0, ctx.getTreatment("exp_system"))
        assertEquals(1, captured.size)
        assertTrue(captured[0].audienceMismatch)
    }

    // §4.4 A user value set after creation overrides the system value; the cached assignment is invalidated.
    @Test
    fun userAttributeOverridesSystemValueAndInvalidatesCache() {
        // production filter matches at first (system value), then stops matching after user sets staging.
        val productionContext = contextWith(eqFilter("environment", "production"))
        assertEquals(1, productionContext.peekTreatment("exp_system"))
        productionContext.setAttribute("environment", "staging")
        assertEquals(0, productionContext.peekTreatment("exp_system"))

        // staging filter does not match the system value, then matches after the user sets staging.
        val stagingContext = contextWith(eqFilter("environment", "staging"))
        assertEquals(0, stagingContext.peekTreatment("exp_system"))
        stagingContext.setAttribute("environment", "staging")
        assertEquals(1, stagingContext.peekTreatment("exp_system"))
    }

    // §4.6 No environment configured means no key in the map: the filter does not match and nothing crashes.
    @Test
    fun missingEnvironmentLeavesKeyOutAndDoesNotMatch() {
        val context = contextWith(eqFilter("environment", "production"), environment = null)
        assertFalse(context.buildEvaluationAttributesMap().containsKey("environment"))
        assertEquals(0, context.peekTreatment("exp_system"))

        // Empty string is treated the same as unset.
        val emptyContext = contextWith(eqFilter("environment", "production"), environment = "")
        assertFalse(emptyContext.buildEvaluationAttributesMap().containsKey("environment"))
        assertEquals(0, emptyContext.peekTreatment("exp_system"))
    }

    // §4.8 app_version is never provided, so a filter on it never matches.
    @Test
    fun appVersionIsNeverProvided() {
        val context = contextWith(eqFilter("app_version", "0"))
        assertFalse(context.buildEvaluationAttributesMap().containsKey("app_version"))
        assertEquals(0, context.peekTreatment("exp_system"))
    }

    // The evaluation map carries the system values first, overlaid by user attributes; getAttributes stays user-only.
    @Test
    fun evaluationMapOverlaysUserAttributesButGetAttributesStaysUserOnly() {
        val context = contextWith(audience = null)
        val baseMap = context.buildEvaluationAttributesMap()
        assertEquals("website", baseMap["application"])
        assertEquals("production", baseMap["environment"])

        context.setAttribute("environment", "staging")
        context.setAttribute("custom", "x")
        val overlaid = context.buildEvaluationAttributesMap()
        assertEquals("staging", overlaid["environment"])
        assertEquals("website", overlaid["application"])
        assertEquals("x", overlaid["custom"])

        // The public attribute map does not expose system values.
        val publicAttrs = context.getAttributes()
        assertFalse(publicAttrs.containsKey("application"))
        assertEquals("staging", publicAttrs["environment"])
        assertEquals("x", publicAttrs["custom"])
    }
}
