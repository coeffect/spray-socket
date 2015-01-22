package spray.can
package socket

sealed abstract class HandshakeRejection

object HandshakeRejection {
  case object MissingConnectionUpgrade extends HandshakeRejection

  case object MissingUpgradeWebSocket extends HandshakeRejection

  final case class IncompatibleWebSocket(version: String) extends HandshakeRejection

  case object WebSocketNotSupported extends HandshakeRejection
}
