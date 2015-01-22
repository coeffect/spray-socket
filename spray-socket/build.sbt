import Dependencies._

description := "Spray WebSocket extension"

libraryDependencies ++= Seq(
  akkaActor % "provided",
  sprayCan  % "provided",
  sprayHttp % "provided",
  sprayIO   % "provided",
  sprayUtil % "provided")
