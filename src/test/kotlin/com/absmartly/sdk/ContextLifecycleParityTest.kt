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

    @Test
    fun numericUidHashingFollowsJavaScriptToFixed() {
        assertEquals("-0", Hashing.unitToString(-0.1))
        assertEquals("0", Hashing.unitToString(-0.0))
        assertEquals("-3", Hashing.unitToString(-2.5))
        assertEquals("1e+21", Hashing.unitToString(1e21))
        assertEquals("999999999999999868928", Hashing.unitToString(999999999999999900000.0))
        assertEquals("NaN", Hashing.unitToString(Double.NaN))
        assertEquals("9007199254740993", Hashing.unitToString(9007199254740993L))
    }

    @Test
    fun distinctLargeLongUnitsAreNotTreatedAsTheSameUid() {
        val context = Context(experimentData(), mutableMapOf(), ContextOptions())
        context.setUnit("user_id", 9007199254740992L)
        context.setUnit("user_id", 9007199254740992.0)
        assertFailsWith<IllegalArgumentException> { context.setUnit("user_id", 9007199254740993L) }
        assertEquals(9007199254740992L, context.getUnit("user_id"))
    }

    @Test
    fun nonFiniteNumericUnitsPublishWithoutLosingEvents() {
        val publisher = FlakyPublisher().apply { fail = false }
        val sent = mutableListOf<PublishEvent>()
        val recording = object : ContextPublisher {
            override fun publish(context: Context, event: PublishEvent): CompletableFuture<Void> {
                sent.add(event); return publisher.publish(context, event)
            }
        }
        val context = Context.create(
            ContextConfig.create().setUnit("session_id", "abc").setPublishDelay(-1),
            CompletableFuture.completedFuture(experimentData()), null, recording, null, null
        )
        context.setUnit("weird", Double.NaN)
        context.track("signup", null)
        context.publish().get(1, TimeUnit.SECONDS)
        assertEquals(0, context.pendingCount)
        assertEquals(1, sent.size)
        assertEquals(String(Hashing.hashUnit("NaN"), Charsets.US_ASCII), sent[0].units!!.first { it.type == "weird" }.uid)
    }

    private class ManualScheduler : java.util.concurrent.ScheduledThreadPoolExecutor(1) {
        val scheduled = AtomicInteger(0)
        override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): java.util.concurrent.ScheduledFuture<*> {
            scheduled.incrementAndGet()
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit)
        }
    }

    @Test
    fun failedCloseRestartsAutomaticPublishing() {
        val publisher = FlakyPublisher()
        val scheduler = ManualScheduler()
        try {
            val context = Context.create(
                ContextConfig.create().setUnit("session_id", "abc").setPublishDelay(50),
                CompletableFuture.completedFuture(experimentData()), null, publisher, null, scheduler
            )
            assertEquals(1, scheduler.scheduled.get())
            context.track("signup", null)
            assertFailsWith<RuntimeException> { context.close() }
            assertEquals(2, scheduler.scheduled.get())
            publisher.fail = false
            val deadline = System.currentTimeMillis() + 2000
            while (context.pendingCount > 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(0, context.pendingCount)
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun initialLoadDuringFailedCloseStillMakesTheContextReady() {
        val publish = CompletableFuture<Void>()
        val data = CompletableFuture<ContextData>()
        val publisher = object : ContextPublisher {
            override fun publish(context: Context, event: PublishEvent) = publish
        }
        val context = Context.create(
            ContextConfig.create().setUnit("session_id", "abc").setPublishDelay(-1),
            data, null, publisher, null, null
        )
        context.track("signup", null)
        val closing = Thread { runCatching { context.close() } }.apply { start() }
        val deadline = System.currentTimeMillis() + 2000
        while (!context.isClosing && System.currentTimeMillis() < deadline) Thread.sleep(5)
        data.complete(experimentData())
        publish.completeExceptionally(RuntimeException("Publish failed"))
        closing.join(2000)

        assertEquals(false, context.isClosed)
        assertTrue(context.isReady)
        context.waitUntilReady()
        assertEquals(1, context.getTreatment("exp_test_ab"))
    }
}
