package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{CreateGuitar, FindAllGuitars, GuitarCreated}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps


trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}
object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("LowLevelRest")
  import GuitarDB._

  import system.dispatcher

  /*
    - GET /api/guitar => ALL the guitars in the store
    - GET on /api/guitar?id=X => fetches the guitar associated with id X
    - POST /api/guitar => insert guitar into the store
   */
  // JSON -> marshalling -- is the process of converting data into a "wire" format that the client can understand
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "stock": 0
      |}
      |""".stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

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
  /*
    server code: Asynchronous handler
   */

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[String]
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse (
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  def findGuitars(inStock: Boolean): Future[HttpResponse] = {
    val guitarsFuture = (guitarDb ? FindInStock(inStock)).mapTo[List[Guitar]]
    guitarsFuture.map {
        guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
    }
  }

  def findAllGuitars(): Future[HttpResponse] = {
    val guitarsFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
    guitarsFuture.map { guitars =>
      HttpResponse(
        entity = HttpEntity(
          ContentTypes.`application/json`,
          guitars.toJson.prettyPrint
        )
      )
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /*
        query parameter handling code
       */
      val query = uri.query() // query object <=> Map[String, String]
      if (query.isEmpty) findAllGuitars()
      else getGuitar(query)

    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      /*
        query parameter handling code
       */
      val query = uri.query() // query object <=> Map[String, String]
      val inStock = query.get("inStock").map(_.toBoolean)
      inStock match {
        case None => findAllGuitars()
        case Some(inStock: Boolean) => findGuitars(inStock)
      }
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
      /*
        query parameter handling code
       */
      val query = uri.query() // query object <=> Map[String, String]
      val id = query.get("id").map(_.toInt)
      val qty = query.get("qty").map(_.toInt)
      id match {
        case None => Future(HttpResponse(StatusCodes.NotFound))
        case Some(id: Int) =>
          val stockAddedFuture: Future[Option[StockAdded]] = (guitarDb ? AddStock(id, qty.getOrElse(0))).mapTo[Option[StockAdded]]
          stockAddedFuture.map { result =>
            result match {
              case None => HttpResponse(
                  StatusCodes.NotFound
                )
              case Some(_) =>
                HttpResponse(
                  StatusCodes.OK
                )
            }
          }

      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }
  Http().newServerAt("localhost", 8080).bind(requestHandler)

  /**
   * Exercise: enhance the Guitar case class with a quantity field, by default 0
   * - GET /api/guitar/inventory?inStock=true/false which returns the guitars in stock as JSON
   * - POST to /api/guitar/inventory?id=X&quantity=Y which adds Y guitars to the stock for guitar with id X
   */
}
