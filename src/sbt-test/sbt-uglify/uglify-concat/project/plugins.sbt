addSbtPlugin("com.github.sbt" % "sbt-uglify" % sys.props("project.version"))

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("snapshots"),
)
