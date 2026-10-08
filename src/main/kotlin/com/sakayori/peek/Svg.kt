package com.sakayori.peek

/*
 * toSvg: a name in, a standalone SVG string out. Same input, same bytes, on
 * any machine. Port of peek-vanilla's svg.js.
 */

/**
 * Render options. An explicit face/color/part wins over the hash; every
 * other axis stays put.
 */
data class PeekOptions(
    val face: String? = null,
    val color: String? = null,
    val eyes: String? = null,
    val brows: String? = null,
    val mouth: String? = null,
    val cheeks: String? = null,
    val trait: String? = null,
    val expression: String = "normal",
    /** [x, y] in -1..1, overrides the expression's gaze. */
    val gaze: DoubleArray? = null,
    val size: Double = 64.0,
    val frame: String = "ink",
    val square: Boolean = true,
    val riso: Boolean = false,
    val id: String? = null,
    /** Defaults to the name. Set hideTitle to drop the aria label. */
    val title: String? = null,
    val hideTitle: Boolean = false,
    val version: Int = LATEST,
)

data class DrawOpts(
    val size: Double,
    val frame: String,
    val square: Boolean,
    val riso: Boolean,
    val live: Boolean,
    val id: String,
    /** Resolved title: the name, an override, or null when hidden. */
    val title: String?,
)

internal fun whoToJson(who: Identity): JVal = JVal.Obj(linkedMapOf(
    "key" to JVal.Str(who.key),
    "hash" to JVal.Num(who.hash.toDouble()),
    "version" to JVal.Num(who.version.toDouble()),
    "face" to JVal.Str(who.face),
    "color" to JVal.Str(who.color),
    "eyes" to JVal.Str(who.eyes),
    "brows" to JVal.Str(who.brows),
    "mouth" to JVal.Str(who.mouth),
    "cheeks" to JVal.Str(who.cheeks),
    "trait" to JVal.Str(who.trait),
    "persona" to JVal.Obj(linkedMapOf(
        "spread" to JVal.Num(who.persona.spread),
        "blink" to JVal.Num(who.persona.blink),
        "add" to JVal.Obj(LinkedHashMap(who.persona.add.mapValues { JVal.Num(it.value) as JVal })),
        "mul" to JVal.Obj(LinkedHashMap(who.persona.mul.mapValues { JVal.Num(it.value) as JVal })),
    )),
))

internal fun poseToJson(pose: Pose): JVal {
    val entries = LinkedHashMap<String, JVal>()
    for ((k, v) in pose.channels) entries[k] = JVal.Num(v)
    entries["expression"] = JVal.Str(pose.expression)
    return JVal.Obj(entries)
}

internal fun lookToJson(o: PeekOptions, live: Boolean): JVal = JVal.Obj(linkedMapOf(
    "size" to JVal.Num(o.size),
    "frame" to JVal.Str(o.frame),
    "square" to JVal.Bool(o.square),
    "riso" to JVal.Bool(o.riso),
    "live" to JVal.Bool(live),
))

fun settle(name: String, o: PeekOptions, live: Boolean = false): Triple<Identity, Pose, DrawOpts> {
    val base = identify(name, o.version)
    val who = base.copy(
        face = o.face ?: base.face,
        color = o.color ?: base.color,
        eyes = o.eyes ?: base.eyes,
        brows = o.brows ?: base.brows,
        mouth = o.mouth ?: base.mouth,
        cheeks = o.cheeks ?: base.cheeks,
        trait = o.trait ?: base.trait,
    )
    val pose = restPose(who, o.expression, o.gaze)
    val id = o.id ?: "peek-${
        fnv1a(
            jsJsonStringify(
                JVal.Arr(listOf(whoToJson(who), poseToJson(pose), lookToJson(o, live))),
            ),
        ).toString(36)
    }"
    val opts = DrawOpts(
        size = o.size,
        frame = o.frame,
        square = o.square,
        riso = o.riso,
        live = live,
        id = id,
        title = if (o.hideTitle) null else (o.title ?: name),
    )
    return Triple(who, pose, opts)
}

// Characters XML 1.0 cannot hold at all: C0 controls and lone surrogates.
private val ILLEGAL =
    Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]|[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]")

private fun esc(s: String): String {
    val cleaned = ILLEGAL.replace(s, "\uFFFD")
    val sb = StringBuilder(cleaned.length)
    for (c in cleaned) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private fun StringBuilder.appendNode(node: SNode) {
    append('<').append(node.tag)
    for ((k, v) in node.attrs) {
        append(' ').append(k).append("=\"").append(esc(v)).append('"')
    }
    if (node.children.isEmpty()) {
        append("/>")
        return
    }
    append('>')
    for (c in node.children) appendNode(c)
    append("</").append(node.tag).append('>')
}

fun serialize(node: SNode): String = buildString { appendNode(node) }

/** A name in, a standalone SVG string out. */
fun toSvg(name: String, opts: PeekOptions = PeekOptions()): String {
    val (who, pose, drawOpts) = settle(name, opts)
    return serialize(draw(who, pose, drawOpts))
}
