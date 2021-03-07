package com.autonomousapps.internal.graph

import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.graph.merge
import com.autonomousapps.internal.utils.fromJson
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import java.io.File

/**
 * Returns a map of [org.gradle.api.Project.getPath] to a [File] containing a JSON representation of
 * a [DependencyGraph].
 */
internal fun projectGraphMapFrom(configuration: Configuration): Map<String, File> =
  configuration.dependencies
    .filterIsInstance<ProjectDependency>()
    .mapNotNull { dep ->
      configuration.fileCollection(dep)
        .filter { it.exists() }
        .singleOrNull()
        ?.let { file -> dep.dependencyProject.path to file }
    }.toMap()

/**
 * Returns a single merged [DependencyGraph] from a collection ([Configuration]) of files that point
 * to JSON representations of [DependencyGraph]s.
 */
internal fun mergedGraphFrom(configuration: Configuration): DependencyGraph =
  configuration.dependencies
    .filterIsInstance<ProjectDependency>()
    .flatMap { dep ->
      configuration.fileCollection(dep)
        .filter { it.exists() }
        .map { it.fromJson<DependencyGraph>() }
    }.merge()
