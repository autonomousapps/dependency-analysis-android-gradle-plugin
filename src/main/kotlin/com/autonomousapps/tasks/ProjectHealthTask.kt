@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.ConsoleReport
import com.autonomousapps.internal.advice.AdvicePrinter
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.partitionOf
import com.autonomousapps.shouldFail
import com.autonomousapps.shouldNotBeSilent
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*

abstract class ProjectHealthTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Consumes output of aggregateAdvice and can fail the build if desired"

    // TODO remove
    outputs.upToDateWhen { false }
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val comprehensiveAdvice: RegularFileProperty

  @get:Input
  abstract val dependencyRenamingMap: MapProperty<String, String>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val graphJson: RegularFileProperty

  @get:OutputFile
  abstract val projGraphPath: RegularFileProperty

  @get:OutputFile
  abstract val projGraphModPath: RegularFileProperty

  private val origGraph by lazy {
    graphJson.fromJson<DependencyGraph>()
  }

  private val compAdvice by lazy {
    comprehensiveAdvice.fromJson<ComprehensiveAdvice>()
  }

  @TaskAction fun action() {
    // TODO remove
    val projGraphFile = projGraphPath.getAndDelete()
    val projModGraphFile = projGraphModPath.getAndDelete()

    val inputFile = comprehensiveAdvice.get().asFile

    val consoleReport = ConsoleReport.from(compAdvice)
    val advicePrinter = AdvicePrinter(consoleReport, dependencyRenamingMap.orNull)
    val shouldFail = compAdvice.shouldFail || shouldFail()
    val consoleText = advicePrinter.consoleText()

    // Only print to console if we're not configured to fail
    if (!shouldFail) {
      if (shouldNotBeSilent()) {
        logger.quiet(consoleText)
        if (consoleReport.isNotEmpty()) {
          // TODO prettify
          val origNodeCount = origGraph.nodeCount()
          val origEdgeCount = origGraph.edgeCount()
          val newNodeCount = expectedResultGraph.nodeCount()
          val newEdgeCount = expectedResultGraph.edgeCount()
          logger.quiet("Current graph has $origNodeCount nodes and $origEdgeCount. If you follow all of this advice, the new graph will have $newNodeCount nodes and $newEdgeCount edges.")

          // TODO remove
          projGraphFile.writeText(GraphWriter.toDot(origGraph))
          projModGraphFile.writeText(GraphWriter.toDot(expectedResultGraph))
          logger.quiet("Paths to orig graph:\n- ${projGraphFile.absolutePath}\nmod graph:\n- ${projModGraphFile.absolutePath}")

          logger.quiet("See machine-readable report at ${inputFile.path}")
        }
      } else {
        logger.debug(consoleText)
        if (consoleReport.isNotEmpty()) {
          // TODO prettify
          val origNodeCount = origGraph.nodeCount()
          val origEdgeCount = origGraph.edgeCount()
          val newNodeCount = expectedResultGraph.nodeCount()
          val newEdgeCount = expectedResultGraph.edgeCount()
          logger.quiet("Current graph has $origNodeCount nodes and $origEdgeCount edges. If you follow all of this advice, the new graph will have $newNodeCount nodes and $newEdgeCount edges.")

          logger.debug("See machine-readable report at ${inputFile.path}")
        }
      }
    }

    if (shouldFail) {
      throw GradleException(consoleText)
    }
  }

  private val expectedResultGraph by lazy {
    val result = origGraph.copy()

    val (addAdvice, removeAdvice) = compAdvice.dependencyAdvice
      .partitionOf({ it.isAdd() }, { it.isRemove() })

    val projPath = compAdvice.projectPath
    addAdvice.forEach {
      result.addEdge(from = projPath, to = it.dependency.identifier)
    }

    result.removeEdges(projPath, removeAdvice.map { removal ->
      projPath to removal.dependency.identifier
    })
  }
}
