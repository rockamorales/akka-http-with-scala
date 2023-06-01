package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import part2_lowlevelserver.HttpsContext

object HigLevelIntro extends App {

  implicit val system = ActorSystem("HigLevelIntro")
  import system.dispatcher

  // Directives
  import akka.http.scaladsl.server.Directives._
  val simpleRoute: Route =
    path("home") { // DIRECTIVE
      complete(StatusCodes.OK) // DIRECTIVE
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

//  Http().newServerAt("localhost", 8080).bindFlow(pathGetRoute)

  // chaining directives with ~
  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
      path("home") {
        complete(
          HttpEntity(
            """
              | <html>
              |   <body>
              |     Hello from the high level Akka HTTP!
              |   </body>
              | </html>
            """.stripMargin
          )
        )
      } //Routing tree


  Http().newServerAt("localhost", 8443).enableHttps(HttpsContext.httpsConnectionContext).bindFlow(chainedRoute)

}
