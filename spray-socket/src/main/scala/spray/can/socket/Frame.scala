package spray.can
package socket

import akka.util._
import java.nio.ByteOrder.{ BIG_ENDIAN => BE }

class Frame(val finRsvOp: Byte, val payload: ByteString) {
  final def fin: Boolean  = (finRsvOp & 0x80) != 0
  final def rsv1: Boolean = (finRsvOp & 0x40) != 0
  final def rsv2: Boolean = (finRsvOp & 0x20) != 0
  final def rsv3: Boolean = (finRsvOp & 0x10) != 0

  final def rsv: Byte = ((finRsvOp >>> 4) & 0x7).toByte

  final def opcode: Opcode = Opcode((finRsvOp & 0xF).toByte)

  final def isData: Boolean = opcode.isData
  final def isControl: Boolean = opcode.isControl

  def withFin: Frame =
    new Frame((finRsvOp | 0x80).toByte, payload)

  def withOpcode(opcode: Opcode): Frame =
    new Frame(((finRsvOp & ~0xF) | (opcode.code & 0xF)).toByte, payload)

  def :+ (that: Frame): Frame =
    new Frame(finRsvOp, payload ++ that.payload)

  def toByteString: ByteString = toByteString(Array.empty[Byte])

  def toByteString(maskingKey: Array[Byte]): ByteString = {
    val data = ByteString.newBuilder

    val b0 = finRsvOp
    data.putByte(b0)

    val masked = if (maskingKey.length > 0) 1 else 0
    val payloadLength = payload.length
    val payloadLen =
      if (payloadLength <= 125) payloadLength
      else if (payloadLength <= 65535) 126
      else 127
    val b1 = (masked << 7) | payloadLen
    data.putByte(b1.toByte)

    (b1 & 0x7F) match {
      case 126 => data.putShort(payloadLength)(BE)
      case 127 => data.putLong(payloadLength)(BE)
      case _   =>
    }

    if (masked == 1) {
      data.putBytes(maskingKey)
      data.append(Frame.mask(payload.iterator, maskingKey))
    }
    else data.append(payload)

    data.result()
  }

  override def toString: String = f"Frame(0x$finRsvOp%2x, [${payload.length} bytes])"
}

object Frame {
  private[can] val UTF8 = java.nio.charset.Charset.forName("UTF-8")

  private[can] def mask(data: ByteIterator, maskingKey: Array[Byte]): ByteString = {
    var i = 0
    val n = data.len
    val payload = new Array[Byte](n)
    while (i < n) {
      payload(i) = (data.getByte ^ (maskingKey(i % 4))).toByte
      i += 1
    }
    ByteString(payload)
  }

  object Text {
    def apply(payload: ByteString): Frame = new Frame(0x81.toByte, payload)

    def apply(content: String): Frame = new Frame(0x81.toByte, ByteString(content.getBytes(UTF8)))

    def apply(frame: Frame): Option[String] =
      if (frame.opcode == Opcode.Text) Some(frame.payload.utf8String) else None
  }

  object Binary {
    def apply(payload: ByteString): Frame = new Frame(0x82.toByte, payload)

    def unapply(frame: Frame): Option[ByteString] =
      if (frame.opcode == Opcode.Binary) Some(frame.payload) else None
  }

  object Close {
    def apply(statusCode: StatusCode, reason: String): Frame =
      new Frame(0x88.toByte, statusCode.toByteString ++ reason.getBytes(UTF8))

    def unapply(frame: Frame): Option[(StatusCode, String)] = {
      if (frame.opcode == Opcode.Close) Some(frame.payload.length match {
        case 0 => (StatusCode.NormalClose, "")
        case 1 => (StatusCode.ProtocolError, "Illegal close frame payload")
        case _ =>
          val (code, reason) = frame.payload.splitAt(2)
          val status = StatusCode(code.iterator.getShort(BE))
          if (status.isAllowed) {
            if (ValidUTF8(reason)) (status, reason.utf8String)
            else (StatusCode.ProtocolError, "Invalid UTF-8 in close frame")
          }
          else (StatusCode.ProtocolError, "Illegal close code: " + status.code)
      })
      else None
    }
  }
}
