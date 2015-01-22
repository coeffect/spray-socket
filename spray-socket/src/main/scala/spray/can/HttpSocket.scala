package spray.can

import akka.actor._
import akka.io._
import spray.can.socket._
import spray.http._

class HttpSocketExt(system: ExtendedActorSystem) extends HttpExt(system) {
  override val manager = system.actorOf(
    props = Props(new HttpSocketManager(Settings)).withDispatcher(Settings.ManagerDispatcher),
    name  = "IO-HTTP-SOCKET")
}

object HttpSocket extends ExtensionKey[HttpSocketExt] {
  case class UpgradeClient(request: HttpRequest, handler: ActorRef) extends Tcp.Command

  case class UpgradeServer(response: HttpResponse, handler: ActorRef) extends Tcp.Command

  case object Upgraded extends Tcp.Event

  private[can] final case class FrameEvent(frame: Frame) extends Tcp.Event

  private[can] final case class FrameCommand(frame: Frame) extends Tcp.Command
}
