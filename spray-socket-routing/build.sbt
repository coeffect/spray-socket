import Dependencies._

description := "Spray WebSocket routing extension"

libraryDependencies ++= Seq(
  akkaActor    % "provided",
  sprayHttp    % "provided",
  sprayRouting % "provided")
