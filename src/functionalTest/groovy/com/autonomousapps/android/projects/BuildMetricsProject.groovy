package com.autonomousapps.android.projects

import com.autonomousapps.AbstractProject
import com.autonomousapps.kit.AndroidBlock
import com.autonomousapps.kit.AndroidManifest
import com.autonomousapps.kit.BuildscriptBlock
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.GradleProperties
import com.autonomousapps.kit.Plugin

import static com.autonomousapps.kit.Dependency.appcompat
import static com.autonomousapps.kit.Dependency.project

final class BuildMetricsProject extends AbstractProject {

  final GradleProject gradleProject
  private final String agpVersion

  BuildMetricsProject(String agpVersion) {
    this.agpVersion = agpVersion
    this.gradleProject = build()
  }

  private GradleProject build() {
    def builder = newGradleProjectBuilder()
    builder.withRootProject { r ->
      r.gradleProperties = GradleProperties.minimalAndroidProperties()
      r.withBuildScript { bs ->
        bs.buildscript = BuildscriptBlock.defaultAndroidBuildscriptBlock(agpVersion)
      }
    }
    builder.withAndroidSubproject('app') { s ->
      s.manifest = AndroidManifest.app('com.example.MainApplication')
      s.withBuildScript { bs ->
        bs.plugins = [Plugin.androidAppPlugin]
        bs.dependencies = [
          appcompat('implementation'),
          project('implementation', ':strings'),
          project('implementation', ':not-strings')
        ]
      }
    }
    builder.withAndroidSubproject('strings') { s ->
      s.manifest = AndroidManifest.defaultLib('com.example.strings')
      s.withBuildScript { bs ->
        bs.plugins = [Plugin.androidLibPlugin, Plugin.kotlinAndroidPlugin]
        bs.android = AndroidBlock.defaultAndroidLibBlock(false)
      }
    }
    builder.withAndroidSubproject('not-strings') { s ->
      s.manifest = AndroidManifest.defaultLib('com.example.not.strings')
      s.withBuildScript { bs ->
        bs.plugins = [Plugin.androidLibPlugin, Plugin.kotlinAndroidPlugin]
        bs.android = AndroidBlock.defaultAndroidLibBlock(false)
      }
    }

    def project = builder.build()
    project.writer().write()
    return project
  }
}
