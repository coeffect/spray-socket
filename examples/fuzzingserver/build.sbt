import Dependencies._

description := "WebSocket server for autobahn fuzzingclient testsuite"

libraryDependencies ++= Seq(
  akkaActor,
  akkaSlf4j,
  logback,
  sprayCan,
  sprayRouting)

publish := ()
