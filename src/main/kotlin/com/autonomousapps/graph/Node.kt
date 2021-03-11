package com.autonomousapps.graph

import com.autonomousapps.advice.ReasonableDependency
import com.autonomousapps.internal.Manifest
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine

/**
 * Represents a module in the dependency hierarchy rooted on the project-under-analysis (PUA). May
 * be a [ConsumerNode] (i.e., the PUA), or a [ProducerNode] (i.e. a dependency).
 */
internal sealed class Node(
  open val identifier: String
) : Comparable<Node> {

  override fun toString(): String = identifier

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    if (identifier != (other as? Node)?.identifier) return false

    return true
  }

  override fun hashCode(): Int {
    return identifier.hashCode()
  }

  override fun compareTo(other: Node): Int = identifier.compareTo(other.identifier)
}

internal data class BareNode(override val identifier: String) : Node(identifier)

/**
 * The project under analysis. It "consumes" its dependencies.
 */
internal data class ConsumerNode(
  override val identifier: String,
  val classes: Set<String>? = null
) : Node(identifier) {

  override fun equals(other: Any?): Boolean {
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return super.hashCode()
  }
}

/**
 * A dependency. May be a project or an external binary. It "provides" facilities for use by the
 * project-under-analysis, represented by [ConsumerNode].
 */
internal data class ProducerNode(
  override val identifier: String,
  val reasonableDependency: ReasonableDependency? = null
) : Node(identifier) {

  override fun equals(other: Any?): Boolean {
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return super.hashCode()
  }
}

internal object NodePrinter {

  fun print(node: Node): String = when (node) {
    is ConsumerNode -> printConsumer(node)
    is ProducerNode -> printProducer(node)
    is BareNode -> printBare(node)
  }

  private fun printBare(node: BareNode) = buildString {
    append("todo") // TODO
  }

  private fun printConsumer(node: ConsumerNode) = buildString {
    append("what are you doing")
  }

  private fun printProducer(node: ProducerNode) = buildString {
    val reasonableDependency = node.reasonableDependency ?: error("")
    val constantCount = reasonableDependency.constantFields
      ?.map { it.value.size }
      ?.fold(0) { acc, i -> acc + i }
      ?: 0

    append("Dependency ${node.identifier} provides the following:\n")
    append("- ${reasonableDependency.classes.size} classes\n") // TODO only public, or all?
    append("- $constantCount public constants\n")
    if (reasonableDependency.isCompileOnly) append("- is a compileOnly candidate\n")
    if (reasonableDependency.securityProviders?.isNotEmpty() == true) {
      append("- provides these security providers:\n")
      append(securityProviders(reasonableDependency.securityProviders))
    }
    reasonableDependency.androidLinters?.let { linter ->
      append("- provides an Android linter:\n")
      append("  - $linter")
    }
    if (reasonableDependency.providesInlineMembers == true) append("- provides inline functions\n")
    if (reasonableDependency.manifestComponents?.isNotEmpty() == true) {
      append("- provides these Android manifest components:\n")
      append(manifestComponents(reasonableDependency.manifestComponents))
    }
    if (reasonableDependency.providesNativeLibs == true) append("- provides native libraries\n")
  }

  private fun securityProviders(securityProviders: Set<String>): String {
    if (securityProviders.isEmpty()) error("Expected non-empty set")
    return securityProviders.joinToString(prefix = "  - ", separator = "\n  - ")
  }

  private fun manifestComponents(components: Map<String, Set<String>>): String {
    if (components.isEmpty()) error("Expected non-empty component map")

    val activities = components[Manifest.Component.ACTIVITY.mapKey]
    val services = components[Manifest.Component.SERVICE.mapKey]
    val providers = components[Manifest.Component.PROVIDER.mapKey]
    val receivers = components[Manifest.Component.RECEIVER.mapKey]

    val builder = StringBuilder()
    if (activities?.isNotEmpty() == true) {
      builder.appendReproducibleNewLine("  - Activities:")
      builder.append(activities.joinToString(prefix = "    - ", separator = "\n    - "))
    }
    if (services?.isNotEmpty() == true) {
      builder.appendReproducibleNewLine("  - Services:")
      builder.append(services.joinToString(prefix = "    - ", separator = "\n    - "))
    }
    if (providers?.isNotEmpty() == true) {
      builder.appendReproducibleNewLine("  - Providers:")
      builder.append(providers.joinToString(prefix = "    - ", separator = "\n    - "))
    }
    if (receivers?.isNotEmpty() == true) {
      builder.appendReproducibleNewLine("  - Receivers:")
      builder.append(receivers.joinToString(prefix = "    - ", separator = "\n    - "))
    }
    return builder.toString()
  }
}

internal object PathPrinter {
  fun printPath(path: Iterable<Edge>) = buildString {
    val rev = path.reversed()
    val last = rev.last()
    append("Shortest path to ${last.to.identifier} from the current project:\n")

    var level = 0
    rev.forEach { edge ->
      append("${indent(level++)}${edge.from.identifier}\n")
    }
    append("${indent(level)}${last.to.identifier}\n")
  }

  private fun indent(level: Int): String =
    if (level == 0) ""
    else "     ".repeat(level - 1) + "\\--- "
}
