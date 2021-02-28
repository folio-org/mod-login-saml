package org.folio.config;


import com.google.common.base.Strings;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Connect to mod-configuration via Okapi
 *
 * @author rsass
 */
public class ConfigurationsClient {

  private static final Logger log = LogManager.getLogger(ConfigurationsClient.class);

  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  public static final String MISSING_OKAPI_URL = "Missing Okapi URL";
  public static final String MISSING_TENANT = "Missing Tenant";
  public static final String MISSING_TOKEN = "Missing Token";

  private ConfigurationsClient() {

  }

  protected static void verifyOkapiHeaders(OkapiHeaders okapiHeaders) throws MissingHeaderException {
    if (Strings.isNullOrEmpty(okapiHeaders.getUrl())) {
      throw new MissingHeaderException(MISSING_OKAPI_URL);
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getTenant())) {
      throw new MissingHeaderException(MISSING_TENANT);
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getToken())) {
      throw new MissingHeaderException(MISSING_TOKEN);
    }
  }

  public static Future<SamlConfiguration> getConfiguration(OkapiHeaders okapiHeaders) {

    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + ")";

    try {
      Promise<SamlConfiguration> promise = Promise.promise();
      verifyOkapiHeaders(okapiHeaders);
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

      HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
      httpClient.setDefaultHeaders(headers);
      httpClient.request(CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery) // this is ugly :/
        .whenComplete((Response response, Throwable throwable) -> {
          if (Response.isSuccess(response.getCode())) {

            JsonObject responseBody = response.getBody();
            JsonArray configs = responseBody.getJsonArray("configs");

            promise.handle(ConfigurationObjectMapper.map1(configs, SamlConfiguration.class));
          } else {
            log.warn("Cannot get configuration data: {}", response.getError());
            promise.fail(response.getException());
          }
        });
      return promise.future();
    } catch (Exception e) {
      log.warn("Cannot get configuration data: {}", e.getMessage());
      return Future.failedFuture(e);
    }
  }

  public static Future<SamlConfiguration> storeEntries(OkapiHeaders headers, Map<String, String> entries) {

    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    Promise<SamlConfiguration> result = Promise.promise();

    // CompositeFuture.all(...) called below only accepts a list of Future (raw type)
    @SuppressWarnings("java:S3740")
    List<Future> futures = entries.entrySet().stream()
      .map(entry -> ConfigurationsClient.storeEntry(headers, entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());

    CompositeFuture.all(futures).onComplete(compositeEvent -> {
      if (compositeEvent.succeeded()) {
        ConfigurationsClient.getConfiguration(headers).onComplete(newConfigHandler -> {

          if (newConfigHandler.succeeded()) {
            result.complete(newConfigHandler.result());
          } else {
            result.fail(newConfigHandler.cause());
          }

        });
      } else {
        log.warn("Cannot save configuration entries: {}", compositeEvent.cause().getMessage());
        result.fail(compositeEvent.cause());
      }
    });

    return result.future();
  }


  public static Future<Void> storeEntry(OkapiHeaders okapiHeaders, String code, String value) {
    Assert.hasText(code, "config entry CODE is mandatory");

    Promise<Void> result = Promise.promise();

    JsonObject requestBody = new JsonObject();
    requestBody
      .put("module", MODULE_NAME)
      .put("configName", CONFIG_NAME)
      .put("code", code)
      .put("value", value);

    // decide to POST or PUT
    checkEntry(okapiHeaders, code).onComplete(checkHandler -> {
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
          HttpClientInterface storeEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant(), true);
          storeEntryClient.setDefaultHeaders(headers);
          storeEntryClient.request(httpMethod, requestBody, endpoint, null)
            .whenComplete((storeEntryResponse, throwable) -> {

              if (storeEntryResponse == null) {
                if (throwable == null) {
                  result.fail("Cannot " + httpMethod.toString() + " configuration entry");
                } else {
                  result.fail(throwable);
                }
              }
              // POST->201 created, PUT->204 no content
              else if ((httpMethod.equals(HttpMethod.POST) && storeEntryResponse.getCode() == 201)
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


    return result.future();
  }

  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(OkapiHeaders okapiHeaders, String code) {
    Promise<String> result = Promise.promise();

    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + " AND code== " + code + ")";
    try {
      verifyOkapiHeaders(okapiHeaders);
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());
      HttpClientInterface checkEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant(), true);
      checkEntryClient.setDefaultHeaders(headers);
      checkEntryClient.request(CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery)
        .whenComplete((checkEntryResponse, throwable) -> {
          if (checkEntryResponse.getCode() != 200) {
            result.fail("Failed to check configuration entry: " + code
              + " HTTP result was " + checkEntryResponse.getCode() + " " + checkEntryResponse.getBody().encode());
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

    return result.future();
  }

  public static class MissingHeaderException extends Exception {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }
}
