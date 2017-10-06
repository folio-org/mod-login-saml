package org.folio.config;


import com.google.common.base.Strings;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.model.OkapiHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Connect to mod-configuration via Okapi
 *
 * @author rsass
 */
public class ConfigurationsClient {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationsClient.class);

  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  public static Future<SamlConfiguration> getConfiguration(OkapiHeaders okapiHeaders) {

    if (Strings.isNullOrEmpty(okapiHeaders.getUrl())) {
      return Future.failedFuture("Missing Okapi URL");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getTenant())) {
      return Future.failedFuture("Missing Tenant");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getToken())) {
      return Future.failedFuture("Missing Token");
    }

    Future<SamlConfiguration> future = Future.future();

    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + ")";

    try {
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

      HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
      httpClient.setDefaultHeaders(headers);
      httpClient.request(CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery) // this is ugly :/
        .whenComplete((Response response, Throwable throwable) -> {
          if (Response.isSuccess(response.getCode())) {

            JsonObject responseBody = response.getBody();
            JsonArray configs = responseBody.getJsonArray("configs"); //{"configs": [],"total_records": 0}
            try {
              future.complete(ConfigurationObjectMapper.map(configs, SamlConfiguration.class));
            } catch (Exception ex) {
              future.fail(ex);
            }

          } else {
            log.warn("Cannot get configuration data: " + response.getError().toString());
            future.fail(response.getException());
          }
        });

    } catch (Exception e) {
      log.warn("Cannot get configuration data: " + e.getMessage());
      future.fail(e);
    }

    return future;
  }


  public static Future<Void> storeEntry(OkapiHeaders okapiHeaders, Vertx vertx, String code, String value) {

    Assert.hasText(code, "config entry CODE is mandatory");

    if (Strings.isNullOrEmpty(okapiHeaders.getUrl())) {
      return Future.failedFuture("Missing Okapi URL");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getTenant())) {
      return Future.failedFuture("Missing Tenant");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getToken())) {
      return Future.failedFuture("Missing Token");
    }

    Future<Void> result = Future.future();

    JsonObject requestBody = new JsonObject();
    requestBody
      .put("module", MODULE_NAME)
      .put("configName", CONFIG_NAME)
      .put("code", code)
      .put("value", value);

    // decide to POST or PUT
    checkEntry(okapiHeaders, vertx, code).setHandler(checkHandler -> {
      if (checkHandler.failed()) {
        result.fail(checkHandler.cause());
      } else {
        String configId = checkHandler.result();

        // not existing -> POST, existing->PUT
        HttpMethod httpMethod = configId == null ? HttpMethod.POST : HttpMethod.PUT;
        String endpoint = configId == null ? CONFIGURATIONS_ENTRIES_ENDPOINT_URL : CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId;

        Map<String, String> headers = new HashMap<>();
        headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

        try {
          HttpClientInterface storeEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
          storeEntryClient.setDefaultHeaders(headers);
          storeEntryClient.request(httpMethod, requestBody, endpoint, null)
            .whenComplete((storeEntryResponse, throwable) -> {

              // POST->201 created, PUT->204 no content
              if ((httpMethod.equals(HttpMethod.POST) && storeEntryResponse.getCode() == 201)
                || (httpMethod.equals(HttpMethod.PUT) && storeEntryResponse.getCode() == 204)) {

                result.complete();
              } else {
                result.fail("The response status is not 'created',instead "
                  + storeEntryResponse.getCode()
                  + " with message  "
                  + storeEntryResponse.getError());
              }

            });
        } catch (Exception ex) {
          result.fail(ex);
        }

      }
    });


    return result;
  }

  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(OkapiHeaders okapiHeaders, Vertx vertx, String code) {
    Future<String> result = Future.future();

    if (Strings.isNullOrEmpty(okapiHeaders.getUrl())) {
      return Future.failedFuture("Missing Okapi URL");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getTenant())) {
      return Future.failedFuture("Missing Tenant");
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getToken())) {
      return Future.failedFuture("Missing Token");
    }

    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + " AND code== " + code + ")";
    try {
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());
      HttpClientInterface checkEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
      checkEntryClient.setDefaultHeaders(headers);
      checkEntryClient.request(CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery)
        .whenComplete((checkEntryResponse, throwable) -> {
          if (checkEntryResponse.getCode() != 200) {
            result.fail("Failed to check configuration entry: " + code
              + "HTTP result was " + checkEntryResponse.getCode() + " " + String.valueOf(checkEntryResponse.getBody()));
          } else {
            JsonObject entries = checkEntryResponse.getBody();
            JsonArray configs = entries.getJsonArray("configs");
            if (configs == null || configs.isEmpty()) {
              result.complete(); // null
            } else {
              JsonObject entry = configs.getJsonObject(0);
              String id = entry.getString("id");
              result.complete(id);
            }
          }
        });

    } catch (Exception exception) {
      result.fail(exception);
    }

    return result;
  }
}
