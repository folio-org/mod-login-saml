package org.folio.util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class MockJson extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(MockJson.class);

  JsonArray mocks;
  String resource;

  public void setMockContent(String resource, Function<String,String> function) {
    try {
      this.resource = resource;
      String file = IOUtils.toString(MockJson.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8);
      JsonObject config = new JsonObject(function.apply(file));
      mocks = config.getJsonArray("mocks");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void setMockContent(String resource) {
    setMockContent(resource, s -> s);
  }

  private void handle(RoutingContext context) {
    HttpServerRequest request = context.request();
    HttpServerResponse response = context.response();
    String method = request.method().name();
    String uri = request.uri();
    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      if (method.equalsIgnoreCase(entry.getString("method", "get"))
        && uri.equals(entry.getString("url"))) {
        response.setStatusCode(entry.getInteger("status", 200));
        JsonArray headers = entry.getJsonArray("headers");
        if (headers != null) {
          for (int j = 0; j < headers.size(); j++) {
            JsonObject headObject = headers.getJsonObject(j);
            response.putHeader(headObject.getString("name"), headObject.getString("value"));
          }
        }
        JsonObject responseData = entry.getJsonObject("receivedData");
        if (responseData != null) {
          response.putHeader("Content-Type", "application/json");
          response.end(responseData.encodePrettily());
        } else {
          response.end();
        }
        return;
      }
    }
    log.info("Not found in mock={} method={} uri={}", resource, method, uri);
    response.setStatusCode(404);
    response.putHeader("Content-Type", "text/plain");
    response.end("Not found in mock");
  }

  public void start(Promise<Void> promise) {
    final int port = context.config().getInteger("http.port");

    log.info("Running Mock JSON on port {}", port);

    Router router = Router.router(vertx);
    router.routeWithRegex("/.*").handler(this::handle);
    vertx.createHttpServer().requestHandler(router).listen(port).<Void>mapEmpty().onComplete(promise);
  }
}
