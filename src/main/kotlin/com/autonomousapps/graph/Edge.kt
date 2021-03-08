package com.autonomousapps.graph

/**
 * An edge [from] &rarr; [to].
 */
internal data class Edge(
  val from: Node,
  val to: Node,
  val weight: Int = 1
) : Comparable<Edge> {

  fun nodeIds(): Pair<String, String> = from.identifier to to.identifier

  override fun toString(): String = "${from.identifier} -> ${to.identifier}"

  override fun compareTo(other: Edge): Int {
    if (from > other.from) return 1
    if (from < other.from) return -1

    if (to > other.to) return 1
    if (to < other.to) return -1

    if (weight > other.weight) return 1
    if (weight < other.weight) return -1

    return 0
  }
}
