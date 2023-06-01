package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging}

case class Guitar(make: String, model: String, stock: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class AddStock(id: Int, quantity: Int)
  case class StockAdded()
  case class FindInStock(inStock: Boolean)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
}
class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._
  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"Searching guitar by id: $id")
      sender() ! guitars.get(id)
    case FindInStock(inStock) =>
      log.info(s"Searching inStock: $inStock")
      if (inStock)
        sender() ! guitars.values.filter(guitar => guitar.stock > 0)
      else
        sender() ! guitars.values.filter(guitar => guitar.stock == 0)

    case AddStock(id, qty) =>
      log.info(s"Adding $qty for guitar id: $id")
      val guitarOption = guitars.get(id)
      if (guitarOption.isEmpty) sender() ! None
      else {
        val guitar = guitarOption.get
        guitars = guitars + (id -> guitar.copy(stock=guitar.stock + qty))
        sender() ! Option(StockAdded())
      }
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
  }
}

