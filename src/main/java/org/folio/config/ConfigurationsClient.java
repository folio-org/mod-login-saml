package org.folio.config;


import com.google.common.base.Strings;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.config.model.SamlConfiguration;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import static org.folio.util.model.OkapiHeaders.OKAPI_TENANT_HEADER;
import static org.folio.util.model.OkapiHeaders.OKAPI_TOKEN_HEADER;

/**
 * Connect to mod-configuration via Okapi
 * <p>
 * TODO: replace with OkapiClient
 *
 * @author rsass
 */
public class ConfigurationsClient {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationsClient.class);

  public static final String KEYSTORE_FILE_CODE = "keystore.file";
  public static final String KEYSTORE_PASSWORD_CODE = "keystore.password";
  public static final String KEYSTORE_PRIVATEKEY_PASSWORD_CODE = "keystore.privatekey.password";
  public static final String IDP_URL_CODE = "idp.url";

  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  public static Future<SamlConfiguration> getConfiguration(RoutingContext routingContext) {

    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);

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

    WebClient webClient = WebClient.create(routingContext.vertx());
    webClient.getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL)
      .addQueryParam("query", query)
      .putHeader("Content-Type", "application/json")
      .putHeader("Accept", "application/json")
      .putHeader(OKAPI_TENANT_HEADER, okapiHeaders.getTenant())
      .putHeader(OKAPI_TOKEN_HEADER, okapiHeaders.getToken())
      .timeout(10000)
      .send(queryResult -> {
        if (queryResult.failed()) {
          future.fail(queryResult.cause());
        } else {
          if (queryResult.result().statusCode() != 200) {
            log.warn("Configuration query was unsuccessful. Status: {}-{}, body:{}",
              queryResult.result().statusCode(),
              queryResult.result().statusMessage(),
              queryResult.result().body());
            future.fail(queryResult.result().statusMessage());
          } else {
            HttpResponse<Buffer> responseBuffer = queryResult.result();
            SamlConfiguration conf = new SamlConfiguration();

            try {
              JsonObject entries = responseBuffer.bodyAsJsonObject(); //{"configs": [],"total_records": 0}
              JsonArray configs = entries.getJsonArray("configs");
              configs
                .forEach(entry -> {
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
                      default:
                        log.warn("Unknown SAML configuration entry. Code: {}, Value: {}", code, value);
                    }
                  }
                });

              future.complete(conf);
            } catch (Throwable cause) {
              future.fail(cause); // JSON decoding fail
            }

          }
        }
      });

    return future;
  }


  public static Future<Void> storeEntry(RoutingContext rc, String code, String value) {

    Assert.hasText(code, "config entry CODE is mandatory");
    // Assert.notNull(value); // TODO ??

    final OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(rc);

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
    checkEntry(rc, code).setHandler(checkHandler -> {
      if (checkHandler.failed()) {
        result.fail(checkHandler.cause());
      } else {
        String configId = checkHandler.result();
        if (configId == null) {
          // not-existing config
          WebClient webClient = WebClient.create(rc.vertx());
          webClient.postAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL)
            .putHeader("Content-Type", "application/json")
            .putHeader("Accept", "application/json")
            .putHeader(OKAPI_TENANT_HEADER, okapiHeaders.getTenant())
            .putHeader(OKAPI_TOKEN_HEADER, okapiHeaders.getToken())
            .timeout(10000)
            .sendJsonObject(requestBody, postResult -> {
              if (postResult.failed()) {
                result.fail(postResult.cause());
              } else {
                // POST-> 201 created
                if (postResult.result().statusCode() == 201) {
                  result.complete();
                } else {
                  result.fail("The response status is not 'created',instead "
                    + postResult.result().statusCode()
                    + " with message  "
                    + postResult.result().statusMessage());
                }
              }
            });
        } else {
          //existing config
          WebClient webClient = WebClient.create(rc.vertx());
          webClient.putAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId)
            .putHeader("Content-Type", "application/json")
            .putHeader("Accept", "application/json")
            .putHeader(OKAPI_TENANT_HEADER, okapiHeaders.getTenant())
            .putHeader(OKAPI_TOKEN_HEADER, okapiHeaders.getToken())
            .timeout(10000)
            .sendJsonObject(requestBody, postResult -> {
              if (postResult.failed()) {
                result.fail(postResult.cause());
              } else {
                // PUT -> 204 no content
                if (postResult.result().statusCode() == 204) {
                  result.complete();
                } else {
                  result.fail("The response status is not '204',instead "
                    + postResult.result().statusCode()
                    + " with message  "
                    + postResult.result().statusMessage());
                }
              }
            });
        }
      }
    });


    return result;
  }

  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(RoutingContext rc, String code) {
    Future<String> result = Future.future();

    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(rc);
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

    WebClient webClient = WebClient.create(rc.vertx());
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
