package org.folio.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    if (StringUtils.isBlank(okapiHeaders.getUrl())) {
      throw new MissingHeaderException(MISSING_OKAPI_URL);
    }
    if (StringUtils.isBlank(okapiHeaders.getTenant())) {
      throw new MissingHeaderException(MISSING_TENANT);
    }
    if (StringUtils.isBlank(okapiHeaders.getToken())) {
      throw new MissingHeaderException(MISSING_TOKEN);
    }
  }

  public static Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders) {
    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + ")";

    return checkConfig(vertx, okapiHeaders, query)
      .compose(configs -> ConfigurationObjectMapper.map(configs, SamlConfiguration.class));
  }

  public static Future<SamlConfiguration> storeEntries(Vertx vertx, OkapiHeaders headers, Map<String, String> entries) {

    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    List<Future<Void>> futures = entries.entrySet().stream()
      .map(entry -> ConfigurationsClient.storeEntry(vertx, headers, entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(futures).compose(compositeEvent ->
      ConfigurationsClient.getConfiguration(vertx, headers)
    );
  }

  public static Future<Void> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value) {
    Assert.hasText(code, "config entry CODE is mandatory");

    JsonObject requestBody = new JsonObject();
    requestBody
      .put("module", MODULE_NAME)
      .put("configName", CONFIG_NAME)
      .put("code", code)
      .put("value", value);

    // decide to POST or PUT
    return checkEntry(vertx, okapiHeaders, code)
      .compose(configId -> {
        // not existing -> POST, existing->PUT
        HttpMethod httpMethod = configId == null ? HttpMethod.POST : HttpMethod.PUT;
        String endpoint = configId == null ? CONFIGURATIONS_ENTRIES_ENDPOINT_URL : CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId;

        return WebClientFactory.getWebClient(vertx)
          .requestAbs(httpMethod, okapiHeaders.getUrl() + endpoint)
          .putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
          .putHeader(XOkapiHeaders.URL, okapiHeaders.getUrl())
          .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
          .sendJsonObject(requestBody)
          .map(res -> {
            if (res.statusCode() != 200 && res.statusCode() != 201 && res.statusCode() != 204) {
              throw new RuntimeException("Could not store entry code=" + code + " value=" + value + " status= " + res.statusCode());
            }
            return null;
          });
      });
  }

  public static Future<JsonArray> checkConfig(Vertx vertx, OkapiHeaders okapiHeaders, String query) {
    verifyOkapiHeaders(okapiHeaders);
    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
    return WebClientFactory.getWebClient(vertx)
      .getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery)
      .putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
      .putHeader(XOkapiHeaders.URL, okapiHeaders.getUrl())
      .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
      .send()
      .map(res -> {
        if (res.statusCode() != 200) {
          throw new RuntimeException("Unexpected status " + res.statusCode() + " for config query=" + query);
        }
        return res.bodyAsJsonObject().getJsonArray("configs");
      });
  }
  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code) {
    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + " AND code== " + code + ")";
    return checkConfig(vertx, okapiHeaders, query)
      .map(configs -> configs.isEmpty() ? null : configs.getJsonObject(0).getString("id"));
  }

  public static class MissingHeaderException extends RuntimeException {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }
}
