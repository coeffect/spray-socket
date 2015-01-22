package spray.can
package client

import spray.can.parsing._
import spray.can.server._
import spray.can.socket._
import spray.io._

private[can] object HttpSocketClientConnection {
  def pipelineStage(
      clientSettings: ClientConnectionSettings,
      socketSettings: WebSocketSettings)
    : RawPipelineStage[SslTlsContext] = {

    import clientSettings._

    val httpStage =
      ClientFrontend(requestTimeout) >>
      ResponseChunkAggregation(responseChunkAggregationLimit) ? (responseChunkAggregationLimit > 0) >>
      SSLSessionInfoSupport ? parserSettings.sslSessionInfoHeader >>
      ResponseParsing(parserSettings) >>
      RequestRendering(clientSettings)

    HttpSocketClientSupport(clientSettings, socketSettings, httpStage) >>
    ConnectionTimeouts(idleTimeout) ? (reapingCycle.isFinite && idleTimeout.isFinite) >>
    SslTlsSupportPatched(maxEncryptionChunkSize, parserSettings.sslSessionInfoHeader) >>
    TickGenerator(reapingCycle) ? (idleTimeout.isFinite || requestTimeout.isFinite)
  }
}
