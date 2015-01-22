package spray.can
package server

import spray.can.parsing._
import spray.can.socket._
import spray.io._

private[can] object HttpSocketServerConnection {
  private[can] def pipelineStage[C <: ServerFrontend.Context with SslTlsContext](
      serverSettings: ServerSettings,
      socketSettings: WebSocketSettings,
      statsHolder: Option[StatsSupport.StatsHolder])
    : RawPipelineStage[C] = {

    import serverSettings._
    import timeouts._

    val httpStage =
      ServerFrontend(serverSettings) >>
      RequestChunkAggregation(requestChunkAggregationLimit) ? (requestChunkAggregationLimit > 0) >>
      PipeliningLimiter(pipeliningLimit) ? (pipeliningLimit > 0) >>
      StatsSupport(statsHolder.get) ? statsSupport >>
      RemoteAddressHeaderSupport ? remoteAddressHeader >>
      SSLSessionInfoSupport ? parserSettings.sslSessionInfoHeader >>
      RequestParsing(serverSettings) >>
      ResponseRendering(serverSettings)

    HttpSocketServerSupport(socketSettings, httpStage) >>
    ConnectionTimeouts(idleTimeout) ? (reapingCycle.isFinite && idleTimeout.isFinite) >>
    PreventHalfClosedConnections(sslEncryption) >>
    SslTlsSupportPatched(maxEncryptionChunkSize, parserSettings.sslSessionInfoHeader, tracing = sslTracing) ? sslEncryption >>
    TickGenerator(reapingCycle) ? (reapingCycle.isFinite && (idleTimeout.isFinite || requestTimeout.isFinite)) >>
    BackPressureHandling(backpressureSettings.get.noAckRate, backpressureSettings.get.readingLowWatermark) ? autoBackPressureEnabled
  }
}
