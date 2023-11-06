package org.folio.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.WebClientFactory;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.PercentCodec;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

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
  private static final Logger LOGGER = LogManager.getLogger(ConfigurationsClient.class);
  private static final String ERROR_MESSAGE_STRING = "errorMessage : %s";
  
  public static final String CONFIGURATIONS_ENTRIES_ENDPOINT_URL = "/configurations/entries";
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final String CONFIG_NAME = "saml";

  public static final String MISSING_OKAPI_URL = "Missing Okapi URL";
  public static final String MISSING_TENANT = "Missing Tenant";
  public static final String MISSING_TOKEN = "Missing Token";

  private static final String MODULE_QUERY_CONSTANT = "(module==";
  private static final String CONFIG_NAME_QUERY_CONSTANT = " AND configName==";
  private static final String QUERY_END_CONSTANT = ")";

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
    String query = MODULE_QUERY_CONSTANT + MODULE_NAME + CONFIG_NAME_QUERY_CONSTANT + CONFIG_NAME + QUERY_END_CONSTANT;

    return checkConfig(vertx, okapiHeaders, query)
      .compose(configs -> ConfigurationObjectMapper.map(configs, SamlConfiguration.class));
  }

  public static Future<SamlConfiguration> getConfigurationWithIds(Vertx vertx, OkapiHeaders okapiHeaders) {
    String query = MODULE_QUERY_CONSTANT + MODULE_NAME + CONFIG_NAME_QUERY_CONSTANT + CONFIG_NAME + QUERY_END_CONSTANT;
    
    return checkConfig(vertx, okapiHeaders, query)
      .compose(configs -> ConfigurationObjectMapperWithList.map(configs,
          ConfigurationObjectMapper.mapWithoutFuture(configs, SamlConfiguration.class)));
  }

  public static Future<SamlConfiguration> storeEntries(Vertx vertx, OkapiHeaders headers, Map<String, String> entries) {

    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    List<Future<Void>> futures = entries.entrySet().stream()
      .map(entry -> ConfigurationsClient.storeEntry(vertx, headers, entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());

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
    verifyOkapiHeaders(okapiHeaders);
    CharSequence encodedQuery = PercentCodec.encode(query);

    return WebClientFactory.getWebClient(vertx)
      .getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery)
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
    String query = MODULE_QUERY_CONSTANT + MODULE_NAME + CONFIG_NAME_QUERY_CONSTANT + CONFIG_NAME + " AND code==" + code + QUERY_END_CONSTANT;
    return checkConfig(vertx, okapiHeaders, query)
      .map(configs -> configs.isEmpty() ? null : configs.getJsonObject(0).getString("id"));
  }

  public static class MissingHeaderException extends RuntimeException {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }

  public static Future<SamlConfiguration> deleteConfigurationEntries(Vertx vertx, OkapiHeaders headers, SamlConfiguration samlConfiguration) {
      
    List<String> entries = samlConfiguration.getIdsList();
    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    List<Future<Void>> futures = entries.stream()
      .map(entry -> ConfigurationsClient.deleteConfigurationEntry(vertx, headers, entry))
      .collect(Collectors.toList());
    
    return GenericCompositeFuture.all(futures)
      .compose(compositeEvent -> ConfigurationsClient.getConfiguration(vertx, headers)
    );
  }
  
  public static Future<Void> deleteConfigurationEntry(Vertx vertx, OkapiHeaders okapiHeaders, String configId) {
    Assert.hasText(configId, "config entry ID is mandatory");

    HttpMethod httpMethod = HttpMethod.DELETE;
    String endpoint = CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId;
    JsonObject requestBody = new JsonObject();
    requestBody
      .put("module", MODULE_NAME)
      .put("configName", CONFIG_NAME);
    if(configId != null) {
      return WebClientFactory.getWebClient(vertx)
        .requestAbs(httpMethod, okapiHeaders.getUrl() + endpoint)
        .putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
        .putHeader(XOkapiHeaders.URL, okapiHeaders.getUrl())
        .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
        .expect(ResponsePredicate.status(201, 205))
        .sendJsonObject(requestBody)
        .mapEmpty();
    } else {
      String warnMessage = String.format("The Configurations Entry Id with value : %s", configId, " does not exist");
      LOGGER.warn(warnMessage);
      return Future.failedFuture(warnMessage);
    }
  }

  public static Map<String, String> samlConfiguration2Map(SamlConfiguration samlConfiguration) {
    Map localMap = new HashMap();

    if(samlConfiguration.getKeystore() != null)
      localMap.put(SamlConfiguration.KEYSTORE_FILE_CODE, samlConfiguration.getKeystore());
    if(samlConfiguration.getKeystorePassword() != null)
      localMap.put(SamlConfiguration.KEYSTORE_PASSWORD_CODE, samlConfiguration.getKeystorePassword());
    if(samlConfiguration.getPrivateKeyPassword() != null)
      localMap.put(SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, samlConfiguration.getPrivateKeyPassword());
    if(samlConfiguration.getIdpUrl() != null)
      localMap.put(SamlConfiguration.IDP_URL_CODE, samlConfiguration.getIdpUrl());
    if(samlConfiguration.getSamlBinding() != null)
      localMap.put(SamlConfiguration.SAML_BINDING_CODE, samlConfiguration.getSamlBinding());
    if(samlConfiguration.getSamlAttribute() != null)
      localMap.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, samlConfiguration.getSamlAttribute());
    if(samlConfiguration.getIdpMetadata() != null)
      localMap.put(SamlConfiguration.IDP_METADATA_CODE, samlConfiguration.getIdpMetadata());
    if(samlConfiguration.getUserProperty() != null)
      localMap.put(SamlConfiguration.USER_PROPERTY_CODE, samlConfiguration.getUserProperty());
    if(samlConfiguration.getMetadataInvalidated() != null)
      localMap.put(SamlConfiguration.METADATA_INVALIDATED_CODE, samlConfiguration.getMetadataInvalidated());
    if(samlConfiguration.getOkapiUrl() != null)
      localMap.put(SamlConfiguration.OKAPI_URL, samlConfiguration.getOkapiUrl());
    
    return localMap;
  }
}
