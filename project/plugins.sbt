logLevel := Level.Warn

resolvers += Resolver.bintrayRepo("tpolecat", "maven")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.3")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.2.8")

addSbtPlugin("com.softwaremill.clippy" %% "plugin-sbt" % "0.3.0")
