package com.sakayori.peek

import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/*
 * JavaScript semantics, exactly. Every helper here exists so the Kotlin port
 * produces byte-identical output to peek-vanilla: the same doubles, printed
 * the same way, hashed the same way.
 */

/** JS Math.round: ties toward +Infinity. Exact for the magnitudes we use. */
fun jsRound(v: Double): Long = floor(v + 0.5).toLong()

/**
 * JS String(number): integral values print bare ("2", not "2.0"), the rest
 * use the shortest round-trip decimal. Also maps -0 to "0" like JS.
 */
fun jsNumToString(v: Double): String {
    if (v.isNaN()) return "NaN"
    if (v == 0.0) return "0" // catches -0.0: JS String(-0) === "0"
    if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
    val l = v.toLong()
    if (l.toDouble() == v && v > -1e21 && v < 1e21) return l.toString()
    // Our values are coordinates in [-400, 400], so Java's E-notation never
    // triggers in practice; this is a safety net that matches JS formatting.
    val s = v.toString()
    if ('E' in s) {
        val plain = java.math.BigDecimal(v).toPlainString().trimEnd('0').trimEnd('.')
        return if (plain == "-0" || plain.isEmpty()) "0" else plain
    }
    return s
}

/** String(Math.round(v * 100) / 100) */
fun f2(v: Double): String = jsNumToString(jsRound(v * 100) / 100.0)

/** String(Math.round(v * 1000) / 1000) */
fun f3(v: Double): String = jsNumToString(jsRound(v * 1000) / 1000.0)

/**
 * JS /\s+/ without the /u flag: the WhiteSpace + LineTerminator list.
 * (Java's unicode \s would wrongly include U+0085.)
 */
private fun isJsSpace(c: Char): Boolean = c in '\u0009'..'\u000D' ||
    c == '\u0020' || c == '\u00A0' || c == '\u1680' ||
    c in '\u2000'..'\u200A' || c == '\u2028' || c == '\u2029' ||
    c == '\u202F' || c == '\u205F' || c == '\u3000' || c == '\uFEFF'

private val WS_RUN = Regex("[\u0009-\u000D\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+")

/** s.normalize('NFC').trim().replace(/\s+/g, ' ').toLowerCase() */
fun tidy(s: String): String =
    WS_RUN.replace(
        Normalizer.normalize(s, Normalizer.Form.NFC).trim(::isJsSpace),
        " ",
    ).lowercase(Locale.ROOT)

/* ---------- minimal JSON.stringify for settle()'s id ---------- */

sealed interface JVal {
    data class Str(val v: String) : JVal
    data class Num(val v: Double) : JVal
    data class Bool(val v: Boolean) : JVal
    data object Null : JVal
    data class Arr(val items: List<JVal>) : JVal
    /** Insertion order is significant. */
    data class Obj(val entries: LinkedHashMap<String, JVal>) : JVal
}

private fun StringBuilder.appendJsonString(s: String) {
    append('"')
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\b' -> append("\\b")
            c == '\u000C' -> append("\\f")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> {
                append("\\u00")
                append(c.code.toString(16).padStart(2, '0'))
            }
            c.isHighSurrogate() -> {
                // a valid pair goes out raw, like JSON.stringify; a lone one is escaped
                val d = s.getOrNull(i + 1)
                if (d != null && d.isLowSurrogate()) {
                    append(c)
                    append(d)
                    i++
                } else {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                }
            }
            c.isLowSurrogate() -> {
                // lone (a paired one was consumed with its high half above)
                append("\\u")
                append(c.code.toString(16).padStart(4, '0'))
            }
            else -> append(c)
        }
        i++
    }
    append('"')
}

private fun StringBuilder.appendJson(v: JVal) {
    when (v) {
        is JVal.Str -> appendJsonString(v.v)
        is JVal.Num -> append(jsNumToString(v.v))
        is JVal.Bool -> append(if (v.v) "true" else "false")
        is JVal.Null -> append("null")
        is JVal.Arr -> {
            append('[')
            v.items.forEachIndexed { i, item ->
                if (i > 0) append(',')
                appendJson(item)
            }
            append(']')
        }
        is JVal.Obj -> {
            append('{')
            v.entries.entries.forEachIndexed { i, (k, item) ->
                if (i > 0) append(',')
                appendJsonString(k)
                append(':')
                appendJson(item)
            }
            append('}')
        }
    }
}

/** JSON.stringify for the narrow shapes settle() hashes. No spaces, like JS. */
fun jsJsonStringify(v: JVal): String = buildString { appendJson(v) }
