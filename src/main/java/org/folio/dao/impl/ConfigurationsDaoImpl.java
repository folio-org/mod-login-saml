package org.folio.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.ConfigurationsClient;
import org.folio.config.ConfigurationsClient.MissingHeaderException;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.persist.Criteria.Criteria; 
import org.folio.rest.persist.Criteria.Criterion; 
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.model.OkapiHeaders;

import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;

/**
 * @author barbaraloehle 
 */
public class ConfigurationsDaoImpl implements ConfigurationsDao {
    private static final Logger LOGGER = LogManager.getLogger(ConfigurationsDaoImpl.class);

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
    public ConfigurationsDaoImpl() {}


    @Override
    public Future<SamlConfiguration> getConfigurationMigration(Vertx vertx, OkapiHeaders okapiHeaders) {
	Objects.requireNonNull(okapiHeaders);
	verifyOkapiHeaders(okapiHeaders);
 	return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
	    .compose(results -> localFutureGetConfigurationMigration(results, vertx, okapiHeaders));
    }

    private Future<SamlConfiguration> localFutureGetConfigurationMigration(Results<SamlConfiguration> results, Vertx vertx, OkapiHeaders okapiHeaders){
	int localLength = results.getResultInfo().getTotalRecords();
	if (localLength == 1){
	    return Future.succeededFuture(results.getResults().get(0));
	}
	if(localLength > 1) {
	    String errorMessage = String.format("TotalRecords are not unique. Instead the number is : %s", Integer.toString(localLength));
	    LOGGER.error("errorMessage", errorMessage);
	    return Future.failedFuture(new IllegalArgumentException(errorMessage));
	}
      return ConfigurationsClient.getConfiguration(vertx, okapiHeaders)
          .compose(result -> storeEntry(vertx, okapiHeaders, result))
	  .onFailure(e -> LOGGER.error("Configuration migration failed: {}", e.getMessage()));
    }
    
     
    @Override
    public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut) {
	Objects.requireNonNull(okapiHeaders);
	verifyOkapiHeaders(okapiHeaders);
	return PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion(), true)
	    .compose(results -> localFutureGetConfiguration(results, vertx, okapiHeaders, isPut));
    }


    private Future<SamlConfiguration> localFutureGetConfiguration(Results<SamlConfiguration> results, Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut){
       int localLength = results.getResultInfo().getTotalRecords();
        if(localLength == 1){
	   return Future.succeededFuture(results.getResults().get(0));
	} else if(localLength == 0) {
	    if (isPut)
		return Future.succeededFuture(new SamlConfiguration() );
	    else
		return Future.failedFuture("There is an empty DB");
	} else {
	    String errorMessage = String.format("TotalRecords are not unique. Instead the number is : %s", Integer.toString(localLength));
	    LOGGER.error("errorMessage", errorMessage);
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
    public Future<SamlConfiguration> storeEntryMetadataInvalidated(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value){
	Objects.requireNonNull(okapiHeaders);
	verifyOkapiHeaders(okapiHeaders);
	
	return getConfiguration(vertx, okapiHeaders, false)
	    .onSuccess(result -> {
		    result.setMetadataInvalidated(value);	
		    storeEntry(vertx, okapiHeaders, result);
		});
    }
    
     private Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration) {
	Objects.requireNonNull(okapiHeaders);
	Objects.requireNonNull(samlConfiguration);
	verifyOkapiHeaders(okapiHeaders);
	
	Promise<String> promise = Promise.promise();
	PostgresClient.getInstance(vertx, okapiHeaders.getTenant()) 
	    .upsert(CONFIGURATION_TABLE, samlConfiguration.getId(), samlConfiguration, true, promise);
	return promise.future().map(result -> localFutureMapStoreEntry(result, samlConfiguration))
	    .onFailure(e -> LOGGER.error("Configuration Storage failed: {}", e.getMessage()));
	} 

       private SamlConfiguration localFutureMapStoreEntry(String result, SamlConfiguration samlConfiguration){	
	    if(result != null && samlConfiguration.getId() == null){
		samlConfiguration.setId(result);
	    }
	    return samlConfiguration;
    }	
    
  
}
