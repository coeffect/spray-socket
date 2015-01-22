# spray-socket

[![Build Status](https://travis-ci.org/coeffect/spray-socket.svg?branch=master)](https://travis-ci.org/coeffect/spray-socket)

WebSocket extension for [Spray](http://spray.io).  Supports simple routing
directives, and delegated WebSocket handler actors.

## Getting Started

Add the `spray-socket` dependency to your SBT build.

```
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies += "net.coeffect" %% "spray-socket" % "1.3.2-SNAPSHOT"
```

## Example: Echo Server

```scala
import akka.actor._
import akka.io._
import spray.can._
import spray.can.socket._
import spray.routing._

object EchoServer extends App {
  val host = if (args.length > 0) args(0) else "localhost"
  val port = if (args.length > 1) args(1).toShort else 9001

  implicit val system = ActorSystem()
  val server = system.actorOf(Props(classOf[EchoServer]))
  IO(HttpSocket) ! Http.Bind(server, host, port)
  system.awaitTermination()
}

class EchoServer extends Actor {
  override def receive: Receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val handler = context.actorOf(Props(classOf[EchoSocket]))
      sender ! Http.Register(handler)
  }
}

class EchoSocket extends Actor with ActorLogging with HttpSocketService {
  override def receive: Receive = httpMode

  def httpMode: Receive = runRoute {
    // The websocket(handler: => ActorRef) directive performs the websocket
    // handshake and then passes frames to a specified handler actor.
    // Use the websocket(protocol: String)(handler: => ActorRef) directive
    // to match against the Sec-WebSocket-Protocol header.
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
```

## Example: Console Client

```scala
// This example depends on the JBoss jreadline library.
// libraryDependencies += "org.jboss.jreadline" % "jreadline" % "0.20"

import akka.actor._
import akka.io._
import org.jboss.jreadline.console._
import scala.concurrent._
import spray.can._
import spray.http._

object ConsoleClient extends App {
  val host = if (args.length > 0) args(0) else "localhost"
  val port = if (args.length > 1) args(1).toShort else 9001
  val path = if (args.length > 2) args(2) else "/"

  implicit val system = ActorSystem()
  system.actorOf(Props(classOf[ConsoleClient], host, port, path))
  system.awaitTermination()
}

private class ConsoleClient(host: String, port: Short, path: String) extends Actor {
  import context._

  IO(HttpSocket) ! Http.Connect(host, port, true)

  private val console = new Console()

  private val request = HttpRequest(HttpMethods.GET, path)

  private val Handshake = websocket.HandshakeRequest(request)

  override def receive: Receive = httpMode

  private def httpMode: Receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      console.pushToStdOut(s"Connected to $host:$port\n")
      sender ! HttpSocket.UpgradeClient(Handshake.request, self)

    case Handshake(response) =>

    case HttpSocket.Upgraded =>
      console.pushToStdOut("WebSocket open\n")
      context.become(socketMode(sender))
      readLine()

    case _: HttpResponse =>
      console.pushToStdErr("WebSocket handshake failed\n")
      onClose()

    case _: Http.ConnectionClosed =>
      onClose()

    case Http.CommandFailed(_: Http.Connect) =>
      console.pushToStdErr(s"Can't connect to $host:$port\n")
      console.stop()
      stop(self)
      system.shutdown()
  }

  private def socketMode(socket: ActorRef): Receive = {
    case frame: websocket.Frame =>
      onMessage(frame)

    case command: String =>
      socket ! websocket.Frame.Text(command)

    case _: Http.ConnectionClosed =>
      onClose()
  }

  private def readLine(): Unit = Future {
    val line = blocking(console.read(">> "))
    val command = if (line != null) line.getBuffer else null
    if (command != null && !command.equalsIgnoreCase("quit") && !command.equalsIgnoreCase("exit")) {
      if (command.length > 0) self ! command
      readLine()
    }
    else {
      console.stop()
      stop(self)
      system.shutdown()
    }
  }

  private def onMessage(frame: websocket.Frame): Unit = {
    if (frame.opcode == websocket.Opcode.Text) {
      val message = frame.payload.utf8String
      console.pushToStdOut("\n<< ")
      console.pushToStdOut(message)
      console.pushToStdOut("\n")
    }
  }

  private def onClose(): Unit = {
    console.pushToStdOut("Connection closed\n")
    console.stop()
    stop(self)
    system.shutdown()
  }
}
```
