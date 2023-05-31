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
