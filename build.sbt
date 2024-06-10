lazy val `sbt-uglify` = project in file(".")

enablePlugins(SbtWebBase)

sonatypeProfileName := "com.github.sbt.sbt-uglify" // See https://issues.sonatype.org/browse/OSSRH-77819#comment-1203625

description := "sbt-web plugin for minifying JavaScript files"

developers += Developer(
  "playframework",
  "The Play Framework Team",
  "contact@playframework.com",
  url("https://github.com/playframework")
)

addSbtJsEngine("1.3.7")

libraryDependencies ++= Seq(
  "org.webjars.npm" % "uglify-js" % "3.17.4",
  "io.monix" %% "monix" % "3.4.1"
)

// Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
ThisBuild / dynverVTagPrefix := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  dynverAssertTagVersion.value
  s
}

//scriptedBufferLog := false
