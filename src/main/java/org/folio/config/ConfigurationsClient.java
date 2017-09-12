package org.folio.config;


import com.google.common.base.Strings;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
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

import static org.folio.util.model.OkapiHeaders.OKAPI_TENANT_HEADER;
import static org.folio.util.model.OkapiHeaders.OKAPI_TOKEN_HEADER;

/**
 * Connect to mod-configuration via Okapi
 *
 * @author rsass
 */
public class ConfigurationsClient {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationsClient.class);

  public static final String KEYSTORE_FILE_CODE = "keystore.file";
  public static final String KEYSTORE_PASSWORD_CODE = "keystore.password";
  public static final String KEYSTORE_PRIVATEKEY_PASSWORD_CODE = "keystore.privatekey.password";
  public static final String IDP_URL_CODE = "idp.url";
  public static final String SAML_BINDING_CODE = "saml.binding";
  public static final String SAML_ATTRIBUTE_CODE = "saml.attribute";
  public static final String USER_PROPERTY_CODE = "user.property";

  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  public static Future<SamlConfiguration> getConfiguration(OkapiHeaders okapiHeaders, Vertx vertx) {

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
            SamlConfiguration conf = new SamlConfiguration();
            JsonObject responseBody = response.getBody();

            JsonArray configs = responseBody.getJsonArray("configs"); //{"configs": [],"total_records": 0}
            configs.forEach(entry -> {
              if (entry instanceof JsonObject) {

                JsonObject entryAsJsonObject = JsonObject.class.cast(entry);
                String code = entryAsJsonObject.getString("code");
                String value = entryAsJsonObject.getString("value");

                switch (code) {
                  case KEYSTORE_FILE_CODE:
                    conf.setKeystore(value);
                    break;
                  case KEYSTORE_PASSWORD_CODE:
                    conf.setKeystorePassword(value);
                    break;
                  case KEYSTORE_PRIVATEKEY_PASSWORD_CODE:
                    conf.setPrivateKeyPassword(value);
                    break;
                  case IDP_URL_CODE:
                    conf.setIdpUrl(value);
                    break;
                  case SAML_BINDING_CODE:
                    conf.setSamlBinding(value);
                    break;
                  case SAML_ATTRIBUTE_CODE:
                    conf.setSamlAttribute(value);
                    break;
                  case USER_PROPERTY_CODE:
                    conf.setUserProperty(value);
                    break;
                  default:
                    log.warn("Unknown SAML configuration entry. Code: {}, Value: {}", code, value);
                }
              }
            });

            future.complete(conf);

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
              if (!Response.isSuccess(storeEntryResponse.getCode())) {
                result.fail(storeEntryResponse.getError().encodePrettily());
              } else {

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

    WebClient webClient = WebClient.create(vertx);
    webClient.getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL)
      .addQueryParam("query", query)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "application/json")
      .putHeader(OKAPI_TENANT_HEADER, okapiHeaders.getTenant())
      .putHeader(OKAPI_TOKEN_HEADER, okapiHeaders.getToken())
      .timeout(10000)
      .send(handler -> {
        if (handler.failed()) {
          result.fail(handler.cause());
        } else {
          if (handler.result().statusCode() != 200) {
            result.fail(handler.result().statusMessage());
          } else {
            HttpResponse<Buffer> responseBuffer = handler.result();
            try {
              JsonObject entries = responseBuffer.bodyAsJsonObject(); //{"configs": [],"total_records": 0}
              JsonArray configs = entries.getJsonArray("configs");
              if (configs == null || configs.isEmpty()) {
                result.complete(); // null
              } else {
                JsonObject entry = configs.getJsonObject(0);
                String id = entry.getString("id");
                result.complete(id);
              }
            } catch (Throwable cause) {
              result.fail(cause);
            }

          }

        }
      });

    return result;
  }
}
