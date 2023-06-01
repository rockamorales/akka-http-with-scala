package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
// step 1
import spray.json._

import scala.concurrent.duration._
import scala.language.postfixOps

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}
class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._
  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all the players")
      sender() ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"Getting player with nickname $nickname")
      sender() ! players.get(nickname)
    case GetPlayersByClass(characterClass) =>
      log.info(s"Getting all players with the character class $characterClass")
      sender() ! players.values.filter(_.characterClass == characterClass)
    case AddPlayer(player) =>
      log.info(s"Trying to add player $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(s"Trying to mreove $player")
      players = players - player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}
object MarshallingJSON extends App
  with PlayerJsonProtocol
  with SprayJsonSupport {
  implicit val system = ActorSystem("MarshallingJSON")
  import system.dispatcher
  import GameAreaMap._

  val rtjvmGameMap = system.actorOf(Props[GameAreaMap], "rockTheJVMGameAreaMap")
  val playersList = List(
    Player("martin_killz_u", "Warrior",  70),
    Player("rolandbraveheart007", "Elf",  67),
    Player("daniel_rock03", "Wizard",  30),
  )

  playersList.foreach{ player =>
    rtjvmGameMap ! AddPlayer(player)
  }

  /*
    - GET /api/player, returns all the players in the map, as JSON
    - GET /api/player/(nickname), returns the player with the given nickname (as JSON)
    - GET /api/player?nickname=X, same as above
    - GET /api/player/class/(charClass), returns all the players with the given character class
    - POST /api/player with JSON payload, adds the player to the map
    - (exercise) DELETE /api/player with JSON payload, removes the player from the map
   */

  implicit val timeout = Timeout(2 seconds)

  val rtjvmGameRouteSkel =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          val playersByClassFuture = (rtjvmGameMap ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          complete(playersByClassFuture)
        } ~
        (path(Segment) | parameter(Symbol("nickname"))) { nickname =>
          val playerOptionFuture = (rtjvmGameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
          complete(playerOptionFuture)
        } ~
        pathEndOrSingleSlash {
          complete((rtjvmGameMap ? GetAllPlayers).mapTo[List[Player]])
        }
      } ~
      post {
        entity(as[Player]) { player =>
          complete((rtjvmGameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
        }
      } ~
      delete {
        entity(as[Player]) { player =>
          complete((rtjvmGameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
        }
      }
    }

  Http().newServerAt("localhost", 8080).bindFlow(rtjvmGameRouteSkel)
}
