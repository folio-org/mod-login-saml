package org.folio.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.WebClientFactory;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.PercentCodec;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Connect to mod-configuration via Okapi

 *
 * @author rsass
 */
public class ConfigurationsClient {
  public static final String GET_CONFIGURATION_WARN_MESSAGE = "There is a wrong config from mod-configuration";
  private static final Logger LOGGER = LogManager.getLogger(ConfigurationsClient.class);

  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  private static final String MODULE_QUERY_CONSTANT = "(module==";
  private static final String CONFIG_NAME_QUERY_CONSTANT = " AND configName==";
  private static final String QUERY_END_CONSTANT = ")";
  private static final String QUERY_CONSTANT = MODULE_QUERY_CONSTANT + MODULE_NAME + CONFIG_NAME_QUERY_CONSTANT + CONFIG_NAME;

  private ConfigurationsClient() {
  }

  public static Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders) {
    Objects.requireNonNull(okapiHeaders);

    return checkConfig(vertx, okapiHeaders, QUERY_CONSTANT + QUERY_END_CONSTANT)
      .onFailure(cause -> LOGGER.error("There aren't any data received from mod-configuration: {}", cause.getMessage()))
      .map(configs -> {
        try {
          return ConfigurationObjectMapper.map(configs, SamlConfiguration.class);
        } catch (IllegalArgumentException iArgEx) {
          String errorMessage = String.format(GET_CONFIGURATION_WARN_MESSAGE + ": %s",
            iArgEx.getMessage());
          LOGGER.error(errorMessage, iArgEx);
          throw new IllegalArgumentException(iArgEx.getMessage());
        }
      });
  }

  public static Future<SamlConfiguration> getConfigurationWithIds(Vertx vertx, OkapiHeaders okapiHeaders) {
    Objects.requireNonNull(okapiHeaders);

    return checkConfig(vertx, okapiHeaders, QUERY_CONSTANT + QUERY_END_CONSTANT)
      .onFailure(cause -> LOGGER.error("There are no data from mod-configuration received: {}", cause.getMessage()))
      .map(configs -> {
        try {
          return ConfigurationObjectMapperWithList.map(configs, ConfigurationObjectMapper.map(configs, SamlConfiguration.class));
        } catch (IllegalArgumentException iArgEx) {
          LOGGER.warn(GET_CONFIGURATION_WARN_MESSAGE, iArgEx);
          throw new IllegalArgumentException(iArgEx.getMessage());
        }
      });
  }

  public static Future<SamlConfiguration> storeEntries(Vertx vertx, OkapiHeaders headers, Map<String, String> entries) {

    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    List<Future<Void>> futures = entries.entrySet().stream()
      .map(entry -> ConfigurationsClient.storeEntry(vertx, headers, entry.getKey(), entry.getValue()))
      .toList();

    return GenericCompositeFuture.all(futures)
      .compose(compositeEvent -> ConfigurationsClient.getConfiguration(vertx, headers)
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
          .expect(ResponsePredicate.status(201, 205))
          .sendJsonObject(requestBody)
          .mapEmpty();
      });
  }

  public static Future<JsonArray> checkConfig(Vertx vertx, OkapiHeaders okapiHeaders, String query) {
    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    CharSequence encodedQuery = PercentCodec.encode(query);

    return WebClientFactory.getWebClient(vertx)
      .getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?limit=1000&query=" + encodedQuery)
      .putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
      .putHeader(XOkapiHeaders.URL, okapiHeaders.getUrl())
      .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
      .expect(ResponsePredicate.SC_OK)
      .expect(ResponsePredicate.JSON)
      .send()
      .map(res -> res.bodyAsJsonObject().getJsonArray("configs"));
  }

  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code) {
    String query = QUERY_CONSTANT + " AND code==" + code + QUERY_END_CONSTANT;
    return checkConfig(vertx, okapiHeaders, query)
      .map(configs -> configs.isEmpty() ? null : configs.getJsonObject(0).getString("id"));
  }

  public static Future<SamlConfiguration> deleteConfigurationEntries(Vertx vertx, OkapiHeaders okapiHeaders,
      SamlConfiguration samlConfiguration) {
    Objects.requireNonNull(okapiHeaders);
    Objects.requireNonNull(samlConfiguration);
    Objects.requireNonNull(samlConfiguration.getIdsList());

    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);

    List<String> entries = samlConfiguration.getIdsList();

    List<Future<Void>> futures = entries.stream()
      .map(entry -> ConfigurationsClient.deleteConfigurationEntry(vertx, okapiHeaders, entry)).toList();

    return GenericCompositeFuture.all(futures)
      .compose(compositeEvent -> ConfigurationsClient.getConfiguration(vertx, okapiHeaders)
    );
  }

  public static Future<Void> deleteConfigurationEntry(Vertx vertx, OkapiHeaders okapiHeaders, String configId) {
    Assert.hasText(configId, "config entry ID is mandatory");

    HttpMethod httpMethod = HttpMethod.DELETE;
    String endpoint = CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId;

    return WebClientFactory.getWebClient(vertx)
      .requestAbs(httpMethod, okapiHeaders.getUrl() + endpoint)
      .putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
      .putHeader(XOkapiHeaders.URL, okapiHeaders.getUrl())
      .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
      .expect(ResponsePredicate.SC_NO_CONTENT) //204 No Content
      .send()
      .otherwise(ex -> {
         String error = "To delete" + " " + configId + " " + ex.getMessage();
         LOGGER.error(error, ex);
         throw new IllegalArgumentException(error);
      })
     .mapEmpty();
  }

}
