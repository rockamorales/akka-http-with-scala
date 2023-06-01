package part3_highlevelserver

import akka.http.javadsl.server.MethodRejection
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

class RouteDSLSpec extends AnyWordSpec
  with Matchers
  with ScalatestRouteTest
  with BookJsonProtocol {

  import RouteDSLSpec._

  "A digital library backend" should {
    "return all th books in the library" in {
      //send an HTTP request through an endpoint that you want to test
      // inspect the response
      Get("/api/book") ~> libraryRoute ~> check {
        // assertion
        status mustBe StatusCodes.OK
        entityAs[List[Book]] mustBe books
      }
    }
    "return a book by htting the query parameter endpoint" in {
      Get("/api/book?id=2") ~> libraryRoute ~> check {
        status mustBe StatusCodes.OK
        responseAs[Option[Book]] mustBe Some(Book(2, "JRR Tolkien", "The Lord of the Rings"))
      }
    }
    "return a book by calling the endpoint with the id in the path" in {
      Get("/api/book/2") ~> libraryRoute ~> check {
        response.status mustBe StatusCodes.OK
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book mustBe Some(Book(2, "JRR Tolkien", "The Lord of the Rings"))
      }
    }
    "insert a book into the 'database'" in {
      val newBook = Book(5, "Steven Pressfield", "The War of Art")
      Post("/api/book", newBook) ~> libraryRoute ~> check {
        status mustBe StatusCodes.OK
        assert(books.contains(newBook))
        books must contain(newBook)
      }
    }

    "not accept other methods than POST and GET" in {
      Delete("/api/book") ~> libraryRoute ~> check {
        rejections must not be empty
        val methodRejections = rejections.collect {
          case rejection: MethodRejection => rejection
        }

        methodRejections.length mustBe 2
      }
    }

    "must return all books for a given actor" in {
      Get("/api/book/author/JRR%20Tolkien") ~> libraryRoute ~> check {
        status mustBe StatusCodes.OK
        responseAs[List[Book]] mustBe books.filter(_.author == "JRR Tolkien")
      }
    }
  }

}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport{

  // code under test
  var books = List(
    Book(1, "Harper Lee", "To Kill a Mockingbird"),
    Book(2, "JRR Tolkien", "The Lord of the Rings"),
    Book(3, "GRR Marting", "A Son of Ice and Fire"),
    Book(4, "Tony Robins", "Awaken the Giant Within")
  )

  /*
    GET /api/book - returns all the books in the library
    GET /api/book/X - returns return a single book with id
    GET /api/book?id=X - same
    POST /api/book - add a new book to the library
    GET /api/book/author/X - returns all the books from the actor X
   */

  val libraryRoute =
    pathPrefix("api" / "book") {
      (path("author" / Segment) & get){ author =>
        complete(books.filter(_.author == author))
      } ~
      get {
        (path(IntNumber) | parameter(Symbol("id").as[Int])) {id =>
          complete(books.find(_.id == id))
        } ~
          pathEndOrSingleSlash {
            complete(books)
          }
      } ~
        post {
          entity(as[Book]) { book =>
            books = books :+ book
            complete(StatusCodes.OK)
          } ~
            complete(StatusCodes.BadRequest)
        }
    }


}