package spray.can
package socket

import akka.actor._
import akka.spray._
import spray.http._
import spray.io._

private[can] object WebSocketFrontend {
  def apply(settings: WebSocketSettings, handler: ActorRef): PipelineStage =
    new WebSocketFrontend(settings, handler)
}

private[can] class WebSocketFrontend(
    protected val settings: WebSocketSettings,
    protected val handler: ActorRef)
  extends PipelineStage {

  override def apply(context: PipelineContext, commandPL: Command => Unit, eventPL: Event => Unit): Pipelines =
    new WebSocketFrontendPipelines(context, commandPL, eventPL, settings, handler)
}

private[can] class WebSocketFrontendPipelines(
    protected[this] val context: PipelineContext,
    protected val commandPL: Command => Unit,
    protected val eventPL: Event => Unit,
    protected val settings: WebSocketSettings,
    protected val handler: ActorRef)
  extends Pipelines {

  protected val socketReceiver = new WebSocketFrontendReceiver(context.actorContext.self)

  override def commandPipeline: Command => Unit = commandPL
  override val eventPipeline: Event => Unit = new EventPipeline

  private[can] class EventPipeline extends (Event => Unit) {
    override def apply(event: Event): Unit = event match {
      case HttpSocket.FrameEvent(frame) =>
        val opcode = frame.opcode
        if (opcode == Opcode.Close) commandPL(HttpSocket.FrameCommand(frame))
        else if (opcode == Opcode.Ping) commandPL(HttpSocket.FrameCommand(frame.withOpcode(Opcode.Pong)))
        else commandPL(Pipeline.Tell(handler, frame, socketReceiver))

      case HttpSocket.Upgraded =>
        commandPL(Pipeline.Tell(handler, HttpSocket.Upgraded, socketReceiver))

      case Http.MessageEvent(response: HttpResponse) =>
        commandPL(Pipeline.Tell(handler, response, socketReceiver))

      case _ =>
        commandPL(Pipeline.Tell(handler, event, socketReceiver))
        eventPL(event)
    }
  }
}

private[can] class WebSocketFrontendReceiver(replyReceiver: ActorRef) extends UnregisteredActorRef(replyReceiver) {
  override def handle(reply: Any)(implicit replySender: ActorRef): Unit = reply match {
    case frame: Frame => replyReceiver.tell(HttpSocket.FrameCommand(frame), replySender)
    case _ => replyReceiver.tell(reply, replySender)
  }
}
