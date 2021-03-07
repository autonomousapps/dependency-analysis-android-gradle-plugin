package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.graph.GraphTrimmer
import com.autonomousapps.internal.graph.projectGraphMapFrom
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonList
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Metrics at the whole-build level.
 */
@CacheableTask
abstract class BuildMetricsTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Calculates metrics for reporting by ${BuildHealthTask::class.java.simpleName}"
  }

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  lateinit var graphs: Configuration

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

  @get:OutputFile
  abstract val mergedGraphModPath: RegularFileProperty

  private val origGraph by lazy {
    fullGraphJson.fromJson<DependencyGraph>()
  }

  private val compAdvice by lazy {
    adviceReport.fromJsonList<ComprehensiveAdvice>()
  }

  @TaskAction fun action() {
    val outputFile = output.getAndDelete()
    val mergedGraphModFile = mergedGraphModPath.getAndDelete()

    val metrics = ProjectMetrics.fromGraphs(
      origGraph = origGraph, expectedResultGraph = expectedResultGraph
    )

    outputFile.writeText(metrics.toJson())
    mergedGraphModFile.writeText(GraphWriter.toDot(expectedResultGraph))

    logger.quiet("Modified graph: ${mergedGraphModFile.absolutePath}")
  }

  private val projectGraphs: Map<String, File> by lazy {
    projectGraphMapFrom(graphs)
  }

  private val expectedResultGraph: DependencyGraph by lazy {
    GraphTrimmer(compAdvice) { path ->
      projectGraphs[path]?.fromJson()
    }.trimmedGraph
//    compAdvice.mapNotNull { projAdvice ->
//      val projPath = projAdvice.projectPath
//      val projectGraph = projectGraphs[projPath]?.fromJson<DependencyGraph>()
//        ?: return@mapNotNull null
//
//      val (addAdvice, removeAdvice) = projAdvice.dependencyAdvice.partitionOf(
//        { it.isAdd() },
//        { it.isRemove() }
//      )
//      addAdvice.forEach {
//        projectGraph.addEdge(from = projPath, to = it.dependency.identifier)
//      }
//
//      projectGraph.removeEdges(projPath, removeAdvice.map { removal ->
//        projPath to removal.dependency.identifier
//      })
//    }.merge()
  }
}
