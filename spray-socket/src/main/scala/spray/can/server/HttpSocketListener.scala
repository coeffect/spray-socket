package spray.can
package server

import akka.actor._
import akka.io._
import spray.can.socket._

private[can] class HttpSocketListener(
    protected val bindCommander: ActorRef,
    protected val bind: Http.Bind,
    protected val httpSettings: HttpExt#Settings,
    protected val socketSettings: WebSocketSettings)
  extends HttpListener(bindCommander, bind, httpSettings) {

  import context._
  import bind._

  private val connectionCounter = Iterator from 0
  private val serverSettings    = bind.settings getOrElse ServerSettings(system)
  private val statsHolder       = if (serverSettings.statsSupport) Some(new StatsSupport.StatsHolder) else None
  private val pipelineStage     = HttpSocketServerConnection.pipelineStage(serverSettings, socketSettings, statsHolder)

  override def connected(tcpListener: ActorRef): Receive = {
    case Tcp.Connected(remoteAddress, localAddress) =>
      val conn = sender
      actorOf(
        props = Props(new HttpServerConnection(conn, listener, pipelineStage, remoteAddress, localAddress, serverSettings))
                     .withDispatcher(httpSettings.ConnectionDispatcher),
        name  = connectionCounter.next().toString)

    case Http.GetStats            => statsHolder foreach { holder => sender ! holder.toStats }
    case Http.ClearStats          => statsHolder foreach { _.clear() }

    case Http.Unbind(timeout)     => unbind(tcpListener, Set(sender), timeout)

    case _: Http.ConnectionClosed =>
      // ignore; we receive this event when the user didn't register the handler within the registration timeout period
  }
}
