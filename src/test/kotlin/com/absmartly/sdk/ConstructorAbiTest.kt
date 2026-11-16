package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The public constructors that shipped before assignment rules keep their JVM descriptors, both the full-arity one Java
 * calls and the Kotlin default-mask one, so callers compiled against the earlier SDK still link.
 */
class ConstructorAbiTest {
    private val mask = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
    private val int = Int::class.javaPrimitiveType!!
    private val long = Long::class.javaPrimitiveType!!
    private val bool = Boolean::class.javaPrimitiveType!!

    private val contextParams = arrayOf(
        ContextData::class.java, Map::class.java, ContextOptions::class.java, ContextEventLogger::class.java, bool
    )
    private val experimentParams = arrayOf(
        int, String::class.java, String::class.java, int, int, int, DoubleArray::class.java, int, int,
        DoubleArray::class.java, int, List::class.java, List::class.java, bool, String::class.java, List::class.java
    )
    private val exposureParams = arrayOf(
        int, String::class.java, String::class.java, int, long, bool, bool, bool, bool, bool, bool
    )

    @Test
    fun contextKeepsItsConstructors() {
        val ctor = Context::class.java.getConstructor(*contextParams)
        val context = ctor.newInstance(ContextData(), emptyMap<String, Any>(), ContextOptions(), null, true)
        assertEquals(true, context.isReady)
        Context::class.java.getConstructor(*contextParams, int, mask)
    }

    @Test
    fun experimentKeepsItsConstructors() {
        val ctor = Experiment::class.java.getConstructor(*experimentParams)
        val experiment = ctor.newInstance(
            1, "exp", "session_id", 1, 0, 0, doubleArrayOf(), 0, 0, doubleArrayOf(), 0, null, emptyList<Any>(), false,
            null, null
        )
        assertEquals(null, experiment.assignmentRules)
        Experiment::class.java.getConstructor(*experimentParams, int, mask)
    }

    @Test
    fun exposureKeepsItsConstructors() {
        val ctor = Exposure::class.java.getConstructor(*exposureParams)
        val exposure = ctor.newInstance(1, "exp", "session_id", 1, 0L, true, true, false, false, false, false)
        assertFalse(exposure.ruleOverride)
        Exposure::class.java.getConstructor(*exposureParams, int, mask)
    }
}
