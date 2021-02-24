@file:Suppress("UnstableApiUsage")

package com.autonomousapps.internal

import com.autonomousapps.advice.Advice
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.advice.Dependency
import com.autonomousapps.advice.HasDependency
import com.autonomousapps.advice.PluginAdvice
import com.autonomousapps.graph.DependencyGraph
import com.autonomousapps.internal.AndroidPublicRes.Line
import com.autonomousapps.internal.Location.Companion.findMatch
import com.autonomousapps.internal.advice.ComputedAdvice
import com.autonomousapps.internal.asm.Opcodes
import com.autonomousapps.internal.utils.asString
import com.autonomousapps.internal.utils.efficient
import com.autonomousapps.internal.utils.filterToSet
import com.autonomousapps.internal.utils.mapToSet
import com.autonomousapps.internal.utils.resolvedVersion
import org.gradle.api.artifacts.component.ComponentIdentifier
import java.io.File
import java.io.Serializable
import java.lang.annotation.RetentionPolicy
import java.util.regex.Pattern

/**
 * A dependency's "location" is the configuration that it's connected to. A dependency may actually
 * be connected to more than one configuration, and that would not be an error.
 */
data class Location(
  val identifier: String,
  val configurationName: String,
  /**
   * Only a small handful of configurations are "interesting" for dependency analysis. The rest
   * should be ignored.
   */
  val isInteresting: Boolean
) : Serializable {

  /**
   * Returns true if this is an interesting location and if the identifiers match.
   */
  fun matchesComponentIdentifier(componentIdentifier: ComponentIdentifier): Boolean {
    return isInteresting && identifier == componentIdentifier.asString()
  }

  companion object {
    internal fun Iterable<Location>.findMatch(
      componentIdentifier: ComponentIdentifier
    ): Location? = find {
      it.matchesComponentIdentifier(componentIdentifier)
    }
  }
}

/**
 * Primarily used as a pointer to a [file] on disk; a physical artifact.
 */
data class Artifact(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * If false, a direct dependency (declared in the `dependencies {}` block). If true, a transitive
   * dependency.
   */
  var isTransitive: Boolean? = null,
  /**
   * Physical artifact on disk; a jar file.
   */
  var file: File
) {
  constructor(
    componentIdentifier: ComponentIdentifier,
    file: File,
    candidates: Set<Location>
  ) : this(
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion(),
      configurationName = candidates.findMatch(componentIdentifier)?.configurationName
    ),
    file = file
  )
}

/**
 * A dependency that has bundled native libs (.so files).
 */
data class NativeLibDependency(
  val dependency: Dependency,
  val fileNames: Set<String>
) {
  constructor(
    componentIdentifier: ComponentIdentifier,
    candidates: Set<Location>,
    fileNames: Set<String>
  ) : this(
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion(),
      configurationName = candidates.findMatch(componentIdentifier)?.configurationName
    ),
    fileNames = fileNames
  )
}

/**
 * A dependency that includes a lint jar. (Which is maybe always named lint.jar?) Example registry:
 * `nl.littlerobots.rxlint.RxIssueRegistry`.
 */
data class AndroidLinterDependency(
  val dependency: Dependency,
  val lintRegistry: String
) {
  constructor(
    componentIdentifier: ComponentIdentifier,
    candidates: Set<Location>,
    lintRegistry: String
  ) : this(
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion(),
      configurationName = candidates.findMatch(componentIdentifier)?.configurationName
    ),
    lintRegistry = lintRegistry
  )
}

class VariantClass(
  /**
   * A class (fully-qualified) _used-by_ a given project.
   */
  val theClass: String,
  /**
   * The set of variants (e.g., "main", "debug", "release"...) that supply [theClass].
   */
  val variants: Set<String>
) : Comparable<VariantClass> {
  override fun compareTo(other: VariantClass): Int = theClass.compareTo(other.theClass)
  override fun toString(): String = "VariantClass(theClass='$theClass', variants=$variants)"
}

data class VariantDependency(
  override val dependency: Dependency,
  /**
   * The set of variants (e.g., "main", "debug", "release"...) that supply [dependency]. May be
   * empty.
   */
  val variants: Set<String> = emptySet()
) : HasDependency, Comparable<VariantDependency> {
  override fun compareTo(other: VariantDependency): Int = dependency.compareTo(other.dependency)
}

/**
 * A library or project, along with the set of classes declared by, and other information contained
 * within, this component.
 */
data class Component(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * If false, a direct dependency (declared in the `dependencies {}` block). If true, a transitive
   * dependency.
   */
  val isTransitive: Boolean,
  /**
   * True if this dependency contains only annotation that are only needed at compile-time (`CLASS`
   * and `SOURCE` level retention policies). False otherwise.
   */
  val isCompileOnlyAnnotations: Boolean = false,
  /**
   * The set of classes that are service providers (they extend [java.security.Provider]). May be
   * empty.
   */
  val securityProviders: Set<String> = emptySet(),
  /**
   * Android Lint registry, if there is one. May be null.
   */
  val androidLintRegistry: String? = null,
  /**
   * True if this component contains _only_ an Android Lint jar/registry. If this is true,
   * [androidLintRegistry] must be non-null.
   */
  val isLintJar: Boolean = false,
  /**
   * The classes declared by this library.
   */
  val classes: Set<String>,
  /**
   * A map of each class declared by this library to the set of constants it defines. The latter may
   * be empty for any given declared class.
   */
  val constantFields: Map<String, Set<String>>,
  /**
   * All of the "Kt" files within this component.
   */
  val ktFiles: List<KtFile>
) : Comparable<Component> {

  internal constructor(
    artifact: Artifact,
    analyzedJar: AnalyzedJar
  ) : this(
    dependency = artifact.dependency,
    isTransitive = artifact.isTransitive!!,
    isCompileOnlyAnnotations = analyzedJar.isCompileOnlyCandidate,
    securityProviders = analyzedJar.securityProviders,
    androidLintRegistry = analyzedJar.androidLintRegistry,
    isLintJar = analyzedJar.isLintJar,
    classes = analyzedJar.classNames,
    constantFields = analyzedJar.constants,
    ktFiles = analyzedJar.ktFiles
  )

  init {
    if (isLintJar && androidLintRegistry == null) {
      throw IllegalStateException("Android lint jar for $dependency must contain a lint registry")
    }
  }

  override fun compareTo(other: Component): Int = dependency.compareTo(other.dependency)
}

/**
 * Represents a "mis-used" transitive dependency. The [dependency] represents the unique name
 * (see [Dependency.identifier]), and the [usedTransitiveClasses] are the class members of the dependency that are used
 * directly (which "shouldn't" be).
 */
data class TransitiveComponent(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * These are class members of this dependency that are used directly by the project in question. They have leaked
   * onto the classpath (either unintentionally or by design). Unintentional leakage is usually the result of use of
   * the `compile` configuration (or Maven scope); cf the `api` configuration, which "leaks" by design.
   */
  val usedTransitiveClasses: Set<String>,
  /**
   * The variants in which this component is used (e.g., "main", "debug", "release", ...). May be
   * empty if we are unable to determine this.
   */
  val variants: Set<String> = emptySet()
)

data class ComponentWithInlineMembers(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * A set of imports that indicates a possible use of an inline member from this component ([dependency]).
   */
  val imports: Set<String>
) : Comparable<ComponentWithInlineMembers> {
  override fun compareTo(other: ComponentWithInlineMembers): Int = dependency.compareTo(
    other.dependency
  )
}

data class ComponentWithConstantMembers(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * A set of imports that indicates a possible use of a constant member from this component ([dependency]).
   */
  val imports: Set<String>
) : Comparable<ComponentWithConstantMembers> {
  override fun compareTo(other: ComponentWithConstantMembers): Int = dependency.compareTo(
    other.dependency
  )
}

/**
 * Represents a dependency publicly exposed as part of a project's ABI. Includes the classes so
 * exposed.
 */
data class PublicComponent(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * The set of classes publicly exposed.
   */
  val classes: Set<String>
) : Comparable<PublicComponent> {
  override fun compareTo(other: PublicComponent): Int = dependency.compareTo(other.dependency)
}

data class Imports(
  val sourceType: SourceType,
  val imports: Set<String>
)

enum class SourceType {
  JAVA,
  KOTLIN
}

data class Res(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * An import that indicates a possible use of an Android resource from this component ([dependency]).
   */
  val import: String
) {
  constructor(componentIdentifier: ComponentIdentifier, import: String) : this(
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion()
    ),
    import = import
  )
}

data class AndroidPublicRes(
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency,
  /**
   * A list of "lines" ([Line]) that are in a `public.txt` file.
   */
  val lines: List<Line>
) : Comparable<AndroidPublicRes> {
  constructor(componentIdentifier: ComponentIdentifier, lines: List<Line>) : this(
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion()
    ),
    lines = lines
  )

  data class Line(val type: String, val value: String)

  override fun compareTo(other: AndroidPublicRes): Int = dependency.compareTo(other.dependency)
}

/**
 * Metadata from an Android manifest.
 */
data class Manifest(
  /**
   * The package name per `<manifest package="...">`.
   */
  val packageName: String,
  /**
   * A map of component type to components.
   */
  val componentMap: Map<String, Set<String>>,
  /**
   * A tuple of an `identifier` and a resolved version. See [Dependency].
   */
  val dependency: Dependency
) : Comparable<Manifest> {
  constructor(
    packageName: String,
    componentMap: Map<String, Set<String>>,
    componentIdentifier: ComponentIdentifier
  ) : this(
    packageName = packageName,
    componentMap = componentMap,
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion()
    )
  )

  override fun compareTo(other: Manifest): Int {
    return dependency.compareTo(other.dependency)
  }

  internal enum class Component(val tagName: String, val mapKey: String) {
    ACTIVITY("activity", "activities"),
    SERVICE("service", "services"),
    RECEIVER("receiver", "receivers"),
    PROVIDER("provider", "providers");

    val attrName = "android:name"
  }
}

data class AnalyzedClass(
  val className: String,
  val superClassName: String?,
  val retentionPolicy: RetentionPolicy?,
  /**
   * Ignoring constructors and static initializers. Such a class will not prejudice the compileOnly
   * algorithm against declaring the containing jar "annotations-only". See for example
   * `org.jetbrains.annotations.ApiStatus`. This outer class only exists as a sort of "namespace"
   * for the annotations it contains.
   */
  val hasNoMembers: Boolean,
  val access: Access,
  val methods: Set<Method>,
  val innerClasses: Set<String>,
  val constantFields: Set<String>
) : Comparable<AnalyzedClass> {

  constructor(
    className: String,
    superClassName: String?,
    retentionPolicy: String?,
    isAnnotation: Boolean,
    hasNoMembers: Boolean,
    access: Access,
    methods: Set<Method>,
    innerClasses: Set<String>,
    constantClasses: Set<String>
  ) : this(
    className = className,
    superClassName = superClassName,
    retentionPolicy = fromString(retentionPolicy, isAnnotation),
    hasNoMembers = hasNoMembers,
    access = access,
    methods = methods,
    innerClasses = innerClasses,
    constantFields = constantClasses
  )

  companion object {
    fun fromString(name: String?, isAnnotation: Boolean): RetentionPolicy? = when {
      RetentionPolicy.CLASS.name == name -> RetentionPolicy.CLASS
      RetentionPolicy.SOURCE.name == name -> RetentionPolicy.SOURCE
      RetentionPolicy.RUNTIME.name == name -> RetentionPolicy.RUNTIME
      // Default if RetentionPolicy is not specified.
      isAnnotation -> RetentionPolicy.CLASS
      else -> null
    }
  }

  override fun compareTo(other: AnalyzedClass): Int = className.compareTo(other.className)
}

enum class Access {
  PUBLIC,
  PROTECTED,
  PRIVATE,
  PACKAGE_PRIVATE;

  companion object {
    fun fromInt(access: Int): Access {
      return when {
        isPublic(access) -> PUBLIC
        isProtected(access) -> PROTECTED
        isPrivate(access) -> PRIVATE
        isPackagePrivate(access) -> PACKAGE_PRIVATE
        else -> throw IllegalArgumentException("Access <$access> is an unknown value")
      }
    }

    private fun isPackagePrivate(access: Int): Boolean =
      !isPublic(access) && !isPrivate(access) && !isProtected(access)

    private fun isPublic(access: Int): Boolean = access and Opcodes.ACC_PUBLIC != 0

    private fun isPrivate(access: Int): Boolean = access and Opcodes.ACC_PRIVATE != 0

    private fun isProtected(access: Int): Boolean = access and Opcodes.ACC_PROTECTED != 0
  }
}

data class Method internal constructor(val types: Set<String>) {

  constructor(descriptor: String) : this(findTypes(descriptor))

  companion object {
    private val DESCRIPTOR = Pattern.compile("L(.+?);")

    private fun findTypes(descriptor: String): Set<String> {
      val types = sortedSetOf<String>()
      val m = DESCRIPTOR.matcher(descriptor)
      while (m.find()) {
        types.add(m.group(1))
      }
      return types.efficient()
    }
  }
}

data class AnnotationProcessor(
  override val dependency: Dependency,
  val processor: String,
  val supportedAnnotationTypes: Set<String>
) : Comparable<AnnotationProcessor>, HasDependency {

  constructor(
    processor: String,
    supportedAnnotationTypes: Set<String>,
    componentIdentifier: ComponentIdentifier,
    candidates: Set<Location>
  ) : this(
    processor = processor, supportedAnnotationTypes = supportedAnnotationTypes,
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion(),
      configurationName = candidates.findMatch(componentIdentifier)?.configurationName
    )
  )

  override fun compareTo(other: AnnotationProcessor): Int {
    return dependency.compareTo(other.dependency)
  }
}

internal data class ServiceLoader(
  override val dependency: Dependency,
  val providerFile: String,
  val providerClasses: Set<String>
) : HasDependency, Comparable<ServiceLoader> {

  constructor(
    providerFile: String,
    providerClasses: Set<String>,
    componentIdentifier: ComponentIdentifier,
    candidates: Set<Location>
  ) : this(
    providerFile = providerFile,
    providerClasses = providerClasses,
    dependency = Dependency(
      identifier = componentIdentifier.asString(),
      resolvedVersion = componentIdentifier.resolvedVersion(),
      configurationName = candidates.findMatch(componentIdentifier)?.configurationName
    )
  )

  override fun compareTo(other: ServiceLoader): Int {
    return dependency.compareTo(other.dependency)
  }
}

internal data class ConsoleReport(
  val addToApiAdvice: Set<Advice>,
  val addToImplAdvice: Set<Advice>,
  val removeAdvice: Set<Advice>,
  val changeToApiAdvice: Set<Advice>,
  val changeToImplAdvice: Set<Advice>,
  val compileOnlyDependencies: Set<Advice>,
  val unusedProcsAdvice: Set<Advice>,
  val pluginAdvice: Set<PluginAdvice> = emptySet()
) {

  fun isEmpty() = addToApiAdvice.isEmpty() &&
    addToImplAdvice.isEmpty() &&
    removeAdvice.isEmpty() &&
    changeToApiAdvice.isEmpty() &&
    changeToImplAdvice.isEmpty() &&
    compileOnlyDependencies.isEmpty() &&
    unusedProcsAdvice.isEmpty()

  fun isNotEmpty() = !isEmpty()

  companion object {
    fun from(comprehensiveAdvice: ComprehensiveAdvice): ConsoleReport {
      return ConsoleReport(
        addToApiAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isAdd() && it.toConfiguration!!.endsWith("api", ignoreCase = true)
        },
        addToImplAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isAdd() && it.toConfiguration!!.endsWith("implementation", ignoreCase = true)
        },
        removeAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isRemove()
        },
        changeToApiAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isChange() && it.toConfiguration!!.endsWith("api", ignoreCase = true)
        },
        changeToImplAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isChange() && it.toConfiguration!!.endsWith("implementation", ignoreCase = true)
        },
        compileOnlyDependencies = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isCompileOnly()
        },
        unusedProcsAdvice = comprehensiveAdvice.dependencyAdvice.filterToSet {
          it.isProcessor()
        },
        pluginAdvice = comprehensiveAdvice.pluginAdvice
      )
    }

    fun from(computedAdvice: ComputedAdvice): ConsoleReport {
      var addToApiAdvice = emptySet<Advice>()
      var addToImplAdvice = emptySet<Advice>()
      var removeAdvice = emptySet<Advice>()
      var changeToApiAdvice = emptySet<Advice>()
      var changeToImplAdvice = emptySet<Advice>()
      var compileOnlyDependencies = emptySet<Advice>()
      var unusedProcsAdvice = emptySet<Advice>()

      if (!computedAdvice.filterRemove && computedAdvice.removeAdvice.isNotEmpty()) {
        removeAdvice = computedAdvice.removeAdvice
      }

      val addAdvices = computedAdvice.addToApiAdvice + computedAdvice.addToImplAdvice
      if (!computedAdvice.filterAdd && addAdvices.isNotEmpty()) {
        addToApiAdvice = computedAdvice.addToApiAdvice
        addToImplAdvice = computedAdvice.addToImplAdvice
      }

      val changeAdvices = computedAdvice.changeToApiAdvice + computedAdvice.changeToImplAdvice
      if (!computedAdvice.filterChange && changeAdvices.isNotEmpty()) {
        changeToApiAdvice = computedAdvice.changeToApiAdvice
        changeToImplAdvice = computedAdvice.changeToImplAdvice
      }

      if (!computedAdvice.filterCompileOnly && computedAdvice.compileOnlyAdvice.isNotEmpty()) {
        compileOnlyDependencies = computedAdvice.compileOnlyAdvice
      }

      if (!computedAdvice.filterUnusedProcsAdvice && computedAdvice.unusedProcsAdvice.isNotEmpty()) {
        unusedProcsAdvice = computedAdvice.unusedProcsAdvice
      }

      return ConsoleReport(
        addToApiAdvice,
        addToImplAdvice,
        removeAdvice,
        changeToApiAdvice,
        changeToImplAdvice,
        compileOnlyDependencies,
        unusedProcsAdvice
      )
    }
  }
}

internal data class AbiExclusions(
  val annotationExclusions: Set<String> = emptySet(),
  val classExclusions: Set<String> = emptySet(),
  val pathExclusions: Set<String> = emptySet()
) {

  @Transient
  private val annotationRegexes = annotationExclusions.mapToSet(String::toRegex)

  @Transient
  private val classRegexes = classExclusions.mapToSet(String::toRegex)

  @Transient
  private val pathRegexes = pathExclusions.mapToSet(String::toRegex)

  fun excludesAnnotation(fqcn: String): Boolean = annotationRegexes.any {
    it.containsMatchIn(
      fqcn.dotty()
    )
  }

  fun excludesClass(fqcn: String) = classRegexes.any { it.containsMatchIn(fqcn.dotty()) }

  fun excludesPath(path: String) = pathRegexes.any { it.containsMatchIn(path.dotty()) }

  // The user-facing regex expects FQCNs to be delimited with dots, not slashes
  private fun String.dotty() = replace("/", ".")

  companion object {
    val NONE = AbiExclusions()
  }
}

internal data class ProjectMetrics(
  val origGraph: GraphMetrics,
  val newGraph: GraphMetrics
) {

  companion object {
    fun fromGraphs(
      origGraph: DependencyGraph,
      expectedResultGraph: DependencyGraph
    ): ProjectMetrics {
      return ProjectMetrics(
        origGraph = GraphMetrics.fromGraph(origGraph),
        newGraph = GraphMetrics.fromGraph(expectedResultGraph)
      )
    }
  }

  internal data class GraphMetrics(
    val nodeCount: Int,
    val edgeCount: Int
  ) {
    companion object {
      fun fromGraph(graph: DependencyGraph): GraphMetrics {
        return GraphMetrics(nodeCount = graph.nodeCount(), edgeCount = graph.edgeCount())
      }
    }
  }
}
