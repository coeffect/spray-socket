package spray.can
package socket

import akka.io._
import akka.util._
import scala.util._
import spray.io._

private[can] object FrameRendering {
  def masked(settings: WebSocketSettings): PipelineStage = new MaskedFrameRendering(settings)

  def unmasked(settings: WebSocketSettings): PipelineStage = new UnmaskedFrameRendering(settings)
}

private[can] class MaskedFrameRendering(protected val settings: WebSocketSettings) extends PipelineStage {
  override def apply(context: PipelineContext, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new MaskedFrameRenderingPipelines(context, commandPL, eventPL, settings)
}

private[can] class UnmaskedFrameRendering(protected val settings: WebSocketSettings) extends PipelineStage {
  override def apply(context: PipelineContext, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new UnmaskedFrameRenderingPipelines(context, commandPL, eventPL, settings)
}

private[can] abstract class FrameRenderingPipelines(
    protected[this] val context: PipelineContext,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val settings: WebSocketSettings)
  extends Pipelines with DynamicCommandPipeline {

  def render(frame: Frame): ByteString

  override def initialCommandPipeline: Command => Unit = new CommandPipeline

  def closingCommandPipeline: Command => Unit = new ClosingCommandPipeline

  override def eventPipeline: Event => Unit = eventPL

  private[can] class CommandPipeline extends (Command => Unit) {
    override def apply(command: Command): Unit = command match {
      case HttpSocket.FrameCommand(frame) =>
        commandPL(Tcp.Write(render(frame)))
        if (frame.opcode == Opcode.Close) {
          commandPL(Tcp.Close)
          commandPipeline.become(closingCommandPipeline)
        }

      case _ => commandPL(command)
    }
  }

  private[can] class ClosingCommandPipeline extends (Command => Unit) {
    // Filter all commands except ConnectionClosed
    override def apply(command: Command): Unit = command match {
      case Pipeline.Tell(_, _: Tcp.ConnectionClosed, _) => commandPL(command)
      case _ =>
    }
  }
}

private[can] class MaskedFrameRenderingPipelines(
    context: PipelineContext,
    commandPL: Command => Unit,
    eventPL: Event => Unit,
    settings: WebSocketSettings)
  extends FrameRenderingPipelines(context, commandPL, eventPL, settings) {

  override def render(frame: Frame): ByteString = {
    val maskingKey = new Array[Byte](4)
    Random.nextBytes(maskingKey)
    frame.toByteString(maskingKey)
  }
}

private[can] class UnmaskedFrameRenderingPipelines(
    context: PipelineContext,
    commandPL: Command => Unit,
    eventPL: Event => Unit,
    settings: WebSocketSettings)
  extends FrameRenderingPipelines(context, commandPL, eventPL, settings) {

  protected val maskingKey: Array[Byte] = Array.empty[Byte]

  override def render(frame: Frame): ByteString = frame.toByteString(maskingKey)
}
