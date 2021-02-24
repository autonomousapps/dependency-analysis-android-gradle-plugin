package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonList
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.partitionOf
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Metrics at the whole-build level.
 */
@CacheableTask
abstract class BuildMetricsTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Calculates metrics for reporting by ${BuildHealthTask::class.java.simpleName}"
  }

  /**
   * A `List<ComprehensiveAdvice>`.
   */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val adviceReport: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val fullGraphJson: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  // TODO remove these two output files

  @get:OutputFile
  abstract val buildGraphPath: RegularFileProperty

  @get:OutputFile
  abstract val buildGraphModPath: RegularFileProperty

  private val origGraph by lazy {
    fullGraphJson.fromJson<DependencyGraph>()
  }

  private val compAdvice by lazy {
    adviceReport.fromJsonList<ComprehensiveAdvice>()
  }

  @TaskAction fun action() {
    val outputFile = output.getAndDelete()

    val metrics = ProjectMetrics.fromGraphs(
      origGraph = origGraph, expectedResultGraph = expectedResultGraph
    )

    outputFile.writeText(metrics.toJson())

    // TODO remove
    val buildGraphFile = buildGraphPath.getAndDelete()
    val buildModGraphFile = buildGraphModPath.getAndDelete()

    buildGraphFile.writeText(GraphWriter.toDot(origGraph))
    buildModGraphFile.writeText(GraphWriter.toDot(expectedResultGraph))

    logger.quiet("Orig graph: ${buildGraphFile.absolutePath}")
    logger.quiet("New graph: ${buildModGraphFile.absolutePath}")
  }

  private val expectedResultGraph by lazy {
    val result = origGraph.copy()

    compAdvice.forEach { projAdvice ->
      val (addAdvice, removeAdvice) = projAdvice.dependencyAdvice.partitionOf(
        { it.isAdd() },
        { it.isRemove() }
      )
      val projPath = projAdvice.projectPath
      addAdvice.forEach {
        result.addEdge(from = projPath, to = it.dependency.identifier)
      }
      result.removeEdges(projPath, removeAdvice.map { removal ->
        projPath to removal.dependency.identifier
      })
    }

    result
  }
}