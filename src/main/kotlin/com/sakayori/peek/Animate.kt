package com.sakayori.peek

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The rig, headless: springs, choreography, blinks, saccades and breathing.
 * Port of peek-vanilla's animate.js Live class, minus the DOM.
 *
 * Live bound poses to DOM nodes and wrote attributes every frame. Here the
 * platform owns rendering: it constructs a PeekAnimator, calls step(dt) on
 * its own frame loop, and draws the returned Pose with draw(). The pose math
 * — and the randomness order — is identical, so the same seed and scenario
 * yield the same faces as the JS rig.
 *
 * Randomness lives here and only here: a face may be random in what it does,
 * never in what it is. Pass a seeded rng for reproducible tests.
 */

/** Channel -> group, in GROUPS order. */
private val GROUP_OF: LinkedHashMap<String, String> = LinkedHashMap<String, String>().apply {
    for ((gr, cs) in GROUPS) for (c in cs) this[c] = gr
}

/** The 30 channels, in GROUP_OF order. */
val ANIM_CHANNELS: List<String> = GROUP_OF.keys.toList()

/** A choreography key's target: a constant, or a function of the current value. */
sealed interface KeyValue {
    data class Num(val v: Double) : KeyValue
    data class Fn(val f: (Double) -> Double) : KeyValue
}

private data class ChoreoKey(
    val atMs: Int,
    val set: Map<String, KeyValue>,
    val spr: Map<String, DoubleArray>? = null,
)

private data class Choreo(
    val delay: Map<String, Int> = emptyMap(),
    val spr: Map<String, DoubleArray> = emptyMap(),
    val keys: List<ChoreoKey> = emptyList(),
)

/* choreography: when each group starts moving, how springy it is, and timed keys */
private val DEF_DELAY = mapOf(
    "eyes" to 0, "pupil" to 50, "brows" to 110,
    "mouth" to 170, "extra" to 140, "body" to 230,
)
private val DEF_SPR = mapOf(
    "eyes" to doubleArrayOf(14.0, 0.82),
    "pupil" to doubleArrayOf(15.0, 0.8),
    "brows" to doubleArrayOf(11.0, 0.72),
    "mouth" to doubleArrayOf(12.0, 0.74),
    "body" to doubleArrayOf(7.0, 0.72),
    "extra" to doubleArrayOf(8.0, 0.5),
)
private val CHOREO: Map<String, Choreo> = mapOf(
    "happy" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 40, "brows" to 120, "mouth" to 200, "extra" to 220, "body" to 280),
        spr = mapOf("body" to doubleArrayOf(9.0, 0.42), "extra" to doubleArrayOf(9.0, 0.35)),
        keys = listOf(
            ChoreoKey(280, mapOf("alt" to KeyValue.Num(0.2)), mapOf("body" to doubleArrayOf(11.0, 0.5))),
            ChoreoKey(520, mapOf("alt" to KeyValue.Num(0.05)), mapOf("body" to doubleArrayOf(8.0, 0.38))),
        ),
    ),
    "sad" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 160, "brows" to 260, "mouth" to 380, "extra" to 420, "body" to 560),
        spr = mapOf(
            "eyes" to doubleArrayOf(6.0, 0.95), "pupil" to doubleArrayOf(5.0, 0.95),
            "brows" to doubleArrayOf(6.0, 0.95), "mouth" to doubleArrayOf(6.0, 0.9),
            "body" to doubleArrayOf(3.2, 0.95), "extra" to doubleArrayOf(3.0, 0.9),
        ),
    ),
    "angry" to Choreo(
        delay = mapOf("brows" to 0, "eyes" to 40, "pupil" to 60, "mouth" to 160, "body" to 100, "extra" to 60),
        spr = mapOf(
            "brows" to doubleArrayOf(18.0, 0.7), "eyes" to doubleArrayOf(16.0, 0.75),
            "body" to doubleArrayOf(14.0, 0.5),
        ),
    ),
    "sleepy" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 200, "brows" to 750, "mouth" to 950, "extra" to 900, "body" to 1150),
        spr = mapOf(
            "eyes" to doubleArrayOf(2.6, 1.0), "pupil" to doubleArrayOf(3.0, 1.0),
            "brows" to doubleArrayOf(3.2, 1.0), "mouth" to doubleArrayOf(3.0, 1.0),
            "extra" to doubleArrayOf(2.4, 0.9), "body" to doubleArrayOf(1.9, 1.0),
        ),
        keys = listOf(
            ChoreoKey(0, mapOf("lid" to KeyValue.Num(0.62))),
            ChoreoKey(1300, mapOf("lid" to KeyValue.Num(1.0))),
        ),
    ),
    "curious" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 0, "brows" to 220, "mouth" to 320, "extra" to 0, "body" to 0),
        spr = mapOf("body" to doubleArrayOf(6.0, 0.95)),
        keys = listOf(
            ChoreoKey(0, mapOf(
                "alt" to KeyValue.Num(-0.8), "rot" to KeyValue.Num(0.0),
                "gx" to KeyValue.Num(0.0), "gy" to KeyValue.Num(-0.1), "thing" to KeyValue.Num(0.0),
            )),
            ChoreoKey(900, mapOf("alt" to KeyValue.Num(-0.56)), mapOf("body" to doubleArrayOf(2.2, 1.0))),
            ChoreoKey(1500, mapOf("thing" to KeyValue.Num(1.0)), mapOf("extra" to doubleArrayOf(5.0, 0.9))),
            ChoreoKey(2200, mapOf("look" to KeyValue.Num(1.0)), mapOf("pupil" to doubleArrayOf(6.0, 0.9))),
            ChoreoKey(2500, mapOf("rot" to KeyValue.Num(7.0)), mapOf("body" to doubleArrayOf(3.0, 0.9))),
            ChoreoKey(2900, mapOf("alt" to KeyValue.Num(-0.32)), mapOf("body" to doubleArrayOf(2.0, 1.0))),
        ),
    ),
    "surprised" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 0, "brows" to 0, "mouth" to 40, "extra" to 60, "body" to 0),
        spr = mapOf(
            "eyes" to doubleArrayOf(30.0, 0.62), "pupil" to doubleArrayOf(26.0, 0.7),
            "brows" to doubleArrayOf(24.0, 0.55), "mouth" to doubleArrayOf(22.0, 0.6),
            "extra" to doubleArrayOf(15.0, 0.32),
        ),
        keys = listOf(
            ChoreoKey(0, mapOf(
                "alt" to KeyValue.Fn { v -> v - 0.1 },
                "sy" to KeyValue.Num(0.9), "sx" to KeyValue.Num(1.05),
            ), mapOf("body" to doubleArrayOf(30.0, 1.0))),
            ChoreoKey(110, mapOf(
                "alt" to KeyValue.Num(0.36), "sy" to KeyValue.Num(1.08), "sx" to KeyValue.Num(0.97),
            ), mapOf("body" to doubleArrayOf(17.0, 0.42))),
            ChoreoKey(380, mapOf("sy" to KeyValue.Num(1.0), "sx" to KeyValue.Num(1.0)),
                mapOf("body" to doubleArrayOf(10.0, 0.5))),
        ),
    ),
    "excited" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 30, "brows" to 80, "mouth" to 140, "extra" to 120, "body" to 180),
        spr = mapOf("body" to doubleArrayOf(9.0, 0.4), "extra" to doubleArrayOf(12.0, 0.3)),
    ),
    "confused" to Choreo(
        delay = mapOf("eyes" to 0, "brows" to 60, "pupil" to 100, "mouth" to 250, "body" to 200, "extra" to 200),
        spr = mapOf("body" to doubleArrayOf(5.0, 0.7)),
    ),
    "bored" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 300, "brows" to 500, "mouth" to 600, "extra" to 700, "body" to 800),
        spr = mapOf(
            "eyes" to doubleArrayOf(3.0, 1.0), "pupil" to doubleArrayOf(2.5, 1.0),
            "brows" to doubleArrayOf(3.0, 1.0), "mouth" to doubleArrayOf(3.0, 1.0),
            "body" to doubleArrayOf(1.6, 1.0), "extra" to doubleArrayOf(2.0, 1.0),
        ),
    ),
    "attentive" to Choreo(
        delay = mapOf("eyes" to 0, "pupil" to 180, "brows" to 90, "mouth" to 240, "extra" to 200, "body" to 360),
        spr = mapOf(
            "eyes" to doubleArrayOf(20.0, 0.8), "pupil" to doubleArrayOf(20.0, 0.85),
            "brows" to doubleArrayOf(16.0, 0.7), "body" to doubleArrayOf(5.0, 0.78),
            "extra" to doubleArrayOf(11.0, 0.4),
        ),
    ),
)

/* how much the pointer steers the eyes, and the blink interval, per state */
private val ATTENTION = mapOf(
    "normal" to 0.8, "happy" to 0.6, "sad" to 0.2, "angry" to 0.6,
    "sleepy" to 0.0, "curious" to 1.0, "surprised" to 0.5, "excited" to 0.5,
    "confused" to 0.3, "bored" to 0.1, "attentive" to 1.0,
)
private val BLINK: Map<String, DoubleArray?> = mapOf(
    "normal" to doubleArrayOf(2.4, 5.0), "happy" to doubleArrayOf(2.2, 4.0),
    "sad" to doubleArrayOf(3.0, 6.0), "angry" to doubleArrayOf(3.5, 6.0),
    "sleepy" to null, "curious" to doubleArrayOf(3.5, 6.0),
    "surprised" to doubleArrayOf(3.0, 5.0), "excited" to doubleArrayOf(1.8, 3.2),
    "confused" to doubleArrayOf(2.0, 4.0), "bored" to doubleArrayOf(4.5, 7.5),
    "attentive" to doubleArrayOf(4.5, 7.5),
)

/**
 * One live avatar's pose state machine. Starts settled in [expression], the
 * same pose the static renderer paints.
 *
 * @param who the identity
 * @param expression the starting expression
 * @param gaze a fixed [gx, gy] gaze override, or null
 * @param watchPointer whether the pointer steers the eyes; feed it via [updatePointer]
 * @param rng randomness source; pass a seeded one for reproducible tests
 */
class PeekAnimator(
    val who: Identity,
    expression: String = "normal",
    gaze: DoubleArray? = null,
    watchPointer: Boolean = false,
    private val rng: () -> Double = { Random.nextDouble() },
) {
    var state: String = expression
        private set

    private fun rand(a: Double, b: Double): Double = a + rng() * (b - a)

    private val base = LinkedHashMap<String, Double>()
    private val val_ = LinkedHashMap<String, Double>()
    private val vel = LinkedHashMap<String, Double>()
    private val spr = LinkedHashMap<String, DoubleArray>()
    private val out = LinkedHashMap<String, Double>()

    private data class Queued(
        val at: Double,
        val set: Map<String, KeyValue>,
        val spr: Map<String, DoubleArray>?,
    )
    private val queue = mutableListOf<Queued>()

    private data class Bump(
        val ch: String, val amt: Double, val t0: Double,
        val a: Double, val h: Double, val r: Double,
    )
    private val bumps = mutableListOf<Bump>()

    private var clock = 0.0
    private var enteredAt = 0.0
    private var blinkAt = rand(1.0, 3.0)
    private var blinkT = -1.0
    private var blinkDur = 0.16
    private var blinkV = 0.0
    private var doubleBlink = false
    private var saccAt = rand(0.6, 1.8)
    private var saccGx = 0.0
    private var saccGy = 0.0
    private var nodAt = 0.0
    private var lagY = 0.0
    private var lagV = 0.0
    private var fixed: DoubleArray? = null
    private var watch = false
    private var lookThing = false
    var reduced = false
        private set

    /** Pointer in client px + the avatar's rect [left, top, width, height]. */
    private var pointer: DoubleArray? = null
    private var rect: DoubleArray? = null

    init {
        watch = watchPointer
        fixed = gaze
        val start = restPose(who, expression, fixed)
        for ((k, v) in start.channels) val_[k] = v
        for ((k, v) in EXPRESSIONS[expression] ?: throw IllegalArgumentException("unknown expression: $expression")) base[k] = v
        if (fixed != null) {
            base["gx"] = fixed!![0]
            base["gy"] = fixed!![1]
        }
        for (c in ANIM_CHANNELS) {
            spr[c] = DEF_SPR[GROUP_OF[c]!!]!!
            vel[c] = 0.0
            out[c] = 0.0
        }
        lookThing = expression == "curious"
        lagY = -start.channels["alt"]!! * frameOf(FACES[who.face]!!).R
        if (reduced && fixed != null) {
            val_["gx"] = fixed!![0]
            val_["gy"] = fixed!![1]
        }
    }

    /** A fixed gaze override; null clears it. */
    fun setGaze(gaze: DoubleArray?) {
        fixed = gaze
        if (reduced && fixed != null) {
            val_["gx"] = fixed!![0]
            val_["gy"] = fixed!![1]
        }
    }

    fun setPointerWatching(watch: Boolean) {
        this.watch = watch
    }

    /** Feed pointer client px and the avatar's bounding rect; null clears. */
    fun updatePointer(clientX: Double, clientY: Double, left: Double, top: Double, width: Double, height: Double) {
        pointer = doubleArrayOf(clientX, clientY)
        rect = doubleArrayOf(left, top, width, height)
    }

    fun clearPointer() {
        pointer = null
    }

    /** Reduced motion on/off; turning it on snaps to the rest pose. */
    fun setReduced(reduced: Boolean) {
        if (reduced == this.reduced) return
        this.reduced = reduced
        if (reduced) snap()
    }

    /** Each group starts on its own beat. */
    fun setExpression(name: String) {
        if (name == state) return
        val prev = state
        state = name
        enteredAt = clock
        lookThing = false
        queue.clear()
        val tgt = EXPRESSIONS[name] ?: throw IllegalArgumentException("unknown expression: $name")
        val ch = CHOREO[name]
        val delay = LinkedHashMap(DEF_DELAY).apply { ch?.delay?.forEach { (k, v) -> this[k] = v } }
        val sprM = LinkedHashMap(DEF_SPR).apply { ch?.spr?.forEach { (k, v) -> this[k] = v } }
        if (reduced) {
            snap()
            return
        }
        for (gr in GROUPS.keys) {
            val set = LinkedHashMap<String, KeyValue>()
            for (c in GROUPS[gr]!!) set[c] = KeyValue.Num(tgt[c]!!)
            queue.add(Queued(
                at = clock + delay[gr]!! / 1000.0,
                set = set,
                spr = mapOf(gr to sprM[gr]!!),
            ))
        }
        for (key in ch?.keys ?: emptyList()) {
            queue.add(Queued(
                at = clock + key.atMs / 1000.0 + 1e-4,
                set = key.set,
                spr = key.spr,
            ))
        }
        queue.sortBy { it.at }
        // a startled face wakes with a double blink
        if (name == "surprised") {
            blinkAt = clock + 0.75
            doubleBlink = true
        }
        if (name == "sleepy") nodAt = clock + rand(6.0, 8.0)
        if (prev == "sleepy") bumps.clear()
    }

    /** Straight to the rest pose of the current state, no motion. */
    fun snap() {
        val rest = restPose(who, state, fixed)
        for (c in ANIM_CHANNELS) {
            base[c] = EXPRESSIONS[state]!![c]!!
            val_[c] = rest.channels[c]!!
            vel[c] = 0.0
            out[c] = 0.0
        }
        lookThing = state == "curious"
        queue.clear()
        bumps.clear()
        blinkT = -1.0
        blinkV = 0.0
    }

    private fun bump(ch: String, amt: Double, a: Double, h: Double, r: Double) {
        bumps.add(Bump(ch, amt, clock, a, h, r))
    }

    /**
     * Advance the rig by [dt] seconds and return the current pose.
     * dt is clamped to 0.05 like the JS frame loop.
     */
    fun step(dt0: Double): Pose {
        val dt = min(0.05, dt0)
        clock += dt
        val t = clock
        val age = t - enteredAt
        val st = state
        val face = FACES[who.face]!!

        while (queue.isNotEmpty() && queue[0].at <= t) {
            val q = queue.removeAt(0)
            if (q.spr != null) {
                for ((gr, s) in q.spr) for (c in GROUPS[gr]!!) spr[c] = s
            }
            for ((c, v) in q.set) {
                if (c == "look") {
                    lookThing = true
                    continue
                }
                base[c] = when (v) {
                    is KeyValue.Num -> v.v
                    is KeyValue.Fn -> v.f(val_[c]!!)
                }
            }
        }

        val tgt = LinkedHashMap<String, Double>()
        val outM = LinkedHashMap<String, Double>()
        for (c in ANIM_CHANNELS) {
            tgt[c] = 0.0
            outM[c] = 0.0
        }

        // gaze: resting target, micro saccades, the thing, the pointer
        var gx = base["gx"]!!
        var gy = base["gy"]!!
        if (lookThing) {
            val tg = thingGaze(face, val_["x"]!!, val_["alt"]!!)
            gx = tg[0]
            gy = tg[1]
        }
        if (fixed != null) {
            gx = fixed!![0]
            gy = fixed!![1]
        }
        val still = st == "sleepy" || st == "bored"
        if (!still && t >= saccAt) {
            saccGx = rand(-0.17, 0.17)
            saccGy = rand(-0.1, 0.1)
            saccAt = t + rand(0.8, 2.6)
        }
        if (!still) {
            gx += saccGx
            gy += saccGy
        }
        val w = ATTENTION[st]!!
        val r = rect
        val p = pointer
        if (watch && p != null && r != null && w > 0 && r[2] > 0) {
            val fx = VB[0] + ((p[0] - r[0]) / r[2]) * VB[2]
            val fy = VB[1] + ((p[1] - r[1]) / r[3]) * VB[3]
            val fr = frameOf(face)
            val ex = 170 + val_["x"]!!
            val ey = fr.eyeY + fr.baseY - val_["alt"]!! * fr.R
            val px = clamp((fx - ex) / 170, -1.0, 1.0)
            val py = clamp((fy - ey) / 170, -1.0, 1.0)
            gx += (px - gx) * w
            gy += (py - gy) * w
        }
        tgt["gx"] = gx - base["gx"]!!
        tgt["gy"] = gy - base["gy"]!!

        // per-state life
        val br = when (st) {
            "sleepy" -> 1.3
            "bored" -> 1.2
            "attentive" -> 2.6
            else -> 2.1
        }
        val ba = when (st) {
            "sleepy" -> 0.018
            "attentive" -> 0.004
            else -> 0.007
        }
        outM["sy"] = outM["sy"]!! + ba * sin(t * br)
        outM["alt"] = outM["alt"]!! + (if (st == "sleepy") 0.03 else 0.008) * sin(t * br + 1.1)
        if (st == "happy") outM["rot"] = outM["rot"]!! + 1.4 * sin(t * 1.7)
        if (st == "angry") {
            outM["x"] = outM["x"]!! + 7 * sin(age * 42) * exp(-age * 6)
            outM["x"] = outM["x"]!! + 0.35 * sin(t * 31)
        }
        if (st == "excited") {
            val ph = age % 1.5
            if (ph < 0.9) {
                val h = max(0.0, sin((ph / 0.45) * PI))
                outM["alt"] = outM["alt"]!! + h * 0.17
                outM["sy"] = outM["sy"]!! + h * 0.045
                outM["rot"] = outM["rot"]!! + 2 * sin(t * 9) * h
            }
        }
        if (st == "confused") {
            tgt["gx"] = tgt["gx"]!! + 0.45 * sin(age * 1.1)
            outM["rot"] = outM["rot"]!! + 2.2 * sin(age * 0.7)
        }
        if (st == "bored") tgt["gx"] = tgt["gx"]!! + 0.22 * sin(t * 0.33)
        if (st == "sleepy" && t >= nodAt && age > 3) {
            // nods back up, half opens its eyes, gives in again
            bump("alt", 0.3, 0.14, 0.5, 1.6)
            bump("lid", -0.5, 0.14, 0.45, 1.3)
            bump("hair", 0.6, 0.12, 0.4, 1.2)
            nodAt = t + rand(6.5, 9.5)
        }
        if ((st == "sad" || st == "bored") && rng() < dt / 7) {
            bump("sy", -0.035, 0.5, 0.25, 0.9)
            bump("alt", -0.05, 0.5, 0.25, 0.9)
        }

        // springs, two substeps
        val h = dt / 2
        for (c in ANIM_CHANNELS) {
            val (om, ze) = spr[c]!!
            val target = base[c]!! + tgt[c]!!
            var x = val_[c]!!
            var v = vel[c]!!
            repeat(2) {
                v += (om * om * (target - x) - 2 * ze * om * v) * h
                x += v * h
            }
            val_[c] = x
            vel[c] = v
        }

        val bumpIter = bumps.iterator()
        while (bumpIter.hasNext()) {
            val b = bumpIter.next()
            val u = t - b.t0
            if (u > b.a + b.h + b.r) {
                bumpIter.remove()
                continue
            }
            val e = if (u < b.a) smooth(0.0, 1.0, u / b.a)
            else if (u < b.a + b.h) 1.0
            else 1 - smooth(0.0, 1.0, (u - b.a - b.h) / b.r)
            outM[b.ch] = outM[b.ch]!! + b.amt * e
        }

        // blinks
        val bl = BLINK[st]
        var blink = 0.0
        if (bl != null && val_["lid"]!! < 0.75) {
            if (blinkT < 0 && t >= blinkAt) {
                blinkT = 0.0
                blinkDur = if (st == "bored") 0.42 else 0.16
            }
            if (blinkT >= 0) {
                blinkT += dt
                val u = blinkT / blinkDur
                if (u >= 1) {
                    blinkT = -1.0
                    if (doubleBlink) {
                        doubleBlink = false
                        blinkAt = t + 0.09
                    } else {
                        val k = who.persona.blink
                        blinkAt = t + rand(bl[0] * k, bl[1] * k)
                    }
                } else blink = sin(u * PI)
            }
        } else if (bl == null) blinkT = -1.0
        blinkV = blink

        // the trait lags behind the body a little
        val R = frameOf(face).R
        val avY = -(val_["alt"]!! + outM["alt"]!!) * R
        val lom = 10.0
        val lze = 0.32
        lagV += (lom * lom * (avY - lagY) - 2 * lze * lom * lagV) * dt
        lagY += lagV * dt

        for ((k, v) in outM) out[k] = v
        return currentPose()
    }

    /** The current pose without advancing. */
    fun currentPose(): Pose {
        val channels = LinkedHashMap<String, Double>()
        for (c in ANIM_CHANNELS) channels[c] = val_[c]!! + out[c]!!
        channels["blink"] = blinkV
        val R = frameOf(FACES[who.face]!!).R
        channels["lag"] = if (reduced) 0.0
        else clamp((lagY + channels["alt"]!! * R) * 0.4, -16.0, 16.0)
        return Pose(channels, state)
    }
}
