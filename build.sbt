lazy val modules = Seq(
  `spray-socket`,
  `spray-socket-routing`)

lazy val examples = Seq(
  `spray-socket-fuzzingclient`,
  `spray-socket-fuzzingserver`)

lazy val spray = project
  .in(file("."))
  .settings(commonSettings: _*)
  .dependsOn(modules.map(module => module: ClasspathDep[ProjectReference]): _*)
  .aggregate((modules ++ examples).map(module => module: ProjectReference): _*)

lazy val `spray-socket` = project
  .in(file("spray-socket"))
  .settings(moduleSettings: _*)

lazy val `spray-socket-routing` = project
  .in(file("spray-socket-routing"))
  .settings(moduleSettings: _*)
  .dependsOn(`spray-socket`)

lazy val `spray-socket-fuzzingclient` = project
  .in(file("examples/fuzzingclient"))
  .settings(commonSettings: _*)
  .dependsOn(`spray-socket`)

lazy val `spray-socket-fuzzingserver` = project
  .in(file("examples/fuzzingserver"))
  .settings(commonSettings: _*)
  .dependsOn(`spray-socket`, `spray-socket-routing`)

lazy val commonSettings = projectSettings ++ docSettings

lazy val moduleSettings = commonSettings ++ compileSettings

lazy val projectSettings = Seq(
  version := "1.3.2-SNAPSHOT",
  organization := "net.coeffect",
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("http://spray-socket.coeffect.net")),
  scalaVersion := "2.11.5",
  scalacOptions ++= Seq("-language:_"))

lazy val compileSettings = Seq(
  scalacOptions ++= Seq(
    "-optimise",
    "-deprecation",
    "-unchecked",
    "-Xfuture",
    "-Ywarn-adapted-args",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused",
    "-Ywarn-unused-import"))

lazy val docSettings = Seq(
  scalacOptions in (Compile, doc) ++= {
    val tagOrBranch = if (version.value.endsWith("-SNAPSHOT")) "master" else "v" + version.value
    val docSourceUrl = "https://github.com/coeffect/spray-socket/tree/" + tagOrBranch + "€{FILE_PATH}.scala"
    Seq("-groups",
        "-implicits",
        "-diagrams",
        "-sourcepath", (baseDirectory in LocalProject("spray")).value.getAbsolutePath,
        "-doc-source-url", docSourceUrl)
  })

lazy val publishSettings = Seq(
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <scm>
      <url>git@github.com:coeffect/spray-socket.git</url>
      <connection>scm:git:git@github.com:coeffect/spray-socket.git</connection>
    </scm>
    <developers>
      <developer>
        <id>c9r</id>
        <name>Chris Sachs</name>
        <email>chris@coeffect.net</email>
      </developer>
    </developers>
  })

// Root project settings

Autobahn.settings

publish := ()
