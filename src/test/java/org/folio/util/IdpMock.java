package org.folio.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class IdpMock extends AbstractVerticle {

  public void start(Promise<Void> promise) {
    final int port = context.config().getInteger("http.port");

    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();

    router.route().handler(this::handleIdp);
    System.out.println("Running IdpMock on port " + port);
    server.requestHandler(router::handle).listen(port, result -> {
      if (result.failed()) {
        promise.fail(result.cause());
      } else {
        promise.complete();
      }
    });
  }

  private void handleIdp(RoutingContext context) {
    try {
      String idpMetadata = readMockData();
      context.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/xml")
        .end(idpMetadata);
    } catch (Exception e) {
      context.response()
        .setStatusCode(500)
        .end(e.getMessage());
    }
  }

  private String readMockData() throws IOException {
    Path path = Paths.get("src/test/resources/meta-idp.xml");
    return new String(Files.readAllBytes(path));
  }
}