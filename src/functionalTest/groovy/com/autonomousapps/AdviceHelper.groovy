package com.autonomousapps

import com.autonomousapps.advice.*
import com.autonomousapps.graph.Edge
import com.autonomousapps.internal.OutputPathsKt
import com.autonomousapps.internal.utils.MoshiUtils
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Subproject
import com.autonomousapps.kit.utils.Files
import com.squareup.moshi.Types

/**
 * Helps specs find advice output in test projects.
 */
final class AdviceHelper {

  static List<Edge> actualGraph(GradleProject gradleProject, String projectName, String variant = 'debug') {
    if (projectName.startsWith(':')) {
      throw new IllegalArgumentException("Expects a project name, not a path. Was $projectName")
    }
    File advice = Files.resolveFromName(
      gradleProject, projectName, OutputPathsKt.getGraphPerVariantPath(variant))
    return fromGraphJson(advice.text)
  }

  static List<ComprehensiveAdvice> actualBuildHealth(GradleProject gradleProject) {
    File buildHealth = Files.resolveFromRoot(gradleProject, OutputPathsKt.getAdviceAggregatePath())
    return fromBuildHealthJson(buildHealth.text)
  }

  static Pebble actualRipples(GradleProject gradleProject) {
    File ripples = Files.resolveFromRoot(gradleProject, OutputPathsKt.getRipplesPath())
    return fromRipplesJson(ripples.text)
  }

  static List<Advice> actualAdviceForFirstSubproject(GradleProject gradleProject) {
    Subproject first = (Subproject) gradleProject.subprojects.first()
    File advice = Files.resolveFromSingleSubproject(gradleProject, OutputPathsKt.getAdvicePath(first.variant))
    return fromAdviceJson(advice.text)
  }

  static List<Advice> actualAdviceForSubproject(GradleProject gradleProject, String projectName) {
    File advice = Files.resolveFromName(gradleProject, projectName, OutputPathsKt.getAdvicePath('main'))
    return fromAdviceJson(advice.text)
  }

  static String actualConsoleAdvice(GradleProject gradleProject) {
    Subproject first = (Subproject) gradleProject.subprojects.first()
    File console = Files.resolveFromSingleSubproject(
      gradleProject, OutputPathsKt.getAdviceConsolePath(first.variant)
    )
    return console.text
  }

  private static List<Edge> fromGraphJson(String json) {
    def type = Types.newParameterizedType(List, Edge)
    def adapter = MoshiUtils.MOSHI.<List<Edge>> adapter(type)
    return adapter.fromJson(json)
  }

  private static List<Advice> fromAdviceJson(String json) {
    def type = Types.newParameterizedType(List, Advice)
    def adapter = MoshiUtils.MOSHI.<List<Advice>> adapter(type)
    return adapter.fromJson(json)
  }

  private static List<ComprehensiveAdvice> fromBuildHealthJson(String json) {
    def type = Types.newParameterizedType(List, ComprehensiveAdvice)
    def adapter = MoshiUtils.MOSHI.<List<ComprehensiveAdvice>> adapter(type)
    return adapter.fromJson(json)
  }

  private static Pebble fromRipplesJson(String json) {
    def adapter = MoshiUtils.MOSHI.adapter(Pebble)
    return adapter.fromJson(json)
  }

  static Dependency dependency(
    String identifier, String resolvedVersion = null, String configurationName = null
  ) {
    return new Dependency(identifier, resolvedVersion, configurationName)
  }

  @SuppressWarnings('GroovyAssignabilityCheck')
  static Dependency dependency(Map<String, String> dependency) {
    return new Dependency(
      dependency['identifier'],
      dependency['resolvedVersion'],
      dependency['configurationName']
    )
  }

  @SuppressWarnings('GroovyAssignabilityCheck')
  static TransitiveDependency transitiveDependency(Map<String, Object> map) {
    def dep = map['dependency']
    if (dep instanceof String) {
      dep = dependency(dep)
    }

    return transitiveDependency(
      dep as Dependency,
      (map['parents'] ?: []) as List<Dependency>,
      (map['variants'] ?: []) as Set<String>
    )
  }

  static TransitiveDependency transitiveDependency(
    Dependency dependency,
    List<Dependency> parents,
    Set<String> variants = [] as Set<String>
  ) {
    return new TransitiveDependency(dependency, parents as Set<Dependency>, variants)
  }

  @SuppressWarnings('GroovyAssignabilityCheck')
  static ComponentWithTransitives componentWithTransitives(Map<String, Object> map) {
    return componentWithTransitives(
      map['dependency'] as Dependency,
      map['usedTransitiveDependencies'] as Set<Dependency>
    )
  }

  static ComponentWithTransitives componentWithTransitives(
    Dependency dependency,
    Set<Dependency> usedTransitiveDependencies
  ) {
    return new ComponentWithTransitives(dependency, usedTransitiveDependencies)
  }

  static List<ComprehensiveAdvice> emptyBuildHealthFor(String... projectPaths) {
    return projectPaths.collect { emptyCompAdviceFor(it) }
  }

  static ComprehensiveAdvice emptyCompAdviceFor(String projectPath) {
    return new ComprehensiveAdvice(projectPath, [] as Set<Advice>, [] as Set<PluginAdvice>, false)
  }

  static ComprehensiveAdvice compAdviceForDependencies(String projectPath, Set<Advice> advice) {
    return new ComprehensiveAdvice(projectPath, advice, [] as Set<PluginAdvice>, false)
  }
}
