package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.utils.fromJson
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
 * Metrics at the subproject level.
 */
@CacheableTask
abstract class ProjectMetricsTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Calculates metrics for reporting by ${ProjectHealthTask::class.java.simpleName}"
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val comprehensiveAdvice: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val graphJson: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

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
    val outputFile = output.getAndDelete()

    val metrics = ProjectMetrics.fromGraphs(
      origGraph = origGraph, expectedResultGraph = expectedResultGraph
    )

    outputFile.writeText(metrics.toJson())
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