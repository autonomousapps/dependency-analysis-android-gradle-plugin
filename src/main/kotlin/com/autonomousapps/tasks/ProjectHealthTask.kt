@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.internal.ConsoleReport
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.advice.AdvicePrinter
import com.autonomousapps.internal.getMetricsText
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.log
import com.autonomousapps.shouldFail
import com.autonomousapps.shouldNotBeSilent
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ProjectHealthTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Consumes output of aggregateAdvice and can fail the build if desired"
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val comprehensiveAdvice: RegularFileProperty

  @get:Input
  abstract val dependencyRenamingMap: MapProperty<String, String>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val projMetricsJson: RegularFileProperty

  private val compAdvice by lazy {
    comprehensiveAdvice.fromJson<ComprehensiveAdvice>()
  }

  private val projMetrics by lazy {
    projMetricsJson.fromJson<ProjectMetrics>()
  }

  @TaskAction fun action() {
    val inputFile = comprehensiveAdvice.get().asFile

    val consoleReport = ConsoleReport.from(compAdvice)
    val advicePrinter = AdvicePrinter(consoleReport, dependencyRenamingMap.orNull)
    val shouldFail = compAdvice.shouldFail || shouldFail()
    val consoleText = advicePrinter.consoleText()

    // Only print to console if we're not configured to fail
    if (!shouldFail) {
      if (shouldNotBeSilent()) {
        logger.quiet(consoleText)
        if (consoleReport.isNotEmpty()) {
          logger.quiet(metricsText)
          logger.quiet("See machine-readable report at ${inputFile.path}")
        }
      } else {
        logger.debug(consoleText)
        if (consoleReport.isNotEmpty()) {
          logger.log(metricsText)
          logger.log("See machine-readable report at ${inputFile.path}")
        }
      }
    }

    if (shouldFail) {
      throw GradleException(consoleText)
    }
  }

  private val metricsText by lazy {
    getMetricsText(projMetrics)
  }
}
