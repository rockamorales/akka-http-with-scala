package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContext {
  // Step 1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")

  // alternative: new FileInputStream(new File("src/main/resources/keystore.pkcs12"))
  val password = "akka-https".toCharArray // fetch password from a secure place!
  ks.load(keyStoreFile, password)

  // Step 2: Initialize a key manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI = public key infrastructure

  keyManagerFactory.init(ks, password)

  // Step 3: initialize a trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // Step 4: Initialize an SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // Step 5: return the Https connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.httpsServer(sslContext)
}

object LowLevelHttps extends App {
  implicit val system = ActorSystem("LowLevelHttps")

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

  val httpsBinding = Http()
    .newServerAt("localhost", 8443)
    .enableHttps(HttpsContext.httpsConnectionContext)
    .bindSync(requestHandler)

}
