package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal class AudienceMatcher {
    private val objectMapper = jacksonObjectMapper()

    data class Result(val value: Boolean)

    fun evaluate(audience: String, attributes: Map<String, Any?>): Result? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val audienceMap = objectMapper.readValue(audience, Map::class.java) as? Map<String, Any?>
            if (audienceMap != null) {
                val filter = audienceMap["filter"]
                if (filter is Map<*, *> || filter is List<*>) {
                    Result(JsonExpr.evaluateBooleanExpr(filter, attributes))
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

// Port of the JavaScript SDK's jsonexpr Evaluator and operators (src/jsonexpr), so audience
// results match across SDKs: binary operators yield null when either operand is null, and
// compare() converts the right operand to the left operand's type.
internal object JsonExpr {
    // JS: x.toFixed(15).replace(/\.?0{0,15}$/, ""); toFixed falls back to String(x) for |x| >= 1e21.
    private fun numberToString(n: Number): String {
        val d = n.toDouble()
        if (d.isNaN()) return "NaN"
        if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
        val abs = Math.abs(d)
        val body = if (abs >= 1e21) jsExponential(abs) else {
            java.math.BigDecimal(abs).setScale(15, java.math.RoundingMode.HALF_UP).toPlainString()
        }
        return ((if (d < 0) "-" else "") + body).replace(Regex("\\.?0{0,15}$"), "")
    }

    // Shortest round-trip digits; JDK 11 Double.toString is not always shortest.
    internal fun jsExponential(abs: Double): String {
        val exact = java.math.BigDecimal(abs)
        var bd = exact
        for (p in 1..17) {
            bd = exact.round(java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN))
            if (bd.toDouble() == abs) break
        }
        bd = bd.stripTrailingZeros()
        val digits = bd.unscaledValue().toString()
        val exp = digits.length - 1 - bd.scale()
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return mantissa + "e+" + exp
    }

    fun evaluateBooleanExpr(expr: Any?, vars: Map<String, Any?>): Boolean {
        return booleanConvert(evaluate(expr, vars))
    }

    private fun evaluate(expr: Any?, vars: Map<String, Any?>): Any? {
        if (expr is List<*>) {
            return evaluateAnd(expr, vars)
        }
        if (expr is Map<*, *>) {
            // Like JS, only the first key is considered; an unknown operator yields null.
            val entry = expr.entries.firstOrNull() ?: return null
            val op = entry.key as? String ?: return null
            return evalOp(op, entry.value, vars)
        }
        return null
    }

    private fun evaluateAnd(exprs: List<*>, vars: Map<String, Any?>): Boolean {
        for (expr in exprs) {
            if (!booleanConvert(evaluate(expr, vars))) return false
        }
        return true
    }

    private fun evaluateOr(exprs: List<*>, vars: Map<String, Any?>): Boolean {
        for (expr in exprs) {
            if (booleanConvert(evaluate(expr, vars))) return true
        }
        return exprs.isEmpty()
    }

    private fun evalOp(op: String, args: Any?, vars: Map<String, Any?>): Any? {
        return when (op) {
            "and" -> if (args is List<*>) evaluateAnd(args, vars) else null
            "or" -> if (args is List<*>) evaluateOr(args, vars) else null
            "value" -> args
            "var" -> {
                val path = (if (args is Map<*, *>) args["path"] else args) as? String ?: return null
                extractVar(vars, path)
            }
            "not" -> !booleanConvert(evaluate(args, vars))
            "null" -> evaluate(args, vars) == null
            "eq" -> binaryOp(args, vars) { a, b -> compare(a, b)?.let { it == 0 } }
            "gt" -> binaryOp(args, vars) { a, b -> compare(a, b)?.let { it > 0 } }
            "gte" -> binaryOp(args, vars) { a, b -> compare(a, b)?.let { it >= 0 } }
            "lt" -> binaryOp(args, vars) { a, b -> compare(a, b)?.let { it < 0 } }
            "lte" -> binaryOp(args, vars) { a, b -> compare(a, b)?.let { it <= 0 } }
            "in" -> binaryOp(args, vars) { haystack, needle ->
                when (haystack) {
                    is List<*> -> haystack.any { compare(it, needle) == 0 }
                    is String -> stringConvert(needle)?.let { haystack.contains(it) } ?: false
                    is Map<*, *> -> stringConvert(needle)?.let { haystack.containsKey(it) } ?: false
                    else -> null
                }
            }
            "match" -> binaryOp(args, vars) { a, b ->
                val text = stringConvert(a)
                val pattern = stringConvert(b)
                if (text != null && pattern != null) {
                    try {
                        Regex(pattern).containsMatchIn(text)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            else -> null
        }
    }

    private fun binaryOp(args: Any?, vars: Map<String, Any?>, op: (Any, Any) -> Any?): Any? {
        if (args !is List<*>) return null
        val left = if (args.isNotEmpty()) evaluate(args[0], vars) else null
        if (left == null) return null
        val right = if (args.size > 1) evaluate(args[1], vars) else null
        if (right == null) return null
        return op(left, right)
    }

    private fun extractVar(vars: Map<String, Any?>, path: String): Any? {
        val parts = path.split("/")
        var current: Any? = vars
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                is List<*> -> {
                    val idx = part.toIntOrNull() ?: return null
                    if (idx >= 0 && idx < current.size) current[idx] else null
                }
                else -> return null
            }
        }
        return current
    }

    private fun compare(a: Any?, b: Any?): Int? {
        if (a == null) return if (b == null) 0 else null
        if (b == null) return null

        return when (a) {
            is Number -> numberConvert(b)?.let { a.toDouble().compareTo(it) }
            is String -> stringConvert(b)?.let { a.compareTo(it) }
            is Boolean -> a.compareTo(booleanConvert(b))
            else -> if ((a is List<*> && b is List<*>) || (a is Map<*, *> && b is Map<*, *>)) {
                if (deepEquals(a, b)) 0 else null
            } else null
        }
    }

    private fun deepEquals(a: Any?, b: Any?): Boolean {
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        if (a is List<*> && b is List<*>) {
            return a.size == b.size && a.indices.all { deepEquals(a[it], b[it]) }
        }
        if (a is Map<*, *> && b is Map<*, *>) {
            return a.size == b.size && a.all { (k, v) -> b.containsKey(k) && deepEquals(v, b[k]) }
        }
        return a == b
    }

    private fun numberConvert(v: Any?): Double? {
        return when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> try {
                java.lang.Double.parseDouble(v).takeIf { it.isFinite() }
            } catch (e: NumberFormatException) {
                null
            }
            else -> null
        }
    }

    private fun stringConvert(v: Any?): String? {
        return when (v) {
            is String -> v
            is Boolean -> v.toString()
            is Number -> numberToString(v)
            else -> null
        }
    }

    private fun booleanConvert(v: Any?): Boolean {
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v != "false" && v != "0" && v != ""
            else -> v != null
        }
    }
}
