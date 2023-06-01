package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler, Route}
import akka.http.scaladsl.settings.ServerSettings

object HandlingRejections extends App {
  implicit val system = ActorSystem("HandlingRejections")
  import system.dispatcher

  // rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }
  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHanlders =
    handleRejections(badRequestHandler) {
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) {
              parameter(Symbol("myParam")) { - =>
                complete(StatusCodes.OK)
              }
            }
          }
      }

    }

//  Http().newServerAt("localhost", 8080).bindFlow(simpleRouteWithHanlders)

  // a different to create a rejection handler

  // list (method rejection, query param rejection
  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MethodRejection =>
        println(s"I got a method rejection ${m}")
        complete("Rejected method")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"I got a query param rejection: ${m}")
        complete("Rejected query param!")
    }
    .result()


  // new version requires Route.seal in order to take the custom rejection handler
  val simpleRoute =
    Route.seal {
//      handleRejections(forbiddenHandler) {

        path("api" / "myEndpoint") {
          get {
            complete(StatusCodes.OK)
          } ~
            parameter(Symbol("id")) { _ =>
              complete(StatusCodes.OK)
            }
        }
//      }
    }

  Http().newServerAt("localhost", 8080).bind(simpleRoute)
}
