package com.autonomousapps.internal.graph

import com.autonomousapps.test.addAdvice
import com.autonomousapps.test.compAdviceFor
import com.autonomousapps.test.graphFrom
import com.autonomousapps.test.removeAdvice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GraphTrimmerTest {

  @Test fun test() {
    // Given
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
    val expectedGraph = graphFrom(
      ":app", ":lib",
      ":lib", "kotlin-stdlib-jdk8",
      ":lib", "okio",
      "kotlin-stdlib-jdk8", "kotlin-stdlib-jdk7",
      "kotlin-stdlib-jdk7", "kotlin-stdlib",
      "kotlin-stdlib", "jb:annotations",
      "kotlin-stdlib", "kotlin-stdlib-common"
    )

    // ":lib" uses Okio without declaring it, and doesn't use either of the Moshis.
    val addAdviceForLib = addAdvice(
      trans = "okio",
      toConfiguration = "implementation",
      parents = *arrayOf("okhttp", "moshi")
    )
    val removeAdviceForLib1 = removeAdvice("moshi-kotlin", "implementation")
    val removeAdviceForLib2 = removeAdvice("moshi-adapters", "implementation")
    val buildHealth = listOf(
      compAdviceFor(":app"),
      compAdviceFor(":lib", addAdviceForLib, removeAdviceForLib1, removeAdviceForLib2)
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
    assertThat(actual).isEqualTo(expectedGraph)
  }
}