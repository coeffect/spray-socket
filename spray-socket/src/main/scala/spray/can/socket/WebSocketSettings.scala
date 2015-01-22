package spray.can
package socket

import com.typesafe.config._
import spray.util._

case class WebSocketSettings(
    maxFrameSize: Int,
    maxMessageSize: Int) {

  require(125 <= maxFrameSize, "max-frame-size must be >= 125")
  require(125 <= maxMessageSize, "max-message-size must be >= 125")
}

object WebSocketSettings extends SettingsCompanion[WebSocketSettings]("spray.can.socket") {
  def fromSubConfig(conf: Config): WebSocketSettings =
    apply(
      conf.getIntBytes("max-frame-size"),
      conf.getIntBytes("max-message-size"))
}
