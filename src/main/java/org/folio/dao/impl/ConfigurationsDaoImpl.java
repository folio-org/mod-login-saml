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
 	Promise<Results<SamlConfiguration>> promise = Promise.promise();
	PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion().addCriterion(new Criteria().addField("idp.url")), true, promise);
	return promise.future().compose(results -> localFutureMapMigration(results, vertx, okapiHeaders));
    }


    private Future<SamlConfiguration> localFutureMapMigration(Results<SamlConfiguration> results, Vertx vertx, OkapiHeaders okapiHeaders){
       int localLength = results.getResultInfo().getTotalRecords();
        if(localLength == 1){
	   return Future.succeededFuture(results.getResults().get(0));
       } else if(localLength == 0) {
	    return ConfigurationsClient.getConfiguration(vertx, okapiHeaders)
		.onComplete(result -> {
			if (result.succeeded()) {
			    LOGGER.warn("We have a result: " + result.result().toString());
			    Future.succeededFuture(result)
			    .compose(composedResult -> storeEntry(vertx, okapiHeaders, result.result())
				     .onSuccess(composedResult1 -> Future.succeededFuture(composedResult))
				     .onFailure(composedResult1 -> Future.failedFuture("Cannot save config from mod-configuration in DB."))
				     );
			} else {
			    LOGGER.warn("Cannot load config from mod-configuration.");
			    Future.failedFuture("Cannot load config from mod-configuration.");
			}
		    });
	} else {
	    String errorMessage = String.format("TotalRecords are not unique. Instead the number is : %s", Integer.toString(localLength));
	    LOGGER.error("errorMessage", errorMessage);
	    return Future.failedFuture(new IllegalArgumentException(errorMessage));
	    }
       
   }
    
    @Override
    public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut) {
	Objects.requireNonNull(okapiHeaders);
	verifyOkapiHeaders(okapiHeaders);
 	Promise<Results<SamlConfiguration>> promise = Promise.promise();
	PostgresClient.getInstance(vertx, okapiHeaders.getTenant())
	    .get(CONFIGURATION_TABLE, SamlConfiguration.class, new Criterion().addCriterion(new Criteria().addField("idp.url")), true, promise);
	return promise.future().compose(results -> localFutureMap(results, vertx, okapiHeaders, isPut));
    }


    private Future<SamlConfiguration> localFutureMap(Results<SamlConfiguration> results, Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut){
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
    public Future<SamlConfiguration> storeEntries(Vertx vertx, OkapiHeaders  okapiHeaders, Map<String, String> entries) {
	Objects.requireNonNull(okapiHeaders);
	Objects.requireNonNull(entries);
	verifyOkapiHeaders(okapiHeaders);

	SamlConfiguration samlConfigurationLocal = new ObjectMapper().convertValue(entries, SamlConfiguration.class);
	return storeEntry(vertx, okapiHeaders, samlConfigurationLocal);
    }

    @Override
    public Future<SamlConfiguration> storeEntryMetadataInvaildated(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value){
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
	return promise.future().compose(result -> localFutureMapStoreEntry(result, samlConfiguration));
	} 

    
    private Future<SamlConfiguration> localFutureMapStoreEntry(String result, SamlConfiguration samlConfiguration){	
	    if(result != null && samlConfiguration.getId() == null){
		samlConfiguration.setId(result);
	    }
	    return Future.succeededFuture(samlConfiguration);
    }	
    
  
}
