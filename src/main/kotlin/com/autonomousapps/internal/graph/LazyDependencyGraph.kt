package com.autonomousapps.internal.graph

import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.utils.fromJson
import java.io.File
import java.io.FileNotFoundException

internal class LazyDependencyGraph(private val files: Map<String, File>) {

  private val projectGraphMap = mutableMapOf<String, DependencyGraph>()

  fun getDependencyGraph(projectPath: String): DependencyGraph =
    projectGraphMap.getOrPut(projectPath) {
      files[projectPath]
        ?.fromJson()
        ?: throw FileNotFoundException("No graph file found for $projectPath")
    }
}
