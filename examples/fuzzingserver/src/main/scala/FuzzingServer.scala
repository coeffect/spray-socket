import akka.actor._
import akka.io._
import spray.can._
import spray.can.socket._
import spray.routing._

object FuzzingServer extends App {
  val host = if (args.length > 0) args(0) else "localhost"
  val port = if (args.length > 1) args(1).toShort else 9001

  implicit val system = ActorSystem()
  val server = system.actorOf(Props(classOf[FuzzingListener]), "fuzzingserver")
  IO(HttpSocket) ! Http.Bind(server, host, port)
  system.awaitTermination()
}

class FuzzingServer extends Actor with ActorLogging with HttpSocketService {
  override def receive: Receive = httpMode

  def httpMode: Receive = runRoute {
    websocket(self)
  }

  def socketMode: Receive = {
    case frame: Frame if frame.opcode.isData =>
      log.info("Bouncing " + frame)
      sender ! frame

    case _: Http.ConnectionClosed =>
      context.stop(self)
  }

  override def onConnectionUpgraded(): Unit = {
    log.info("Upgraded to WebSocket")
    context.become(socketMode)
  }

  override def onConnectionClosed(event: Tcp.ConnectionClosed): Unit = {
    context.stop(self)
  }
}

class FuzzingListener extends Actor {
  override def receive: Receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val handler = context.actorOf(Props(classOf[FuzzingServer]))
      sender ! Http.Register(handler)
  }
}
