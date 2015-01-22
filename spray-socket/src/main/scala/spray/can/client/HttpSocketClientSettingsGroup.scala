package spray.can
package client

import akka.actor._
import spray.can.socket._

private[can] class HttpSocketClientSettingsGroup(
    protected val clientSettings: ClientConnectionSettings,
    protected val httpSettings: HttpExt#Settings,
    protected val socketSettings: WebSocketSettings)
  extends HttpClientSettingsGroup(clientSettings, httpSettings) {

  import context._

  override val pipelineStage = HttpSocketClientConnection.pipelineStage(clientSettings, socketSettings)

  override def receive: Receive = {
    case connect: Http.Connect =>
      val commander = sender
      context.actorOf(
        props = Props(classOf[HttpClientConnection], commander, connect, pipelineStage, clientSettings)
                     .withDispatcher(httpSettings.ConnectionDispatcher),
        name  = connectionCounter.next().toString)

    case Http.CloseAll(cmd) =>
      val children = context.children.toSet
      if (children.isEmpty) {
        sender ! Http.ClosedAll
        context.stop(self)
      }
      else {
        children.foreach { _ ! cmd }
        context.become(closing(children, Set(sender)))
      }
  }
}
