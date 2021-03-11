@file:JvmName("Files")

package com.autonomousapps.kit.utils

import com.autonomousapps.kit.GradleProject
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.nio.file.Path

/** Returns the path to the build dir of the root project. */
fun GradleProject.rootBuildPath(): Path = buildDir(":")

/** Returns the directory of the build dir of the root project. */
fun GradleProject.rootBuildDir(): File = rootBuildPath().toFile()

/** Returns the file specified, relative to the root project. */
fun GradleProject.resolveFromRoot(relativePath: String): File {
  return rootBuildPath().resolve(relativePath).toFile()
}

/** Returns the path to the build dir of the first subproject, asserting that there is only one. */
fun GradleProject.singleSubprojectBuildPath(): Path {
  assertWithMessage("Expected only a single subproject").that(subprojects.size).isEqualTo(1)
  return buildDir(subprojects.first())
}

/** Returns the directory of the build dir first subproject, asserting that there is only one. */
fun GradleProject.singleSubprojectBuildFile(): File = singleSubprojectBuildPath().toFile()

/**
 * Returns the file specified, relative to the single subproject, asserting that there is only one.
 */
fun GradleProject.resolveFromSingleSubproject(relativePath: String): File {
  return singleSubprojectBuildPath().resolve(relativePath).toFile()
}

/**
 * Returns the path to the subproject of the given name in the build, asserting that there is only
 * one.
 */
fun GradleProject.buildPathForName(path: String): Path {
  val project = if (path == ":") {
    rootProject
  } else {
    subprojects.find { it.name == path }
  }
  assertWithMessage("No project with path $path").that(project as Any?).isNotNull()
  return buildDir(project!!)
}

/**
 * Returns the directory of the build dir of the subproject of the given nam.
 */
fun GradleProject.buildFileForName(path: String): File = buildPathForName(path).toFile()

/**
 * Returns the file specified, relative to the subproject specified by [projectName].
 */
fun GradleProject.resolveFromName(projectName: String, relativePath: String): File {
  return buildPathForName(projectName).resolve(relativePath).toFile()
}
