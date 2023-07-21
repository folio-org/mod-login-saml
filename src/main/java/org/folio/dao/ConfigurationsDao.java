package org.folio.dao;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.folio.config.model.SamlConfiguration;
import org.folio.util.model.OkapiHeaders;

/**
 * @author barbaraloehle 
 */
public interface ConfigurationsDao {

	public static final String MODULE_NAME = "LOGIN-SAML";
	public static final String CONFIG_NAME = "saml";
	
	public static final String MISSING_OKAPI_URL = "Missing Okapi URL";
	public static final String MISSING_TENANT = "Missing Tenant";
	public static final String MISSING_TOKEN = "Missing Token";

  
  public Future<SamlConfiguration> dataMigration(Vertx vertx, OkapiHeaders okapiHeaders);

	public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut);
	
	public Future<SamlConfiguration> storeSamlConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration);
	
	public Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value);
}
