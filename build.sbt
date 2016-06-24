name := "cats-examples"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.typelevel" %% "cats" % "0.6.0"

tutSettings

tutSourceDirectory := baseDirectory.value / "docs" / "src"
tutTargetDirectory := baseDirectory.value / "docs"