package spray.can
package socket

import akka.util._
import java.nio.ByteOrder.{ BIG_ENDIAN => BE }
import scala.annotation._

final class FrameParser(val maxFrameSize: Long) {
  def this() = this(Int.MaxValue.toLong)

  import FrameParser._

  private var finRsvOp: Byte = _
  private var isMasked: Boolean = _
  private val maskingKey: Array[Byte] = new Array[Byte](4)
  private var payloadLength: Long = _

  private var input: ByteIterator.MultiByteArrayIterator = ByteIterator.MultiByteArrayIterator.empty

  private var cont: Cont = FinRsvOp

  private def fin: Boolean = (finRsvOp & 0x80) != 0

  private def opcode: Opcode = Opcode((finRsvOp & 0xF).toByte)

  @tailrec private def run(): Result = cont(this) match {
    case result: Partial.type => result
    case result: Complete     => cont = FinRsvOp; result
    case result: Failure      => cont = FinRsvOp; input = ByteIterator.MultiByteArrayIterator.empty; result
    case state                => cont = state; run()
  }

  def apply(newInput: ByteIterator): Result = {
    input = (input ++ MultiByteArrayIterator(newInput)).asInstanceOf[ByteIterator.MultiByteArrayIterator]
    run()
  }

  def apply(): Result = run()
}

object FrameParser {
  private[FrameParser] sealed abstract class Cont {
    private[FrameParser] def apply(state: FrameParser): Cont
  }

  private object FinRsvOp extends Cont {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < 1) return Partial

      finRsvOp = input.getByte
      val opcode = state.opcode
      if (!opcode.isValid) InvalidOpcode(opcode)
      else if (opcode.isReserved) ReservedOpcode(opcode)
      else if (opcode.isControl && !fin) FragmentedControlFrame
      else MaskPayloadLength
    }
  }

  private abstract class PayloadLength extends Cont {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._

      if (payloadLength > maxFrameSize) OversizedDataFrame(payloadLength, maxFrameSize)
      else if (opcode.isControl && payloadLength > 125) OversizedControlFrame
      else if (isMasked) MaskingKey
      else Payload
    }
  }

  private object MaskPayloadLength extends PayloadLength {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < 1) return Partial

      val b1 = input.getByte
      isMasked = (b1 & 0x80) != 0

      (b1 & 0x7F) match {
        case 126 => ShortPayloadLength
        case 127 => LongPayloadLength
        case length =>
          payloadLength = length
          super.apply(state)
      }
    }
  }

  private object ShortPayloadLength extends PayloadLength {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < 2) return Partial

      payloadLength = input.getShort(BE) & 0xFFFF
      super.apply(state)
    }
  }

  private object LongPayloadLength extends PayloadLength {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < 4) return Partial

      payloadLength = input.getLong(BE)
      super.apply(state)
    }
  }

  private object MaskingKey extends Cont {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < 4) return Partial

      input.getBytes(maskingKey)
      Payload
    }
  }

  private object Payload extends Cont {
    private[FrameParser] override def apply(state: FrameParser): Cont = {
      import state._
      if (input.len < payloadLength) return Partial

      val data = input.clone().take(payloadLength.toInt)
      input.drop(payloadLength.toInt)

      val payload = if (isMasked) Frame.mask(data, maskingKey) else data.toByteString
      new Complete(new Frame(finRsvOp, payload))
    }
  }

  sealed trait Result

  case object Partial extends Cont with Result {
    private[FrameParser] override def apply(state: FrameParser): Cont = this
  }

  final case class Complete(frame: Frame) extends Cont with Result {
    private[FrameParser] override def apply(state: FrameParser): Cont = this
  }

  final case class Failure(statusCode: StatusCode, reason: String) extends Cont with Result {
    private[FrameParser] override def apply(state: FrameParser): Cont = this
  }

  private def InvalidOpcode(opcode: Opcode): Cont =
    new Failure(StatusCode.ProtocolError, f"Invalid opcode: 0x${opcode.code}%X")

  private def ReservedOpcode(opcode: Opcode): Cont =
    new Failure(StatusCode.ProtocolError, f"Reserved opcode: 0x${opcode.code}%X")

  private def FragmentedControlFrame: Cont =
    new Failure(StatusCode.ProtocolError, "Control frame fin bit unset")

  private def OversizedControlFrame: Cont =
    new Failure(StatusCode.ProtocolError, "Control frame payload length exceeded 125 bytes")

  private def OversizedDataFrame(payloadLength: Long, maxPayloadLength: Long): Cont =
    new Failure(StatusCode.MessageTooBig, s"$payloadLength byte payload exceeded $maxPayloadLength byte limit")
}
