package com.autonomousapps.tasks

import com.autonomousapps.advice.Pebble.Ripple
import com.autonomousapps.advice.RippleDetector
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.test.addAdvice
import com.autonomousapps.test.changeAdvice
import com.autonomousapps.test.compAdviceFor
import com.autonomousapps.test.graphFrom
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RippleDetectorTest {

  /*
   * A
   * |
   * B
   */
  @Test fun `a two-node graph can't have ripples`() {
    // Given
    val graph = graphFrom(":a", ":b")
    val buildHealth = setOf(
      compAdviceFor(":a"),
      compAdviceFor(":b")
    )

    // When
    val graphProvider: (String) -> DependencyGraph = { s ->
      when (s) {
        ":a" -> graphFrom(":a", ":b")
        ":b" -> DependencyGraph().apply { addNode(":b") }
        else -> error("Unexpected path $s")
      }
    }
    val actual = RippleDetector(
      queryProject = ":b",
      projectGraphProvider = graphProvider,
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = emptySet<Ripple>()

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }

  /*
   * A uses F. We will break the connection between B and D.
   *
   * A
   * | \
   * B  C
   * ┊  |
   * D  E
   * |  ┊
   * F ┄┘
   */
  @Test fun `can detect ripple for transitive dependency once-removed`() {
    // Given
    val graph = graphFrom(
      ":a", ":b",
      ":a", ":c",
      ":b", ":d",
      ":d", ":f",
      ":c", ":e",
      ":e", ":f"
    )
    val graphProvider: (String) -> DependencyGraph = { s ->
      when (s) {
        ":e" -> graphFrom(":e", ":f")
        else -> graph.subgraph(s).removeEdge(":e", ":f")
      }
    }
    val adviceForA = addAdvice(":f", "implementation", ":d")
    val adviceForB = changeAdvice(":d", "api", "implementation")
    val buildHealth = setOf(
      compAdviceFor(":a", adviceForA),
      compAdviceFor(":b", adviceForB)
    )

    // When
    val actual = RippleDetector(
      queryProject = ":b",
      projectGraphProvider = graphProvider,
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = setOf(
      Ripple(
        impactedProject = ":a",
        downgrade = adviceForB,
        upgrade = adviceForA
      )
    )

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }

  /*
   * A and B both use F. We will break the connection between D and F. This causes a ripple in B
   * (but not in A, since A still has an alternative path to F).
   *
   * A
   * | \
   * B  C
   * |  |
   * D  E
   * ┊ /
   * F
   */
  @Test fun `detects definite ripple and excludes potential ripple that has alternative path`() {
    // Given
    val graph = graphFrom(
      ":a", ":b",
      ":a", ":c",
      ":b", ":d",
      ":d", ":f",
      ":c", ":e",
      ":e", ":f"
    )
    val adviceForA = addAdvice(":f", "implementation", ":d")
    val adviceForB = addAdvice(":f", "implementation", ":d")
    val adviceForD = changeAdvice(":f", "api", "implementation")
    val buildHealth = setOf(
      compAdviceFor(":a", adviceForA),
      compAdviceFor(":b", adviceForB),
      compAdviceFor(":d", adviceForD)
    )

    // When
    val actual = RippleDetector(
      queryProject = ":d",
      projectGraphProvider = { graph.subgraph(it) },
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = setOf(
      Ripple(
        impactedProject = ":b",
        downgrade = adviceForD,
        upgrade = adviceForB
      )
    )

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }

  /*
   * A and B both use F. We will break the connection between D and F. This causes a ripple in both
   * A and B.
   *
   * A
   * | \
   * B  C
   * |  |
   * D  E
   * ┊
   * F
   */
  @Test fun `detects two ripples`() {
    // Given
    val graph = graphFrom(
      ":a", ":b",
      ":a", ":c",
      ":b", ":d",
      ":d", ":f",
      ":c", ":e"
    )
    val adviceForA = addAdvice(":f", "implementation", ":d")
    val adviceForB = addAdvice(":f", "implementation", ":d")
    val adviceForD = changeAdvice(":f", "api", "implementation")
    val buildHealth = setOf(
      compAdviceFor(":a", adviceForA),
      compAdviceFor(":b", adviceForB),
      compAdviceFor(":d", adviceForD)
    )

    // When
    val actual = RippleDetector(
      queryProject = ":d",
      projectGraphProvider = { graph.subgraph(it) },
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = setOf(
      Ripple(
        impactedProject = ":b",
        downgrade = adviceForD,
        upgrade = adviceForB
      ),
      Ripple(
        impactedProject = ":a",
        downgrade = adviceForD,
        upgrade = adviceForA
      )
    )

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }

  /*
   * A and B both use F. We will break the connection between D and F. We also have an advice to
   * break the connection between E and F, but we deliberately only look for single-source ripples.
   * Therefore there will only be a reported ripple in B.
   *
   * A
   * | \
   * B  C
   * |  |
   * D  E
   * ┊  ┊
   * F ┄┘
   */
  @Test fun `detects one ripple from single source (algorithm does not support more than one source)`() {
    // Given
    val graph = graphFrom(
      ":a", ":b",
      ":a", ":c",
      ":b", ":d",
      ":d", ":f",
      ":c", ":e",
      ":e", ":f"
    )
    val adviceForA = addAdvice(":f", "implementation", ":d")
    val adviceForB = addAdvice(":f", "implementation", ":d")
    val adviceForD = changeAdvice(":f", "api", "implementation")
    val adviceForE = changeAdvice(":f", "api", "implementation")
    val buildHealth = setOf(
      compAdviceFor(":a", adviceForA),
      compAdviceFor(":b", adviceForB),
      compAdviceFor(":d", adviceForD),
      compAdviceFor(":e", adviceForE)
    )

    // When
    val actual = RippleDetector(
      queryProject = ":d",
      projectGraphProvider = { graph.subgraph(it) },
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = setOf(
      Ripple(
        impactedProject = ":b",
        downgrade = adviceForD,
        upgrade = adviceForB
      )
    )

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }

  /*
   * A uses F and G. We will break the connection between D and F, and D and G. There will be two
   * ripples in A.
   *
   * A
   * | \
   * B  C
   * |   \
   * D    E
   * ┊ ?
   * F  G
   */
  @Test fun `detects two ripples from two downgrades`() {
    // Given
    val graph = graphFrom(
      ":a", ":b",
      ":a", ":c",
      ":b", ":d",
      ":d", ":f",
      ":d", ":g",
      ":c", ":e"
    )

    val adviceForA1 = addAdvice(":f", "implementation", ":d")
    val adviceForA2 = addAdvice(":g", "implementation", ":d")
    val adviceForD1 = changeAdvice(":f", "api", "implementation")
    val adviceForD2 = changeAdvice(":g", "api", "implementation")
    val buildHealth = setOf(
      compAdviceFor(":a", adviceForA1, adviceForA2),
      compAdviceFor(":d", adviceForD1, adviceForD2)
    )

    // When
    val actual = RippleDetector(
      queryProject = ":d",
      projectGraphProvider = { graph.subgraph(it) },
      fullGraph = graph,
      buildHealth = buildHealth
    ).pebble
    val expected = setOf(
      Ripple(
        impactedProject = ":a",
        downgrade = adviceForD1,
        upgrade = adviceForA1
      ),
      Ripple(
        impactedProject = ":a",
        downgrade = adviceForD2,
        upgrade = adviceForA2
      )
    )

    // Then
    assertThat(actual.ripples).containsExactlyElementsIn(expected)
  }
}