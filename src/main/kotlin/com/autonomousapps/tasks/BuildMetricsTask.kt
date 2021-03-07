package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.graph.merge
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonList
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.partitionOf
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
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

  private val projectGraphs: Map<String, File> by lazy {
    // TODO this code is duplicated elsewhere
    // a map of project-path to DependencyGraph JSON file
    graphs.dependencies
      .filterIsInstance<ProjectDependency>()
      .mapNotNull { dep ->
        graphs.fileCollection(dep)
          .filter { it.exists() }
          .singleOrNull()
          ?.let { file -> dep.dependencyProject.path to file }
      }.toMap()
  }

  private val expectedResultGraph by lazy {
    compAdvice.mapNotNull { projAdvice ->
      val projPath = projAdvice.projectPath
      val projectGraph = projectGraphs[projPath]?.fromJson<DependencyGraph>() ?: return@mapNotNull null

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
}
