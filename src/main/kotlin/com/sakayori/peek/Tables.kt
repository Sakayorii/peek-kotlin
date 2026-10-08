package com.sakayori.peek

/*
 * The peek style's tables. Port of peek-vanilla's tables.js.
 *
 * Every list is append-only and ordered: map key order is list order, and
 * the hash indexes into it. Never reorder or remove an entry. A version
 * pins how much of each list it may pick from (VERSIONS).
 */

/** Structure inks: never a body color. */
val INK: LinkedHashMap<String, String> = linkedMapOf(
    "ink" to "#1A1918",
    "bone" to "#F3F0E8",
    "paper" to "#FFFDF8",
)

data class FaceBody(val d: String? = null, val circle: DoubleArray? = null, val stroke: Double? = null)
data class BrowDef(val y: Double, val hw: Double, val arch: Double, val w: Double)
data class MouthDef(val x: Double, val y: Double, val w: Double, val d: Double, val sw: Double)
data class CrownDef(val poly: List<DoubleArray>, val r: Double)
data class FaceDef(
    val body: FaceBody,
    val bottom: Double,
    val sink: Double,
    val eyes: List<DoubleArray>,
    val rx: Double,
    val ry: Double,
    val pr: Double,
    val brow: BrowDef,
    val cheeks: List<DoubleArray>,
    val crx: Double,
    val cry: Double,
    val mouth: MouthDef,
    val crown: CrownDef,
)

val FACES: LinkedHashMap<String, FaceDef> = linkedMapOf(
    "diamond" to FaceDef(
        body = FaceBody(d = "M170 20L320 170L170 320L20 170Z"),
        bottom = 320.0, sink = 30.0,
        eyes = listOf(doubleArrayOf(132.5, 155.0), doubleArrayOf(207.5, 155.0)),
        rx = 20.0, ry = 27.5, pr = 10.0,
        brow = BrowDef(y = 102.5, hw = 18.75, arch = 15.0, w = 5.62),
        cheeks = listOf(doubleArrayOf(117.5, 200.0), doubleArrayOf(222.5, 200.0)),
        crx = 11.25, cry = 5.62,
        mouth = MouthDef(x = 170.0, y = 218.75, w = 15.0, d = 16.25, sw = 4.38),
        crown = CrownDef(
            poly = listOf(
                doubleArrayOf(170.0, 20.0), doubleArrayOf(320.0, 170.0),
                doubleArrayOf(170.0, 320.0), doubleArrayOf(20.0, 170.0),
            ), r = 0.0,
        ),
    ),
    "semicircle" to FaceDef(
        body = FaceBody(d = "M0 255A170 170 0 0 1 340 255Z"),
        bottom = 255.0, sink = 0.0,
        eyes = listOf(doubleArrayOf(102.5, 175.0), doubleArrayOf(237.5, 175.0)),
        rx = 30.0, ry = 25.0, pr = 11.25,
        brow = BrowDef(y = 127.5, hw = 25.0, arch = 15.0, w = 5.62),
        cheeks = listOf(doubleArrayOf(55.0, 212.5), doubleArrayOf(285.0, 212.5)),
        crx = 11.25, cry = 5.62,
        mouth = MouthDef(x = 170.0, y = 221.25, w = 16.25, d = 16.25, sw = 4.38),
        crown = CrownDef(poly = listOf(doubleArrayOf(170.0, 255.0)), r = 170.0),
    ),
    "circle" to FaceDef(
        body = FaceBody(circle = doubleArrayOf(170.0, 170.0, 150.0)),
        bottom = 320.0, sink = 20.0,
        eyes = listOf(doubleArrayOf(112.8, 162.2), doubleArrayOf(227.2, 162.2)),
        rx = 29.9, ry = 29.9, pr = 13.0,
        brow = BrowDef(y = 110.2, hw = 23.4, arch = 15.6, w = 5.85),
        cheeks = listOf(doubleArrayOf(84.2, 203.8), doubleArrayOf(255.8, 203.8)),
        crx = 11.7, cry = 5.85,
        mouth = MouthDef(x = 170.0, y = 223.3, w = 16.9, d = 16.9, sw = 4.55),
        crown = CrownDef(poly = listOf(doubleArrayOf(170.0, 170.0)), r = 150.0),
    ),
    "triangle" to FaceDef(
        body = FaceBody(d = "M170 40.8L312.8 299.2L27.2 299.2Z", stroke = 27.2),
        bottom = 312.8, sink = 0.0,
        eyes = listOf(doubleArrayOf(132.6, 210.8), doubleArrayOf(207.4, 210.8)),
        rx = 20.4, ry = 27.2, pr = 10.0,
        brow = BrowDef(y = 166.6, hw = 18.7, arch = 15.3, w = 5.62),
        cheeks = listOf(doubleArrayOf(98.6, 238.0), doubleArrayOf(241.4, 238.0)),
        crx = 11.5, cry = 5.75,
        mouth = MouthDef(x = 170.0, y = 254.0, w = 17.0, d = 16.25, sw = 4.38),
        crown = CrownDef(
            poly = listOf(
                doubleArrayOf(170.0, 40.8), doubleArrayOf(312.8, 299.2),
                doubleArrayOf(27.2, 299.2),
            ), r = 13.6,
        ),
    ),
)

data class ColorDef(val body: String, val deep: String)

/** Body inks, each with a deep partner at the same hue. */
val COLORS: LinkedHashMap<String, ColorDef> = linkedMapOf(
    "lavender" to ColorDef(body = "#D8CDF0", deep = "#BDAEE6"),
    "fog" to ColorDef(body = "#C8D6E8", deep = "#A8BCDC"),
    "clay" to ColorDef(body = "#F1CDBF", deep = "#E2A893"),
    "mint" to ColorDef(body = "#CFE7D6", deep = "#A5CDB1"),
    "butter" to ColorDef(body = "#ECE2B9", deep = "#D9C284"),
    "rose" to ColorDef(body = "#EFCAD7", deep = "#DFA4BA"),
    "aqua" to ColorDef(body = "#BDE1E5", deep = "#8FC7D1"),
)

/** Discrete anatomy. */
val PARTS: LinkedHashMap<String, List<String>> = linkedMapOf(
    "eyes" to listOf("oval", "bead", "ring"),
    "brows" to listOf("arch", "bar", "wedge", "dash"),
    "mouth" to listOf("poly", "round", "line", "box"),
    "cheeks" to listOf("oval", "dots", "lines"),
    "trait" to listOf("square", "fin", "ring", "dot", "peak"),
)

/** How much of each list a version may pick from. Append-only. */
val VERSIONS: Map<Int, LinkedHashMap<String, Int>> = mapOf(
    1 to linkedMapOf(
        "face" to 4, "color" to 7, "eyes" to 3, "brows" to 4,
        "mouth" to 4, "cheeks" to 3, "trait" to 5,
    ),
)
const val LATEST = 1

/* ---------- state: channels and the 11 expressions ---------- */

val GROUPS: LinkedHashMap<String, List<String>> = linkedMapOf(
    "eyes" to listOf("lid", "lower", "lidTilt", "eyeS", "lidAsym"),
    "pupil" to listOf("pupil", "gx", "gy", "shine"),
    "brows" to listOf("browY", "browTilt", "browArch", "browW", "browAsym"),
    "mouth" to listOf("mw", "mt", "mb", "mx", "my", "mk"),
    "body" to listOf("alt", "rot", "x", "sx", "sy"),
    "extra" to listOf("blush", "hair", "dim", "thing"),
)

private val BASE: LinkedHashMap<String, Double> = linkedMapOf(
    "alt" to 0.0, "rot" to 0.0, "x" to 0.0, "sx" to 1.0, "sy" to 1.0,
    "lid" to 0.1, "lower" to 0.0, "lidTilt" to 0.0, "eyeS" to 1.0, "lidAsym" to 0.0,
    "pupil" to 1.0, "gx" to 0.0, "gy" to 0.0, "shine" to 0.7,
    "browY" to 0.0, "browTilt" to 0.0, "browArch" to 0.4, "browW" to 1.0, "browAsym" to 0.0,
    "mw" to 0.72, "mt" to 0.0, "mb" to 0.5, "mx" to 0.0, "my" to 0.0, "mk" to 0.0,
    "blush" to 0.0, "hair" to 0.0, "dim" to 0.0, "thing" to 0.0,
)

private fun S(vararg overrides: Pair<String, Double>): LinkedHashMap<String, Double> =
    LinkedHashMap(BASE).apply { overrides.forEach { (k, v) -> this[k] = v } }

val EXPRESSIONS: LinkedHashMap<String, LinkedHashMap<String, Double>> = linkedMapOf(
    "normal" to S(),
    "happy" to S(
        "lid" to 0.0, "lower" to 0.42, "shine" to 1.0, "gy" to -0.05,
        "browY" to -5.0, "browArch" to 1.0, "mw" to 1.0, "mb" to 1.0,
        "blush" to 1.0, "hair" to 0.45, "alt" to 0.05,
    ),
    "sad" to S(
        "lid" to 0.42, "lidTilt" to 16.0, "pupil" to 0.95, "gy" to 0.8,
        "shine" to 0.0, "browY" to 3.0, "browTilt" to 14.0, "browArch" to 0.0,
        "mw" to 0.8, "mt" to -0.55, "mb" to -0.55, "my" to 6.0,
        "alt" to -0.16, "sy" to 0.985, "hair" to -0.75,
    ),
    "angry" to S(
        "lid" to 0.34, "lidTilt" to -22.0, "lower" to 0.12, "pupil" to 0.82,
        "gy" to 0.1, "shine" to 0.0, "browY" to 5.0, "browTilt" to -21.0,
        "browArch" to 0.0, "browW" to 1.3, "mw" to 0.8, "mt" to -0.08,
        "mb" to -0.08, "my" to 3.0, "sx" to 1.02, "sy" to 0.98, "hair" to 0.25,
    ),
    "sleepy" to S(
        "lid" to 1.0, "lidTilt" to 4.0, "pupil" to 0.9, "gy" to 0.5,
        "shine" to 0.0, "browY" to 6.0, "browTilt" to 3.0, "browArch" to 0.15,
        "browW" to 0.9, "mw" to 0.35, "mt" to -0.3, "mb" to 0.3, "my" to 2.0,
        "alt" to -1.12, "rot" to -3.0, "hair" to -1.0,
    ),
    "curious" to S(
        "lid" to 0.0, "eyeS" to 1.06, "pupil" to 1.05, "shine" to 1.0,
        "browY" to -3.0, "browAsym" to 9.0, "browArch" to 0.8, "mw" to 0.38,
        "mt" to -0.36, "mb" to 0.36, "rot" to 6.0, "alt" to -0.32,
        "hair" to 0.3, "thing" to 1.0,
    ),
    "surprised" to S(
        "lid" to 0.0, "eyeS" to 1.2, "pupil" to 0.66, "shine" to 0.8,
        "browY" to -13.0, "browArch" to 1.0, "mw" to 0.55, "mt" to -0.62,
        "mb" to 0.62, "alt" to 0.36, "hair" to 1.0,
    ),
    "excited" to S(
        "lid" to 0.0, "lower" to 0.5, "eyeS" to 1.08, "pupil" to 1.08,
        "shine" to 1.0, "browY" to -9.0, "browArch" to 1.0, "mw" to 1.15,
        "mb" to 1.3, "blush" to 1.0, "hair" to 1.0, "alt" to 0.12,
    ),
    "confused" to S(
        "lid" to 0.22, "lidAsym" to 0.28, "lidTilt" to -4.0, "pupil" to 0.92,
        "gx" to -0.35, "gy" to -0.45, "shine" to 0.3, "browY" to -2.0,
        "browAsym" to 11.0, "browTilt" to -5.0, "browArch" to 0.3, "mw" to 0.6,
        "mt" to -0.1, "mb" to 0.12, "mx" to 5.0, "mk" to 13.0,
        "rot" to -7.0, "alt" to -0.05, "hair" to 0.1,
    ),
    "bored" to S(
        "lid" to 0.5, "pupil" to 0.9, "gx" to -0.55, "gy" to 0.25,
        "shine" to 0.0, "browY" to 3.0, "browArch" to 0.1, "mw" to 0.55,
        "mt" to 0.0, "mb" to 0.04, "mx" to -5.0, "alt" to -0.34,
        "hair" to -0.45, "dim" to 1.0,
    ),
    "attentive" to S(
        "lid" to 0.0, "eyeS" to 1.1, "pupil" to 0.8, "gy" to -0.1,
        "shine" to 1.0, "browY" to -7.0, "browArch" to 0.7, "mw" to 0.55,
        "mb" to 0.4, "alt" to 0.15, "sy" to 1.03, "hair" to 0.65,
    ),
)
