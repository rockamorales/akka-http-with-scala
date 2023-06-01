package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import akka.pattern.ask
import part2_lowlevelserver.GuitarDB.FindGuitar
import part2_lowlevelserver.GuitarStoreJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
// step 1: to support JSON
import spray.json._

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("HighLevelExample")
  import system.dispatcher
  import part2_lowlevelserver._
  import part2_lowlevelserver.GuitarDB._

  /*
      GET /api/guitar fetches ALL the guitars in the store
      GET /api/guitar?id=X fetches the guitar with id X
      GET /api/guitar/X fetches guitar with id X
      GET /api/guitar/inventory?inStock=true

   */
  /*
      setup
   */

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }


  implicit val timeout = Timeout(2 seconds)
  val guitarServerRoute =
    path("api" / "guitar") {
      parameter(Symbol("id").as[Int]) { (guitarId: Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarsFuture.map { guitars =>
          HttpEntity (
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter(Symbol("inStock").as[Boolean]) { inStock =>
            val guitarFuture: Future[List[Guitar]] = (guitarDb ? FindInStock(inStock)).mapTo[List[Guitar]]
            val entityFuture = guitarFuture.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            }
            complete(entityFuture)
          }
        }
      }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter(Symbol("inStock").as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FindInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      (path(IntNumber) | parameter(Symbol("id").as[Int])) { guitarId =>
        complete(
          (guitarDb ? FindGuitar(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        )
      } ~
      pathEndOrSingleSlash {
        complete(
          (guitarDb ? FindAllGuitars)
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity))
      }
    }

  Http().newServerAt("localhost", 8080).bindFlow(simplifiedGuitarServerRoute)

}
