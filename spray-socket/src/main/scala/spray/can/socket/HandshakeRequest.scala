package spray.can
package socket

import java.security._
import scala.annotation._
import scala.util._
import spray.http._
import spray.http.HttpHeaders._

final class HandshakeRequest(val request: HttpRequest, val protocols: List[String], val key: String) {
  val accept: String =
    new sun.misc.BASE64Encoder().encode(
      MessageDigest.getInstance("SHA-1").digest(
        key.getBytes("UTF-8") ++ "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes("UTF-8")))

  private def responseHeaders(protocol: String = ""): List[HttpHeader] =
    RawHeader("Upgrade", "websocket")         ::
    Connection("Upgrade" :: Nil)              ::
    RawHeader("Sec-WebSocket-Accept", accept) ::
    (if (protocol.length > 0) RawHeader("Sec-WebSocket-Protocol", protocol) :: Nil else Nil)

  def response(protocol: String): HttpResponse =
    HttpResponse(
      status  = StatusCodes.SwitchingProtocols,
      headers = responseHeaders(protocol))

  def response: HttpResponse =
    HttpResponse(
      status  = StatusCodes.SwitchingProtocols,
      headers = responseHeaders())

  def unapply(response: HttpResponse): Option[HandshakeResponse] = response match {
    case HttpResponse(StatusCodes.SwitchingProtocols, _, headers, HttpProtocols.`HTTP/1.1`) =>
      var connectionUpgrade = false
      var webSocketUpgrade = false
      var webSocketAccept = ""
      var webSocketProtocol = ""
      @tailrec def shouldUpgradeWebSocket(tokens: List[String]): Boolean = {
        if (tokens.isEmpty) false
        else {
          if (tokens.head.trim.toLowerCase == "websocket") true
          else shouldUpgradeWebSocket(tokens.tail)
        }
      }
      @tailrec def processHeaders(headers: List[HttpHeader]): Unit = {
        if (!headers.isEmpty) {
          headers.head match {
            case connection: Connection =>
              connectionUpgrade = connection.hasUpgrade

            case RawHeader("Upgrade", upgrade) =>
              webSocketUpgrade = shouldUpgradeWebSocket(upgrade.split(',').toList)

            case RawHeader("Sec-WebSocket-Accept", accept) =>
              webSocketAccept = accept

            case RawHeader("Sec-WebSocket-Protocol", protocol) =>
              webSocketProtocol = protocol

            case _ =>
          }
          processHeaders(headers.tail)
        }
      }
      processHeaders(headers)

      if (connectionUpgrade && webSocketUpgrade && webSocketAccept == accept && (protocols.isEmpty || protocols.contains(webSocketProtocol)))
        Some(new HandshakeResponse(response, webSocketProtocol))
      else None

    case _ => None
  }
}

object HandshakeRequest {
  private def nonce(): String = {
    val data = new Array[Byte](16)
    Random.nextBytes(data)
    new sun.misc.BASE64Encoder().encode(data)
  }

  def apply(request: HttpRequest, protocols: List[String] = Nil, key: String = nonce()): HandshakeRequest = {
    val upgradeHeaders =
      RawHeader("Upgrade", "websocket")        ::
      Connection("Upgrade" :: Nil)             ::
      RawHeader("Sec-WebSocket-Key", key)      ::
      RawHeader("Sec-WebSocket-Version", "13") ::
      (if (!protocols.isEmpty) RawHeader("Sec-WebSocket-Protocol", protocols.mkString(", ")) :: Nil else Nil)
    new HandshakeRequest(request.withHeaders(upgradeHeaders), protocols, key)
  }

  def unapply(request: HttpRequest): Option[HandshakeRequest] = validate(request).right.toOption

  def validate(request: HttpRequest): Either[HandshakeRejection, HandshakeRequest] = request match {
    case HttpRequest(HttpMethods.GET, _, headers, _, HttpProtocols.`HTTP/1.1`) =>
      var connectionUpgrade = false
      var webSocketUpgrade = false
      var webSocketVersion = ""
      var webSocketKey = ""
      var webSocketProtocols = List.empty[String]
      @tailrec def shouldUpgradeWebSocket(tokens: List[String]): Boolean = {
        if (tokens.isEmpty) false
        else {
          if (tokens.head.trim.toLowerCase == "websocket") true
          else shouldUpgradeWebSocket(tokens.tail)
        }
      }
      @tailrec def trimProtocols(untrimmed: List[String], trimmed: List[String]): List[String] = {
        if (untrimmed.isEmpty) trimmed
        else trimProtocols(untrimmed.tail, untrimmed.head.trim :: trimmed)
      }
      @tailrec def processHeaders(headers: List[HttpHeader]): Unit = {
        if (!headers.isEmpty) {
          headers.head match {
            case connection: Connection =>
              connectionUpgrade = connection.hasUpgrade

            case RawHeader("Upgrade", upgrade) =>
              webSocketUpgrade = shouldUpgradeWebSocket(upgrade.split(',').toList)

            case RawHeader("Sec-WebSocket-Version", version) =>
              webSocketVersion = version

            case RawHeader("Sec-WebSocket-Key", key) =>
              webSocketKey = key

            case RawHeader("Sec-WebSocket-Protocol", protocols) =>
              webSocketProtocols = trimProtocols(protocols.split(',').toList, Nil)

            case _ =>
          }
          processHeaders(headers.tail)
        }
      }
      processHeaders(headers)

      if (!connectionUpgrade) Left(HandshakeRejection.MissingConnectionUpgrade)
      else if (!webSocketUpgrade) Left(HandshakeRejection.MissingUpgradeWebSocket)
      else if (webSocketVersion != "13") Left(HandshakeRejection.IncompatibleWebSocket(webSocketVersion))
      else Right(new HandshakeRequest(request, webSocketProtocols, webSocketKey))

    case _ => Left(HandshakeRejection.WebSocketNotSupported)
  }
}
