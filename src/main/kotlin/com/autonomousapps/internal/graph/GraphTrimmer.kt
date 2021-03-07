package com.autonomousapps.internal.graph

import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.merge
import com.autonomousapps.internal.utils.partitionOf

internal class GraphTrimmer(
  private val comprehensiveAdvice: List<ComprehensiveAdvice>,
  /** A mapping of project-path to dependency graph anchored on that project. */
  private val projectGraphProvider: (String) -> DependencyGraph?
) {

  val trimmedGraph: DependencyGraph

  init {
    trimmedGraph = trim()
  }

  private fun trim(): DependencyGraph = comprehensiveAdvice.mapNotNull { projAdvice ->
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