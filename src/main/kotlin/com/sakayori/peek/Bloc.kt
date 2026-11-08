package com.sakayori.peek

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Bloc, from the Bloc sheet (direction A, palette P2): every face rebuilt
 * from a square, a circle and a bar. The same four silhouettes, the body cut
 * in two down the middle (strong on the left, light on the right), one ink
 * bar for both brows, and the trait as a block over the upper right outline.
 * No round corners, no gradients, no outlines.
 *
 * Expressions come from the same channels as peek, read as three moulds:
 * round eyes and a whole bar at rest, a level cut from below or above for
 * the smiling and the drowsy, a tilted cut and a bar broken in two for the
 * angry, the sad and the confused. The bar is always two halves, so it
 * breaks and mends continuously when animated.
 */

private val BLOC_RAD = PI / 180

/* Peek's silhouettes; the triangle's round joins are cut sharp and it grows
 * to the 300 box the circle and the diamond fill, its base unmoved. */
private val BLOC_TRI: List<DoubleArray> = listOf(
    doubleArrayOf(170.0, 12.8),
    doubleArrayOf(320.0, 312.8),
    doubleArrayOf(20.0, 312.8),
)

private fun blocBody(face: String): FaceBody = when (face) {
    "triangle" -> FaceBody(d = "M170 12.8L320 312.8L20 312.8Z")
    else -> FACES[face]!!.body
}

private fun blocCrown(face: String): CrownDef = when (face) {
    "triangle" -> CrownDef(poly = BLOC_TRI, r = 0.0)
    else -> FACES[face]!!.crown
}

// Bloc's own ramps: lines thicken 2.6x below 30px, eyes and trait grow
private fun blocStrokeFor(px: Double): Double =
    if (px >= 96) 1.0 else if (px >= 56) 1.25 else if (px >= 40) 1.6 else if (px >= 30) 2.0 else 2.6

private fun blocBoostFor(px: Double): Double = if (px < 30) 1.25 else if (px < 56) 1.12 else 1.0

/** A point turned by `deg` about the origin, y down. */
private fun blocTurn(x: Double, y: Double, deg: Double): DoubleArray {
    val c = cos(deg * BLOC_RAD)
    val s = sin(deg * BLOC_RAD)
    return doubleArrayOf(x * c - y * s, x * s + y * c)
}

private fun blocPoly(pts: List<DoubleArray>): String =
    "M" + pts.joinToString("L") { "${f2(it[0])} ${f2(it[1])}" } + "Z"

fun blocParts(
    who: Identity,
    pose: Pose,
    o: DrawOpts,
    defs: MutableList<SNode>,
    g: (String) -> Double,
): Pair<List<SNode>, List<SNode>> {
    val sh = FACES[who.face]!!
    val body = blocBody(who.face)
    val crown = blocCrown(who.face)
    val id = o.id
    val P = who.persona
    val spread = P.spread
    val st = blocStrokeFor(o.size)
    val up = blocBoostFor(o.size)
    val strong = BLOC[who.color]!!
    // the light half goes deep on light grounds, so the body never sinks in
    val light = if (o.frame == "bone" || o.frame == "paper") COLORS[who.color]!!.deep
    else COLORS[who.color]!!.body
    val lineW = sh.brow.w * st

    // the body: one block, its right half cut out in the light ink
    defs.add(
        n(
            "clipPath", "half", mapOf("id" to "$id-r"),
            listOf(n("rect", "half.shape", mapOf("x" to 170, "y" to -30, "width" to 200, "height" to 400))),
        ),
    )
    fun block(key: String, fill: String, clip: String?): SNode = if (body.d != null)
        n("path", key, mapOf("d" to body.d, "fill" to fill, "clip-path" to clip))
    else n(
        "circle", key, mapOf(
            "cx" to body.circle!![0], "cy" to body.circle!![1], "r" to body.circle!![2],
            "fill" to fill, "clip-path" to clip,
        ),
    )

    // eyes: a paper disc with its pupil, a solid bead, or a rimmed ring
    val eyeS = g("eyeS")
    val r0 = sqrt(sh.rx * sh.ry) * up
    val lid0 = g("lid")
    val lidAsym = g("lidAsym")
    val lower = clamp(g("lower"), 0.0, 1.0)
    val pupil = g("pupil")
    val gx = clamp(g("gx"), -1.1, 1.1)
    val gy = clamp(g("gy"), -1.1, 1.1)
    val tilt = g("lidTilt")
    val bead = who.eyes == "bead"
    val eyes = sh.eyes.mapIndexed { i, e ->
        val ex = e[0]
        val ey = e[1]
        val side = if (i == 0) -1 else 1
        val kk = "eye$i"
        var lid = clamp(lid0 + (if (i == 0) lidAsym else -lidAsym * 0.3), 0.0, 1.0)
        lid = lid + (1 - lid) * pose["blink"]
        // peek rests its lid a tenth down; a Bloc eye rests whole
        val cut = clamp((lid - 0.1) / 0.9, 0.0, 1.0)
        val r = if (bead) r0 * 0.7 * eyeS * (0.4 + 0.6 * pupil) else r0 * eyeS
        val tx = if (bead) ex + side * spread + gx * 6 else ex + side * spread
        val ty = if (bead) ey + gy * 6 else ey
        // the cuts: a straight lid from above, tilted, and a level one below
        val a = side * tilt
        val edge = -r - 1 + cut * (2 * r + 2)
        val floor = r + 1 - lower * r * 2.2
        val W = r + 2
        fun lidAt(x: Double): Double = x * tan(a * BLOC_RAD) + edge / cos(a * BLOC_RAD)
        val t0 = lidAt(-W)
        val t1 = lidAt(W)
        defs.add(
            n(
                "clipPath", "$kk.clip", mapOf("id" to "$id-e$i"),
                listOf(
                    n(
                        "path", "$kk.clip.shape", mapOf(
                            "d" to blocPoly(
                                listOf(
                                    doubleArrayOf(-W, t0),
                                    doubleArrayOf(W, t1),
                                    doubleArrayOf(W, max(floor, t1)),
                                    doubleArrayOf(-W, max(floor, t0)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val pr = sh.pr * pupil * up
        val px = gx * max(0.0, r - pr) * 0.85
        val py = gy * max(0.0, r - pr) * 0.75
        val rim = min(r * 0.27 * min(st, 1.6), r * 0.4)
        val inner = mutableListOf<SNode>()
        if (bead) {
            inner.add(n("circle", "$kk.bead", mapOf("cx" to 0, "cy" to 0, "r" to f2(r), "fill" to INK["ink"]!!)))
        } else {
            inner.add(n("circle", "$kk.white", mapOf("cx" to 0, "cy" to 0, "r" to f2(r), "fill" to INK["paper"]!!)))
            inner.add(
                n(
                    "circle", "$kk.pupil", mapOf(
                        "cx" to f2(px), "cy" to f2(py), "r" to f2(pr), "fill" to INK["ink"]!!,
                    ),
                ),
            )
        }
        if (who.eyes == "ring")
            inner.add(
                n(
                    "circle", "$kk.rim", mapOf(
                        "cx" to 0, "cy" to 0, "r" to f2(r - rim / 2), "fill" to "none",
                        "stroke" to INK["ink"]!!, "stroke-width" to f2(rim),
                    ),
                ),
            )
        // closed: a bar where the eye was
        val co = smooth(0.86, 1.0, lid)
        val yy = min(edge, r) - lineW / 2
        n(
            "g", kk, mapOf("transform" to "translate(${f2(tx)} ${f2(ty)})"),
            listOf(
                n("g", "$kk.inner", mapOf("clip-path" to "url(#$id-e$i)"), inner),
                n(
                    "path", "$kk.closed", mapOf(
                        "d" to "M${f2(-r * 0.95)} ${f2(yy)}H${f2(r * 0.95)}",
                        "transform" to "rotate(${f2(a * 0.6)})",
                        "stroke" to INK["ink"]!!,
                        "stroke-width" to f2(lineW * 1.4),
                        "opacity" to if (co > 0.01) f2(co) else 0,
                    ),
                ),
            ),
        )
    }

    // the bar: both brows in one, from one eye's outer edge to the other's.
    // The persona slants it; the expression's tilt breaks it into a V or a
    // caret; asymmetry lifts its right end. It never leaves the body.
    val bt = pose["browTilt"]
    val basym = g("browAsym")
    val th = lineW * 2 * g("browW")
    val hw = sh.eyes[1][0] - 170 + spread + sqrt(sh.rx * sh.ry) * (P.mul["eyeS"] ?: 1.0) * up
    val gap = smooth(0.0, 8.0, abs(bt)) * th * 0.9
    val slant = (P.add["browTilt"] ?: 0.0) * 0.3 - atan((1.3 * basym) / (2 * hw)) / BLOC_RAD
    val y0 = sh.brow.y + g("browY") - (eyeS - 1) * sh.ry * 0.9 - 0.35 * basym
    // `len` shortens the bar from its outer ends, 1 is full length
    fun halves(dy: Double, len: Double = 1.0): List<List<DoubleArray>> =
        listOf(-1, 1).map { s ->
            val o2 = blocTurn((s * gap) / 2, 0.0, slant)
            val x0 = 170 + o2[0]
            val yy = y0 + dy + o2[1]
            val ang = slant + s * bt
            val u = blocTurn(s * (hw * len - gap / 2), 0.0, ang)
            val nrm = blocTurn(0.0, th / 2, ang)
            listOf(
                doubleArrayOf(x0 - nrm[0], yy - nrm[1]),
                doubleArrayOf(x0 + u[0] - nrm[0], yy + u[1] - nrm[1]),
                doubleArrayOf(x0 + u[0] + nrm[0], yy + u[1] + nrm[1]),
                doubleArrayOf(x0 + nrm[0], yy + nrm[1]),
            )
        }
    fun inside(dy: Double, len: Double = 1.0): Boolean =
        halves(dy, len).all { q -> q.all { pt -> depth(crown, pt[0], pt[1]) + 2 < 0 } }
    // pushed down until it fits; if even that fails, it grows shorter
    var lo = 0.0
    var hi = 0.0
    if (!inside(0.0)) {
        hi = 60.0
        for (k in 0 until 12) {
            val mid = (lo + hi) / 2
            if (inside(mid)) hi = mid else lo = mid
        }
    }
    var len = 1.0
    if (!inside(hi)) {
        var short = 0.0
        for (k in 0 until 12) {
            val mid = (short + len) / 2
            if (inside(hi, mid)) short = mid else len = mid
        }
        len = short
    }
    val bars = halves(hi, len).mapIndexed { i, q ->
        n("path", "bar$i", mapOf("d" to blocPoly(q), "fill" to INK["ink"]!!))
    }

    // mouth: poly a turned square, round a half disc, line a bar, box a
    // block. Diamonds and triangles get a bigger one, it vanished at 16px.
    val m = sh.mouth
    val big = (if (who.face == "diamond" || who.face == "triangle") 1.25 else 1.0) * up
    val mw = m.w * max(0.05, g("mw")) * big
    val mt = g("mt") * m.d * big
    val mb = g("mb") * m.d * big
    val mid = (mt + mb) / 2
    val kb = 4.0 / 3
    val d: String = when (who.mouth) {
        "poly" -> {
            val s2 = max(mw * 0.9, (mb - mt) / 2)
            blocPoly(
                listOf(
                    doubleArrayOf(-s2, mid), doubleArrayOf(0.0, mid - s2),
                    doubleArrayOf(s2, mid), doubleArrayOf(0.0, mid + s2),
                ),
            )
        }
        "box" -> {
            val h = max(mb - mt, lineW * 1.6) / 2
            val w = mw * 0.85
            blocPoly(
                listOf(
                    doubleArrayOf(-w, mid - h), doubleArrayOf(w, mid - h),
                    doubleArrayOf(w, mid + h), doubleArrayOf(-w, mid + h),
                ),
            )
        }
        "line" -> {
            val h = (lineW * 1.4) / 2
            val w = mw * 1.5
            val y = mid * 0.5
            blocPoly(
                listOf(
                    doubleArrayOf(-w, y - h), doubleArrayOf(w, y - h),
                    doubleArrayOf(w, y + h), doubleArrayOf(-w, y + h),
                ),
            )
        }
        else ->
            "M${f2(-mw)} 0C${f2(-mw)} ${f2(mb * kb)} ${f2(mw)} ${f2(mb * kb)} ${f2(mw)} 0C${f2(mw)} ${f2(mt * kb)} ${f2(-mw)} ${f2(mt * kb)} ${f2(-mw)} 0Z"
    }
    val mouth = n(
        "path", "mouth", mapOf(
            "d" to d,
            "transform" to "translate(${f2(m.x + g("mx"))} ${f2(m.y + g("my"))}) rotate(${f2(g("mk"))})",
            "fill" to INK["ink"]!!,
        ),
    )

    // the trait, as a block over the upper right outline. Perky lifts it
    // clear, low slides it down the side.
    val hair = g("hair")
    val s = seat(crown, max(0.0, hair) * 8, 110 + max(0.0, -hair) * 40)
    val shape: SNode = when (who.trait) {
        "square" -> n("rect", "trait.shape", mapOf("x" to -21, "y" to -21, "width" to 42, "height" to 42))
        "dot" -> n("circle", "trait.shape", mapOf("cx" to 0, "cy" to 0, "r" to 22))
        "ring" -> n(
            "circle", "trait.shape", mapOf(
                "cx" to 0, "cy" to 0, "r" to 17.5, "fill" to "none",
                "stroke" to strong, "stroke-width" to 13,
            ),
        )
        "fin" -> n("path", "trait.shape", mapOf("d" to "M-27 27V-27A54 54 0 0 1 27 27Z"))
        else -> n("rect", "trait.shape", mapOf("x" to -16, "y" to -30, "width" to 32, "height" to 60))
    }
    val trait = n(
        "g", "trait", mapOf(
            "transform" to "translate(${f2(s.x)} ${f2(s.y + pose["lag"])}) scale(${f2(up)})",
            "fill" to strong,
        ),
        listOf(shape),
    )

    return Pair(
        listOf(block("body", strong, null), block("body.light", light, "url(#$id-r)"), trait),
        eyes + bars + listOf(mouth),
    )
}
