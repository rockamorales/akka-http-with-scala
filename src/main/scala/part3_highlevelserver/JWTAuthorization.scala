package part3_highlevelserver

import akka.actor.ActorSystem

object JWTAuthorization extends App {
  implicit val system = ActorSystem("JWTAuthorization")

  import system.dispatcher


}
