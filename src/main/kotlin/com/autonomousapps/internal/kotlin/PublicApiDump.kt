/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 *
 * Copied from https://github.com/JetBrains/kotlin/tree/master/libraries/tools/binary-compatibility-validator
 */

package com.autonomousapps.internal.kotlin

import com.autonomousapps.internal.AbiExclusions
import com.autonomousapps.internal.asm.ClassReader
import com.autonomousapps.internal.asm.Opcodes
import com.autonomousapps.internal.asm.tree.ClassNode
import com.autonomousapps.internal.utils.*
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.jar.JarFile

fun main(args: Array<String>) {
  val src = args[0]
  println(src)
  println("------------------\n")
  getBinaryAPI(JarFile(src)).filterOutNonPublic().dump()
}

fun JarFile.classEntries() = Sequence { entries().iterator() }.filter {
  !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/")
}

fun getBinaryAPI(jar: JarFile, visibilityFilter: (String) -> Boolean = { true }): List<ClassBinarySignature> =
    getBinaryAPI(jar.classEntries().map { entry -> jar.getInputStream(entry) }, visibilityFilter)

fun getBinaryAPI(classes: Set<File>, visibilityFilter: (String) -> Boolean = { true }): List<ClassBinarySignature> =
  getBinaryAPI(classes.asSequence().map { it.inputStream() }, visibilityFilter)

fun getBinaryAPI(classStreams: Sequence<InputStream>, visibilityFilter: (String) -> Boolean = { true }): List<ClassBinarySignature> {
  val classNodes = classStreams.map {
    it.use { stream ->
      val classNode = ClassNode()
      ClassReader(stream).accept(classNode, ClassReader.SKIP_CODE)
      classNode
    }
  }

  val visibilityMapNew = classNodes.readKotlinVisibilities().filterKeys(visibilityFilter)

  return classNodes
      .map { clazz ->
        with(clazz) {
          val metadata = kotlinMetadata
          val mVisibility = visibilityMapNew[name]
          val classAccess = AccessFlags(effectiveAccess and Opcodes.ACC_STATIC.inv())

          val supertypes = listOf(superName) - "java/lang/Object" + interfaces.sorted()

          val memberSignatures = (
              fields.map { field ->
                with(field) {
                  FieldBinarySignature(
                    jvmMember = JvmFieldSignature(name, desc),
                    genericTypes = signature?.genericTypes().orEmpty(),
                    annotations = visibleAnnotations.annotationTypes(),
                    isPublishedApi = isPublishedApi(),
                    access = AccessFlags(access)
                  )
                }
              } + methods.map { method ->
                with(method) {
                  val parameterAnnotations = visibleParameterAnnotations.orEmpty()
                    .filterNotNull()
                    .flatMap { annos ->
                      annos.filterNotNull().mapNotNull { anno ->
                        anno.desc
                      }
                    }

                  MethodBinarySignature(
                    jvmMember = JvmMethodSignature(name, desc),
                    genericTypes = signature?.genericTypes().orEmpty(),
                    annotations = visibleAnnotations.annotationTypes(),
                    parameterAnnotations = parameterAnnotations,
                    isPublishedApi = isPublishedApi(),
                    access = AccessFlags(access)
                  )
                }
              }
            ).filter {
              it.isEffectivelyPublic(classAccess, mVisibility)
            }

          val annotations = visibleAnnotations.annotationTypes()

          ClassBinarySignature(
            name = name,
            superName = superName,
            outerName = outerClassName,
            supertypes = supertypes,
            memberSignatures = memberSignatures,
            access = classAccess,
            isEffectivelyPublic = isEffectivelyPublic(mVisibility),
            isNotUsedWhenEmpty = metadata.isFileOrMultipartFacade() || isDefaultImpls(metadata),
            annotations = annotations,
            // TODO toe-hold for filtering by directory
            sourceFileLocation = null
          )
        }
      }
      .asIterable()
      .sortedBy { it.name }
}

internal fun List<ClassBinarySignature>.filterOutNonPublic(
  exclusions: AbiExclusions = AbiExclusions.NONE
): List<ClassBinarySignature> {
  val classByName = associateBy { it.name }

  // Library note - this function (plus the exclusions parameter above) are modified from the original
  // Kotlin sources this was borrowed from.
  fun ClassBinarySignature.isExcluded(): Boolean {
    return (sourceFileLocation?.let(exclusions::excludesPath) ?: false) ||
      exclusions.excludesClass(canonicalName) ||
      annotations.any(exclusions::excludesAnnotation)
  }

  fun ClassBinarySignature.isPublicAndAccessible(): Boolean =
      isEffectivelyPublic &&
          (outerName == null || classByName[outerName]?.let { outerClass ->
            !(this.access.isProtected && outerClass.access.isFinal)
                && outerClass.isPublicAndAccessible()
          } ?: true)

  fun supertypes(superName: String) = generateSequence({ classByName[superName] }, { classByName[it.superName] })

  fun ClassBinarySignature.flattenNonPublicBases(): ClassBinarySignature {

    val nonPublicSupertypes = supertypes(superName).takeWhile { !it.isPublicAndAccessible() }.toList()
    if (nonPublicSupertypes.isEmpty())
      return this

    val inheritedStaticSignatures = nonPublicSupertypes.flatMap { it.memberSignatures.filter { it.access.isStatic } }

    // not covered the case when there is public superclass after chain of private superclasses
    return this.copy(memberSignatures = memberSignatures + inheritedStaticSignatures, supertypes = supertypes - superName)
  }

  return filter {
    !it.isExcluded() && it.isPublicAndAccessible()
  }.map {
    it.flattenNonPublicBases()
  }.filterNot {
    it.isNotUsedWhenEmpty && it.memberSignatures.isEmpty()
  }
}

fun List<ClassBinarySignature>.dump(): PrintStream = dump(to = System.out)

fun <T : Appendable> List<ClassBinarySignature>.dump(to: T): T = to.apply {
  this@dump.forEach {
    it.annotations.forEach { anno ->
      appendln("@$anno")
    }
    append(it.signature).appendln(" {")
    it.memberSignatures.sortedWith(MEMBER_SORT_ORDER).forEach {
      it.annotations.forEach {
        append("\t").appendln("@$it")
      }
      append("\t").appendln(it.signature)
      if ((it as? MethodBinarySignature)?.parameterAnnotations?.isNotEmpty() == true) {
        it.parameterAnnotations.forEach {
          appendln("\t- $it")
        }
      }
    }
    appendln("}\n")
  }
}
