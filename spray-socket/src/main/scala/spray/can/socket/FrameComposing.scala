package spray.can
package socket

import scala.annotation._
import scala.collection._
import spray.io._

private[can] object FrameComposing {
  def apply(settings: WebSocketSettings): PipelineStage = new FrameComposing(settings)
}

private[can] class FrameComposing(protected val settings: WebSocketSettings) extends PipelineStage {
  override def apply(context: PipelineContext, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new FrameComposingPipelines(context, commandPL, eventPL, settings)
}

private[can] class FrameComposingPipelines(
    protected[this] val context: PipelineContext,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val settings: WebSocketSettings)
  extends Pipelines with DynamicEventPipeline {

  import HttpSocket._

  override def commandPipeline: Command => Unit = commandPL

  override def initialEventPipeline: Event => Unit = unfragmentedEventPipeline

  val unfragmentedEventPipeline: Event => Unit = new UnfragmentedEventPipeline

  def fragmentedEventPipeline(fragment: Frame): Event => Unit = new FragmentedEventPipeline(fragment)

  private[can] class UnfragmentedEventPipeline extends (Event => Unit) {
    override def apply(event: Event): Unit = event match {
      case FrameEvent(frame) =>
        val opcode = frame.opcode
        if (frame.rsv != 0) closeWithReason(StatusCode.ProtocolError, "Unnegotiated RSV bit")
        else if (opcode == Opcode.Continuation) closeWithReason(StatusCode.ProtocolError, "Continuation frame with no preceding fragment frames")
        else if (opcode.isData) {
          // An unfragmented message consists of a single frame with the FIN
          // bit set and an opcode other than 0.
          if (frame.fin) {
            if (frame.opcode == Opcode.Text && !ValidUTF8(frame.payload)) closeWithReason(StatusCode.InvalidPayload, "Invalid UTF-8 in text frame")
            else eventPL(event)
          }
          // A fragmented message consists of a single frame with the FIN bit
          // clear and an opcode other than 0.
          else eventPipeline.become(fragmentedEventPipeline(frame))
        }
        else if (!frame.fin) { // opcode.isControl
          // Control frames MUST NOT be fragmented.
          closeWithReason(StatusCode.ProtocolError, "Fragmented control frame")
        }
        else if (opcode == Opcode.Close) {
          val Frame.Close(statusCode, reason) = frame
          closeWithReason(statusCode, reason)
        }
        else eventPL(event)

      case _ => eventPL(event)
    }

    protected def closeWithReason(statusCode: StatusCode, reason: String): Unit =
      commandPL(FrameCommand(Frame.Close(statusCode, reason)))
  }

  private[can] class FragmentedEventPipeline(
      protected val fragments: mutable.ListBuffer[Frame],
      protected var messageSize: Long)
    extends (Event => Unit) {

    def this(fragment: Frame) = this(mutable.ListBuffer.empty[Frame] += fragment, fragment.payload.length.toLong)

    protected def unfragmented: Frame = {
      @tailrec def concat(frame: Frame, frames: List[Frame]): Frame =
        if (frames.isEmpty) frame else concat(frame :+ frames.head, frames.tail)
      val frames = fragments.result()
      concat(frames.head, frames.tail).withFin
    }

    override def apply(event: Event): Unit = event match {
      case FrameEvent(frame) =>
        val opcode = frame.opcode
        if (opcode == Opcode.Continuation) {
          fragments.append(frame)
          messageSize += frame.payload.length.toLong
          if (messageSize > settings.maxMessageSize.toLong)
            closeWithReason(StatusCode.MessageTooBig, s"Message exceeded ${settings.maxMessageSize} byte limit")
          else if (frame.fin) {
            val message = unfragmented
            if (message.opcode == Opcode.Text && !ValidUTF8(message.payload)) closeWithReason(StatusCode.InvalidPayload, "Invalid UTF-8 in text frame")
            else {
              eventPL(FrameEvent(message))
              eventPipeline.become(unfragmentedEventPipeline)
            }
          }
        }
        else if (opcode.isData) {
          // The fragments of one message MUST NOT be interleaved between the fragments of another message.
          closeWithReason(StatusCode.ProtocolError, "Data frame interleaved with fragmented message")
        }
        else if (!frame.fin) { // opcode.isControl
          // Control frames themselves MUST NOT be fragmented.
          closeWithReason(StatusCode.ProtocolError, "Fragmented control frame")
        }
        else if (opcode == Opcode.Close) {
          val Frame.Close(statusCode, reason) = frame
          closeWithReason(statusCode, reason)
        }
        else eventPL(event)

      case _ => eventPL(event)
    }

    protected def closeWithReason(statusCode: StatusCode, reason: String): Unit = {
      commandPL(FrameCommand(Frame.Close(statusCode, reason)))
      eventPipeline.become(unfragmentedEventPipeline)
    }
  }
}
