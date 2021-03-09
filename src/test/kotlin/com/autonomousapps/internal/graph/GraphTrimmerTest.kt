package com.autonomousapps.internal.graph

import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.test.addAdvice
import com.autonomousapps.test.compAdviceFor
import com.autonomousapps.test.graphFrom
import com.autonomousapps.test.removeAdvice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GraphTrimmerTest {

  @Test fun `no advice means no changes`() {
    // Given
    val proj = ProjectWithoutAdvice()

    // When
    val actual = GraphTrimmer(proj.buildHealth, proj.graphProvider).trimmedGraph

    // Then
    assertThat(actual).isEqualTo(proj.expectedGraph)
  }

  @Test fun `when following advice, we expect the graph to change`() {
    // Given
    val proj = ProjectWithAdvice()

    // When
    val actual = GraphTrimmer(proj.buildHealth, proj.graphProvider).trimmedGraph

    // Then
    assertThat(actual).isEqualTo(proj.expectedGraph)
  }

  /**
   * This simple base project has two modules, :app and :lib. :app depends on :lib and :lib depends
   * on kotlin-stdlib, moshi-kotlin, and moshi-adapters, which bring along their transitive
   * dependencies.
   */
  private abstract class BaseProject {
    val appGraph = graphFrom(":app", ":lib")
    val libGraph = graphFrom(
      ":lib", "kotlin-stdlib-jdk8",
      ":lib", "moshi-kotlin",
      ":lib", "moshi-adapters",
      "kotlin-stdlib-jdk8", "kotlin-stdlib-jdk7",
      "kotlin-stdlib-jdk7", "kotlin-stdlib",
      "kotlin-stdlib", "jb:annotations",
      "kotlin-stdlib", "kotlin-stdlib-common",
      "moshi-kotlin", "kotlin-reflect",
      "moshi-kotlin", "kotlin-stdlib",
      "moshi-kotlin", "moshi",
      "moshi-adapters", "moshi",
      "moshi-adapters", "retrofit2",
      "moshi", "okio",
      "retrofit2", "okhttp",
      "okhttp", "okio"
    )

    abstract val expectedGraph: DependencyGraph

    abstract val buildHealth: List<ComprehensiveAdvice>

    val graphProvider: (String) -> DependencyGraph = {
      when (it) {
        ":app" -> appGraph
        ":lib" -> libGraph
        else -> throw IllegalArgumentException("Unexpected test project: $it")
      }
    }
  }

  private class ProjectWithoutAdvice : BaseProject() {
    override val expectedGraph = graphFrom(
      ":app", ":lib",
      ":lib", "kotlin-stdlib-jdk8",
      ":lib", "moshi-kotlin",
      ":lib", "moshi-adapters",
      "kotlin-stdlib-jdk8", "kotlin-stdlib-jdk7",
      "kotlin-stdlib-jdk7", "kotlin-stdlib",
      "kotlin-stdlib", "jb:annotations",
      "kotlin-stdlib", "kotlin-stdlib-common",
      "moshi-kotlin", "kotlin-reflect",
      "moshi-kotlin", "kotlin-stdlib",
      "moshi-kotlin", "moshi",
      "moshi-adapters", "moshi",
      "moshi-adapters", "retrofit2",
      "moshi", "okio",
      "retrofit2", "okhttp",
      "okhttp", "okio"
    )
    override val buildHealth = listOf(compAdviceFor(":app"), compAdviceFor(":lib"))
  }

  private class ProjectWithAdvice : BaseProject() {
    override val expectedGraph = graphFrom(
      ":app", ":lib",
      ":lib", "kotlin-stdlib-jdk8",
      ":lib", "okio",
      "kotlin-stdlib-jdk8", "kotlin-stdlib-jdk7",
      "kotlin-stdlib-jdk7", "kotlin-stdlib",
      "kotlin-stdlib", "jb:annotations",
      "kotlin-stdlib", "kotlin-stdlib-common"
    )

    // ":lib" uses Okio without declaring it, and doesn't use either of the Moshis.
    private val addAdviceForLib = addAdvice(
      trans = "okio",
      toConfiguration = "implementation",
      parents = *arrayOf("okhttp", "moshi")
    )
    private val removeAdviceForLib1 = removeAdvice("moshi-kotlin", "implementation")
    private val removeAdviceForLib2 = removeAdvice("moshi-adapters", "implementation")

    override val buildHealth = listOf(
      compAdviceFor(":app"),
      compAdviceFor(":lib", addAdviceForLib, removeAdviceForLib1, removeAdviceForLib2)
    )
  }
}
