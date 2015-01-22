package akka.util

object MultiByteArrayIterator {
  def apply(xs: ByteIterator): ByteIterator.MultiByteArrayIterator = xs match {
    case xs: ByteIterator.ByteArrayIterator => ByteIterator.MultiByteArrayIterator(xs :: Nil)
    case xs: ByteIterator.MultiByteArrayIterator => xs
  }
}
