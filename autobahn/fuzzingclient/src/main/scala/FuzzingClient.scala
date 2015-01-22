import akka.actor._
import akka.io._
import spray.can._
import spray.can.socket._
import spray.http._

object FuzzingClient extends App {
  val agent = "spray-socket-client"
  val host = if (args.length > 0) args(0) else "localhost"
  val port = if (args.length > 1) args(1).toShort else 9001

  implicit val system = ActorSystem()
  system.actorOf(Props(classOf[TestSuiteClient]))
  system.awaitTermination()
}

abstract class FuzzingClient extends Actor with ActorLogging {
  import context._

  protected def request: HttpRequest

  IO(HttpSocket) ! Http.Connect(FuzzingClient.host, FuzzingClient.port, false)

  private[this] val Handshake = HandshakeRequest(request)

  override def receive: Receive = httpMode

  protected def httpMode: Receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      log.debug("Sending WebSocket handshake request")
      sender ! HttpSocket.UpgradeClient(Handshake.request, self)

    case Handshake(response) =>
      log.debug("Received WebSocket handshake response")
      become(socketMode)

    case _: HttpResponse =>
      log.warning("Invalid WebSocket handshake response")
      onClose()

    case _: Http.ConnectionClosed =>
      onClose()
  }

  protected def socketMode: Receive = {
    case frame: Frame =>
      onMessage(frame)

    case _: Http.ConnectionClosed =>
      onClose()

    case HttpSocket.Upgraded =>
      log.info("Upgraded to WebSocket")
  }

  protected def onMessage(frame: Frame): Unit

  protected def onClose(): Unit
}

class TestSuiteClient extends FuzzingClient {
  import context._

  protected override def request: HttpRequest =
    HttpRequest(HttpMethods.GET, "/getCaseCount")

  private[this] var n = 0

  protected override def onMessage(frame: Frame): Unit = {
    n = frame.payload.utf8String.toInt
    log.info(s"Case count: $n")
  }

  protected override def onClose(): Unit = {
    stop(self)
    if (n > 0) system.actorOf(Props(classOf[TestCaseClient], 1, n), "case1")
    else system.shutdown()
  }
}

class TestCaseClient(i: Int, n: Int) extends FuzzingClient {
  import context._

  protected override def request: HttpRequest =
    HttpRequest(HttpMethods.GET, s"/runCase?case=$i&agent=${FuzzingClient.agent}")

  protected override def onMessage(frame: Frame): Unit = {
    if (frame.opcode != Opcode.Pong) {
      log.info("Bouncing " + frame)
      sender ! frame
    }
  }

  protected override def onClose(): Unit = {
    log.info(s"Finished case $i of $n")
    stop(self)
    if (i < n) system.actorOf(Props(classOf[TestCaseClient], i + 1, n), s"case${i + 1}")
    else system.actorOf(Props(classOf[UpdateReportsClient], n))
  }
}

class UpdateReportsClient(n: Int) extends FuzzingClient {
  import context._

  protected override def request: HttpRequest =
    HttpRequest(HttpMethods.GET, s"/updateReports?agent=${FuzzingClient.agent}")

  protected override def onMessage(frame: Frame): Unit = ()

  protected override def onClose(): Unit = {
    log.info(s"Finished all $n cases")
    stop(self)
    system.shutdown()
  }
}
