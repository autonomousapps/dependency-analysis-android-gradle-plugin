package com.autonomousapps.android

import com.autonomousapps.android.projects.BuildMetricsProject
import spock.lang.Unroll

import static com.autonomousapps.AdviceHelper.actualBuildHealth
import static com.autonomousapps.AdviceHelper.emptyBuildHealthFor
import static com.autonomousapps.utils.Runner.build
import static com.google.common.truth.Truth.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
final class BuildMetricsSpec extends AbstractAndroidSpec {
  @Unroll
  def "test (#gradleVersion AGP #agpVersion)"() {
    given:
    def project = new BuildMetricsProject(agpVersion)
    gradleProject = project.gradleProject

    when:
    build(gradleVersion, gradleProject.rootDir, 'measureBuild')

    then:
    assertThat(actualBuildHealth(gradleProject))
      .containsExactlyElementsIn(emptyBuildHealthFor(':', ':app', ':strings'))

    where:
    [gradleVersion, agpVersion] << gradleAgpMatrix()
  }
}
