package com.autonomousapps.internal.graph

import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.utils.fromJson
import java.io.File
import java.io.FileNotFoundException

internal class LazyDependencyGraph(private val files: Map<String, File>) {

  private val projectGraphMap = mutableMapOf<String, DependencyGraph?>()

  /**
   * @throws [FileNotFoundException] if a file for [projectPath] is not found.
   */
  fun getDependencyGraph(projectPath: String): DependencyGraph =
    nullableGraph(projectPath)
      ?: throw FileNotFoundException("No graph file found for $projectPath")

  fun getDependencyGraphOrNull(projectPath: String): DependencyGraph? = nullableGraph(projectPath)

  private fun nullableGraph(projectPath: String): DependencyGraph? =
    projectGraphMap.getOrPut(projectPath) {
      files[projectPath]?.fromJson()
    }
}
