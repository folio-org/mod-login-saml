package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.ConfigurationsClient;
import org.folio.config.ConfigurationsClient.MissingHeaderException;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.persist.Criteria.Criterion; 
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.model.OkapiHeaders;

import java.util.Objects;

import static java.lang.String.format;

/**
 * @author barbaraloehle 
 */
public class ConfigurationsDaoImpl implements ConfigurationsDao {
	private static final Logger LOGGER = LogManager.getLogger(ConfigurationsDaoImpl.class);
  private static final String ERROR_MESSAGE_STRING = "errorMessage : %s";
  
	public static final String CONFIGURATION_TABLE = "configuration";

	private static void verifyOkapiHeaders(OkapiHeaders okapiHeaders) throws MissingHeaderException {
    
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

	private Future<SamlConfiguration> getConfigurationMigration(Vertx vertx, OkapiHeaders okapiHeaders) {
    
		Objects.requireNonNull(okapiHeaders);
		verifyOkapiHeaders(okapiHeaders);
		return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
	    .compose(results -> localFutureGetConfigurationMigration(results, vertx, okapiHeaders));
	}

	private Future<SamlConfiguration> localFutureGetConfigurationMigration(Results<SamlConfiguration> results, Vertx vertx, OkapiHeaders okapiHeaders) {
    
		int localLength = results.getResultInfo().getTotalRecords();
		if (localLength == 1) {
	    return Future.succeededFuture(results.getResults().get(0));
		}
		if(localLength > 1) {
	    String errorMessage = String.format("Migration: TotalRecords are not unique. Instead the number is : %s", Integer.toString(localLength));
	    LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
	    return Future.failedFuture(new IllegalArgumentException(errorMessage));
		}
		return ConfigurationsClient.getConfiguration(vertx, okapiHeaders)
	    .compose(result -> storeEntry(vertx, okapiHeaders, result))
      .onFailure(cause -> LOGGER.warn("There is an empty local DB : %s", cause.getMessage()));
	}

  @Override
  public Future<SamlConfiguration> dataMigration(Vertx vertx, OkapiHeaders okapiHeaders) {
    
    return getConfigurationMigration(vertx, okapiHeaders)
      .onFailure(cause -> {
          String warnMessage = "Local: Cannot load config from mod-configuration. " + cause.getMessage();
          LOGGER.warn(warnMessage);
      });
  } 
	
	@Override
	public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut) {
    
		Objects.requireNonNull(okapiHeaders);
		verifyOkapiHeaders(okapiHeaders);
		return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
	    .compose(results -> localFutureGetConfiguration(results, isPut));
	}
	
  private Future<SamlConfiguration> localFutureGetConfiguration(Results<SamlConfiguration> results, boolean isPut) {
    
		int localLength = results.getResultInfo().getTotalRecords();
		if(localLength == 1) {
			return Future.succeededFuture(results.getResults().get(0));
		} else if(localLength == 0) {
        if (isPut)
          return Future.succeededFuture(new SamlConfiguration());
        else
        {
          String warnMessage = "There is an empty DB";
          LOGGER.warn(warnMessage);
          return Future.failedFuture(warnMessage);
        }
		} else {
	    String errorMessage = String.format("TotalRecords are not unique. The number is : %s", Integer.toString(localLength));
	    LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
	    return Future.failedFuture(new IllegalArgumentException(errorMessage));
		}
	}
   
	@Override
	public Future<SamlConfiguration> storeSamlConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {
    
		Objects.requireNonNull(okapiHeaders);
		Objects.requireNonNull(samlConfiguration);
		verifyOkapiHeaders(okapiHeaders);
		return storeEntry(vertx, okapiHeaders, samlConfiguration);
	}
	
	@Override
	public Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value) {
    
		Objects.requireNonNull(okapiHeaders);
		verifyOkapiHeaders(okapiHeaders);

		return getConfiguration(vertx, okapiHeaders, false)
	    .compose(result -> {
         switch(code) {
         case SamlConfiguration.ID_CODE: result.setId(value);
           break;
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
         case SamlConfiguration.USER_PROPERTY_CODE: result.setUserProperty(value);
           break;
         default: {
           String errorMessage = String.format("Switch: Incorrect code. The code value is : %s", code);
           LOGGER.error(ERROR_MESSAGE_STRING, errorMessage);
           throw new IllegalArgumentException(errorMessage);}
         }
         return storeEntry(vertx, okapiHeaders, result);
			});
	}
    
	Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {
    
		Objects.requireNonNull(okapiHeaders);
		Objects.requireNonNull(samlConfiguration);
		verifyOkapiHeaders(okapiHeaders);

		return PostgresClient.getInstance(vertx, okapiHeaders.getTenant()) 
	    .upsert(CONFIGURATION_TABLE, samlConfiguration.getId(), samlConfiguration, true)
		  .map(result -> localStoreEntry(result, samlConfiguration))
	    .onFailure(e -> LOGGER.error("Configuration Storage failed: {}", e.getMessage()));
	}
	
	private SamlConfiguration localStoreEntry(String id, SamlConfiguration samlConfiguration) {
    
		if(id != null && samlConfiguration.getId() == null) {
			samlConfiguration.setId(id);
		}
		return samlConfiguration;
	}	
}
