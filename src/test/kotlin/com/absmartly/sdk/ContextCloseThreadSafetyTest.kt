package com.absmartly.sdk

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextCloseThreadSafetyTest {

    private fun createTestContext(): Context {
        val data = ContextData(
            experiments = listOf(
                Experiment(
                    id = 1,
                    name = "exp_test",
                    iteration = 1,
                    unitType = "session_id",
                    seedHi = 3603515,
                    seedLo = 233373850,
                    split = doubleArrayOf(0.5, 0.5),
                    trafficSeedHi = 449867249,
                    trafficSeedLo = 455443629,
                    trafficSplit = doubleArrayOf(0.0, 1.0),
                    fullOnVariant = 0,
                    variants = listOf(ExperimentVariant("A"), ExperimentVariant("B")),
                    audience = null
                )
            )
        )
        return Context(data, mutableMapOf("session_id" to "test"), ContextOptions())
    }

    @Test
    fun concurrentCloseIsIdempotent() {
        val context = createTestContext()
        val closeCount = AtomicInteger(0)
        val logger = object : ContextEventLogger {
            override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
                if (type == ContextEventLogger.EventType.Close) {
                    closeCount.incrementAndGet()
                }
            }
        }

        val contextWithLogger = Context(
            ContextData(experiments = emptyList()),
            mutableMapOf("session_id" to "test"),
            ContextOptions(),
            logger
        )

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val threads = (1..threadCount).map {
            Thread {
                latch.countDown()
                latch.await()
                contextWithLogger.close()
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertTrue(contextWithLogger.isClosed)
        assertEquals(1, closeCount.get())
    }

    @Test
    fun closeFlushesAndClosesAtomically() {
        val context = createTestContext()
        context.getTreatment("exp_test")
        assertEquals(1, context.pendingCount)
        context.close()
        assertTrue(context.isClosed)
        assertEquals(0, context.pendingCount)
    }
}
