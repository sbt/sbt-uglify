lazy val root = (project in file(".")).enablePlugins(SbtWeb)

libraryDependencies += "org.webjars" % "bootstrap" % "3.3.7"

pipelineStages := Seq(uglify)

uglifyOps := { js =>
  Seq(UglifyOps.UglifyOpGrouping(js.sortBy(_._2), "javascripts/concat.min.js", None, Some("javascripts/concat.min.js.map")))
}

val checkMapFileContents = taskKey[Unit]("check that map contents are correct")

checkMapFileContents := {
  val contents = IO.read(file("target/web/stage/javascripts/concat.min.js.map"))
  if (
    !contents.contains(""""version":3""") ||
    !contents.contains(""""sources":["a.js","b.js","x.js"]""") ||
    !contents.contains(""""names":["a","b","define","call","this"]""") ||
    !contents.contains(""""mappings":""") || 
    !contents.contains(""""file":"concat.min.js"""")
    ) {
    sys.error(s"Unexpected contents: $contents")
  }
}
