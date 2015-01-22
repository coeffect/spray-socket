package spray.can
package server

import akka.actor._
import akka.io._
import akka.util._
import scala.annotation._
import spray.can.client._
import spray.can.parsing._
import spray.can.socket._
import spray.http._
import spray.io._

private[can] object HttpSocketClientSupport {
  def apply[C <: PipelineContext](
      clientSettings: ClientConnectionSettings,
      webSocketSettings: WebSocketSettings,
      httpStage: RawPipelineStage[C])
    : RawPipelineStage[C] =
    new HttpSocketClientSupport[C](clientSettings, webSocketSettings, httpStage)
}

private[can] class HttpSocketClientSupport[-C <: PipelineContext](
    protected val clientSettings: ClientConnectionSettings,
    protected val webSocketSettings: WebSocketSettings,
    protected val httpStage: RawPipelineStage[C])
  extends RawPipelineStage[C] {

  override def apply(context: C, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new HttpSocketSupportPipelines[C](context, commandPL, eventPL, clientSettings, webSocketSettings, httpStage)
}

private[can] class HttpSocketSupportPipelines[-C <: PipelineContext](
    protected[this] val context: C,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val clientSettings: ClientConnectionSettings,
    protected val webSocketSettings: WebSocketSettings,
    protected val httpStage: RawPipelineStage[C])
  extends DynamicPipelines {

  become(httpState)

  protected def httpState: State = new HttpState

  protected def upgradingState(httpPipelines: Pipelines, handler: ActorRef): State = new UpgradingState(httpPipelines, handler)

  protected def socketState(socketPipelines: Pipelines): State = new SocketState(socketPipelines)

  private[can] class HttpState extends State {
    protected val httpPipelines: Pipelines = httpStage(context, commandPL, eventPL)

    override val commandPipeline: Command => Unit = new CommandPipeline
    override val eventPipeline: Event => Unit = httpPipelines.eventPipeline

    private[can] class CommandPipeline extends (Command => Unit) {
      protected val httpCPL: Command => Unit = httpPipelines.commandPipeline

      override def apply(command: Command): Unit = command match {
        case HttpSocket.UpgradeClient(request, handler) =>
          httpCPL(Http.MessageCommand(request))
          become(upgradingState(httpPipelines, handler))

        case _ => httpCPL(command)
      }
    }
  }

  private[can] class UpgradingState(protected val httpPipelines: Pipelines, protected val handler: ActorRef) extends State {
    override val commandPipeline: Command => Unit = httpPipelines.commandPipeline
    override val eventPipeline: Event => Unit = new EventPipeline

    private[can] class EventPipeline extends (Event => Unit) {
      protected val httpEPL: Event => Unit = httpPipelines.eventPipeline

      override def apply(event: Event): Unit = event match {
        case Tcp.Received(data) =>
          handleResult(parse(data))

        case HandshakeReceived(response, socketData) =>
          httpEPL(Http.MessageEvent(response))
          val socketStage =
            WebSocketFrontend(webSocketSettings, handler) >>
            FrameComposing(webSocketSettings) >>
            FrameParsing(webSocketSettings) >>
            FrameRendering.masked(webSocketSettings)
          val socketPipelines = socketStage(context, commandPL, eventPL)
          val state = socketState(socketPipelines)
          become(state)
          context.log.debug("WebSocket opened")
          state.eventPipeline(HttpSocket.Upgraded)
          state.eventPipeline(Tcp.Received(socketData))

        case _ => httpEPL(event)
      }

      private[this] var socketData: ByteString = ByteString.empty

      private[this] var parse: Parser = new HandshakeResponseParser

      @tailrec private def handleResult(result: Result): Unit = result match {
        case Result.Emit(part, closeAfterResponseCompletion, continue) =>
          eventPipeline(HandshakeReceived(part.asInstanceOf[HttpResponse], socketData))
          socketData = ByteString.empty
          handleResult(continue())

        case Result.NeedMoreData(next)          => parse = next

        case Result.ParsingError(_, info)       => handleError(info)

        case Result.IgnoreAllFurtherInput       =>

        case Result.Expect100Continue(continue) => handleResult(continue())
      }

      private def handleError(info: ErrorInfo): Unit = {
        context.log.warning("Received invalid response: {}", info.formatPretty)
        commandPL(Http.Close)
        parse = Result.IgnoreAllFurtherInput
      }

      private[can] class HandshakeResponseParser extends HttpResponsePartParser(clientSettings.parserSettings)() {
        setRequestMethodForNextResponse(HttpMethods.GET)

        override def parseEntity(headers: List[HttpHeader], input: ByteString, bodyStart: Int, clh: Option[HttpHeaders.`Content-Length`],
                                 cth: Option[HttpHeaders.`Content-Type`], teh: Option[HttpHeaders.`Transfer-Encoding`], hostHeaderPresent: Boolean,
                                 closeAfterResponseCompletion: Boolean): Result = {
          socketData = input.drop(bodyStart)
          emit(message(headers, HttpEntity.Empty), closeAfterResponseCompletion) { Result.IgnoreAllFurtherInput }
        }
      }

      private[can] case class HandshakeReceived(response: HttpResponse, socketData: ByteString) extends Tcp.Event
    }
  }

  private[can] class SocketState(protected val socketPipelines: Pipelines) extends State {
    override val commandPipeline: Command => Unit = socketPipelines.commandPipeline
    override val eventPipeline: Event => Unit = new EventPipeline

    private[can] class EventPipeline extends (Event => Unit) {
      protected val socketEPL: Event => Unit = socketPipelines.eventPipeline

      override def apply(event: Event): Unit = event match {
        case _: AckEventWithReceiver =>
        case _ => socketEPL(event)
      }
    }
  }
}
