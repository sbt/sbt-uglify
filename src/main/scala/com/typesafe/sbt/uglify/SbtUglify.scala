package com.typesafe.sbt.uglify

import com.typesafe.sbt.jse.{SbtJsEngine, SbtJsTask}
import com.typesafe.sbt.proguard.Sbt10Compat
import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.{Compat, PathMapping, SbtWeb, incremental}
import monix.reactive.Observable
import sbt.Keys._
import sbt.{Task, _}
import Sbt10Compat.SbtIoPath._

import scala.concurrent.Await

object Import {

  val uglify = TaskKey[Pipeline.Stage]("uglify", "Perform UglifyJS optimization on the asset pipeline.")

    val uglifyBuildDir = settingKey[File]("Where UglifyJS will copy source files and write minified files to. Default: resourceManaged / build")
    val uglifyComments = settingKey[Option[String]]("Specifies comments handling. Default: None")
    val uglifyCompress = settingKey[Boolean]("Enables compression. Default: true")
    val uglifyCompressOptions = settingKey[Seq[String]]("Options for compression such as hoist_vars, if_return etc. Default: Nil")
    val uglifyDefine = settingKey[Option[String]]("Define globals. Default: None")
    val uglifyEnclose = settingKey[Boolean]("Enclose in one big function. Default: false")
    val uglifyIncludeSource = settingKey[Boolean]("Include the content of source files in the source map as the sourcesContent property. Default: false")
    val uglifyMangle = settingKey[Boolean]("Enables name mangling. Default: true")
    val uglifyMangleOptions = settingKey[Seq[String]]("Options for mangling such as sort, topLevel etc. Default: Nil")
    val uglifyPreamble = settingKey[Option[String]]("Any preamble to include at the start of the output. Default: None")
    val uglifyReserved = settingKey[Seq[String]]("Reserved names to exclude from mangling. Default: Nil")
    val uglifyBeautify = settingKey[Boolean]("Whether to beautify the output. Defaults to false.")
    val uglifyBeautifyOptions = settingKey[Seq[String]]("Additional arguments for --beautify that control the code output. Defaults to Nil.")
    val uglifyOps = settingKey[UglifyOps.UglifyOpsMethod]("A function defining how to combine input files into output files. Default: UglifyOps.singleFileWithSourceMapOut")

  object UglifyOps {

    /** A list of input files mapping to a single output file. */
    case class UglifyOpGrouping(inputFiles: Seq[PathMapping], outputFile: String, inputMapFile: Option[PathMapping], outputMapFile: Option[String])

    type UglifyOpsMethod = (Seq[PathMapping]) => Seq[UglifyOpGrouping]

    def dotMin(file: String): String = {
      val exti = file.lastIndexOf('.')
      val (pfx, ext) = if (exti == -1) (file, "")
      else file.splitAt(exti)
      pfx + ".min" + ext
    }

    /** Use when uglifying single files */
    val singleFile: UglifyOpsMethod = { mappings =>
      mappings.map(fp => UglifyOpGrouping(Seq(fp), dotMin(fp._2), None, None))
    }

    /** Use when uglifying single files and you want a source map out */
    val singleFileWithSourceMapOut: UglifyOpsMethod = { mappings =>
      mappings.map(fp => UglifyOpGrouping(Seq(fp), dotMin(fp._2), None, Some(dotMin(fp._2) + ".map")))
    }

    /** Use when uglifying single files and you want a source map in and out - remember to includeFilter .map files */
    val singleFileWithSourceMapInAndOut: UglifyOpsMethod = { mappings =>
      val sources = mappings.filter(source => source._2.endsWith(".js"))
      val sourceMaps = mappings.filter(sourceMap => sourceMap._2.endsWith(".js.map"))

      sources.map { source =>
        UglifyOpGrouping(
          Seq(source),
          dotMin(source._2),
          sourceMaps.find(sourceMap =>
            sourceMap._2 equals (source._2 + ".map")
          ),
          Some(dotMin(source._2) + ".map")
        )
      }
    }
  }

}

object SbtUglify extends AutoPlugin {

  override def requires = SbtJsTask

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtJsEngine.autoImport.JsEngineKeys._
  import SbtJsTask.autoImport.JsTaskKeys._
  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import UglifyOps._

  implicit private class RichFile(val self: File) extends AnyVal {
    def startsWith(dir: File): Boolean = self.getPath.startsWith(dir.getPath)
  }

  override def projectSettings = Seq(
    uglifyBuildDir := (resourceManaged in uglify).value / "build",
    uglifyComments := None,
    uglifyCompress := true,
    uglifyCompressOptions := Nil,
    uglifyDefine := None,
    uglifyEnclose := false,
    excludeFilter in uglify :=
      HiddenFileFilter ||
        GlobFilter("*.min.js") ||
        new SimpleFileFilter({ file =>
          file.startsWith((WebKeys.webModuleDirectory in Assets).value)
        }),
    includeFilter in uglify := GlobFilter("*.js"),
    uglifyIncludeSource := false,
    resourceManaged in uglify := webTarget.value / uglify.key.label,
    uglifyMangle := true,
    uglifyMangleOptions := Nil,
    uglifyPreamble := None,
    uglifyReserved := Nil,
    uglify := runOptimizer.dependsOn(webJarsNodeModules in Plugin).value,
    uglifyBeautify := false,
    uglifyBeautifyOptions := Nil,
    uglifyOps := singleFileWithSourceMapOut
  )

  private def runOptimizer: Def.Initialize[Task[Pipeline.Stage]] = Def.task {
    val include = (includeFilter in uglify).value
    val exclude = (excludeFilter in uglify).value
    val buildDirValue = uglifyBuildDir.value
    val uglifyOpsValue = uglifyOps.value
    val streamsValue = streams.value
    val nodeModuleDirectoriesInPluginValue = (nodeModuleDirectories in Plugin).value
    val webJarsNodeModulesDirectoryInPluginValue = (webJarsNodeModulesDirectory in Plugin).value
    val mangleValue = uglifyMangle.value
    val mangleOptionsValue = uglifyMangleOptions.value
    val reservedValue = uglifyReserved.value
    val compressValue = uglifyCompress.value
    val compressOptionsValue = uglifyCompressOptions.value
    val beautifyValue = uglifyBeautify.value
    val beautifyOptionsValue = uglifyBeautifyOptions.value
    val encloseValue = uglifyEnclose.value
    val includeSourceValue = uglifyIncludeSource.value
    val timeout = (timeoutPerSource in uglify).value
    val stateValue = state.value
    val engineTypeInUglifyValue = (engineType in uglify).value
    val commandInUglifyValue = (command in uglify).value
    val options = Seq(
      uglifyComments.value,
      compressValue,
      compressOptionsValue,
      beautifyValue,
      beautifyOptionsValue,
      uglifyDefine.value,
      encloseValue,
      (excludeFilter in uglify).value,
      (includeFilter in uglify).value,
      (resourceManaged in uglify).value,
      mangleValue,
      mangleOptionsValue,
      uglifyPreamble.value,
      reservedValue,
      includeSourceValue
    ).mkString("|")

    (mappings) => {
      val optimizerMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      SbtWeb.syncMappings(
        Compat.cacheStore(streamsValue, "uglify-cache"),
        optimizerMappings,
        buildDirValue
      )
      val appInputMappings = optimizerMappings.map(p => uglifyBuildDir.value / p._2 -> p._2)
      val groupings = uglifyOpsValue(appInputMappings)

      implicit val opInputHasher = OpInputHasher[UglifyOpGrouping](io =>
        OpInputHash.hashString(
          (io.outputFile +: io.inputFiles.map(_._1.getAbsolutePath)).mkString("|") + "|" + options
        )
      )

      val (outputFiles, ()) = incremental.syncIncremental(streamsValue.cacheDirectory / "run", groupings) {
        modifiedGroupings: Seq[UglifyOpGrouping] =>
          if (modifiedGroupings.nonEmpty) {

            streamsValue.log.info(s"Optimizing ${modifiedGroupings.size} JavaScript(s) with Uglify")

            val nodeModulePaths = nodeModuleDirectoriesInPluginValue.map(_.getPath)
            val uglifyjsShell = webJarsNodeModulesDirectoryInPluginValue / "uglify-js" / "bin" / "uglifyjs"


            val mangleArgs = if (mangleValue) {
              val stdArg = Seq("--mangle")
              val stdArgWithOptions = if (mangleOptionsValue.isEmpty) stdArg else stdArg :+ mangleOptionsValue.mkString(",")
              val reservedArgs = if (reservedValue.isEmpty) Nil else Seq("--reserved", reservedValue.mkString(","))
              stdArgWithOptions ++ reservedArgs
            } else {
              Nil
            }

            val compressArgs = if (compressValue) {
              val stdArg = Seq("--compress")
              if (compressOptionsValue.isEmpty) stdArg else stdArg :+ compressOptionsValue.mkString(",")
            } else {
              Nil
            }

            val beautifyArgs = if (beautifyValue) {
              val stdArg = Seq("--beautify")
              if (beautifyOptionsValue.isEmpty) stdArg else stdArg :+ beautifyOptionsValue.mkString(",")
            } else {
              Nil
            }

            val defineArgs = uglifyDefine.value.map(Seq("--define", _)).getOrElse(Nil)

            val encloseArgs = if (encloseValue) Seq("--enclose") else Nil

            val commentsArgs = uglifyComments.value.map(Seq("--comments", _)).getOrElse(Nil)

            val preambleArgs = uglifyPreamble.value.map(Seq("--preamble", _)).getOrElse(Nil)

            val includeSourceArgs = if (includeSourceValue) Seq("--source-map-include-sources") else Nil

            val commonArgs =
              mangleArgs ++
                compressArgs ++
                beautifyArgs ++
                defineArgs ++
                encloseArgs ++
                commentsArgs ++
                preambleArgs ++
                includeSourceArgs

            def executeUglify(args: Seq[String]) = monix.eval.Task {
              SbtJsTask.executeJs(
                stateValue.copy(),
                engineTypeInUglifyValue,
                commandInUglifyValue,
                nodeModulePaths,
                uglifyjsShell,
                args: Seq[String],
                timeout
              )
            }


            val resultObservable: Observable[(UglifyOpGrouping, OpResult)] = Observable.fromIterable(
              modifiedGroupings
                .sortBy(_.inputFiles.map(_._1.length()).sum)
                .reverse
            ).map { grouping =>
              val inputFiles = grouping.inputFiles.map(_._1)
              val inputFileArgs = inputFiles.map(_.getPath)

              val outputFile = buildDirValue / grouping.outputFile
              IO.createDirectory(outputFile.getParentFile)
              val outputFileArgs = Seq("--output", outputFile.getPath)

              val inputMapFileArgs = if (grouping.inputMapFile.isDefined) {
                val inputMapFile = grouping.inputMapFile.map(_._1)
                Seq("--in-source-map") ++ inputMapFile.map(_.getPath)
              } else {
                Nil
              }

              val (outputMapFile, outputMapFileArgs) = if (grouping.outputMapFile.isDefined) {
                val outputMapFile = buildDirValue / grouping.outputMapFile.get
                IO.createDirectory(outputMapFile.getParentFile)
                (Some(outputMapFile), Seq(
                  "--source-map", outputMapFile.getPath,
                  "--source-map-url", outputMapFile.getName,
                  "--prefix", "relative"))
              } else {
                (None, Nil)
              }

              val args =
                outputFileArgs ++
                  inputFileArgs ++
                  outputMapFileArgs ++
                  inputMapFileArgs ++
                  commonArgs


              executeUglify(args).map { result =>
                val success = result.headOption.fold(true)(_ => false)
                grouping -> (
                  if (success)
                    OpSuccess(inputFiles.toSet, Set(outputFile) ++ outputMapFile)
                  else
                    OpFailure)
              }
            }.mergeMap(task => Observable.fromTask(task))

            val uglifyPool = monix.execution.Scheduler.computation(
              parallelism = java.lang.Runtime.getRuntime.availableProcessors
            )
            val result = Await.result(
              resultObservable.toListL.runAsync(uglifyPool),
              timeout * modifiedGroupings.size
            )

            (result.toMap, ())
          } else {
            (Map.empty, ())
          }
      }

      (mappings.toSet ++ outputFiles.pair(relativeTo(buildDirValue))).toSeq
    }
  }
}
