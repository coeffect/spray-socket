package spray.can
package socket

import spray.http._

case class HandshakeResponse(response: HttpResponse, protocol: String)
