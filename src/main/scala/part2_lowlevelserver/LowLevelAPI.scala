package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object LowLevelAPI extends App {
  implicit val system = ActorSystem("LowLevelAPI")

  import system.dispatcher

  val serverSource = Http().newServerAt("localhost", 8000).connectionSource()
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from: " + {connection.remoteAddress})
  }

//  val serverBindingFuture = serverSource.to(connectionSink).run()

//  serverBindingFuture.onComplete {
//    case Success(binding) =>
//      println("Server binding successful")
//      binding.terminate(2 seconds)
//    case Failure(exception) => println(s"Server biding failed: $exception")
//  }

  /**
   * Method 1: synchronously server HTTP responses
   */

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html
              |""".stripMargin
          )
        )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   OOPS! the resource can't be found
              | </body>
              |</html
              |
          """.stripMargin
          )
        )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

//  Http().newServerAt("localhost", 8080).connectionSource().runWith(httpSyncConnectionHandler)

  //shorthand version ?
//  Http().newServerAt("localhost", 8080).bindSync(requestHandler)

  // Method #2: serve back HTTP response ASYNCHRONOUSLY
  val requestHandlerFuture: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2...
      Future {
        HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html
              |""".stripMargin
          )
        )
      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   OOPS! the resource can't be found
              | </body>
              |</html
              |
        """.stripMargin
          )
        )
      }
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(requestHandlerFuture)
  }

  // streams-based "manual" version
//  Http().newServerAt("localhost", 8082).connectionSource().runWith(httpAsyncConnectionHandler)

  //shorthand version
//  Http().newServerAt("localhost", 8082).bind(requestHandlerFuture)


  /*
   Method 3: async via Akka streams
   */
  val streamsBasedRequestHandler: Flow[HttpRequest,  HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2...
        HttpResponse(
          StatusCodes.OK, // HTTP 200
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html
              |""".stripMargin
          )
        )
    case request: HttpRequest =>
      request.discardEntityBytes()
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   OOPS! the resource can't be found
              | </body>
              |</html
              |
      """.stripMargin
          )
        )
  }

  // "Manual" versiom
//  Http().newServerAt("localhost", 8083).connectionSource().runForeach({ connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  })

  // shorthand version

  Http().newServerAt("localhost", 8083).bindFlow(streamsBasedRequestHandler)

  /**
   * Exercise: create your own HTTP server running on localhost on 8388, which replies
   *  - with a welcome message on the "front door" localhost:8388
   *  - with a proper HTML on localhost:8388/about
   *  - with a 404 message otherwise
   *
   */

  val myStreamFlowBasedServer: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/") , _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2...
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   "Front door"
            | </body>
            |</html
            |""".stripMargin
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) => // method, URI, HTTP headers, content and the protocol (HTTP1.1/HTTP2...
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   "About me"
            | </body>
            |</html
            |""".stripMargin
        )
      )
    // path /search redirects to some other part of our website/webapp/microservice
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS! the resource can't be found
            | </body>
            |</html
            |
    """.stripMargin
        )
      )
  }

  val bindingFuture = Http().newServerAt("localhost", 8388).bindFlow(myStreamFlowBasedServer)

  //shutdown the server:
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
