import sbt._

object Dependencies {
  def akkaVersion    = "2.3.9"
  def logbackVersion = "1.1.2"
  def sprayVersion   = "1.3.2"

  val akkaActor    = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaSlf4j    = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val logback      = "ch.qos.logback" % "logback-classic" % logbackVersion

  val sprayCan     = "io.spray" %% "spray-can"     % sprayVersion
  val sprayHttp    = "io.spray" %% "spray-http"    % sprayVersion
  val sprayIO      = "io.spray" %% "spray-io"      % sprayVersion
  val sprayRouting = "io.spray" %% "spray-routing" % sprayVersion
  val sprayUtil    = "io.spray" %% "spray-util"    % sprayVersion
}
