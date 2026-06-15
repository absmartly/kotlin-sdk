package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JsonExprTest {

    private val emptyVars = emptyMap<String, Any?>()

    @Test
    fun evaluateBooleanExprHandlesNullAndPrimitives() {
        assertFalse(JsonExpr.evaluateBooleanExpr(null, emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr("raw_string", emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(42, emptyVars))
    }

    @Test
    fun evaluateBooleanExprWithValueOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("value" to true), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("value" to false), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("value" to null), emptyVars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("value" to 1), emptyVars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("value" to 2), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("value" to 0), emptyVars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("value" to "abc"), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("value" to ""), emptyVars))
    }

    @Test
    fun andCombinatorWithList() {
        val vars = mapOf<String, Any?>("a" to true, "b" to true)
        assertTrue(JsonExpr.evaluateBooleanExpr(
            listOf(mapOf("var" to "a"), mapOf("var" to "b")), vars
        ))

        val vars2 = mapOf<String, Any?>("a" to true, "b" to false)
        assertFalse(JsonExpr.evaluateBooleanExpr(
            listOf(mapOf("var" to "a"), mapOf("var" to "b")), vars2
        ))
    }

    @Test
    fun andCombinatorEmptyList() {
        assertTrue(JsonExpr.evaluateBooleanExpr(emptyList<Any>(), emptyVars))
    }

    @Test
    fun orCombinator() {
        val expr = mapOf("or" to listOf(mapOf("value" to false), mapOf("value" to true)))
        assertTrue(JsonExpr.evaluateBooleanExpr(expr, emptyVars))

        val exprAllFalse = mapOf("or" to listOf(mapOf("value" to false), mapOf("value" to 0)))
        assertFalse(JsonExpr.evaluateBooleanExpr(exprAllFalse, emptyVars))
    }

    @Test
    fun valueOperator() {
        val expr = mapOf("value" to 42)
        assertTrue(JsonExpr.evaluateBooleanExpr(expr, emptyVars))

        val exprNull = mapOf("value" to null)
        assertFalse(JsonExpr.evaluateBooleanExpr(exprNull, emptyVars))
    }

    @Test
    fun varOperator() {
        val vars = mapOf<String, Any?>("x" to 5, "y" to "hello")
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "x"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "y"), vars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("var" to "z"), vars))
    }

    @Test
    fun varOperatorNestedPaths() {
        val vars = mapOf<String, Any?>(
            "a" to 1,
            "b" to true,
            "c" to false,
            "d" to listOf(1, 2, 3),
            "e" to listOf(1, mapOf("z" to 2), 3),
            "f" to mapOf("y" to mapOf("x" to 3, "0" to 10))
        )

        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "a"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "b"), vars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("var" to "c"), vars))

        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "d/0"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "d/1"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "d/2"), vars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("var" to "d/3"), vars))

        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "e/0"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "e/1/z"), vars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "e/2"), vars))

        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("var" to "f/y/x"), vars))
    }

    @Test
    fun notOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("not" to mapOf("value" to false)), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("not" to mapOf("value" to true)), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("not" to mapOf("value" to 1)), emptyVars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("not" to mapOf("value" to 0)), emptyVars))
    }

    @Test
    fun nullOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("null" to mapOf("value" to null)), emptyVars))
        assertTrue(JsonExpr.evaluateBooleanExpr(mapOf("null" to mapOf("var" to "missing")), emptyVars))
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("null" to mapOf("value" to 5)), emptyVars))
    }

    @Test
    fun eqOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("eq" to listOf(mapOf("value" to 1), mapOf("value" to 1))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("eq" to listOf(mapOf("value" to 1), mapOf("value" to 2))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("eq" to listOf(mapOf("value" to "abc"), mapOf("value" to "abc"))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("eq" to listOf(mapOf("value" to "abc"), mapOf("value" to "def"))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("eq" to listOf(mapOf("value" to null), mapOf("value" to null))), emptyVars
        ))
    }

    @Test
    fun gtOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("gt" to listOf(mapOf("value" to 2), mapOf("value" to 1))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("gt" to listOf(mapOf("value" to 1), mapOf("value" to 2))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("gt" to listOf(mapOf("value" to 1), mapOf("value" to 1))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("gt" to listOf(mapOf("value" to "b"), mapOf("value" to "a"))), emptyVars
        ))
    }

    @Test
    fun gteOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("gte" to listOf(mapOf("value" to 2), mapOf("value" to 1))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("gte" to listOf(mapOf("value" to 1), mapOf("value" to 1))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("gte" to listOf(mapOf("value" to 0), mapOf("value" to 1))), emptyVars
        ))
    }

    @Test
    fun ltOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("lt" to listOf(mapOf("value" to 1), mapOf("value" to 2))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("lt" to listOf(mapOf("value" to 2), mapOf("value" to 1))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("lt" to listOf(mapOf("value" to 1), mapOf("value" to 1))), emptyVars
        ))
    }

    @Test
    fun lteOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("lte" to listOf(mapOf("value" to 1), mapOf("value" to 2))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("lte" to listOf(mapOf("value" to 1), mapOf("value" to 1))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("lte" to listOf(mapOf("value" to 2), mapOf("value" to 1))), emptyVars
        ))
    }

    @Test
    fun inOperatorWithStrings() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("in" to listOf(mapOf("value" to "abcdef"), mapOf("value" to "cd"))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("in" to listOf(mapOf("value" to "abcdef"), mapOf("value" to "xy"))), emptyVars
        ))
    }

    @Test
    fun inOperatorWithList() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("in" to listOf(mapOf("value" to listOf(1, 2, 3)), mapOf("value" to 2))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("in" to listOf(mapOf("value" to listOf(1, 2, 3)), mapOf("value" to 5))), emptyVars
        ))
    }

    @Test
    fun matchOperator() {
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("match" to listOf(mapOf("value" to "hello world"), mapOf("value" to "^hello"))), emptyVars
        ))
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("match" to listOf(mapOf("value" to "hello world"), mapOf("value" to "^world"))), emptyVars
        ))
        assertTrue(JsonExpr.evaluateBooleanExpr(
            mapOf("match" to listOf(mapOf("value" to "hello world"), mapOf("value" to "world$"))), emptyVars
        ))
    }

    @Test
    fun unknownOperatorReturnsNull() {
        assertFalse(JsonExpr.evaluateBooleanExpr(mapOf("unknown_op" to listOf(1, 2)), emptyVars))
    }

    @Test
    fun mapWithMoreThanOneKeyReturnsNull() {
        assertFalse(JsonExpr.evaluateBooleanExpr(
            mapOf("value" to 1, "extra" to 2), emptyVars
        ))
    }
}
