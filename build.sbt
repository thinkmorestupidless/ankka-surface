// ankka-surface: a surface is the representation of what a person last asked for, held on the
// server as an event sourced entity, rendered by any number of devices and steered from any of
// them. One Scala module on ankka's published SDK, `com.thinkmorestupidless:ankka-surface_3`, and
// one TypeScript package beside it, `ankka-surface` on npm (`device/`), for the device that renders
// and steers. Released on its own cadence against a published ankka version (`V.ankka` in
// project/Dependencies.scala); `sbt -Dankka.version=<v>` picks another.

import Dependencies.*

ThisBuild / scalaVersion  := V.scala
ThisBuild / organization  := "com.thinkmorestupidless"
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / homepage := Some(url("https://github.com/thinkmorestupidless/ankka-surface"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer(
    "thinkmorestupidless",
    "Trevor Burton-McCreadie",
    "",
    url("https://github.com/thinkmorestupidless")
  )
)

// Tests are serialised: the integration suites share one Postgres and contend when they overlap.
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Werror", "-source:3.7"),
  javacOptions ++= Seq("--release", "21"),
  Test / fork              := true,
  Test / parallelExecution := false,
  libraryDependencies ++= Seq(munit % Test)
)

/**
 * The surface: its entity, its rows, its endpoint, the interpreter seam and the vocabulary a
 * consumer declares.
 */
lazy val surface = project
  .in(file("surface"))
  .settings(commonSettings)
  .settings(
    name := "ankka-surface",
    libraryDependencies ++= Seq(
      ankkaSdk,
      ankkaRuntime,
      ankkaHttp,
      ankkaTestkit % Test,
      logback      % Test
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(surface)
  .settings(name := "ankka-surface-root", publish / skip := true)
