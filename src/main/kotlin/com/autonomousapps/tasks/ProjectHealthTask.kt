@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.ConsoleReport
import com.autonomousapps.internal.advice.AdvicePrinter
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.log
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

  // TODO remove these two output files

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
          logger.quiet(metricsText)
          logger.quiet("See machine-readable report at ${inputFile.path}")
        }
      } else {
        logger.debug(consoleText)
        if (consoleReport.isNotEmpty()) {
          logger.log(metricsText)
          logger.log("See machine-readable report at ${inputFile.path}")
        }
      }
    }

    if (shouldFail) {
      throw GradleException(consoleText)
    }
  }

  private val metricsText by lazy {
    val origNodeCount = origGraph.nodeCount()
    val origEdgeCount = origGraph.edgeCount()
    val newNodeCount = expectedResultGraph.nodeCount()
    val newEdgeCount = expectedResultGraph.edgeCount()

    "Current graph has $origNodeCount nodes and $origEdgeCount edges. If you follow all of this" +
      " advice, the new graph will have $newNodeCount nodes and $newEdgeCount edges.\n"
  }

  private val expectedResultGraph by lazy {
    val result = origGraph.copy()

    val (addAdvice, removeAdvice) = compAdvice.dependencyAdvice.partitionOf(
      { it.isAdd() },
      { it.isRemove() }
    )

    val projPath = compAdvice.projectPath
    addAdvice.forEach {
      result.addEdge(from = projPath, to = it.dependency.identifier)
    }

    result.removeEdges(projPath, removeAdvice.map { removal ->
      projPath to removal.dependency.identifier
    })
  }
}
