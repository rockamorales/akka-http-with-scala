ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val akkaVersion = "2.8.2"
lazy val scalaTestVersion = "3.1.4"
lazy val akkaHttpVersion = "10.5.2"

lazy val root = (project in file("."))
  .settings(
    name := "akk-http-with-scala",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,

      //testing
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion
    )
  )
