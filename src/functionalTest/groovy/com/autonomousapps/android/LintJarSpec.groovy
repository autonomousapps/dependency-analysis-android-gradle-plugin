package com.autonomousapps.android

import com.autonomousapps.AdviceHelper
import com.autonomousapps.android.projects.LintJarProject
import com.autonomousapps.android.projects.TimberProject
import spock.lang.Unroll

import static com.autonomousapps.utils.Runner.build
import static com.google.common.truth.Truth.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
final class LintJarSpec extends AbstractAndroidSpec {
  @Unroll
  def "do not recommend removing rxlint (#gradleVersion AGP #agpVersion)"() {
    given:
    def project = new LintJarProject(agpVersion)
    gradleProject = project.gradleProject

    when:
    build(gradleVersion, gradleProject.rootDir, 'buildHealth')

    then:
    assertThat(AdviceHelper.actualBuildHealth(gradleProject))
      .containsExactlyElementsIn(AdviceHelper.emptyBuildHealthFor(':app', ':'))

    where:
    [gradleVersion, agpVersion] << gradleAgpMatrix()
  }

  @Unroll
  def "do recommend removing timber if it is unused (#gradleVersion AGP #agpVersion)"() {
    given:
    def project = new TimberProject(agpVersion)
    gradleProject = project.gradleProject

    when:
    build(gradleVersion, gradleProject.rootDir, 'buildHealth')

    then:
    assertThat(AdviceHelper.actualBuildHealth(gradleProject))
      .containsExactlyElementsIn([
        TimberProject.removeTimberAdvice(),
        AdviceHelper.emptyCompAdviceFor(':')
      ])

    where:
    [gradleVersion, agpVersion] << gradleAgpMatrix()
  }
}
