// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package com.microsoft.ml.spark.codegen

import Config._
import DocGen._
import WrapperClassDoc._

import scala.util.matching.Regex
import java.util.regex.Pattern
import com.microsoft.ml.spark.core.env.FileUtilities._
import org.apache.commons.io.FilenameUtils._
import org.apache.commons.io.FileUtils

object CodeGen {

  def copyAllFiles(fromDir: File, rx: Regex, toDir: File): Unit = {
    if (!fromDir.isDirectory) {
      println(s"'$fromDir' is not a directory"); return
    }
    allFiles(fromDir, if (rx == null) null else f => rx.findFirstIn(f.getName).isDefined)
      .foreach { x => copyFile(x, toDir, overwrite = true) }
  }

  def copyAllFiles(fromDir: File, extension: String, toDir: File): Unit =
    copyAllFiles(fromDir,
      if (extension == null || extension == "") null
      else (Pattern.quote("." + extension) + "$").r,
      toDir)

  def copyAllFilesFromRoots(fromDir: File, roots: List[String], relPath: String,
                            extension: String, toDir: File): Unit = {
    roots.foreach { root =>
      val dir = new File(new File(fromDir, root), relPath)
      if (dir.exists && dir.isDirectory) copyAllFiles(dir, extension, toDir)
    }
  }

  def copyAllFilesFromRoots(fromDir: File, roots: List[String], relPath: String,
                            rx: Regex, toDir: File): Unit = {
    roots.foreach { root =>
      val dir = new File(new File(fromDir, root), relPath)
      if (dir.exists && dir.isDirectory) copyAllFiles(dir, rx, toDir)
    }
  }

  def generateArtifacts(): Unit = {
    println(
      s"""|Running code generation with config:
          |  topDir:    $topDir
          |  outputDir: $outputDir
          |  pyDir:     $pyDir
          |  pyTestDir: $pyTestDir
          |  pyDocDir:  $pyDocDir
          |  rDir:      $rDir
          |  rsrcDir:   $rSrcDir
          |  tmpDocDir: $tmpDocDir""".stripMargin)
    val roots = List("src")
    println("Creating temp folders")
    pySdkDir.mkdirs
    pyDir.mkdirs
    pyTestDir.mkdirs
    pyDocDir.mkdirs
    tmpDocDir.mkdirs
    rSrcDir.mkdirs
    rSdkDir.mkdirs

    println("Copying jar files to output directory")
    copyAllFilesFromRoots(topDir, roots, jarRelPath,
      (Pattern.quote("-" + mmlVer + ".jar") + "$").r,
      outputDir)

    println("Generating python APIs")
    PySparkWrapperGenerator()
    println("Generating R APIs")
    SparklyRWrapperGenerator()
    println("Generating .rst files for the Python APIs documentation")
    genRstFiles()

    // build init file
    val importStrings =
      allFiles(pyDir, f => "^[a-zA-Z]\\w*[.]py$".r.findFirstIn(f.getName).isDefined)
        .map(f => s"from mmlspark.${getBaseName(f.getName)} import *\n").mkString("")
    writeFile(new File(pyDir, "__init__.py"), packageHelp(importStrings))
    // package python+r zip files
    println(pyZipFile)
    zipFolder(pyDir, pyZipFile)
    zipFolder(rDir, rZipFile)
    FileUtils.forceDelete(rDir)
    // leave the python source files, so they will be included in the super-jar
    // FileUtils.forceDelete(pyDir)
    // delete the text files with the Python Class descriptions - truly temporary
    FileUtils.forceDelete(tmpDocDir)
  }

  def main(args: Array[String]): Unit = {
    org.apache.log4j.BasicConfigurator.configure(new org.apache.log4j.varia.NullAppender())
    generateArtifacts()
  }

}
