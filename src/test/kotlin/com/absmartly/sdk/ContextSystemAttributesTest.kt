package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The configured application and environment names are always available to audience filters and rule conditions,
 * with user-set attributes overriding them. ContextConfig.setIncludeSystemAttributes additionally exposes
 * app_version and publishes the system attributes.
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

    // A strict audience on the environment matches.
    @Test
    fun strictEnvironmentAudienceMatches() {
        val context = contextWith(eqFilter("environment", "production"))
        assertEquals(1, context.peekTreatment("exp_system"))
        context.getTreatment("exp_system")
        context.publish()
    }

    // A strict audience on the application matches.
    @Test
    fun strictApplicationAudienceMatches() {
        val context = contextWith(eqFilter("application", "website"))
        assertEquals(1, context.peekTreatment("exp_system"))
    }

    // No match gives variant 0 and a published audienceMismatch.
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

    // A user value set after creation overrides the system value and invalidates the cached assignment.
    @Test
    fun userAttributeOverridesSystemValueAndInvalidatesCache() {
        val productionContext = contextWith(eqFilter("environment", "production"))
        assertEquals(1, productionContext.peekTreatment("exp_system"))
        productionContext.setAttribute("environment", "staging")
        assertEquals(0, productionContext.peekTreatment("exp_system"))

        val stagingContext = contextWith(eqFilter("environment", "staging"))
        assertEquals(0, stagingContext.peekTreatment("exp_system"))
        stagingContext.setAttribute("environment", "staging")
        assertEquals(1, stagingContext.peekTreatment("exp_system"))
    }

    // With no environment configured the key is absent and the filter does not match.
    @Test
    fun missingEnvironmentLeavesKeyOutAndDoesNotMatch() {
        val context = contextWith(eqFilter("environment", "production"), environment = null)
        assertFalse(context.buildEvaluationAttributesMap().containsKey("environment"))
        assertEquals(0, context.peekTreatment("exp_system"))

        val emptyContext = contextWith(eqFilter("environment", "production"), environment = "")
        assertFalse(emptyContext.buildEvaluationAttributesMap().containsKey("environment"))
        assertEquals(0, emptyContext.peekTreatment("exp_system"))
    }

    // Without setIncludeSystemAttributes app_version is not provided, so a filter on it never matches.
    @Test
    fun appVersionIsNotProvidedByDefault() {
        val context = contextWith(eqFilter("app_version", "0"))
        assertFalse(context.buildEvaluationAttributesMap().containsKey("app_version"))
        assertEquals(0, context.peekTreatment("exp_system"))
    }

    // getAttributes returns user attributes only.
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

        val publicAttrs = context.getAttributes()
        assertFalse(publicAttrs.containsKey("application"))
        assertEquals("staging", publicAttrs["environment"])
        assertEquals("x", publicAttrs["custom"])
    }

    private class CapturingPublisher : ContextPublisher {
        val events = mutableListOf<PublishEvent>()
        override fun publish(context: Context, event: PublishEvent): CompletableFuture<Void> {
            events.add(event)
            return CompletableFuture.completedFuture(null)
        }
    }

    private fun publishingContext(
        include: Boolean,
        publisher: ContextPublisher,
        applicationVersion: String? = "2.4.1",
        configure: (ContextConfig) -> kotlin.Unit = {}
    ): Context {
        val config = ContextConfig.create().setPublishDelay(-1).setUnit("session_id", units.getValue("session_id"))
            .setIncludeSystemAttributes(include)
        configure(config)
        return Context.create(
            config = config,
            dataFuture = CompletableFuture.completedFuture(ContextData(listOf(experiment(eqFilter("app_version", "2.4.1"))))),
            dataProvider = null,
            eventHandler = publisher,
            eventLogger = null,
            scheduler = null,
            application = "website",
            environment = "production",
            applicationVersion = applicationVersion
        ).waitUntilReady()
    }

    @Test
    fun systemAttributesAreOffByDefault() {
        val publisher = CapturingPublisher()
        val context = publishingContext(include = false, publisher = publisher)
        assertFalse(context.buildEvaluationAttributesMap().containsKey("app_version"))
        assertEquals(0, context.getTreatment("exp_system"))
        context.publish().join()
        assertNull(publisher.events.single().attributes)
    }

    @Test
    fun includedSystemAttributesAreEvaluatedAndPublishedFirst() {
        val publisher = CapturingPublisher()
        val context = publishingContext(include = true, publisher = publisher)
        assertEquals("2.4.1", context.buildEvaluationAttributesMap()["app_version"])
        assertEquals(1, context.getTreatment("exp_system"))
        context.setAttribute("user", "u1")
        context.publish().join()

        val attrs = publisher.events.single().attributes!!
        assertEquals(
            listOf("sdk_name", "sdk_version", "application", "environment", "app_version", "user"),
            attrs.map { it.name }
        )
        assertEquals(SdkVersion.NAME, attrs[0].value)
        assertEquals(SdkVersion.VERSION, attrs[1].value)
        assertEquals("website", attrs[2].value)
        assertEquals("production", attrs[3].value)
        assertEquals("2.4.1", attrs[4].value)
    }

    @Test
    fun systemAttributesCarryTheEarliestEventTime() {
        val publisher = CapturingPublisher()
        val context = publishingContext(include = true, publisher = publisher)
        context.track("goal", null)
        Thread.sleep(5)
        context.getTreatment("exp_system")
        context.publish().join()

        val event = publisher.events.single()
        val earliest = minOf(event.exposures!!.minOf { it.exposedAt }, event.goals!!.minOf { it.achievedAt })
        assertEquals(event.goals!!.single().achievedAt, earliest)
        val systemNames = setOf("sdk_name", "sdk_version", "application", "environment", "app_version")
        event.attributes!!.filter { it.name in systemNames }.forEach { assertEquals(earliest, it.setAt) }
    }

    @Test
    fun userSetSystemNameIsNotDuplicated() {
        val publisher = CapturingPublisher()
        val context = publishingContext(include = true, publisher = publisher) { it.setAttribute("environment", "qa") }
        context.track("goal", null)
        context.publish().join()
        val environments = publisher.events.single().attributes!!.filter { it.name == "environment" }
        assertEquals(listOf("qa"), environments.map { it.value })
    }

    @Test
    fun unsetApplicationVersionIsLeftOut() {
        for (version in listOf(null, "0", " ")) {
            val publisher = CapturingPublisher()
            val context = publishingContext(include = true, publisher = publisher, applicationVersion = version)
            assertFalse(context.buildEvaluationAttributesMap().containsKey("app_version"))
            context.track("goal", null)
            context.publish().join()
            assertFalse(publisher.events.single().attributes!!.any { it.name == "app_version" })
        }
    }

    @Test
    fun sdkCreateContextPassesTheClientApplicationVersion() {
        val publisher = CapturingPublisher()
        val client = Client.create(
            ClientConfig.create().setEndpoint("https://test.example.com").setAPIKey("k")
                .setApplication("website").setEnvironment("production").setApplicationVersion("2.4.1"),
            object : HTTPClient {
                private fun ok() = CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))
                override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?) = ok()
                override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) = ok()
                override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) = ok()
                override fun close() {}
            }
        )
        val sdk = ABsmartly.create(ABSmartlyConfig.create().setClient(client).setContextPublisher(publisher))
        try {
            val context = sdk.createContextWith(
                ContextConfig.create().setUnit("session_id", units.getValue("session_id")).setIncludeSystemAttributes(true),
                ContextData(listOf(experiment(eqFilter("app_version", "2.4.1"))))
            ).waitUntilReady()
            assertEquals(1, context.peekTreatment("exp_system"))
        } finally {
            sdk.close()
        }
    }
}
