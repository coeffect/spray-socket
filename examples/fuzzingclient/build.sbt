import Dependencies._

description := "WebSocket client for autobahn fuzzingserver testsuite"

libraryDependencies ++= Seq(
  akkaActor,
  akkaSlf4j,
  logback,
  sprayCan)

publish := ()
