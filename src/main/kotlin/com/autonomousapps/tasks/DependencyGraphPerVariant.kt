package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.utils.asString
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.mapNotNullToSet
import com.autonomousapps.internal.utils.toIdentifier
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class DependencyGraphPerVariant : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Produces the dependency graph, for a given variant, for the current project"
  }

  @get:Classpath
  abstract val compileClasspathArtifacts: ConfigurableFileCollection

  /**
   * This is required as an input, in addition to [compileClasspathArtifacts], because the latter
   * (which is just a classpath), can be the same for multiple projects, which leads to incorrect
   * output, since we expect our output to have a node for _this_ project.
   */
  @get:Input
  abstract val projectPath: Property<String>

  @get:Internal
  lateinit var compileClasspath: Configuration

  @get:OutputFile
  abstract val outputJson: RegularFileProperty

  @get:OutputFile
  abstract val outputDot: RegularFileProperty

  @TaskAction fun action() {
    val outputJsonFile = outputJson.getAndDelete()
    val outputDotFile = outputDot.getAndDelete()

    val graph = DependencyGraphWalker(compileClasspath).graph

    logger.quiet("Graph JSON at ${outputJsonFile.path}")
    outputJsonFile.writeText(graph.toJson())

    logger.quiet("Graph DOT at ${outputDotFile.path}")
    outputDotFile.writeText(GraphWriter.toDot(graph))
  }
}

/**
 * Walks the resolved dependency graph to create a dependency graph rooted on the current project,
 * in a form more conducive for analysis.
 */
private class DependencyGraphWalker(conf: Configuration) {

  val graph = DependencyGraph()

  private val visited = mutableSetOf<String>()

  init {
    val root = conf
      .incoming
      .resolutionResult
      .root

    walkFileDeps(root, conf)
    walk(root)
  }

  private fun walkFileDeps(root: ResolvedComponentResult, conf: Configuration) {
    val rootId = root.id.asString()

    // the only way to get flat jar file dependencies
    conf.allDependencies
      .filterIsInstance<FileCollectionDependency>()
      .mapNotNullToSet { it.toIdentifier() }
      .forEach { id ->
        graph.addEdge(rootId, id)
      }
  }

  private fun walk(root: ResolvedComponentResult) {
    val rootId = root.id.asString()

    root.dependencies
      .filterIsInstance<ResolvedDependencyResult>()
      // AGP adds all runtime dependencies as constraints to the compile classpath, and these show
      // up in the resolution result. Filter them out.
      .filterNot { it.isConstraint }
      // For similar reasons as above
      .filterNot { it.isJavaPlatform() }
      .forEach { dependencyResult ->
        val depId = dependencyResult.selected.id.asString()

        // add an edge
        graph.addEdge(rootId, depId)

        if (!visited.contains(depId)) {
          visited.add(depId)
          // recursively walk the graph in a depth-first pattern
          walk(dependencyResult.selected)
        }
      }
  }
}

/**
 * Returns true if any of the variants are a kind of platform.
 * TODO this is duplicated in DependencyMisuseTask.
 */
private fun ResolvedDependencyResult.isJavaPlatform(): Boolean = selected.variants.any { variant ->
  val category = variant.attributes.getAttribute(CATEGORY)
  category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
}

/**
 * This is different than [org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE], which has type
 * `Category` (cf `String`).
 */
private val CATEGORY = Attribute.of("org.gradle.category", String::class.java)
