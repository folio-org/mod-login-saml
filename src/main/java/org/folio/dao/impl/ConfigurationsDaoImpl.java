package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.ConfigurationsClient;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.SamlConfigurationUtil;
import org.folio.util.model.OkapiHeaders;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * @author barbaraloehle
 */
public class ConfigurationsDaoImpl implements ConfigurationsDao {
  private static final Logger LOGGER = LogManager.getLogger(ConfigurationsDaoImpl.class);
  private static final String ERROR_MESSAGE_STRING = "errorMessage : %s";

  public static final String CONFIGURATION_TABLE = "configuration";

  private Future<SamlConfiguration> getConfigurationMigration(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete) {

    Objects.requireNonNull(okapiHeaders);
    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
      .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
      .compose(results -> localFutureGetConfigurationMigration(results, vertx, okapiHeaders, withDelete));
  }

  private Future<SamlConfiguration> localFutureGetConfigurationMigration(Results<SamlConfiguration> results, Vertx vertx,
      OkapiHeaders okapiHeaders, boolean withDelete) {

    int localLength = results.getResults().size();
    if (localLength == 1) {
      return Future.succeededFuture(results.getResults().get(0));
    }
    if(localLength > 1) {
      String errorMessage = String.format("Migration: Number of records are not unique. Instead the number is : %s",
        Integer.toString(localLength));
      LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
      return Future.failedFuture(new IllegalArgumentException(errorMessage));
    }
    return ConfigurationsClient.getConfigurationWithIds(vertx, okapiHeaders)
      .compose(result -> {
        if (withDelete) {
          return localDeleteConfigurationEntries(vertx, okapiHeaders, result)
            .compose(resultAfterDeletion -> Future.succeededFuture(result));
        }
        else
          return Future.succeededFuture(result);
      })
      .compose(result -> localStoreSamlConfiguration(vertx, okapiHeaders, result))
      .onFailure(cause -> LOGGER.warn("There is an empty local DB : {}", cause.getMessage()));
  }

  private Future<Void> localDeleteConfigurationEntries(Vertx vertx, OkapiHeaders okapiHeaders,
      SamlConfiguration samlConfiguration){
    return ConfigurationsClient.deleteConfigurationEntries(vertx, okapiHeaders, samlConfiguration)
      .compose(this::localCompareObjects)
      .onFailure(cause -> {
        String warnMessage = "The data of mod-configuration are not correctly deleted:" + " " + cause.getMessage();
        LOGGER.warn(warnMessage);
        Future.failedFuture(warnMessage);
      });
  }


  private Future<Void> localCompareObjects(SamlConfiguration samlConfiguration) {
    if(SamlConfigurationUtil.isEqual(samlConfiguration, emptySamlConfiguration))
      return Future.succeededFuture();
    else {
      String warnMessage = "After deletion of the data of mod-configuration the compared Objects are different";
      LOGGER.warn(warnMessage);
      return Future.failedFuture(warnMessage);
    }
  }

  @Override
  public Future<SamlConfiguration> dataMigration(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete) {

    return getConfigurationMigration(vertx, okapiHeaders, withDelete)
      .onFailure(cause -> {
        String warnMessage = "Local: Cannot load config from mod-configuration. " + cause.getMessage();
        LOGGER.warn(warnMessage);
        Future.failedFuture(warnMessage);
      });
  }

  private Future<SamlConfiguration> dataMigrationToLoadData(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete) {

    return dataMigration(vertx, okapiHeaders, withDelete)
      .onFailure(cause -> {
        String warnMessage = "Local: Cannot load config from mod-configuration. " + cause.getMessage();
        LOGGER.warn(warnMessage);
        Future.failedFuture(warnMessage);
      });
  }

  @Override
  public Future<Integer> dataMigrationLoadData(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete) {

    return dataMigrationToLoadData(vertx, okapiHeaders, withDelete)
      .map(result -> {
        if(result.getId() == null)
          return Integer.valueOf(0);
        else
          return Integer.valueOf(SamlConfigurationUtil.samlConfiguration2Map(result).size());
      })
      .compose(num -> {
        LOGGER.info("Number of records loaded num={}", num);
        return Future.succeededFuture(num);
      });
  }

  @Override
  public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut) {

    Objects.requireNonNull(okapiHeaders);
    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
      .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
      .compose(results -> localFutureGetConfiguration(results, isPut));
  }

  private Future<SamlConfiguration> localFutureGetConfiguration(Results<SamlConfiguration> results, boolean isPut) {

    int localLength = results.getResults().size();
    if(localLength == 1) {
      return Future.succeededFuture(results.getResults().get(0));
    } else if (localLength == 0) {
      if (isPut)
        return Future.succeededFuture(new SamlConfiguration());
      else {
        String warnMessage = "There is an empty DB";
        LOGGER.warn(ERROR_MESSAGE_STRING, warnMessage);
        return Future.failedFuture(warnMessage);
      }} else {
        String errorMessage = String.format("Number of records are not unique. The number is : %s", Integer.toString(localLength));
        LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
        return Future.failedFuture(new IllegalArgumentException(errorMessage));
      }
  }

  @Override
  public Future<SamlConfiguration> storeSamlConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {

    Objects.requireNonNull(okapiHeaders);
    Objects.requireNonNull(samlConfiguration);
    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    return storeEntry(vertx, okapiHeaders, samlConfiguration);
  }

  private Future<SamlConfiguration> localStoreSamlConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {

    Objects.requireNonNull(okapiHeaders);
    Objects.requireNonNull(samlConfiguration);
    samlConfiguration.setIdsList(new ArrayList<>(0));
    return storeSamlConfiguration(vertx, okapiHeaders, samlConfiguration);
  }

  @Override
  public Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, Map<String, String> map2Update) {
    Objects.requireNonNull(okapiHeaders);

    return getConfiguration(vertx, okapiHeaders, true)
      .compose(result -> storeEntry(vertx, okapiHeaders, localUpdateSamlConfiguration(result, map2Update)));
  }

  private static SamlConfiguration localUpdateSamlConfiguration(SamlConfiguration result, Map<String, String> map2Update) {
    for (Map.Entry<String, String> entry : map2Update.entrySet()) {
      localUpdateSamlConfiguration(result, entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static void localUpdateSamlConfiguration(SamlConfiguration result, String code, String value) {
    switch(code) {
      //remark: SamlConfiguration.ID_CODE is not allowed to be updated
        case SamlConfiguration.IDP_METADATA_CODE: result.setIdpMetadata(value);
          break;
        case SamlConfiguration.IDP_URL_CODE: result.setIdpUrl(value);
          break;
        case SamlConfiguration.KEYSTORE_FILE_CODE: result.setKeystore(value);
          break;
        case SamlConfiguration.KEYSTORE_PASSWORD_CODE: result.setKeystorePassword(value);
          break;
        case SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE: result.setPrivateKeyPassword(value);
          break;
        case SamlConfiguration.METADATA_INVALIDATED_CODE: result.setMetadataInvalidated(value);
          break;
        case SamlConfiguration.OKAPI_URL: result.setOkapiUrl(value);
          break;
        case SamlConfiguration.SAML_BINDING_CODE: result.setSamlBinding(value);
          break;
        case SamlConfiguration.SAML_ATTRIBUTE_CODE: result.setSamlAttribute(value);
          break;
        case SamlConfiguration.SAML_CALLBACK: result.setCallback(value);
          break;
        case SamlConfiguration.USER_PROPERTY_CODE: result.setUserProperty(value);
          break;
        default:
          String errorMessage = String.format("Switch: Incorrect code. The code value is : %s", code);
          LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
          throw new IllegalArgumentException(errorMessage);
    }
  }

  Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {

    Objects.requireNonNull(okapiHeaders);
    Objects.requireNonNull(samlConfiguration);

    return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
      .upsert(CONFIGURATION_TABLE, samlConfiguration.getId(), samlConfiguration, true)
      .map(result -> localStoreEntry(result, samlConfiguration))
      .onFailure(e -> LOGGER.error("Configuration Storage failed: {}", e.getMessage()));
  }

  private SamlConfiguration localStoreEntry(String id, SamlConfiguration samlConfiguration) {

    if (id != null && samlConfiguration.getId() == null) {
      samlConfiguration.setId(id);
    }
    return samlConfiguration;
  }
}
