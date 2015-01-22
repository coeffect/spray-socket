package spray.routing
package directives

import akka.actor._
import spray.can._
import spray.can.socket._

trait WebSocketDirectives {
  def websocket(handler: => ActorRef): StandardRoute =
    new WebSocketDirectives.UpgradeToWebSocket(handler)

  def websocket(protocol: String)(handler: => ActorRef): StandardRoute =
    new WebSocketDirectives.UpgradeToWebSocketProtocol(protocol)(handler)
}

object WebSocketDirectives extends WebSocketDirectives {
  private final class UpgradeToWebSocket(handler: => ActorRef) extends StandardRoute {
    override def apply(ctx: RequestContext): Unit = HandshakeRequest.validate(ctx.request) match {
      case Left(rejection) => ctx.reject(UpgradeRejection(rejection))
      case Right(handshake) => ctx.responder ! HttpSocket.UpgradeServer(handshake.response, handler)
    }
  }

  private final class UpgradeToWebSocketProtocol(protocol: String)(handler: => ActorRef) extends StandardRoute {
    override def apply(ctx: RequestContext): Unit = HandshakeRequest.validate(ctx.request) match {
      case Left(rejection) => ctx.reject(UpgradeRejection(rejection))
      case Right(handshake) =>
        if (handshake.protocols.contains(protocol)) ctx.responder ! HttpSocket.UpgradeServer(handshake.response(protocol), handler)
        else ctx.reject(UpgradeRejection.ProtocolNotSupported(protocol))
    }
  }
}
