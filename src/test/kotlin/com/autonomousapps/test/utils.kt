package com.autonomousapps.utils

import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipOutputStream

fun Any.fileFromResource(resourcePath: String): File = pathFromResource(resourcePath).toFile()

fun Any.pathFromResource(resourcePath: String): Path = Paths.get(uriFromResource(resourcePath))

fun Any.uriFromResource(resourcePath: String): URI = urlFromResource(resourcePath).toURI()

fun Any.urlFromResource(resourcePath: String): URL =
    javaClass.classLoader.getResource(resourcePath) ?: error("No resource at '$resourcePath'")

fun walkFileTree(path: Path, predicate: (Path) -> Boolean = { true }): Set<File> {
  val files = mutableSetOf<File>()
  Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
    override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (predicate.invoke(path)) {
        files.add(path.toFile())
      }
      return super.visitFile(path, attrs)
    }
  })

  return files
}

fun TemporaryFolder.emptyZipFile(): File = newFile("${System.currentTimeMillis()}.zip").apply {
  FileOutputStream(this).use { fos ->
    ZipOutputStream(fos).run {
      close()
    }
  }
}
