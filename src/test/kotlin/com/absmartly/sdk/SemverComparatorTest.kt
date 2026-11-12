package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemverComparatorTest {

    private fun cmp(a: Any?, b: Any?) = SemverComparator.compare(a, b)

    @Test
    fun ordersCoreVersionsNumerically() {
        assertEquals(0, cmp("1.2.3", "1.2.3"))
        assertEquals(1, cmp("1.10.0", "1.9.0"))
        assertEquals(-1, cmp("1.2", "1.2.1"))
        assertEquals(0, cmp("1.2", "1.2.0"))
        assertEquals(0, cmp("01.002.3", "1.2.3"))
    }

    @Test
    fun ignoresLeadingVAndBuildMetadata() {
        assertEquals(0, cmp("v1.2.3", "1.2.3"))
        assertEquals(0, cmp("V1.2.3+build.5", "1.2.3+other"))
    }

    @Test
    fun releaseRanksAboveItsPreReleases() {
        assertEquals(1, cmp("1.0.0", "1.0.0-rc.1"))
        assertEquals(-1, cmp("1.0.0-alpha", "1.0.0-alpha.1"))
        assertEquals(-1, cmp("1.0.0-alpha.1", "1.0.0-alpha.beta"))
        assertEquals(-1, cmp("1.0.0-beta.2", "1.0.0-beta.11"))
        assertEquals(1, cmp("1.0.0-rc.1", "1.0.0-beta.11"))
    }

    @Test
    fun convertsNumbersAndBooleans() {
        assertEquals(0, cmp(2, "2.0.0"))
        assertEquals(0, cmp(2.5, "2.5"))
        assertEquals(1, cmp(10, "9"))
    }

    @Test
    fun emptyOrNonScalarOperandsAreNull() {
        assertNull(cmp("", "1.0"))
        assertNull(cmp("v", "1.0"))
        assertNull(cmp("+build", "1.0"))
        assertNull(cmp("-beta", "1.0"))
        assertNull(cmp(null, "1.0"))
        assertNull(cmp(listOf(1), "1.0"))
    }

    @Test
    fun jsonExprSemverOperators() {
        fun eval(op: String, a: String, b: String) =
            JsonExpr.evaluateBooleanExpr(mapOf(op to listOf(mapOf("value" to a), mapOf("value" to b))), emptyMap())
        assertEquals(true, eval("semver_eq", "1.0.0", "v1.0.0"))
        assertEquals(true, eval("semver_gt", "1.10.0", "1.9.0"))
        assertEquals(true, eval("semver_gte", "1.0.0", "1.0.0"))
        assertEquals(true, eval("semver_lt", "1.0.0-rc.1", "1.0.0"))
        assertEquals(true, eval("semver_lte", "1.0.0", "1.0.0"))
        assertEquals(false, eval("semver_gt", "", "1.0.0"))
    }
}
