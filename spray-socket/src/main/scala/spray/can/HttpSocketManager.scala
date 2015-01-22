package spray.can

import akka.actor._
import spray.can.client._
import spray.can.server._
import spray.can.socket._

private[can] class HttpSocketManager(httpSettings: HttpExt#Settings) extends HttpManager(httpSettings) {
  private val socketSettings = WebSocketSettings(context.system)

  override def newHttpClientSettingsGroup(settings: ClientConnectionSettings, httpSettings: HttpExt#Settings) =
    new HttpSocketClientSettingsGroup(settings, httpSettings, socketSettings)

  override def newHttpListener(commander: ActorRef, bind: Http.Bind, httpSettings: HttpExt#Settings) =
    new HttpSocketListener(commander, bind, httpSettings, socketSettings)
}
