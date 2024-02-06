package org.folio.util;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ConfigurationObjectMapper;
import org.folio.config.model.SamlConfiguration;

import java.util.ArrayList;
import java.util.List;

public class MockJsonExtended extends MockJson {
  private static final Logger log = LogManager.getLogger(MockJsonExtended.class);

  JsonArray receivedData = new JsonArray();
  List<String> requestedUrlList = new ArrayList<String>();

  public JsonArray getMocks() {
    return mocks;
  }

  public JsonArray getMockConfigs() {
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
            return ((JsonObject)receivedData).getJsonArray(configsConstant);
          }
        }
      }
    }
    return (null);
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

  protected void handle(RoutingContext context) {
    HttpServerRequest request = context.request();
    HttpServerResponse response = context.response();
    String method = request.method().name();
    String uri = request.uri();
    log.info("Before: Used in mock={} method={} uri={}", resource, method, uri);
    if (method.equalsIgnoreCase("put") || method.equalsIgnoreCase("post")) {
      log.info("Used in mock={} method={} uri={}", resource, method, uri);
      request.bodyHandler(buff -> {
        JsonObject localJsonObject = buff.toJsonObject();
        if (localJsonObject!= null)
          receivedData.add(localJsonObject);
      });
    }

    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      if (!method.equalsIgnoreCase("delete")
        &&method.equalsIgnoreCase(entry.getString("method", "get"))
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
      else if (method.equalsIgnoreCase(entry.getString("method", "delete"))
        && uri.equals(entry.getString("url"))) {
        log.info("Used in method={} uri={} mock={}", method, uri, resource);/////
        requestedUrlList.add(StringUtils.substringAfterLast(uri, "/"));
        response.setStatusCode(entry.getInteger("status", 204));
        response.end();
        return;
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

  public ArrayList<String> getReceivedDataAsList() {
    return new ArrayList<String>(receivedData.getList());
  }

  public void resetReceivedData() {
    receivedData.clear();
  }

  public ArrayList<String> getRequestedUrlList() {
    return new ArrayList<String>(requestedUrlList);
  }

  public void resetRequestedUrlList() {
    requestedUrlList.clear();
  }
}
