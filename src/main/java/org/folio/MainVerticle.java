package org.folio;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.rxjava.core.Vertx;

/**
 * Hello world!
 *
 */
public class MainVerticle extends AbstractVerticle {

  private final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    super.start();

    HttpServer server = vertx.createHttpServer();
    final Router router = Router.router(vertx);

    router.route().path("/login-saml").handler(routingContext -> {

      // This handler will be called for every request
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain");

      // Write to the response and end it
      response.end("Hello World from Vert.x-Web!");
    });

    server.requestHandler(router::accept).listen(8080);

    startFuture.complete();

  }
}
