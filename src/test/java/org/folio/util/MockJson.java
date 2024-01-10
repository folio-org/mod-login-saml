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
import org.folio.config.ConfigurationObjectMapper;
import org.folio.config.model.SamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class MockJson extends AbstractVerticle {
  private static final Logger log = LogManager.getLogger(MockJson.class);

  JsonArray mocks;
  String resource;
  JsonArray receivedData = new JsonArray();

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

  public SamlConfiguration getMockPartialContent() {
    final String partialUrlConstant = "/configurations/entries?query=%28module%3D%3DLOGIN-SAML%20AND%20configName%3D%3Dsaml%29";
    final String receivedDataConstant = "receivedData";
    final String configsConstant = "configs";
    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      Object receivedData = null;
      if (entry.containsKey("url")) {
        if (entry.getString("url").contains(partialUrlConstant) ) {
          receivedData = entry.getValue(receivedDataConstant);
          if (receivedData instanceof JsonObject) {
            return ConfigurationObjectMapper
              .map(((JsonObject)receivedData).getJsonArray(configsConstant), SamlConfiguration.class);
          }
        }
      }
    }
    return (null);
  }

  public void setMockContent(String resource) {
    setMockContent(resource, s -> s);
  }

  private void handle(RoutingContext context) {
    HttpServerRequest request = context.request();
    HttpServerResponse response = context.response();
    String method = request.method().name();
    String uri = request.uri();
    log.info("Before: Used in mock={} method={} uri={}", resource, method, uri);
    if (method.equalsIgnoreCase("put") || method.equalsIgnoreCase("post")) {
      log.info("Put: Used in mock={} method={} uri={}", resource, method, uri);
      request.bodyHandler(buff -> {
        JsonObject localJsonObject = buff.toJsonObject();
        if (localJsonObject!= null)
          receivedData.add(localJsonObject);
      });
    }

    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      if ((method.equalsIgnoreCase(entry.getString("method", "get"))
          || method.equalsIgnoreCase(entry.getString("method", "delete")))
          && uri.equals(entry.getString("url"))) {
        //log.info("Used in mock={} method={} uri={}", resource, method, uri);/////
        response.setStatusCode(entry.getInteger("status", 200));
        JsonArray headers = entry.getJsonArray("headers");
        if (headers != null) {
          for (int j = 0; j < headers.size(); j++) {
            JsonObject headObject = headers.getJsonObject(j);
            response.putHeader(headObject.getString("name"), headObject.getString("value"));
          }
        }
        Object responseData = entry.getValue("receivedData");
        if (responseData == null) {
          response.end();
          return;
        }
        if (responseData instanceof JsonObject) {
          response.putHeader("Content-Type", "application/json");
          response.end(((JsonObject) responseData).encodePrettily());
          return;
        }
        response.end(responseData.toString());
        return;
      }
      else if (method.equalsIgnoreCase(entry.getString("method", "put"))
        && uri.equals(entry.getString("url"))) {
        log.info("Used in mock={} method={} uri={}", resource, method, uri);/////
      }
    }
    log.info("Not found in mock={} method={} uri={}", resource, method, uri);
    response.setStatusCode(404);
    response.putHeader("Content-Type", "text/plain");
    response.end("Not found in mock");
  }

  public SamlConfiguration getReceivedData() {
    return ConfigurationObjectMapper.map(receivedData, SamlConfiguration.class);
  }

  public void resetReceivedData() {
    receivedData = new JsonArray();
  }

  public void start(Promise<Void> promise) {
    final int port = context.config().getInteger("http.port");

    log.info("Running Mock JSON on port {}", port);

    Router router = Router.router(vertx);
    router.routeWithRegex("/.*").handler(this::handle);
    vertx.createHttpServer().requestHandler(router).listen(port).<Void>mapEmpty().onComplete(promise);
  }
}
