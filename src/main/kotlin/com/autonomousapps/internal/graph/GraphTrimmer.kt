package com.autonomousapps.internal.graph

import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.merge
import com.autonomousapps.internal.utils.partitionOf

/**
 * Given a set of [DependencyGraph]s (one per project) and build-level advice ([buildHealth]),
 * compute the trimmed graph, that is, the graph that would result from applying the given advice.
 */
internal class GraphTrimmer(
  private val buildHealth: List<ComprehensiveAdvice>,
  /** A mapping of project-path to dependency graph anchored on that project. */
  private val projectGraphProvider: (String) -> DependencyGraph?
) {

  val trimmedGraph: DependencyGraph

  init {
    trimmedGraph = trim()
  }

  private fun trim(): DependencyGraph = buildHealth.mapNotNull { projAdvice ->
    val projPath = projAdvice.projectPath
    val projectGraph = projectGraphProvider(projPath) ?: return@mapNotNull null

    val (addAdvice, removeAdvice) = projAdvice.dependencyAdvice.partitionOf(
      { it.isAdd() },
      { it.isRemove() }
    )
    addAdvice.forEach {
      projectGraph.addEdge(from = projPath, to = it.dependency.identifier)
    }

    projectGraph.removeEdges(projPath, removeAdvice.map { removal ->
      projPath to removal.dependency.identifier
    })
  }.merge()
}
