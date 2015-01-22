package spray.routing

import akka.actor._
import akka.io._
import spray.can._
import spray.http._
import spray.http.StatusCodes._
import spray.routing.directives._
import spray.util._

object HttpSocketService extends HttpSocketService

trait HttpSocketService extends Directives with WebSocketDirectives {
  def runRoute(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler, ac: ActorContext,
                             rs: RoutingSettings, log: LoggingContext): Actor.Receive = {
    val sealedExceptionHandler = eh orElse ExceptionHandler.default
    val sealedRoute = sealRoute(route)(sealedExceptionHandler, rh)
    def runSealedRoute(ctx: RequestContext): Unit =
      try sealedRoute(ctx)
      catch {
        case scala.util.control.NonFatal(e) =>
          val errorRoute = sealedExceptionHandler(e)
          errorRoute(ctx)
      }

    {
      case request: HttpRequest =>
        val ctx = RequestContext(request, ac.sender, request.uri.path).withDefaultSender(ac.self)
        runSealedRoute(ctx)

      case ctx: RequestContext => runSealedRoute(ctx)

      case Tcp.Connected(_, _) =>
        // by default we register ourselves as the handler for a new connection
        ac.sender ! Tcp.Register(ac.self)

      case HttpSocket.Upgraded                => onConnectionUpgraded

      case x: Tcp.ConnectionClosed        => onConnectionClosed(x)

      case Timedout(request: HttpRequest) => runRoute(timeoutRoute)(eh, rh, ac, rs, log)(request)
    }
  }

  def onConnectionClosed(ev: Tcp.ConnectionClosed): Unit = ()

  def onConnectionUpgraded(): Unit = ()

  def sealRoute(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler): Route =
    (handleExceptions(eh) & handleRejections(sealRejectionHandler(rh)))(route)

  def sealRejectionHandler(rh: RejectionHandler): RejectionHandler =
    rh orElse RejectionHandler.Default orElse handleUnhandledRejections

  def handleUnhandledRejections: RejectionHandler.PF = {
    case x :: _ => sys.error("Unhandled rejection: " + x)
  }

  def timeoutRoute: Route = complete((InternalServerError, "The server was not able to produce a timely response to your request."))
}
