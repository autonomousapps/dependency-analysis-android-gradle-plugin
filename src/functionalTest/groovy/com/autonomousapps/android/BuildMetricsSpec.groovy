package com.autonomousapps.android

import com.autonomousapps.android.projects.BuildMetricsProject
import com.autonomousapps.graph.BareNode
import com.autonomousapps.graph.Edge
import spock.lang.Unroll

import static com.autonomousapps.AdviceHelper.actualGraph
import static com.autonomousapps.utils.Runner.build
import static com.google.common.truth.Truth.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
final class BuildMetricsSpec extends AbstractAndroidSpec {
  // There was a bug caused by the fact that BuildMetricsTask had as its only input the classpath,
  // which was the same for two projects, meaning that the generated graph in the second project was
  // missing the expected project node (and had an unexpected node).
  @Unroll
  def "graphs are not wrong because they're pulled from the build cache (#gradleVersion AGP #agpVersion)"() {
    given:
    def project = new BuildMetricsProject(agpVersion)
    gradleProject = project.gradleProject

    when:
    build(gradleVersion, gradleProject.rootDir, ':strings:graphDebug', '--build-cache')
    build(gradleVersion, gradleProject.rootDir, ':not-strings:graphDebug', '--build-cache')

    then:
    assertThat(actualGraph(gradleProject, 'strings')).containsExactlyElementsIn([
      edge('org.jetbrains.kotlin:kotlin-stdlib', 'org.jetbrains.kotlin:kotlin-stdlib-common'),
      edge('org.jetbrains.kotlin:kotlin-stdlib', 'org.jetbrains:annotations'),
      edge(':strings', 'org.jetbrains.kotlin:kotlin-stdlib')
    ])
    assertThat(actualGraph(gradleProject, 'not-strings')).containsExactlyElementsIn([
      edge('org.jetbrains.kotlin:kotlin-stdlib', 'org.jetbrains.kotlin:kotlin-stdlib-common'),
      edge('org.jetbrains.kotlin:kotlin-stdlib', 'org.jetbrains:annotations'),
      edge(':not-strings', 'org.jetbrains.kotlin:kotlin-stdlib')
    ])

    where:
    [gradleVersion, agpVersion] << gradleAgpMatrix()
  }

  private static Edge edge(String from, String to) {
    return new Edge(new BareNode(from), new BareNode(to), 1)
  }
}
