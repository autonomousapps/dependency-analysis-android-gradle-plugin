package com.autonomousapps.internal

internal fun getMetricsText(projectMetrics: ProjectMetrics): String {
  val origNodeCount = projectMetrics.origGraph.nodeCount
  val origEdgeCount = projectMetrics.origGraph.edgeCount
  val newNodeCount = projectMetrics.newGraph.nodeCount
  val newEdgeCount = projectMetrics.newGraph.edgeCount

  return "Current graph has $origNodeCount nodes and $origEdgeCount edges. If you follow all of " +
    "this advice, the new graph will have $newNodeCount nodes and $newEdgeCount edges.\n"
}
