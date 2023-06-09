# Akka HTTP basics

### Akka HTTP is
- a suite of libraries
- focused on HTTP integration of an application
- designed for both servers and clients
- based on Akka actors and Akka Streams

### Akka HTTP is NOT:
- a framework

### Akka HTTP strengths
- streams-based, with backpressure for free
- multiple API levels for control vs ease of use

### Core concepts
- HttpRequest, HttpResponse
- HttpEntity
- marshalling

## Akka HTTP server

Goal: receive HTTP requests, send HTTP response
- synchronously vis function HttpRequest => HttpResponse
- async via a function HttpReqyest => Future[HttpResponse]
- async via streams, with a Flow[HttpRequest, HttpResponse, _]

(all of the above turn into flows sooner or later)

### Under the hood:
- the server receives HttpRequests (transparently)
- the requests go through the flow we write
- the resulting response are served back (transparently)

# Akka-HTTP High Level API
## What a route Is
```scala
val myRoute: Route =
  path("home") {
    complete(StatusCodes.Ok)
  }

type Route = requestContext => Future[RouteResult]
```

A RequestContext contains
- the HttpRequest being handled
- the actor system
- the actor materializer
- the logging adapter
- routing settings
- etc

This is teh data structure handled by a route

you'll almost never need to build a RequestContext by yourself

### Directives create Routes; composing routes creates a routing tree
- filtering and nesting
- chaining with ~
- extracting data

```scala
path("home") {
  get {
    complete(StatusCodes.OK)
  } ~
  post {
    complete(StatusCode.Forbidden)
  }
}

//  Equivalent to 
//        |
//        |
//        |
//        V
Route 1, filtering on path "/home" {
    Route 2, filtering on the GET verb {
        Route 3, complete with 200 OK
    }
    Route 4, filtering on the POST verb {
        Route 5, complete with 403 Forbidden
    }
}
```

### What a Route can do with a RequestContext:
- complete it synchronously with a response
- complete it asynchronously with a Future(response)
- handle it asynchronously by returning  Source (advanced)
- reject it and pass it on the next Route
- fail it

# Rejections

#### if a request doesn't match a filter directive, it's rejected
- reject = pass the request to another branch in the routing tree
- a rejection is NOT a failure

### Rejections are aggregated

```scala
get { // <----- if request is not a GET, add rejection
  // Route 1
} ~ 
  post { <---- if request is not a POST, add rejection
    // Route 2
  } ~
  parameter(Symbol("myParam")) { myParam => 
    // Route 3 <------ if request has a query param, clear the rejections list 
    //                  (other rejections might be added within)
  }
```

#### We can choose how to handle the rejection list

# JSON Web tokens
- authorization
- exchange of data

### Not Akka HTTP specific, but often used in web apps/microservices

## Principles
- you authenticate to the server (username + pass, OAuth, your blood etc)
- server sends you back a string aka token
- you then use that string for secure endpoints
  - special HTTP header Authorization: (token)
  - the endpoint will check the token for permissions
  - your call is allowed/rejected

### Result: Authorization
- not authentication: you receive the token

## JWT
Structure 
### Part 1: header

```json
{
    "typ": "JWT"
    "alg": "HS256"
}
```

is base64 encode

header JSON: 
- type = JWT
- hashing algorithm = HMAC SHA256

Part 2: payload (claims)
```json
{
  "iss": "rockthejvm.com",
  "exp": 1300819380,
  "name": "Daniel Ciocirlan",
  "admin": true
}
// Base64 encoded
```

Registered claims (standard)
- issuer
- expiration date

Public claims (custom)
- name
- admin
- any kind of permissions

### Part 3: signature
- take encoded header + "." + encoded claims
- sign with the algorithm in the header and a secret key
- encode base64

