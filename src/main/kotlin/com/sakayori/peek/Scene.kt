package com.sakayori.peek

/*
 * The scene: draw() builds a plain node tree, backends render it.
 * Port of the node helpers in peek-vanilla's draw.js.
 */

data class SNode(
    val tag: String,
    val key: String,
    val attrs: LinkedHashMap<String, String>,
    val children: List<SNode>,
)

/**
 * n(tag, key, attrs, children): values that are null or false are dropped,
 * everything else is stringified the way JS String() would.
 */
fun n(tag: String, key: String, attrs: Map<String, Any?>, children: List<SNode> = emptyList()): SNode {
    val out = LinkedHashMap<String, String>()
    for ((k, v) in attrs) {
        if (v == null || v == false) continue
        out[k] = when (v) {
            is String -> v
            is Double -> jsNumToString(v)
            is Float -> jsNumToString(v.toDouble())
            is Int -> v.toString()
            is Long -> v.toString()
            is UInt -> v.toString()
            else -> v.toString()
        }
    }
    return SNode(tag, key, out, children)
}

/** Drops what cannot be seen: opacity 0, then groups left empty. */
fun prune(node: SNode): SNode? {
    if (node.attrs["opacity"] == "0") return null
    val children = node.children.mapNotNull(::prune)
    if (node.tag == "g" && children.isEmpty()) return null
    return node.copy(children = children)
}
