organization := "com.typesafe.sbt"
name := "sbt-uglify"
description := "sbt-web plugin for minifying JavaScript files"
addSbtJsEngine("1.2.3")
libraryDependencies ++= Seq(
  "org.webjars.npm" % "uglify-js" % "2.8.14",
  "io.monix" %% "monix" % "2.3.0"
)

//scriptedBufferLog := false
