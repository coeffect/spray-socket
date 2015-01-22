package spray.can
package server

import spray.can.socket._
import spray.io._

private[can] object HttpSocketServerSupport {
  def apply[C <: SslTlsContext](settings: WebSocketSettings, httpStage: RawPipelineStage[C]): RawPipelineStage[C] =
    new HttpSocketServerSupport[C](settings, httpStage)
}

private[can] class HttpSocketServerSupport[-C <: SslTlsContext](
    protected val settings: WebSocketSettings,
    protected val httpStage: RawPipelineStage[C])
  extends RawPipelineStage[C] {

  override def apply(context: C, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new HttpSocketServerSupportPipelines[C](context, commandPL, eventPL, settings, httpStage)
}

private[can] class HttpSocketServerSupportPipelines[-C <: SslTlsContext](
    protected[this] val context: C,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val settings: WebSocketSettings,
    protected val httpStage: RawPipelineStage[C])
  extends DynamicPipelines {

  become(httpState)

  protected def httpState: State = new HttpState

  protected def socketState(socketPipelines: Pipelines): State = new SocketState(socketPipelines)

  private[can] class HttpState extends State {
    protected val httpPipelines: Pipelines = httpStage(context, commandPL, eventPL)

    override val commandPipeline: Command => Unit = new CommandPipeline
    override val eventPipeline: Event => Unit = httpPipelines.eventPipeline

    private[can] class CommandPipeline extends (Command => Unit) {
      protected val httpCPL: Command => Unit = httpPipelines.commandPipeline

      override def apply(command: Command): Unit = command match {
        case HttpSocket.UpgradeServer(response, handler) =>
          httpCPL(Http.MessageCommand(response))
          val socketStage =
            WebSocketFrontend(settings, handler) >>
            FrameComposing(settings) >>
            FrameParsing(settings) >>
            FrameRendering.unmasked(settings)
          val socketPipelines = socketStage(context, commandPL, eventPL)
          val state = socketState(socketPipelines)
          become(state)
          context.log.debug("WebSocket opened")
          state.eventPipeline(HttpSocket.Upgraded)

        case _ => httpCPL(command)
      }
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
