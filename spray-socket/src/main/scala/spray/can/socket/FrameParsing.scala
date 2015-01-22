package spray.can
package socket

import akka.io._
import scala.annotation._
import spray.io._

private[can] object FrameParsing {
  def apply(settings: WebSocketSettings): PipelineStage = new FrameParsing(settings)
}

private[can] class FrameParsing(protected val settings: WebSocketSettings) extends PipelineStage {
  override def apply(context: PipelineContext, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new FrameParsingPipelines(context, commandPL, eventPL, settings)
}

private[can] class FrameParsingPipelines(
    protected[this] val context: PipelineContext,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val settings: WebSocketSettings)
  extends Pipelines {

  import HttpSocket._

  override def commandPipeline: Command => Unit = commandPL

  override val eventPipeline: Event => Unit = new EventPipeline

  protected def closeWithReason(statusCode: StatusCode, reason: String): Unit =
    commandPL(FrameCommand(Frame.Close(statusCode, reason)))

  private[can] class EventPipeline extends (Event => Unit) {
    protected val parse: FrameParser = new FrameParser(settings.maxFrameSize.toLong)

    override def apply(event: Event): Unit = event match {
      case Tcp.Received(data) => process(parse(data.iterator))
      case _                  => eventPL(event)
    }

    @tailrec protected final def process(result: FrameParser.Result): Unit = result match {
      case FrameParser.Partial                     => ()
      case FrameParser.Complete(frame)             => eventPL(FrameEvent(frame)); process(parse())
      case FrameParser.Failure(statusCode, reason) => closeWithReason(statusCode, reason)
    }
  }
}
