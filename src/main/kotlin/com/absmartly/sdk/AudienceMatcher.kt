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

internal object JsonExpr {
    fun evaluateBooleanExpr(expr: Any?, vars: Map<String, Any?>): Boolean {
        val result = evaluate(expr, vars)
        return when (result) {
            is Boolean -> result
            is Number -> result.toDouble() != 0.0
            is String -> result.isNotEmpty()
            null -> false
            else -> true
        }
    }

    private fun evaluate(expr: Any?, vars: Map<String, Any?>): Any? {
        if (expr is List<*>) {
            return evaluateAnd(expr, vars)
        }
        if (expr is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val map = expr as Map<String, Any?>
            if (map.size == 1) {
                val entry = map.entries.first()
                val op = entry.key
                val args = entry.value
                return evalOp(op, args, vars)
            }
        }
        return null
    }

    private fun evaluateAnd(exprs: List<*>, vars: Map<String, Any?>): Boolean {
        for (expr in exprs) {
            val result = evaluate(expr, vars)
            if (result == null || !toBool(result)) return false
        }
        return true
    }

    private fun evaluateOr(exprs: List<*>, vars: Map<String, Any?>): Boolean {
        for (expr in exprs) {
            val result = evaluate(expr, vars)
            if (result != null && toBool(result)) return true
        }
        return false
    }

    private fun evalOp(op: String, args: Any?, vars: Map<String, Any?>): Any? {
        return when (op) {
            "and" -> if (args is List<*>) evaluateAnd(args, vars) else null
            "or" -> if (args is List<*>) evaluateOr(args, vars) else null
            "value" -> args
            "var" -> {
                val path = args as? String ?: return null
                extractVar(vars, path)
            }
            "not" -> {
                val result = evaluate(args, vars)
                if (result != null) !toBool(result) else null
            }
            "null" -> {
                val value = evaluate(args, vars)
                value == null
            }
            "eq" -> binaryOp(args, vars) { a, b -> compare(a, b) == 0 }
            "gt" -> binaryOp(args, vars) { a, b -> val c = compare(a, b); c != null && c > 0 }
            "gte" -> binaryOp(args, vars) { a, b -> val c = compare(a, b); c != null && c >= 0 }
            "lt" -> binaryOp(args, vars) { a, b -> val c = compare(a, b); c != null && c < 0 }
            "lte" -> binaryOp(args, vars) { a, b -> val c = compare(a, b); c != null && c <= 0 }
            "in" -> {
                if (args is List<*> && args.size == 2) {
                    val haystack = evaluate(args[0], vars)
                    val needle = evaluate(args[1], vars)
                    if (haystack is String && needle is String) {
                        haystack.contains(needle)
                    } else if (haystack is List<*>) {
                        haystack.any { compare(it, needle) == 0 }
                    } else {
                        null
                    }
                } else null
            }
            "match" -> {
                if (args is List<*> && args.size == 2) {
                    val value = evaluate(args[0], vars)
                    val pattern = evaluate(args[1], vars)
                    if (value is String && pattern is String) {
                        try {
                            Regex(pattern).containsMatchIn(value)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                } else null
            }
            else -> null
        }
    }

    private fun binaryOp(args: Any?, vars: Map<String, Any?>, op: (Any?, Any?) -> Any?): Any? {
        if (args is List<*> && args.size == 2) {
            val left = evaluate(args[0], vars)
            val right = evaluate(args[1], vars)
            return op(left, right)
        }
        return null
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
        if (a == null && b == null) return 0
        if (a == null || b == null) return null

        val na = toNumber(a)
        val nb = toNumber(b)
        if (na != null && nb != null) return na.compareTo(nb)

        if (a is String && b is String) return a.compareTo(b)

        return null
    }

    private fun toNumber(v: Any?): Double? {
        return when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun toBool(v: Any?): Boolean {
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotEmpty()
            null -> false
            else -> true
        }
    }
}
