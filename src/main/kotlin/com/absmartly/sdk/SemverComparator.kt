package com.absmartly.sdk

import java.math.BigDecimal
import java.math.RoundingMode

internal object SemverComparator {
    fun compare(left: Any?, right: Any?): Int? = compare(stringConvert(left), stringConvert(right))

    fun compare(left: String?, right: String?): Int? {
        val a = parse(left) ?: return null
        val b = parse(right) ?: return null
        val n = maxOf(a.core.size, b.core.size)
        for (i in 0 until n) {
            val c = compareIdentifier(a.core.getOrElse(i) { "0" }, b.core.getOrElse(i) { "0" })
            if (c != 0) return c
        }
        if (a.pre.isEmpty() && b.pre.isEmpty()) return 0
        if (a.pre.isEmpty()) return 1
        if (b.pre.isEmpty()) return -1
        val ap = a.pre.split('.'); val bp = b.pre.split('.')
        for (i in 0 until maxOf(ap.size, bp.size)) {
            if (i >= ap.size) return -1
            if (i >= bp.size) return 1
            val c = compareIdentifier(ap[i], bp[i])
            if (c != 0) return c
        }
        return 0
    }

    private data class Version(val core: List<String>, val pre: String)
    private fun parse(input: String?): Version? {
        var value = input ?: return null
        if (value.startsWith("v", true)) value = value.substring(1)
        value = value.substringBefore('+')
        if (value.isEmpty()) return null
        val dash = value.indexOf('-')
        val core = if (dash < 0) value else value.substring(0, dash)
        if (core.isEmpty()) return null
        return Version(core.split('.'), if (dash < 0) "" else value.substring(dash + 1))
    }
    private fun stringConvert(value: Any?): String? = when (value) {
        is String -> value
        is Boolean -> value.toString()
        is Number -> try { BigDecimal(value.toString()).setScale(15, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() } catch (_: Exception) { null }
        else -> null
    }
    private fun compareIdentifier(a: String, b: String): Int {
        val an = a.isNotEmpty() && a.all { it in '0'..'9' }
        val bn = b.isNotEmpty() && b.all { it in '0'..'9' }
        if (an && bn) {
            val x = a.trimStart('0').ifEmpty { "0" }; val y = b.trimStart('0').ifEmpty { "0" }
            return x.length.compareTo(y.length).takeIf { it != 0 } ?: x.compareTo(y).coerceIn(-1, 1)
        }
        if (an) return -1
        if (bn) return 1
        return a.compareTo(b).coerceIn(-1, 1)
    }
}
