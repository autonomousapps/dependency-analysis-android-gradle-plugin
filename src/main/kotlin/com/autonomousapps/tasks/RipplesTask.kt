@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.advice.RippleDetector
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.advice.RippleWriter
import com.autonomousapps.internal.graph.LazyDependencyGraph
import com.autonomousapps.internal.graph.projectGraphMapFrom
import com.autonomousapps.internal.utils.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

abstract class RipplesTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description =
      "Emits to console all potential 'ripples' relating to dependency advice" // TODO rewrite
  }

  @get:Input
  @set:Option(option = "id", description = "The subproject for which to compute potential ripples")
  var query: String? = null

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  lateinit var graphs: Configuration

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val graph: RegularFileProperty

  /**
   * A `List<ComprehensiveAdvice>`.
   */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val buildHealthReport: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    val queryProject = validateProjectId()
    workerExecutor.noIsolation().submit(Action::class.java) {
      sourceProject.set(queryProject)
      graphFiles.set(projectGraphMapFrom(graphs))
      graph.set(this@RipplesTask.graph)
      buildHealthReport.set(this@RipplesTask.buildHealthReport)
      output.set(this@RipplesTask.output)
    }
  }

  private fun validateProjectId(): String {
    val queryProject = query ?: throw GradleException("This task must be called with --id set")
    if (!queryProject.startsWith(":")) throw GradleException("Can only query a project")
    return queryProject
  }

  interface Parameters : WorkParameters {
    val sourceProject: Property<String>
    val graphFiles: MapProperty<String, File>
    val graph: RegularFileProperty
    val buildHealthReport: RegularFileProperty
    val output: RegularFileProperty
  }

  abstract class Action : WorkAction<Parameters> {

    private val logger = getLogger<RipplesTask>()

    private val lazyDepGraph = LazyDependencyGraph(parameters.graphFiles.get())

    override fun execute() {
      val outputFile = parameters.output.getAndDelete()

      val graph = parameters.graph.fromJson<DependencyGraph>()
      val buildHealth = parameters.buildHealthReport.fromJsonSet<ComprehensiveAdvice>()

      val sourceProject = parameters.sourceProject.get()
      val pebble = RippleDetector(
        queryProject = sourceProject,
        projectGraphProvider = this::getDependencyGraph,
        fullGraph = graph,
        buildHealth = buildHealth
      ).pebble

      outputFile.writeText(pebble.toJson())

      val msg = RippleWriter(pebble).buildMessage()
      logger.quiet(msg)
    }

    private fun getDependencyGraph(projectPath: String): DependencyGraph {
      return lazyDepGraph.getDependencyGraph(projectPath)
    }
  }
}
