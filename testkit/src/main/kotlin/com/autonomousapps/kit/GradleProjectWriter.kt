package com.autonomousapps.kit

import com.autonomousapps.kit.internal.writeAny
import java.lang.IllegalStateException
import java.nio.file.Path

class GradleProjectWriter(
  private val gradleProject: GradleProject
) {

  fun write() {
    val rootDir = gradleProject.rootDir
    rootDir.mkdirs()

    val rootPath = rootDir.toPath()

    // gradle.properties
    val gradleProperties = rootPath.resolve("gradle.properties")
    gradleProperties.toFile().writeText(gradleProject.rootProject.gradleProperties.toString())

    // Settings script
    val settingsFile = rootPath.resolve("settings.gradle")
    settingsFile.toFile().writeText(gradleProject.rootProject.settingsScript.toString())

    // Root build script
    val rootBuildScript = rootPath.resolve("build.gradle")
    rootBuildScript.toFile().writeText(gradleProject.rootProject.buildScript.toString())

    // (Optional) arbitrary files
    gradleProject.rootProject.files.forEach { file ->
      val filePath = rootPath.resolve(file.path)
      filePath.parent.toFile().mkdirs()
      filePath.toFile().writeText(file.content)
    }

    // (Optional) Source
    gradleProject.rootProject.sources.forEach { source ->
      SourceWriter(rootPath, source).write()
    }

    // (Optional) Subprojects
    gradleProject.subprojects.forEach { subproject ->
      if (subproject is AndroidSubproject) {
        AndroidSubprojectWriter(rootPath, subproject).write()
      } else {
        SubprojectWriter(rootPath, subproject).write()
      }
    }
  }

  private class SourceWriter(private val rootPath: Path, private val source: Source) {
    fun write() {
      if (source.path.isNotEmpty() && !source.source.contains("package")) {
        throw IllegalStateException("Source does not contain a package declaration. Did you forget it?")
      }

      val sourceRootPath = rootPath.resolve("src/${source.sourceSet}/${source.sourceType.value}")
      val sourcePath = sourceRootPath.resolve(source.path)
      sourcePath.toFile().mkdirs()
      val filePath = sourcePath.resolve("${source.name}.${source.sourceType.fileExtension}")

      filePath.toFile().writeAny(source)
    }
  }

  private open class SubprojectWriter(
    rootPath: Path,
    private val subproject: Subproject
  ) {

    protected val projectPath: Path = rootPath.resolve(subproject.name).also {
      it.toFile().mkdirs()
    }

    open fun write() {
      // Build script
      val buildScriptPath = projectPath.resolve("build.gradle")
      buildScriptPath.toFile().writeText(subproject.buildScript.toString())

      // Sources
      subproject.sources.forEach { source ->
        SourceWriter(projectPath, source).write()
      }

      // (Optional) arbitrary files
      subproject.files.forEach { file ->
        val filePath = projectPath.resolve(file.path)
        filePath.parent.toFile().mkdirs()
        filePath.toFile().writeText(file.content)
      }
    }
  }

  private class AndroidSubprojectWriter(
    rootPath: Path,
    private val androidSubproject: AndroidSubproject
  ) : SubprojectWriter(rootPath, androidSubproject) {

    override fun write() {
      super.write()

      val manifestPath = projectPath.resolve("src/main/AndroidManifest.xml")
      manifestPath.parent.toFile().mkdirs()
      manifestPath.toFile().writeText(androidSubproject.manifest.toString())

      val stylesPath = projectPath.resolve("src/main/res/values/styles.xml")
      stylesPath.parent.toFile().mkdirs()
      stylesPath.toFile().writeText(androidSubproject.styles.toString())

      val stringsPath = projectPath.resolve("src/main/res/values/strings.xml")
      stringsPath.parent.toFile().mkdirs()
      stringsPath.toFile().writeText(androidSubproject.strings.toString())

      val colorsPath = projectPath.resolve("src/main/res/values/colors.xml")
      colorsPath.parent.toFile().mkdirs()
      colorsPath.toFile().writeText(androidSubproject.colors.toString())

      val layoutsPath = projectPath.resolve("src/main/res/layout/")
      layoutsPath.toFile().mkdirs()
      androidSubproject.layouts.forEach { layout ->
        val layoutPath = layoutsPath.resolve(layout.filename)
        layoutPath.toFile().writeText(layout.content)
      }
    }
  }
}
