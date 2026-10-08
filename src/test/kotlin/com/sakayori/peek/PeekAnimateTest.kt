package com.sakayori.peek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The animator must reproduce peek-vanilla's Live pose-for-pose on scripted
 * scenarios with the same seeded RNG. Poses come from the animate-poses.txt resource
 * (dumped from the JS rig); the scenario scripts below mirror the harness.
 */
class PeekAnimateTest {
    private val dt = 1.0 / 60.0
    private val eps = 1e-9

    private data class GoldenPose(val expression: String, val v: DoubleArray)

    /** scenario -> poses, loaded once from the resource dumped from the JS rig. */
    private val golden: Map<String, List<GoldenPose>> by lazy {
        val map = LinkedHashMap<String, MutableList<GoldenPose>>()
        var cur: MutableList<GoldenPose>? = null
        javaClass.getResourceAsStream("/com/sakayori/peek/animate-poses.txt")!!
            .bufferedReader().forEachLine { line ->
                if (line.startsWith("@")) {
                    cur = mutableListOf<GoldenPose>().also { map[line.drop(1)] = it }
                } else if (line.isNotBlank()) {
                    val parts = line.split('\t')
                    cur!!.add(GoldenPose(parts[0], DoubleArray(parts.size - 1) { i -> parts[i + 1].toDouble() }))
                }
            }
        map
    }

    private fun capture(p: Pose): GoldenPose {
        val vs = DoubleArray(ANIM_CHANNELS.size + 2)
        for ((i, c) in ANIM_CHANNELS.withIndex()) vs[i] = p.channels[c]!!
        vs[ANIM_CHANNELS.size] = p.channels["blink"]!!
        vs[ANIM_CHANNELS.size + 1] = p.channels["lag"]!!
        return GoldenPose(p.expression, vs)
    }

    private fun check(name: String, actual: List<GoldenPose>) {
        val expected = golden[name]!!
        assertEquals("pose count for $name", expected.size, actual.size)
        var worst = 0.0
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            assertEquals("expression at step $i of $name", e.expression, a.expression)
            for (j in e.v.indices) {
                val d = abs(e.v[j] - a.v[j])
                if (d > worst) worst = d
                assertTrue(
                    "case $name step $i channel $j: expected ${e.v[j]} but was ${a.v[j]}",
                    d <= eps,
                )
            }
        }
        println("OK $name: ${actual.size} poses, worst drift $worst")
    }

    private fun run(anim: PeekAnimator, steps: Int, out: MutableList<GoldenPose>) {
        repeat(steps) { out.add(capture(anim.step(dt))) }
    }

    @Test
    fun normalHappySleepy() {
        val rng = mulberry32(111u)
        val anim = PeekAnimator(identify("Sakayori"), "normal", rng = rng)
        val poses = mutableListOf<GoldenPose>()
        run(anim, 120, poses)
        anim.setExpression("happy")
        run(anim, 120, poses)
        anim.setExpression("sleepy")
        run(anim, 240, poses)
        check("normal-happy-sleepy", poses)
    }

    @Test
    fun surprisedDoubleBlink() {
        val rng = mulberry32(222u)
        val anim = PeekAnimator(identify("Nguyễn Văn An"), "normal", rng = rng)
        val poses = mutableListOf<GoldenPose>()
        run(anim, 60, poses)
        anim.setExpression("surprised")
        run(anim, 180, poses)
        check("surprised-double-blink", poses)
    }

    @Test
    fun pointerGaze() {
        val rng = mulberry32(333u)
        val anim = PeekAnimator(identify("rem"), "normal", watchPointer = true, rng = rng)
        val poses = mutableListOf<GoldenPose>()
        val moves = listOf(
            doubleArrayOf(30.0, 40.0), doubleArrayOf(70.0, 30.0), doubleArrayOf(20.0, 80.0),
            doubleArrayOf(60.0, 60.0), doubleArrayOf(40.0, 20.0),
        )
        for (m in moves) {
            anim.updatePointer(m[0], m[1], 10.0, 20.0, 64.0, 64.0)
            run(anim, 40, poses)
        }
        check("pointer-gaze", poses)
    }

    @Test
    fun curiousFixedGaze() {
        val rng = mulberry32(444u)
        val anim = PeekAnimator(
            identify("😀"), "curious",
            gaze = doubleArrayOf(0.5, -0.3), rng = rng,
        )
        val poses = mutableListOf<GoldenPose>()
        run(anim, 250, poses)
        check("curious-fixed-gaze", poses)
    }

    @Test
    fun rapidChanges() {
        val rng = mulberry32(555u)
        val anim = PeekAnimator(identify("a"), "normal", rng = rng)
        val poses = mutableListOf<GoldenPose>()
        for (e in listOf(
            "happy", "sad", "angry", "sleepy", "curious", "surprised",
            "excited", "confused", "bored", "attentive", "normal",
        )) {
            anim.setExpression(e)
            run(anim, 30, poses)
        }
        check("rapid-changes", poses)
    }

    @Test
    fun boredExcited() {
        val rng = mulberry32(666u)
        val anim = PeekAnimator(identify("Yukki"), "bored", rng = rng)
        val poses = mutableListOf<GoldenPose>()
        run(anim, 150, poses)
        anim.setExpression("excited")
        run(anim, 150, poses)
        check("bored-excited", poses)
    }

    @Test
    fun reducedMotionSnaps() {
        // reduced motion: no stepping, snap() lands exactly on the rest pose
        val rng = mulberry32(777u)
        val anim = PeekAnimator(identify("Sakayori"), "normal", rng = rng)
        anim.setReduced(true)
        anim.setExpression("happy")
        val p = anim.currentPose()
        val rest = restPose(anim.who, "happy", null)
        for (c in ANIM_CHANNELS) {
            assertTrue(
                "reduced channel $c",
                abs(p.channels[c]!! - rest.channels[c]!!) <= eps,
            )
        }
        assertEquals(0.0, p.channels["blink"]!!, eps)
        assertEquals(0.0, p.channels["lag"]!!, eps)
    }
}
