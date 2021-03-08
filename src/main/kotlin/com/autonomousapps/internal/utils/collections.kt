package com.autonomousapps.internal.utils

import org.gradle.api.file.FileCollection
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * Transforms a [ZipFile] into a collection of [ZipEntry]s, which contains only class files (and not
 * the module-info.class file).
 */
internal fun ZipFile.asClassFiles(): Set<ZipEntry> {
  return entries().toList().filterToSetOfClassFiles()
}

/**
 * Filters a collection of [ZipEntry]s to contain only class files (and not the module-info.class
 * file).
 */
internal fun Iterable<ZipEntry>.filterToSetOfClassFiles(): Set<ZipEntry> {
  return filterToSet {
    it.name.endsWith(".class") && it.name != "module-info.class"
  }
}

/**
 * Filters a [FileCollection] to contain only class files (and not the module-info.class file).
 */
internal fun FileCollection.filterToClassFiles(): FileCollection {
  return filter {
    it.isFile && it.name.endsWith(".class") && it.name != "module-info.class"
  }
}

internal inline fun <T> Iterable<T>.filterToSet(predicate: (T) -> Boolean): Set<T> {
  return filterTo(HashSet(), predicate)
}

internal inline fun <T> Iterable<T>.filterNotToSet(predicate: (T) -> Boolean): Set<T> {
  return filterNotTo(HashSet(), predicate)
}

internal inline fun <T> Iterable<T>.filterToOrderedSet(predicate: (T) -> Boolean): Set<T> {
  return filterTo(TreeSet(), predicate)
}

internal inline fun <T> Iterable<T>.filterToOrderedSet(
  comparator: Comparator<T>, predicate: (T) -> Boolean
): Set<T> {
  return filterTo(TreeSet(comparator), predicate)
}

internal fun <T> Iterable<T>.filterNoneMatchingSorted(unwanted: Iterable<T>): Set<T> {
  return filterToOrderedSet { a ->
    unwanted.none { b ->
      a == b
    }
  }
}

internal inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> {
  return mapTo(LinkedHashSet(collectionSizeOrDefault(10)), transform)
}

internal inline fun <T, R> Iterable<T>.mapToOrderedSet(transform: (T) -> R): Set<R> {
  return mapTo(TreeSet(), transform)
}

internal inline fun <T, R> Iterable<T>.flatMapToSet(transform: (T) -> Iterable<R>): Set<R> {
  return flatMapToMutableSet(transform)
}

internal inline fun <T, R> Iterable<T>.flatMapToMutableSet(transform: (T) -> Iterable<R>): MutableSet<R> {
  return flatMapTo(HashSet(collectionSizeOrDefault(10)), transform)
}

internal inline fun <T, R> Iterable<T>.flatMapToOrderedSet(transform: (T) -> Iterable<R>): Set<R> {
  return flatMapTo(TreeSet(), transform)
}

internal fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
  if (this is Collection<*>) this.size
  else default

internal inline fun <T, R : Any> Iterable<T>.mapNotNullToSet(transform: (T) -> R?): Set<R> {
  return mapNotNullTo(HashSet(), transform)
}

internal inline fun <T, R : Any> Iterable<T>.mapNotNullToOrderedSet(transform: (T) -> R?): Set<R> {
  return mapNotNullTo(TreeSet(), transform)
}

internal fun <T> Iterable<Iterable<T>>.flattenToSet(): Set<T> {
  val result = HashSet<T>()
  for (element in this) {
    result.addAll(element)
  }
  return result
}

internal inline fun <R> NodeList.mapNotNull(transform: (Node) -> R?): List<R> {
  val destination = ArrayList<R>(length)
  for (i in 0 until length) {
    transform(item(i))?.let { destination.add(it) }
  }
  return destination
}

internal inline fun <R> NodeList.map(transform: (Node) -> R): List<R> {
  val destination = ArrayList<R>(length)
  for (i in 0 until length) {
    destination.add(transform(item(i)))
  }
  return destination
}

internal inline fun <R> NodeList.mapToSet(transform: (Node) -> R): Set<R> {
  val destination = HashSet<R>(length)
  for (i in 0 until length) {
    destination.add(transform(item(i)))
  }
  return destination
}

internal inline fun NodeList.filter(predicate: (Node) -> Boolean): List<Node> {
  val destination = ArrayList<Node>(length)
  for (i in 0 until length) {
    if (predicate(item(i))) destination.add(item(i))
  }
  return destination
}

internal inline fun NodeList.filterToSet(predicate: (Node) -> Boolean): Set<Node> {
  val destination = LinkedHashSet<Node>(length)
  for (i in 0 until length) {
    if (predicate(item(i))) destination.add(item(i))
  }
  return destination
}

internal inline fun <T> Iterable<T>.partitionToSets(predicate: (T) -> Boolean): Pair<Set<T>, Set<T>> {
  val first = HashSet<T>()
  val second = HashSet<T>()
  for (element in this) {
    if (predicate(element)) {
      first.add(element)
    } else {
      second.add(element)
    }
  }
  return Pair(first, second)
}

/**
 * Partitions an `Iterable` into a pair of sets matching the two predicates, discarding the rest.
 */
internal inline fun <T> Iterable<T>.partitionOf(
  predicate1: (T) -> Boolean, predicate2: (T) -> Boolean
): Pair<Set<T>, Set<T>> {
  val first = HashSet<T>()
  val second = HashSet<T>()
  for (element in this) {
    if (predicate1(element)) {
      first.add(element)
    } else if (predicate2(element)) {
      second.add(element)
    }
  }
  return Pair(first, second)
}

// standard `all` function returns true if collection is empty!
internal inline fun <T> Collection<T>.reallyAll(predicate: (T) -> Boolean): Boolean {
  if (isEmpty()) return false
  for (element in this) if (!predicate(element)) return false
  return true
}

internal fun <T> Set<T>.efficient(): Set<T> = when {
  isEmpty() -> emptySet()
  size == 1 -> Collections.singleton(first())
  else -> this
}

/**
 * Given a list of pairs, where the pairs are key -> (value as Set) pairs, merge into a map (not
 * losing any values).
 */
internal fun <T, U> List<Pair<T, MutableSet<U>>>.mergedMap(): Map<T, Set<U>> {
  return foldRight(linkedMapOf<T, MutableSet<U>>()) { (key, values), map ->
    map.apply {
      merge(key, values) { old, new -> old.apply { addAll(new) } }
    }
  }
}

internal fun <T : Comparable<T>> Iterable<T>.toSortedList(): List<T> {
  return toSortedSet().toList()
}
