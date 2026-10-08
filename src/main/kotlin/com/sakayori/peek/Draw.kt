package com.sakayori.peek

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * One pure geometry function: an identity and a pose in, a plain node tree
 * out. Port of peek-vanilla's draw.js.
 *
 * Structure depends only on the identity and the options, never the pose,
 * so a live tree keeps the same nodes frame to frame. A static tree drops
 * what is invisible (opacity 0) to stay small.
 */

/** The frame: viewBox -30 -30 400 400, the floor on its bottom edge. */
val VB = doubleArrayOf(-30.0, -30.0, 400.0, 400.0)
private const val FLOOR = 370.0
private val THING = doubleArrayOf(330.0, 20.0)
private val RAD = PI / 180

fun clamp(v: Double, a: Double, b: Double): Double = if (v < a) a else if (v > b) b else v

fun smooth(a: Double, b: Double, v: Double): Double {
    val t = clamp((v - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)
}

fun strokeFor(px: Double): Double =
    if (px >= 96) 1.0 else if (px >= 56) 1.25 else if (px >= 40) 1.6 else if (px >= 30) 2.0 else 2.4

/** Pose: expression channels in BASE order, plus blink/lag, plus the expression name. */
data class Pose(val channels: LinkedHashMap<String, Double>, val expression: String) {
    operator fun get(c: String): Double = channels[c] ?: 0.0
}

/** Where a face rests: the body offset, the eye line, one altitude unit. */
data class Frame(val baseY: Double, val eyeY: Double, val R: Double)

fun frameOf(face: FaceDef): Frame {
    val baseY = FLOOR + face.sink - face.bottom
    val eyeY = face.eyes[0][1]
    // altitude -1 puts the eye line on the floor
    return Frame(baseY, eyeY, FLOOR - (eyeY + baseY))
}

/** The eyes' aim at the thing in the corner, from wherever they are. */
fun thingGaze(face: FaceDef, x: Double, alt: Double): DoubleArray {
    val (baseY, eyeY, R) = frameOf(face)
    val ey = eyeY + baseY - alt * R
    return doubleArrayOf(
        clamp((THING[0] - 170 - x) / 150, -1.0, 1.0),
        clamp((THING[1] - ey) / 150, -1.0, 1.0),
    )
}

/**
 * The pose an expression settles to. A curious face ends up looking at the
 * thing in the corner; an explicit gaze wins over both.
 */
fun restPose(who: Identity, expression: String, gaze: DoubleArray?): Pose {
    val s = EXPRESSIONS[expression]
        ?: throw IllegalArgumentException("unknown expression: $expression")
    val channels = LinkedHashMap(s)
    channels["blink"] = 0.0
    channels["lag"] = 0.0
    // the choreography's last key leaves curious leaning 7, not the table's 6
    if (expression == "curious") channels["rot"] = 7.0
    val g = gaze ?: if (expression == "curious") thingGaze(FACES[who.face]!!, 0.0, s["alt"]!!) else null
    if (g != null) {
        channels["gx"] = g[0]
        channels["gy"] = g[1]
    }
    return Pose(channels, expression)
}

data class Seat(val x: Double, val y: Double, val a: Double)

/**
 * A point on the crown outline grown by `d`, reached by walking `u` along it
 * from the top (where the outward normal points straight up); u > 0 walks
 * right. Returns the point and the normal's bearing in degrees.
 */
fun seat(crown: CrownDef, d: Double, u: Double): Seat {
    val R = max(0.0, crown.r + d)
    fun at(p: DoubleArray, b: Double) = Seat(p[0] + R * sin(b), p[1] - R * cos(b), b / RAD)
    val P = crown.poly
    if (P.size == 1) return at(P[0], if (R != 0.0) u / R else 0.0)
    val n = P.size
    val dir = if (u >= 0) 1 else -1
    var left = abs(u)
    var i = 0
    var b = 0.0
    for (step in 0 until n) {
        val j = (i + dir + n) % n
        val (x0, y0) = P[i]
        val (x1, y1) = P[j]
        val ex = x1 - x0
        val ey = y1 - y0
        val L = hypot(ex, ey)
        val nx = (dir * ey) / L
        val ny = (-dir * ex) / L
        val be = atan2(nx, -ny)
        var db = be - b
        while (dir * db < 0) db += dir * 2 * PI
        val arc = R * abs(db)
        if (left <= arc) return at(P[i], if (R != 0.0) b + (dir * left) / R else b)
        left -= arc
        if (left <= L) return Seat(
            x0 + (ex / L) * left + R * nx,
            y0 + (ey / L) * left + R * ny,
            be / RAD,
        )
        left -= L
        i = j
        b = be
    }
    return at(P[i], b)
}

/** Signed distance from a point to the crown outline, negative inside. */
fun depth(crown: CrownDef, x: Double, y: Double): Double {
    val P = crown.poly
    if (P.size == 1) return hypot(x - P[0][0], y - P[0][1]) - crown.r
    var out = Double.NEGATIVE_INFINITY
    var near = Double.POSITIVE_INFINITY
    for (i in P.indices) {
        val a = P[i]
        val b = P[(i + 1) % P.size]
        val ex = b[0] - a[0]
        val ey = b[1] - a[1]
        val L2 = ex * ex + ey * ey
        val L = sqrt(L2)
        out = max(out, ((x - a[0]) * ey - (y - a[1]) * ex) / L)
        val t = clamp(((x - a[0]) * ex + (y - a[1]) * ey) / L2, 0.0, 1.0)
        near = min(near, hypot(x - a[0] - t * ex, y - a[1] - t * ey))
    }
    return (if (out <= 0) out else near) - crown.r
}

private val RING_U = 158 * 35 * RAD

/**
 * The ring's rest position and how far up it may ride, as (rest, min).
 * Port of ringWalk in draw.js.
 */
fun ringWalk(sh: FaceDef): Pair<Double, Double> {
    val ex = sh.eyes[1][0] + sh.brow.hw + 7
    val top = sh.brow.y - 30
    val bottom = sh.brow.y + 8
    var u = RING_U
    while (u < 400) {
        val s = seat(sh.crown, 8.0, u)
        val dx = s.x - ex
        val dy = s.y - clamp(s.y, top, bottom)
        if (if (s.x > ex) hypot(dx, dy) > 27 else s.y < top - 27) break
        u += 2
    }
    return if (u > RING_U) Pair(u + 20, u) else Pair(u, Double.NEGATIVE_INFINITY)
}

fun draw(who: Identity, pose: Pose, o: DrawOpts): SNode {
    val sh = FACES[who.face]!!
    val color = COLORS[who.color]!!
    val fill = color.body
    val deep = color.deep
    val P = who.persona
    val mul = P.mul
    val add = P.add
    fun g(c: String): Double = pose[c] * (mul[c] ?: 1.0) + (add[c] ?: 0.0)
    val spread = P.spread
    val id = o.id
    val st = strokeFor(o.size)
    val (baseY, _, R) = frameOf(sh)
    val defs = mutableListOf<SNode>()
    // the frame
    defs.add(
        n(
            "clipPath", "frame.clip", mapOf("id" to "$id-f"),
            listOf(
                if (o.square) n(
                    "rect", "frame.clip.shape",
                    mapOf("x" to VB[0], "y" to VB[1], "width" to VB[2], "height" to VB[3]),
                )
                else n("circle", "frame.clip.shape", mapOf("cx" to 170, "cy" to 170, "r" to 200)),
            ),
        ),
    )
    // riso: registration error grows with size; a different seed per plate
    val k = if (o.riso) smooth(120.0, 480.0, o.size) else 0.0
    val riso = k > 0.01
    val upp = VB[2] / max(o.size, 1.0)
    val mk = k * k
    fun plate(p: String, seedA: Int, seedB: Int, sc: Double): SNode =
        n(
            "filter", "riso.$p", mapOf(
                "id" to "$id-p$p",
                "x" to "-20%", "y" to "-20%", "width" to "140%", "height" to "140%",
                "color-interpolation-filters" to "sRGB",
            ),
            listOf(
                n(
                    "feTurbulence", "riso.$p.warp", mapOf(
                        "type" to "fractalNoise", "baseFrequency" to "0.032",
                        "numOctaves" to 2, "seed" to seedA, "result" to "warp",
                    ),
                ),
                n(
                    "feDisplacementMap", "riso.$p.disp", mapOf(
                        "in" to "SourceGraphic", "in2" to "warp", "scale" to f2(sc * k),
                        "xChannelSelector" to "R", "yChannelSelector" to "G", "result" to "moved",
                    ),
                ),
                n(
                    "feTurbulence", "riso.$p.grain", mapOf(
                        "type" to "fractalNoise", "baseFrequency" to "0.85",
                        "numOctaves" to 2, "seed" to seedB, "result" to "grain",
                    ),
                ),
                n(
                    "feColorMatrix", "riso.$p.mask", mapOf(
                        "in" to "grain", "type" to "matrix",
                        "values" to "0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  ${f3(-8 * mk)} 0 0 0 ${f3(1 + 5.85 * mk)}",
                        "result" to "mask",
                    ),
                ),
                n(
                    "feComposite", "riso.$p.out", mapOf(
                        "in" to "moved", "in2" to "mask", "operator" to "in",
                    ),
                ),
            ),
        )
    val grainy = riso && o.frame != "none"
    if (riso) {
        defs.add(plate("A", 4, 11, 3.2))
        defs.add(plate("B", 9, 23, 2.2))
    }
    if (grainy) {
        // bone speckle on ink, ink speckle on light grounds
        val (r, gg, b) = if (o.frame == "ink") doubleArrayOf(0.953, 0.941, 0.91)
        else doubleArrayOf(0.102, 0.098, 0.094)
        defs.add(
            n(
                "filter", "grain.filter", mapOf(
                    "id" to "$id-grain", "x" to 0, "y" to 0, "width" to 1, "height" to 1,
                    "color-interpolation-filters" to "sRGB",
                ),
                listOf(
                    n(
                        "feTurbulence", "grain.noise", mapOf(
                            "type" to "fractalNoise", "baseFrequency" to "0.9",
                            "numOctaves" to 2, "seed" to 5,
                        ),
                    ),
                    n(
                        "feColorMatrix", "grain.matrix", mapOf(
                            "type" to "matrix",
                            "values" to "0 0 0 0 ${jsNumToString(r)}  0 0 0 0 ${jsNumToString(gg)}  0 0 0 0 ${jsNumToString(b)}  7 0 0 0 -5.1",
                        ),
                    ),
                ),
            ),
        )
    }
    // colour drains a little when bored
    val dim = clamp(g("dim"), 0.0, 1.0)
    if (o.live || dim > 0.01)
        defs.add(
            n(
                "filter", "dim.filter",
                mapOf("id" to "$id-dim", "color-interpolation-filters" to "sRGB"),
                listOf(
                    n(
                        "feColorMatrix", "dim.matrix",
                        mapOf("type" to "saturate", "values" to f3(1 - dim * 0.55)),
                    ),
                ),
            ),
        )
    // the body
    val alt = g("alt")
    val rot = g("rot")
    val x = g("x")
    val sx = g("sx")
    val sy = g("sy")
    val B = sh.bottom
    val ty = baseY - alt * R
    fun sc(v: Double) = jsRound(v * 100000) / 100000.0
    val bodyNode = if (sh.body.d != null)
        n(
            "path", "body", mapOf(
                "d" to sh.body.d,
                "fill" to fill,
                "stroke" to if (sh.body.stroke != null) fill else null,
                "stroke-width" to sh.body.stroke,
                "stroke-linejoin" to if (sh.body.stroke != null) "round" else null,
            ),
        )
    else n(
        "circle", "body", mapOf(
            "cx" to sh.body.circle!![0], "cy" to sh.body.circle!![1],
            "r" to sh.body.circle!![2], "fill" to fill,
        ),
    )
    // eyes
    val eyeS = g("eyeS")
    val rx = sh.rx * eyeS
    val ry = sh.ry * eyeS
    val lid0 = g("lid")
    val lidAsym = g("lidAsym")
    val lower = clamp(g("lower"), 0.0, 1.0)
    val blink = pose["blink"]
    val pupil = g("pupil")
    val gx = clamp(g("gx"), -1.1, 1.1)
    val gy = clamp(g("gy"), -1.1, 1.1)
    val tilt = g("lidTilt")
    val shine = clamp(g("shine"), 0.0, 1.0)
    val sw = sh.brow.w * st
    val bead = who.eyes == "bead"
    val eyes = sh.eyes.mapIndexed { i, e ->
        val ex = e[0]
        val ey = e[1]
        val side = if (i == 0) -1 else 1
        val kk = "eye$i"
        var lid = clamp(lid0 + (if (i == 0) lidAsym else -lidAsym * 0.3), 0.0, 1.0)
        lid = lid + (1 - lid) * blink
        defs.add(
            n(
                "clipPath", "$kk.clip", mapOf("id" to "$id-e$i"),
                listOf(
                    n(
                        "ellipse", "$kk.clip.shape",
                        mapOf("cx" to 0, "cy" to 0, "rx" to f2(rx), "ry" to f2(ry)),
                    ),
                ),
            ),
        )
        defs.add(
            n(
                "clipPath", "$kk.lidclip", mapOf("id" to "$id-l$i"),
                listOf(
                    n(
                        "ellipse", "$kk.lidclip.shape",
                        mapOf("cx" to 0, "cy" to 0, "rx" to f2(rx + 1.6), "ry" to f2(ry + 1.6)),
                    ),
                ),
            ),
        )
        // what sits in the socket: a pupil on paper, a bare bead, or a rimmed eye
        val br = min(sh.rx, sh.ry) * 0.62
        val pr = if (bead) br * eyeS * (0.4 + 0.6 * pupil) else sh.pr * pupil
        val base = if (bead) br else sh.pr
        val px = gx * max(0.0, sh.rx - base) * 0.9
        val py = gy * max(0.0, sh.ry - base) * 0.8
        // capped so a small rimmed eye stays an eye, not an ink blot
        val rimW = min(sw * 0.8, min(sh.rx, sh.ry) * 0.28)
        val inner = mutableListOf<SNode>()
        if (!bead)
            inner.add(
                n(
                    "ellipse", "$kk.white",
                    mapOf(
                        "cx" to 0, "cy" to 0, "rx" to f2(rx), "ry" to f2(ry),
                        "fill" to INK["paper"]!!,
                    ),
                ),
            )
        inner.add(
            n(
                "circle", "$kk.pupil",
                mapOf(
                    "cx" to f2(px), "cy" to f2(py), "r" to f2(pr),
                    "fill" to INK["ink"]!!,
                ),
            ),
        )
        inner.add(
            n(
                "circle", "$kk.shine",
                mapOf(
                    "cx" to f2(px + pr * 0.44), "cy" to f2(py - pr * 0.44),
                    "r" to f2(pr * (if (bead) 0.26 else 0.3)),
                    "fill" to INK["paper"]!!, "opacity" to f2(shine),
                ),
            ),
        )
        if (who.eyes == "ring")
            inner.add(
                n(
                    "ellipse", "$kk.rim",
                    mapOf(
                        "cx" to 0, "cy" to 0, "rx" to f2(rx - rimW / 2),
                        "ry" to f2(ry - rimW / 2), "fill" to "none",
                        "stroke" to INK["ink"]!!, "stroke-width" to f2(rimW),
                    ),
                ),
            )
        // upper lid: a straight cut, tilted, same as the v1.0 sad and angry lids
        val edge = -ry - 1.6 + lid * (2 * ry + 3.2)
        val a = side * tilt
        // lower lid: a wide arc pushing up from below, the smiling squint
        val lrx = rx * 1.55
        val lry = ry * 1.15
        val top = ry + 1.6 - lower * ry * 1.5
        val parts = mutableListOf(
            n("g", "$kk.inner", mapOf("clip-path" to "url(#$id-e$i)"), inner),
            n(
                "g", "$kk.lids", mapOf("clip-path" to "url(#$id-l$i)"),
                listOf(
                    n(
                        "rect", "$kk.lid", mapOf(
                            "x" to f2(-rx * 2.2), "y" to f2(-ry * 3),
                            "width" to f2(rx * 4.4),
                            "height" to f2(max(0.0, edge + ry * 3)),
                            "transform" to "rotate(${f2(a)})", "fill" to fill,
                        ),
                    ),
                    n(
                        "ellipse", "$kk.lower", mapOf(
                            "cx" to 0, "cy" to f2(top + lry),
                            "rx" to f2(lrx), "ry" to f2(lry), "fill" to fill,
                        ),
                    ),
                ),
            ),
        )
        // a rimmed eye inks its lid edges too, so the outline follows the lids
        if (who.eyes == "ring")
            parts.add(
                n(
                    "g", "$kk.edges", mapOf(
                        "clip-path" to "url(#$id-e$i)", "fill" to "none",
                        "stroke" to INK["ink"]!!, "stroke-width" to f2(rimW),
                    ),
                    listOf(
                        n(
                            "path", "$kk.edge", mapOf(
                                "d" to "M${f2(-rx * 2.2)} ${f2(edge)}H${f2(rx * 2.2)}",
                                "transform" to "rotate(${f2(a)})",
                                "opacity" to if (lid > 0.02) 1 else 0,
                            ),
                        ),
                        n(
                            "ellipse", "$kk.edge.lower", mapOf(
                                "cx" to 0, "cy" to f2(top + lry),
                                "rx" to f2(lrx), "ry" to f2(lry),
                                "opacity" to if (lower > 0.02) 1 else 0,
                            ),
                        ),
                    ),
                ),
            )
        // closed eye: a hairline drawn once the lid is down
        val co = smooth(0.86, 1.0, lid)
        val yy = min(edge, ry) - 1
        val hw = rx * 0.92
        parts.add(
            n(
                "path", "$kk.closed", mapOf(
                    "d" to "M${f2(-hw)} ${f2(yy - 2)}Q0 ${f2(yy + 6)} ${f2(hw)} ${f2(yy - 2)}",
                    "transform" to "rotate(${f2(a * 0.6)})", "fill" to "none",
                    "stroke" to INK["ink"]!!, "stroke-linecap" to "round",
                    "stroke-width" to f2(sw * 0.85),
                    "opacity" to if (co > 0.01) f2(co) else 0,
                ),
            ),
        )
        n(
            "g", kk, mapOf(
                // a bead has no white to show where it looks, so the whole eye leans
                "transform" to if (bead)
                    "translate(${f2(ex + side * spread + gx * 6)} ${f2(ey + gy * 6)})"
                else
                    "translate(${f2(ex + side * spread)} ${jsNumToString(ey)})",
            ),
            parts,
        )
    }
    // brows
    val browY = g("browY") - (eyeS - 1) * sh.ry * 0.9
    val btilt = g("browTilt")
    val arch = g("browArch")
    val basym = g("browAsym")
    val bw = sw * g("browW")
    val brows = sh.eyes.mapIndexed { i, e ->
        val ex = e[0]
        val side = if (i == 0) -1 else 1
        val hw = sh.brow.hw
        val c = -sh.brow.arch * arch
        // half-length, arch depth and stroke weight per type
        val (w, ca, k2) = when (who.brows) {
            "bar" -> Triple(hw * 0.9, c * 0.3, 1.75)
            "dash" -> Triple(hw * 0.42, c * 0.25, 1.9)
            else -> Triple(hw, c, 1.0)
        }
        // wedge: tapered, heavy at the inner end, toward the nose
        val ti = bw * 1.7
        val to = bw * 0.45
        val (tl, tr) = if (side < 0) Pair(to, ti) else Pair(ti, to)
        val wedge = who.brows == "wedge"
        val (hl, hr) = if (wedge) Pair(tl / 2 + to / 2, tr / 2 + to / 2) else Pair(0.0, 0.0)
        fun half(t: Double) = if (wedge) hl + (hr - hl) * t else (bw * k2) / 2
        // a brow never leaves the face: raised into the outline, it stops there
        val bx = ex + side * spread
        val by = sh.brow.y + browY + (if (i == 1) -basym else basym * 0.3)
        val cos = cos(side * btilt * RAD)
        val sin = sin(side * btilt * RAD)
        fun inside(dy: Double): Boolean {
            for (tt in 0..4) {
                val t = tt / 4.0
                val lx = w * (2 * t - 1)
                val ly = 2 * t * (1 - t) * ca
                val px = bx + lx * cos - ly * sin
                val py = by + dy + lx * sin + ly * cos
                if (depth(sh.crown, px, py) + half(t) + 2 > 0) return false
            }
            return true
        }
        var lo = 0.0
        var hi = 0.0
        if (!inside(0.0)) {
            hi = 40.0
            repeat(12) {
                val mid = (lo + hi) / 2
                if (inside(mid)) hi = mid else lo = mid
            }
        }
        val transform = "translate(${f2(bx)} ${f2(by + hi)}) rotate(${f2(side * btilt)})"
        val key = "brow$i"
        if (wedge) {
            val dm = ((tl + tr) / 2) * 0.75
            n(
                "path", key, mapOf(
                    "d" to "M${f2(-hw)} ${f2(-tl / 2)}Q0 ${f2(c - dm)} ${f2(hw)} ${f2(-tr / 2)}" +
                        "L${f2(hw)} ${f2(tr / 2)}Q0 ${f2(c + dm)} ${f2(-hw)} ${f2(tl / 2)}Z",
                    "transform" to transform,
                    "fill" to INK["ink"]!!, "stroke" to INK["ink"]!!,
                    "stroke-linejoin" to "round", "stroke-width" to f2(to),
                ),
            )
        } else n(
            "path", key, mapOf(
                "d" to "M${f2(-w)} 0Q0 ${f2(ca)} ${f2(w)} 0",
                "transform" to transform,
                "fill" to "none", "stroke" to INK["ink"]!!,
                "stroke-linecap" to "round", "stroke-linejoin" to "round",
                "stroke-width" to f2(bw * k2),
            ),
        )
    }
    // mouth
    val m = sh.mouth
    val mw = m.w * max(0.05, g("mw"))
    val mt = g("mt") * m.d
    val mb = g("mb") * m.d
    val kb = 4.0 / 3.0
    val w7 = mw * 0.72
    // line: one open stroke along the middle of the lips, so it stays a line
    // in every state: a smile, a frown, or flat where the others open
    val mid = ((mt + mb) / 2) * kb
    val d = when (who.mouth) {
        "poly" -> "M${f2(-mw)} 0L0 ${f2(mt)}L${f2(mw)} 0L0 ${f2(mb)}Z"
        "box" -> "M${f2(-mw)} 0L${f2(-w7)} ${f2(mb)}L${f2(w7)} ${f2(mb)}L${f2(mw)} 0" +
            "L${f2(w7)} ${f2(mt)}L${f2(-w7)} ${f2(mt)}Z"
        "line" -> "M${f2(-mw)} 0C${f2(-mw * 0.5)} ${f2(mid)} ${f2(mw * 0.5)} ${f2(mid)} ${f2(mw)} 0"
        else -> "M${f2(-mw)} 0C${f2(-mw)} ${f2(mb * kb)} ${f2(mw)} ${f2(mb * kb)} ${f2(mw)} 0" +
            "C${f2(mw)} ${f2(mt * kb)} ${f2(-mw)} ${f2(mt * kb)} ${f2(-mw)} 0Z"
    }
    val open = who.mouth == "line"
    val mouth = n(
        "path", "mouth", mapOf(
            "d" to d,
            "transform" to "translate(${f2(m.x + g("mx"))} ${f2(m.y + g("my"))}) rotate(${f2(g("mk"))})",
            "fill" to if (open) "none" else INK["ink"]!!,
            "stroke" to INK["ink"]!!,
            "stroke-linecap" to "round", "stroke-linejoin" to "round",
            "stroke-width" to f2(if (open) sw else m.sw * st),
        ),
    )
    // cheeks
    val blush = clamp(g("blush"), 0.0, 1.0)
    val cheeks = sh.cheeks.mapIndexed { i, cc ->
        val cx = cc[0]
        val cy = cc[1]
        val key = "cheek$i"
        val at = mapOf(
            "opacity" to f2(blush),
            "transform" to "translate(${f2(cx + (if (i == 0) -1 else 1) * spread * 0.6)} " +
                "${f2(cy - lower * 4)}) scale(${f2(0.6 + 0.4 * blush)}) " +
                "translate(${jsNumToString(-cx)} ${jsNumToString(-cy)})",
        )
        when (who.cheeks) {
            "dots" -> {
                val r = sh.cry * 0.9
                n(
                    "g", key, at + mapOf("fill" to deep),
                    listOf(
                        n("circle", "$key.0", mapOf("cx" to f2(cx - sh.crx * 1.05), "cy" to jsNumToString(cy), "r" to f2(r))),
                        n("circle", "$key.1", mapOf("cx" to jsNumToString(cx), "cy" to f2(cy + sh.cry * 0.5), "r" to f2(r))),
                        n("circle", "$key.2", mapOf("cx" to f2(cx + sh.crx * 1.05), "cy" to jsNumToString(cy), "r" to f2(r))),
                    ),
                )
            }
            "lines" -> {
                val h = sh.cry * 1.3
                n(
                    "g", key, at + mapOf(
                        "fill" to "none", "stroke" to deep,
                        "stroke-linecap" to "round", "stroke-width" to f2(sh.cry * 0.78),
                    ),
                    listOf(-1, 0, 1).map { j ->
                        val x0 = cx + j * sh.crx * 0.8
                        n(
                            "path", "$key.${j + 1}", mapOf(
                                "d" to "M${f2(x0 + h * 0.5)} ${f2(cy - h)}L${f2(x0 - h * 0.5)} ${f2(cy + h)}",
                            ),
                        )
                    },
                )
            }
            else -> n(
                "ellipse", key,
                mapOf(
                    "cx" to cx, "cy" to cy, "rx" to sh.crx, "ry" to sh.cry,
                    "fill" to deep,
                ) + at,
            )
        }
    }
    // signature trait: each one keeps its v1.2 motion, measured from a seat on
    // the crown outline instead of a spot on its old face
    val hair = g("hair")
    val lag = pose["lag"]
    val (traitT, shape) = when (who.trait) {
        "square" -> {
            val s = seat(sh.crown, 22 + hair * 10, 0.0)
            ("translate(${f2(s.x)} ${f2(s.y + lag)}) rotate(${f2(s.a + 22 + hair * 23)})" to
                n(
                    "rect", "trait.shape",
                    mapOf("x" to -11, "y" to -11, "width" to 22, "height" to 22, "fill" to deep),
                ))
        }
        "fin" -> {
            val s = seat(sh.crown, -6.0, -164 * 22 * RAD)
            val r = s.a + (if (hair >= 0) hair * 20 else hair * 55) - lag * 0.8
            ("translate(${f2(s.x)} ${f2(s.y + lag * 0.25)}) rotate(${f2(r)})" to
                n("path", "trait.shape", mapOf("d" to "M0 0L0 -40A40 40 0 0 1 40 0Z", "fill" to deep)))
        }
        "ring" -> {
            // worn at one o'clock; rides up when excited, slides when sleepy
            val b = 35 - hair * 14 - (if (hair < 0) hair * -21 else 0.0) + lag * 0.9
            val w = ringWalk(sh)
            val s = seat(sh.crown, 8.0, max(w.second, w.first + 158 * (b - 35) * RAD))
            ("translate(${f2(s.x)} ${f2(s.y)}) scale(${f2(1 + max(0.0, hair) * 0.1)})" to
                n(
                    "circle", "trait.shape", mapOf(
                        "cx" to 0, "cy" to 0, "r" to 12.5, "fill" to "none",
                        "stroke" to deep,
                        "stroke-width" to f2(7 * (if (pose.expression == "angry") 1.25 else 1.0)),
                    ),
                ))
        }
        "dot" -> {
            // rests on the top; lifts clear when perky, rolls over and down the
            // right slope when low
            val ss = clamp(-hair, 0.0, 1.2)
            val th = min(1.0, ss / 0.35) * 61.05 * RAD
            val travel = (max(0.0, ss - 0.35) / 0.65) * 92
            val p = seat(sh.crown, 12.5, 26.1 * th + travel)
            var cy = p.y
            if (hair > 0) cy -= hair * 18
            cy += lag * (if (ss > 0.35) 0.3 else 1.0)
            ("translate(${f2(p.x)} ${f2(cy)}) scale(${f2(1 + max(0.0, hair) * 0.1)})" to
                n("circle", "trait.shape", mapOf("cx" to 0, "cy" to 0, "r" to 12.5, "fill" to deep)))
        }
        else -> {
            // peak: the mark's triangle worn on the crown; lifts when perky, keels
            // over sideways when low
            val s = seat(sh.crown, 4 + max(0.0, hair) * 9, 0.0)
            val r = s.a + (if (hair < 0) -hair * 38 else 0.0) - lag * 0.5
            ("translate(${f2(s.x)} ${f2(s.y + lag)}) rotate(${f2(r)}) scale(${f2(1 + max(0.0, hair) * 0.1)})" to
                n(
                    "path", "trait.shape", mapOf(
                        "d" to "M0 -27L13 -3L-13 -3Z", "fill" to deep,
                        "stroke" to deep, "stroke-width" to 6,
                        "stroke-linejoin" to "round",
                    ),
                ))
        }
    }
    val trait = n("g", "trait", mapOf("transform" to traitT), listOf(shape))
    // the plates sit a hair out of register at large sizes
    fun off(dx: Double, dy: Double): String? =
        if (riso) "translate(${f2(dx * k * upp)} ${f2(dy * k * upp)})" else null
    val av = n(
        "g", "av", mapOf(
            "transform" to "translate(${f2(x)} ${f2(ty)}) rotate(${f2(rot)} 170 ${jsNumToString(B)}) " +
                "translate(170 ${jsNumToString(B)}) scale(${jsNumToString(sc(sx))} ${jsNumToString(sc(sy))}) " +
                "translate(-170 ${jsNumToString(-B)})",
            "filter" to if (dim > 0.01) "url(#$id-dim)" else null,
        ),
        listOf(
            n(
                "g", "plateA", mapOf(
                    "transform" to off(-0.55, 0.45),
                    "filter" to if (riso) "url(#$id-pA)" else null,
                ),
                listOf(bodyNode) + cheeks + listOf(trait),
            ),
            n(
                "g", "plateB", mapOf(
                    "transform" to off(0.6, -0.5),
                    "filter" to if (riso) "url(#$id-pB)" else null,
                ),
                eyes + brows + listOf(mouth),
            ),
        ),
    )
    // the thing in the corner: a dot lattice from the mark's vocabulary
    val scene = mutableListOf<SNode>()
    if (o.frame != "none") {
        val bg = when (o.frame) {
            "ink" -> INK["ink"]!!
            "bone" -> INK["bone"]!!
            else -> INK["paper"]!!
        }
        scene.add(
            n(
                "rect", "bg",
                mapOf("x" to VB[0], "y" to VB[1], "width" to VB[2], "height" to VB[3], "fill" to bg),
            ),
        )
        val th = clamp(g("thing"), 0.0, 1.0)
        val dots = mutableListOf<SNode>()
        for (j in 0..2) for (i in 0..2) {
            val q = j * 3 + i
            val kk = smooth(q / 12.0, q / 12.0 + 0.35, th)
            dots.add(
                n(
                    "circle", "lattice.$q", mapOf(
                        "cx" to 312 + i * 18, "cy" to 2 + j * 18,
                        "r" to f2(1.2 + 2.2 * kk), "opacity" to f3(kk * 0.55),
                    ),
                ),
            )
        }
        scene.add(
            n(
                "g", "lattice",
                mapOf("fill" to if (o.frame == "ink") INK["bone"]!! else INK["ink"]!!),
                dots,
            ),
        )
    }
    scene.add(av)
    if (grainy)
        scene.add(
            n(
                "rect", "grain", mapOf(
                    "x" to VB[0], "y" to VB[1], "width" to VB[2], "height" to VB[3],
                    "filter" to "url(#$id-grain)",
                    "opacity" to if (o.frame == "ink") 0.3 else 0.16,
                    "pointer-events" to "none",
                ),
            ),
        )
    val root = n(
        "svg", "root", mapOf(
            "xmlns" to "http://www.w3.org/2000/svg",
            "viewBox" to VB.joinToString(" ", transform = ::jsNumToString),
            "width" to o.size,
            "height" to o.size,
            "role" to if (o.title == null) null else "img",
            "aria-label" to o.title,
            "aria-hidden" to if (o.title == null) "true" else null,
        ),
        listOf(
            n("defs", "defs", emptyMap(), defs),
            n("g", "frame", mapOf("clip-path" to "url(#$id-f)"), scene),
        ),
    )
    return if (o.live) root else prune(root) ?: root
}
