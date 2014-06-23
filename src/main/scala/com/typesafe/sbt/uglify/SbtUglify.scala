package com.typesafe.sbt.uglify

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}

object Import {

  val uglify = TaskKey[Pipeline.Stage]("uglify", "Perform Uglify optimization on the asset pipeline.")

  object UglifyKeys {
    val buildDir = SettingKey[File]("uglify-build-dir", "Where uglifyjs will read from. It likes to have all the files in one place.")
    val comments = SettingKey[Option[String]]("uglify-comments", "Specifies comments handling. Defaults to None.")
    val compress = SettingKey[Boolean]("uglify-compress", "Enables compression. The default is to compress.")
    val compressOptions = SettingKey[Seq[String]]("uglify-compress-options", "Options for compression such as hoist_vars, if_return etc.")
    val define = SettingKey[Option[String]]("uglify-define", "Define globals. Defaults to None.")
    val enclose = SettingKey[Boolean]("uglify-enclose", "Enclose in one big function. Defaults to false.")
    val mangle = SettingKey[Boolean]("uglify-mangle", "Enables name mangling. The default is to mangle.")
    val mangleOptions = SettingKey[Seq[String]]("uglify-mangle-options", "Options for mangling such as sort, topLevel etc.")
    val output = TaskKey[String]("uglify-output", "The target relative url path for Uglify output. Defaults to ./main.min.js")
    val preamble = SettingKey[Option[String]]("uglify-preamble", "Any preamble to include at the start of the output. Defaults to None")
    val reserved = SettingKey[Seq[String]]("uglify-reserved", "Reserved names to exclude from mangling.")
    val sourceMap = SettingKey[Boolean]("uglify-source-map", "Enables source maps. The default is that source maps are enabled (true).")
  }

}

object SbtUglify extends AutoPlugin {

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import autoImport._
  import UglifyKeys._

  override def projectSettings = Seq(
    buildDir := (resourceManaged in uglify).value / "build",
    comments := None,
    compress := true,
    compressOptions := Nil,
    define := None,
    enclose := false,
    excludeFilter in uglify := HiddenFileFilter,
    includeFilter in uglify := GlobFilter("*.js"),
    resourceManaged in uglify := webTarget.value / uglify.key.label,
    mangle := true,
    mangleOptions := Nil,
    output := getOutputPath.value + "/main.min.js",
    preamble := None,
    reserved := Nil,
    sourceMap := true,
    uglify := runOptimizer.dependsOn(webJarsNodeModules in Plugin).value
  )


  private def getOutputPath: Def.Initialize[Task[String]] = Def.task {
    def dirIfExists(dir: String): Option[String] = {
      val dirPath = dir + java.io.File.separator
      if ((mappings in Assets).value.exists(m => m._2.startsWith(dirPath))) {
        Some(dir)
      } else {
        None
      }
    }
    dirIfExists("js").orElse(dirIfExists("javascripts")).getOrElse(".")
  }

  private def runOptimizer: Def.Initialize[Task[Pipeline.Stage]] = Def.task {
    mappings =>

      val include = (includeFilter in uglify).value
      val exclude = (excludeFilter in uglify).value
      val optimizerMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      SbtWeb.syncMappings(
        streams.value.cacheDirectory,
        optimizerMappings,
        buildDir.value
      )

      val buildMappings = optimizerMappings.map(o => buildDir.value / o._2)

      val cacheDirectory = streams.value.cacheDirectory / uglify.key.label
      val runUpdate = FileFunction.cached(cacheDirectory, FilesInfo.hash) {
        inputFiles =>
          streams.value.log.info("Optimizing JavaScript with Uglify")

          val outputFile = buildDir.value / output.value.replaceAllLiterally("/", java.io.File.separator)

          val inputFileArgs = inputFiles.map(_.getPath)

          val outputFileMap = file(outputFile.getPath + ".map")

          val sourceMapArgs = if (sourceMap.value) {
            Seq(
              "--source-map", outputFileMap.getPath,
              "--source-map-url", outputFileMap.getName,
              "--prefix", "relative"
            )
          } else {
            Nil
          }

          val mangleArgs = if (mangle.value) {
            val stdArg = Seq("--mangle")
            val stdArgWithOptions = if (mangleOptions.value.isEmpty) stdArg else stdArg :+ mangleOptions.value.mkString(",")
            val reservedArgs = if (reserved.value.isEmpty) Nil else Seq("--reserved", reserved.value.mkString(","))
            stdArgWithOptions ++ reservedArgs
          } else {
            Nil
          }

          val compressArgs = if (compress.value) {
            val stdArg = Seq("--compress")
            if (compressOptions.value.isEmpty) stdArg else stdArg :+ compressOptions.value.mkString(",")
          } else {
            Nil
          }

          val defineArgs = define.value.map(Seq("--define", _)).getOrElse(Nil)

          val encloseArgs = if (enclose.value) Seq("--enclose") else Nil

          val commentsArgs = comments.value.map(Seq("--comments", _)).getOrElse(Nil)

          val preambleArgs = preamble.value.map(Seq("--preamble", _)).getOrElse(Nil)

          val allArgs =
            Seq("--output", outputFile.getPath) ++
              inputFileArgs ++
              sourceMapArgs ++
              mangleArgs ++
              compressArgs ++
              defineArgs ++
              encloseArgs ++
              commentsArgs ++
              preambleArgs

          SbtJsTask.executeJs(
            state.value,
            (engineType in uglify).value,
            (command in uglify).value,
            (nodeModuleDirectories in Plugin).value.map(_.getPath),
            (webJarsNodeModulesDirectory in Plugin).value / "uglify-js" / "bin" / "uglifyjs",
            allArgs,
            (timeoutPerSource in uglify).value * optimizerMappings.size
          )

          buildDir.value.***.get.filter(!_.isDirectory).toSet
      }

      val optimizedMappings = runUpdate(buildMappings.toSet).pair(relativeTo(buildDir.value))
      (mappings.toSet -- optimizerMappings ++ optimizedMappings).toSeq
  }

}
