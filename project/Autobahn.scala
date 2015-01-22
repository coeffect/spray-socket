import sbt._
import sbt.Keys._

object Autobahn {
  lazy val fuzzingclient     = taskKey[Unit]("Run the spray-socket-fuzzingclient app against the Autobahn fuzzingserver testsuite")
  lazy val fuzzingserver     = taskKey[Unit]("Run the Autobahn fuzzingclient testsuite against the spray-socket-fuzzingserver app")

  lazy val settings = Seq(
    fuzzingclient     := fuzzingclientTask.value,
    fuzzingserver     := fuzzingserverTask.value)

  private lazy val fuzzingclientTask = Def.task {
    val spec = (resourceDirectory in Compile in LocalProject("spray-socket-fuzzingclient")).value / "fuzzingserver.json"
    val serverCommand = s"wstest --mode=fuzzingserver --spec=$spec"
    val clientCommand = "sbt spray-socket-fuzzingclient/run"

    val server = serverCommand.run()
    Thread.sleep(10000)
    clientCommand.!
    server.destroy()
  }

  private lazy val fuzzingserverTask = Def.task {
    val spec = (resourceDirectory in Compile in LocalProject("spray-socket-fuzzingserver")).value / "fuzzingclient.json"
    val serverCommand = "sbt spray-socket-fuzzingserver/run"
    val clientCommand = s"wstest --mode=fuzzingclient --spec=$spec"

    val server = serverCommand.run()
    Thread.sleep(10000)
    clientCommand.!
    server.destroy()
  }
}
