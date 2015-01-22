package spray.can
package socket

final case class Opcode(val code: Byte) extends AnyVal {
  def isValid: Boolean    = code >= 0x0 && code <= 0xF
  def isData: Boolean     = code <= 0x7
  def isControl: Boolean  = code >= 0x8
  def isReserved: Boolean = code >= 0x3 && code <= 0x7 || code >= 0xB && code <= 0xF
}

object Opcode {
  val Continuation = Opcode(0x0)
  val Text         = Opcode(0x1)
  val Binary       = Opcode(0x2)

  val Close        = Opcode(0x8)
  val Ping         = Opcode(0x9)
  val Pong         = Opcode(0xA)
}
