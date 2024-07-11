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
import org.folio.config.ConfigurationObjectMapperWithList;
import org.folio.config.model.SamlConfiguration;

import java.util.ArrayList;
import java.util.List;

public class MockJsonExtended extends MockJson {
  private static final Logger log = LogManager.getLogger(MockJsonExtended.class);
  private static final String PARTIAL_URL = "/configurations/entries?query=%28module%3D%3DLOGIN-SAML%20AND%20configName%3D%3Dsaml%29";
  private static final String RECEIVED_DATA_CONSTANT = "receivedData";
  private static final String CONFIGS_CONSTANT = "configs";
  private static final String URL_CONSTANT = "url";

  JsonArray receivedData = new JsonArray();
  List<String> requestedUrlList = new ArrayList<String>();
  List<String> mockIds = new ArrayList<String>();

  public JsonArray getMocks() {
    return mocks;
  }

  public JsonArray getMockConfigs() {
    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      Object localReceivedData = null;
      if (entry.containsKey(URL_CONSTANT) && entry.getString(URL_CONSTANT).contains(PARTIAL_URL)) {
          localReceivedData = entry.getValue(RECEIVED_DATA_CONSTANT);
          if (localReceivedData instanceof JsonObject) {
            return ((JsonObject)localReceivedData).getJsonArray(CONFIGS_CONSTANT);
          }
      }
    }
    return (null);
  }

  public SamlConfiguration getMockPartialContent() {
    var mockConfigs = getMockConfigs();
    if (mockConfigs == null) {
      return (null);
    }
    return ConfigurationObjectMapper.map(mockConfigs, SamlConfiguration.class);
  }

  public List<String> getMockPartialContentIds() {
    var mockConfigs = getMockConfigs();
    if (mockConfigs == null) {
      return (null);
    }
    return ConfigurationObjectMapperWithList.mapInternal(mockConfigs);
  }

  public void setMockIds() {
    mockIds = getMockPartialContentIds();
  }

  @Override
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

    if((mockIds.size() > 0 && requestedUrlList.size() > 0 && requestedUrlList.containsAll(mockIds)))
      {
        super.setMockContent("mock_200_empty.json");
        mockIds = new ArrayList<String>();
      }

    for (int i = 0; i < mocks.size(); i++) {
      JsonObject entry = mocks.getJsonObject(i);
      if (!(mockIds.size() > 0 && requestedUrlList.size() > 0 && requestedUrlList.containsAll(mockIds))
        && !method.equalsIgnoreCase("delete")
        && method.equalsIgnoreCase(entry.getString("method", "get"))
        && uri.equals(entry.getString("url"))) {
        log.info("Used in mock={} method={} uri={}", resource, method, uri);
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
      else if (!(mockIds.size() > 0 && requestedUrlList.size() > 0 && requestedUrlList.containsAll(mockIds))
        && method.equalsIgnoreCase(entry.getString("method", "delete"))
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
    ArrayList<String> localList = new ArrayList<String>();
    if (receivedData != null) {
      for (int i = 0; i < receivedData.size(); i++)
        localList.add(receivedData.getString(i));
    }
    return localList;
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
