package spray.routing

import spray.can.socket._

sealed abstract class UpgradeRejection extends Rejection

object UpgradeRejection {
  def apply(rejection: HandshakeRejection): UpgradeRejection = rejection match {
    case HandshakeRejection.MissingConnectionUpgrade => MissingConnectionUpgrade
    case HandshakeRejection.MissingUpgradeWebSocket => MissingUpgradeWebSocket
    case HandshakeRejection.IncompatibleWebSocket(version) => IncompatibleWebSocket(version)
    case HandshakeRejection.WebSocketNotSupported => WebSocketNotSupported
  }

  case object MissingConnectionUpgrade extends UpgradeRejection

  case object MissingUpgradeWebSocket extends UpgradeRejection

  final case class IncompatibleWebSocket(version: String) extends UpgradeRejection

  final case class ProtocolNotSupported(protocol: String) extends UpgradeRejection

  case object WebSocketNotSupported extends UpgradeRejection
}
