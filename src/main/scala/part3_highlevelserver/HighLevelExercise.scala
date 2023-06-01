package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps


object HighLevelExercise extends App {
  implicit val system = ActorSystem("HighLevelExercise")
  import system.dispatcher

  /**
   * Exericise
   * - GET /api/people: retrieve ALL the people you have registered
   * - GET /api/people/pin: retrieve the person with that PIN, return as JSON
   * - GET /api/people?pin=X (same)
   * - (harder) POST /api/people with a JSON payload denoting a Person, add that person to your database
   *   - extract the HTTP request's payload (entity)
   *   - process the entity's data
   *
   */

  case class Person(pin: Int, name: String)
  trait RegistrationSystemJsonProtocol extends DefaultJsonProtocol {
    implicit val personFormat = jsonFormat2(Person)
  }

  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )

  object ServerRouter extends RegistrationSystemJsonProtocol {
    def toEntity(payload: String) =
      Future(HttpEntity(
        ContentTypes.`application/json`,
        payload
      ))

    val registrationServerRoute =
      pathPrefix("api" / "people") {
        get {
          (path(IntNumber) | parameter(Symbol("pin").as[Int])) { pin =>
            // logic to retrieve person by pin
            val person = people
              .find(_.pin == pin)
              .map(_.toJson.prettyPrint)
              .map(toEntity)
            person match {
              case None => complete(StatusCodes.NotFound)
              case Some(entity) => complete(entity)
            }
          } ~
            pathEndOrSingleSlash {
              // logic to retrieve all persons
              complete(toEntity(people.toJson.prettyPrint))
            }
        } ~
          (post & pathEndOrSingleSlash & extractStrictEntity(3 seconds) & extractLog) { (body, log) =>
              val newPerson = body.data.utf8String.parseJson.convertTo[Person]
              val person = people.find(_.pin == newPerson.pin)
              log.info(s"new person: $newPerson")
              person match {
                case None =>
                  people = people :+ newPerson
                  complete(StatusCodes.Created)
                case Some(_) => complete(StatusCodes.NotModified)
              }
          }
      }
  }

  Http().newServerAt("localhost", 8080).bindFlow(ServerRouter.registrationServerRoute)


}
