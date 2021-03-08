package com.autonomousapps.graph

import org.gradle.kotlin.dsl.support.appendReproducibleNewLine

internal object GraphWriter {

  fun toDot(graph: DependencyGraph) = buildString {
    val projectNodes = graph.nodes().filter {
      it.isProjectNode()
    }.map { it.identifier }

    appendReproducibleNewLine("strict digraph DependencyGraph {")
    appendReproducibleNewLine("  ratio=0.6;")
    appendReproducibleNewLine("  node [shape=box];")
    projectNodes.forEach {
      appendReproducibleNewLine("\n  \"$it\" [style=filled fillcolor=\"#008080\"];")
    }

    graph.edges().forEach { edge ->
      val (from, to) = edge.nodeIds()
      val style =
        if (from.isProjectNode() && to.isProjectNode()) " [style=bold color=\"#FF6347\" weight=8]"
        else ""
      append("  \"$from\" -> \"$to\"$style;")
      append("\n")
    }
    append("}")
  }

  fun toDot(path: Iterable<Edge>) = buildString {
    appendReproducibleNewLine("strict digraph DependencyGraph {")
    appendReproducibleNewLine("  ratio=0.6;")
    appendReproducibleNewLine("  node [shape=box];")
    path.forEach { edge ->
      val (from, to) = edge.nodeIds()
      append("  \"$from\" -> \"$to\";")
      append("\n")
    }
    append("}")
  }

  fun toDot(graph: DependencyGraph, path: Iterable<Edge>) = buildString {
    val importantNodes = path.flatMap {
      it.nodeIds().toList()
    }

    appendReproducibleNewLine("strict digraph DependencyGraph {")
    appendReproducibleNewLine("  ratio=0.6;")
    appendReproducibleNewLine("  node [shape=box];")
    importantNodes.forEach {
      appendReproducibleNewLine("\n  \"$it\" [style=filled fillcolor=\"#008080\"];")
    }
    append("\n")

    graph.edges().forEach { edge ->
      val style =
        if (path.contains(edge)) " [style=bold color=\"#FF6347\" weight=8]"
        else ""

      val (from, to) = edge.nodeIds()
      append("  \"$from\" -> \"$to\"$style;")
      append("\n")
    }
    append("}")
  }

  private fun Node.isProjectNode() = identifier.isProjectNode()
  private fun String.isProjectNode() = startsWith(":")
}
