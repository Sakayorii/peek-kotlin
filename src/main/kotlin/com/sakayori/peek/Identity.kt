package com.sakayori.peek

/*
 * A name in, an identity out. Port of peek-vanilla's identity.js, which is
 * itself a port of the identity logic in Doan Labs' Peek.
 *
 * Every axis hashes on its own seed, so changing one list never moves
 * another axis: seed(axis) = fnv1a("peek@1:axis:tidy(name)").
 */

const val STYLE = "peek"

/** Lone surrogates, which TextEncoder maps to U+FFFD (Java would use '?'). */
private val LONE_SURROGATE =
    Regex("[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]")

/**
 * FNV-1a over the UTF-8 bytes, with exactly the WHATWG TextEncoder semantics
 * (lone surrogates become U+FFFD, not '?').
 */
fun fnv1a(str: String): UInt {
    var h = 0x811c9dc5.toInt()
    val bytes = LONE_SURROGATE.replace(str, "\uFFFD").toByteArray(Charsets.UTF_8)
    for (b in bytes) {
        h = h xor (b.toInt() and 0xFF) // bytes are 0..255, like TextEncoder
        h *= 0x01000193.toInt()
    }
    return h.toUInt()
}

/** Seeded PRNG. The returned lambda is stateful, like the JS closure. */
fun mulberry32(seed: UInt): () -> Double {
    var a = seed.toInt()
    return {
        a += 0x6d2b79f5.toInt()
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

/** What each axis picks from, in list order. Append-only. */
val LISTS: LinkedHashMap<String, List<String>> = linkedMapOf(
    "face" to FACES.keys.toList(),
    "color" to COLORS.keys.toList(),
    "eyes" to PARTS["eyes"]!!,
    "brows" to PARTS["brows"]!!,
    "mouth" to PARTS["mouth"]!!,
    "cheeks" to PARTS["cheeks"]!!,
    "trait" to PARTS["trait"]!!,
)

/** The axes in readout order. */
val AXES: List<String> = LISTS.keys.toList()

fun seed(name: String, axis: String, version: Int = LATEST): UInt =
    fnv1a("$STYLE@$version:$axis:${tidy(name)}")

data class Persona(
    val spread: Double,
    val blink: Double,
    val add: LinkedHashMap<String, Double>,
    val mul: LinkedHashMap<String, Double>,
)

data class Identity(
    val key: String,
    val hash: UInt,
    val version: Int,
    val face: String,
    val color: String,
    val eyes: String,
    val brows: String,
    val mouth: String,
    val cheeks: String,
    val trait: String,
    val persona: Persona,
)

fun identify(name: String, version: Int = LATEST): Identity {
    val lengths = VERSIONS[version]
        ?: throw IllegalArgumentException("peek@$version does not exist")
    fun pick(axis: String): String {
        val list = LISTS[axis]!!
        return list[(seed(name, axis, version) % lengths[axis]!!.toUInt()).toInt()]
    }
    val rnd = mulberry32(seed(name, "persona", version))
    fun r(a: Double, b: Double): Double = jsRound((a + rnd() * (b - a)) * 100) / 100.0
    // map order is the draw order: never reorder these entries
    val persona = Persona(
        spread = r(-7.0, 7.0),
        blink = r(0.75, 1.4),
        add = linkedMapOf(
            "browY" to r(-4.0, 4.0),
            "browTilt" to r(-5.0, 5.0),
            "browArch" to r(-0.2, 0.3),
            "rot" to r(-3.0, 3.0),
            "hair" to r(-0.2, 0.35),
            "gx" to r(-0.12, 0.12),
            "my" to r(-2.0, 3.0),
        ),
        mul = linkedMapOf(
            "eyeS" to r(0.92, 1.08),
            "pupil" to r(0.9, 1.12),
            "browW" to r(0.85, 1.2),
            "mw" to r(0.85, 1.18),
        ),
    )
    val key = tidy(name)
    return Identity(
        key = key,
        hash = fnv1a(key),
        version = version,
        face = pick("face"),
        color = pick("color"),
        eyes = pick("eyes"),
        brows = pick("brows"),
        mouth = pick("mouth"),
        cheeks = pick("cheeks"),
        trait = pick("trait"),
        persona = persona,
    )
}
