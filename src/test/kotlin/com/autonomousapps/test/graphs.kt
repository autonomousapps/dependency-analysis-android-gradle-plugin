package com.autonomousapps.test

import com.autonomousapps.graph.DependencyGraph

internal fun graphFrom(vararg edges: String): DependencyGraph {
  val edgeList = edges.toList()
  if (edgeList.size % 2 != 0) {
    throw IllegalArgumentException("Must pass in an even number of edges. Was ${edgeList.size}.")
  }

  return DependencyGraph().apply {
    for (i in edgeList.indices step 2) {
      addEdge(edgeList[i], edgeList[i + 1])
    }
  }
}
