package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.graph.GraphTrimmer
import com.autonomousapps.internal.graph.LazyDependencyGraph
import com.autonomousapps.internal.graph.projectGraphMapFrom
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonList
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
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

  private val projectGraphFiles: Map<String, File> by lazy {
    projectGraphMapFrom(graphs)
  }

  private val lazyDepGraph = LazyDependencyGraph(projectGraphFiles)

  private val expectedResultGraph: DependencyGraph by lazy {
    GraphTrimmer(
      comprehensiveAdvice = compAdvice,
      projectGraphProvider = this::getDependencyGraph
    ).trimmedGraph
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

  private fun getDependencyGraph(projectPath: String): DependencyGraph {
    return lazyDepGraph.getDependencyGraph(projectPath)
  }
}
