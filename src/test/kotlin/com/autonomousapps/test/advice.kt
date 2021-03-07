package com.autonomousapps.test

import com.autonomousapps.advice.Advice
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.advice.Dependency
import com.autonomousapps.advice.TransitiveDependency
import com.autonomousapps.internal.utils.mapToSet

internal fun addAdvice(trans: String, toConfiguration: String, vararg parents: String) =
  Advice.ofAdd(
    transitiveDependency = TransitiveDependency(
      dependency = Dependency(identifier = trans),
      parents = parents.toSet().mapToSet { Dependency(identifier = it) }
    ),
    toConfiguration = toConfiguration
  )

internal fun changeAdvice(id: String, fromConfiguration: String, toConfiguration: String) =
  Advice.ofChange(
    hasDependency = Dependency(
      identifier = id,
      configurationName = fromConfiguration
    ),
    toConfiguration = toConfiguration
  )

internal fun removeAdvice(id: String, fromConfiguration: String) =
  Advice.ofRemove(
    dependency = Dependency(
      identifier = id,
      configurationName = fromConfiguration
    )
  )

internal fun compAdviceFor(project: String, vararg advice: Advice) = ComprehensiveAdvice(
  projectPath = project, dependencyAdvice = advice.toSet(),
  pluginAdvice = emptySet(), shouldFail = false
)
