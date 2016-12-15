name := "cats-examples"

version := "1.0"

val scalaV = "2.11.8"

lazy val core = project
  .settings(moduleName := "core")
  .settings(libraryDependencies += "org.typelevel" %% "cats" % "0.7.2")
  .settings(scalaVersion := scalaV)
  .settings(tutSettings)
  .settings(tutSourceDirectory := baseDirectory.value / "docs" / "src")
  .settings(tutTargetDirectory := baseDirectory.value / "docs")

