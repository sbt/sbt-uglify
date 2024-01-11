organization := "com.typesafe.sbt"
name := "sbt-uglify"
description := "sbt-web plugin for minifying JavaScript files"
addSbtJsEngine("1.2.2")
libraryDependencies ++= Seq(
  "org.webjars.npm" % "uglify-js" % "3.16.3",
  "io.monix" %% "monix" % "2.3.3"
)

//scriptedBufferLog := false
