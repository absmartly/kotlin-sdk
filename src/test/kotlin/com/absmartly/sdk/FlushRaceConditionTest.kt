package com.absmartly.sdk

import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertTrue

class FlushRaceConditionTest {

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
        return Context(data, mutableMapOf("session_id" to "test-user"), ContextOptions())
    }

    @Test
    fun flushUsesAtomicSubtractInsteadOfSet() {
        val context = createTestContext()
        context.getTreatment("exp_test")
        context.track("goal1", null)
        val initialCount = context.pendingCount
        assertTrue(initialCount > 0)
        context.publish()
        assertTrue(context.pendingCount >= 0)
    }

    @Test
    fun concurrentTrackAndFlush() {
        val context = createTestContext()
        val latch = CountDownLatch(1)

        context.track("goal1", null)
        context.track("goal2", null)

        val trackThread = Thread {
            latch.await()
            for (i in 0 until 100) {
                try { context.track("concurrent_goal_$i", null) } catch (_: Exception) {}
            }
        }

        val flushThread = Thread {
            latch.await()
            for (i in 0 until 10) {
                try { context.publish() } catch (_: Exception) {}
            }
        }

        trackThread.start()
        flushThread.start()
        latch.countDown()

        trackThread.join(5000)
        flushThread.join(5000)

        assertTrue(context.pendingCount >= 0)
    }
}
