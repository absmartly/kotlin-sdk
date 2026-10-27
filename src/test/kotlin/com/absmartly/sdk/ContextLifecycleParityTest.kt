package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContextLifecycleParityTest {

    private class RecordingLogger : ContextEventLogger {
        val events = java.util.Collections.synchronizedList(mutableListOf<Pair<ContextEventLogger.EventType, Any?>>())
        override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
            events.add(type to data)
        }
        fun types() = synchronized(events) { events.map { it.first } }
    }

    private class CountingProvider(private val supply: () -> CompletableFuture<ContextData>) : ContextDataProvider {
        val calls = AtomicInteger(0)
        override fun getContextData(): CompletableFuture<ContextData> {
            calls.incrementAndGet()
            return supply()
        }
    }

    private fun failed(): CompletableFuture<ContextData> =
        CompletableFuture<ContextData>().also { it.completeExceptionally(RuntimeException("load failed")) }

    private fun experimentData() = ContextData(
        experiments = listOf(
            Experiment(
                id = 1, name = "exp_test_ab", iteration = 1, unitType = "session_id",
                seedHi = 3603515, seedLo = 233373850, split = doubleArrayOf(0.5, 0.5),
                trafficSeedHi = 449867249, trafficSeedLo = 455443629, trafficSplit = doubleArrayOf(0.0, 1.0),
                fullOnVariant = 0, applications = listOf(ExperimentApplication("website")),
                variants = listOf(ExperimentVariant("A", null), ExperimentVariant("B", null)), audience = null
            )
        )
    )

    @Test
    fun refreshOnFailedContextIsANoOpAndContextStaysFailed() {
        val provider = CountingProvider { CompletableFuture.completedFuture(experimentData()) }
        val logger = RecordingLogger()
        val config = ContextConfig.create().setUnit("session_id", "abc")
        val context = Context.create(config, failed(), provider, null, logger, null)
        context.waitUntilReady()
        assertTrue(context.isFailed)

        context.refresh().get(1, TimeUnit.SECONDS)
        assertEquals(0, provider.calls.get())
        assertTrue(context.isFailed)

        context.refresh(experimentData())
        assertTrue(context.isFailed)
        assertEquals(emptyList(), context.experiments)
        assertEquals(listOf(ContextEventLogger.EventType.Error), logger.types())
    }

    @Test
    fun readyEventIsLoggedBeforeReadinessIsSignalled() {
        val logger = RecordingLogger()
        val data = CompletableFuture<ContextData>()
        val context = Context.create(ContextConfig.create().setUnit("session_id", "abc"), data, null, null, logger, null)
        val seenAtReady = context.waitUntilReadyAsync().thenApply { logger.types() }
        data.complete(experimentData())
        assertEquals(listOf(ContextEventLogger.EventType.Ready), seenAtReady.get(1, TimeUnit.SECONDS))
    }

    @Test
    fun failedRefreshLogsAnErrorEvent() {
        var first = true
        val provider = CountingProvider {
            if (first) { first = false; CompletableFuture.completedFuture(experimentData()) } else failed()
        }
        val logger = RecordingLogger()
        val context = Context.create(
            ContextConfig.create().setUnit("session_id", "abc"),
            provider.getContextData(), provider, null, logger, null
        )
        context.waitUntilReady()
        assertFailsWith<Exception> { context.refresh().get(1, TimeUnit.SECONDS) }
        assertEquals(ContextEventLogger.EventType.Error, logger.types().last())
        assertEquals("load failed", (logger.events.last().second as Throwable).message)
    }

    @Test
    fun numericUnitsKeepTheirTypeAndHashAsRoundedIntegers() {
        val context = Context(experimentData(), mutableMapOf(), ContextOptions())
        context.setUnit("user_id", 125)
        context.setUnit("float_id", 125.75)
        context.setUnit("session_id", "abc")

        assertEquals(125, context.getUnit("user_id"))
        assertEquals(125.75, context.getUnit("float_id"))
        assertEquals(mapOf<String, Any>("user_id" to 125, "float_id" to 125.75, "session_id" to "abc"), context.getUnits())

        context.setUnit("user_id", 125.0)
        assertFailsWith<IllegalArgumentException>("Unit 'user_id' UID already set.") { context.setUnit("user_id", "125") }

        assertContentEquals(Hashing.hashUnit("126"), Hashing.hashUnit(125.75))
        assertContentEquals(Hashing.hashUnit("125"), Hashing.hashUnit(125))
    }

    @Test
    fun configUnitsAreValidatedLikeSetUnit() {
        assertFailsWith<IllegalArgumentException> {
            Context.create(
                ContextConfig.create().setUnit("session_id", "  "),
                CompletableFuture.completedFuture(experimentData()), null, null, null, null
            )
        }
    }

    @Test
    fun encodingHelpersMatchTheJavaScriptSdk() {
        assertContentEquals(byteArrayOf(97, 0xc3.toByte(), 0xa7.toByte(), 98, 0xe2.toByte(), 0x86.toByte(), 0x93.toByte(), 99),
            Hashing.utf8Bytes("açb↓c"))
        assertEquals("", Hashing.base64UrlNoPadding(ByteArray(0)))
        assertEquals("dA", Hashing.base64UrlNoPadding("t".toByteArray()))
        assertEquals("dGU", Hashing.base64UrlNoPadding("te".toByteArray()))
        assertEquals("dGVz", Hashing.base64UrlNoPadding("tes".toByteArray()))
        assertEquals("dGVzdA", Hashing.base64UrlNoPadding("test".toByteArray()))
        assertEquals("c3BlY2lhbCBjaGFyYWN0ZXJzIGHDp2LihpNj",
            Hashing.base64UrlNoPadding(Hashing.utf8Bytes("special characters açb↓c")))
    }

    private class FlakyPublisher : ContextPublisher {
        @Volatile var fail = true
        override fun publish(context: Context, event: PublishEvent): CompletableFuture<Void> =
            if (fail) CompletableFuture<Void>().also { it.completeExceptionally(RuntimeException("Publish failed")) }
            else CompletableFuture.completedFuture(null)
    }

    @Test
    fun failedFinalPublishKeepsEventsQueuedAndContextOpen() {
        val logger = RecordingLogger()
        val publisher = FlakyPublisher()
        val context = Context.create(
            ContextConfig.create().setUnit("session_id", "abc").setPublishDelay(-1),
            CompletableFuture.completedFuture(experimentData()), null, publisher, logger, null
        )
        context.getTreatment("exp_test_ab")
        context.track("signup", null)
        logger.events.clear()

        val error = assertFailsWith<RuntimeException> { context.close() }
        assertEquals("Publish failed", error.message)
        assertEquals(listOf(ContextEventLogger.EventType.Error), logger.types())
        assertEquals(false, context.isClosed)
        assertEquals(false, context.isClosing)
        assertEquals(2, context.pendingCount)
        context.getTreatment("exp_test_ab")

        logger.events.clear()
        publisher.fail = false
        context.close()
        assertTrue(context.isClosed)
        assertEquals(0, context.pendingCount)
        assertEquals(listOf(ContextEventLogger.EventType.Publish, ContextEventLogger.EventType.Close), logger.types())
    }
}
