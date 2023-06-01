package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}

object HandlingExceptions extends App {
  implicit val system = ActorSystem("HandlingExceptions")

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
//    case e: RuntimeException =>
//      complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val simpleRoute = {
    Route.seal {
      path("api" / "people") {
        get {
          throw new RuntimeException("Getting all the people took too long")
        } ~
          post {
            parameter(Symbol("id")) { id =>
              if (id.length > 2)
                throw new NoSuchElementException((s"Parameter $id cannot be found in the database, TABLE FLIP!"))

              complete(StatusCodes.OK)
            }
          }
      }
    }
  }

//  Http().newServerAt("localhost", 8080).bind(simpleRoute)
//  Http().bindAndHandle()

  // if an exception is not handled by any custom exception handler it will be handled by the default handler

  // Explicits exception handling with directives
  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val delicateHandleRoute = {
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          throw new RuntimeException("Getting all the people took too long")
        } ~
          handleExceptions(noSuchElementExceptionHandler){
            post {
              parameter(Symbol("id")) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException((s"Parameter $id cannot be found in the database, TABLE FLIP!"))
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }
  }

  Http().newServerAt("localhost", 8080).bind(delicateHandleRoute)

}
