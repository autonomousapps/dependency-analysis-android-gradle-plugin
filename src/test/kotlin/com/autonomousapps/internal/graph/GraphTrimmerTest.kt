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

  // This test prevents regression that led to a crash when a graph had no edges and only a single
  // node (the ":lib" node in libGraph, here).
  @Test fun `project with no dependencies doesn't break`() {
    // Given
    val appGraph = graphFrom(
      ":app", ":lib",
      ":app", "stdlib"
    )
    val libGraph = graphFrom(
      ":lib", "stdlib"
    )
    val expected = graphFrom(
      ":app", ":lib",
      ":app", "stdlib"
    )
    val buildHealth = listOf(
      compAdviceFor(":app"),
      compAdviceFor(":lib", removeAdvice("stdlib", "implementation"))
    )

    // When
    val actual = GraphTrimmer(buildHealth) {
      when (it) {
        ":app" -> appGraph
        ":lib" -> libGraph
        else -> throw IllegalArgumentException("Unexpected test project: $it")
      }
    }.trimmedGraph

    // Then
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun `graph can have single node after advice taken`() {
    // Given
    val appGraph = graphFrom(
      ":app", "stdlib"
    )
    val expected = DependencyGraph().apply { addNode(":app") }
    val buildHealth = listOf(
      compAdviceFor(":app", removeAdvice("stdlib", "implementation"))
    )

    // When
    val actual = GraphTrimmer(buildHealth) {
      when (it) {
        ":app" -> appGraph
        else -> throw IllegalArgumentException("Unexpected test project: $it")
      }
    }.trimmedGraph

    // Then
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun `monorepo with two distinct graphs can be analyzed`() {
    // Given
    val appGraph = graphFrom(
      ":app", "stdlib",
      ":app", "retrofit"
    )
    val libGraph = graphFrom(
      ":lib", "stdlib",
      ":lib", "moshi"
    )
    val expected = graphFrom(
      ":app", "retrofit",
      ":lib", "moshi"
    )
    val buildHealth = listOf(
      compAdviceFor(":app", removeAdvice("stdlib", "implementation")),
      compAdviceFor(":lib", removeAdvice("stdlib", "implementation"))
    )

    // When
    val actual = GraphTrimmer(buildHealth) {
      when (it) {
        ":app" -> appGraph
        ":lib" -> libGraph
        else -> throw IllegalArgumentException("Unexpected test project: $it")
      }
    }.trimmedGraph

    // Then
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun `monorepo with two distinct entry points can be analyzed`() {
    // Given
    val appGraph = graphFrom(
      ":app", "stdlib",
      ":app", "retrofit"
    )
    val libGraph = graphFrom(
      ":lib", "stdlib",
      ":lib", "moshi"
    )
    val expected = graphFrom(
      ":app", "stdlib",
      ":lib", "stdlib"
    )
    val buildHealth = listOf(
      compAdviceFor(":app", removeAdvice("retrofit", "implementation")),
      compAdviceFor(":lib", removeAdvice("moshi", "implementation"))
    )

    // When
    val actual = GraphTrimmer(buildHealth) {
      when (it) {
        ":app" -> appGraph
        ":lib" -> libGraph
        else -> throw IllegalArgumentException("Unexpected test project: $it")
      }
    }.trimmedGraph

    // Then
    assertThat(actual).isEqualTo(expected)
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
