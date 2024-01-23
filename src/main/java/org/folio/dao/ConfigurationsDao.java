package org.folio.dao;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.folio.config.model.SamlConfiguration;
import org.folio.util.model.OkapiHeaders;

/**
 * @author barbaraloehle
 */
public interface ConfigurationsDao {

  public static final String MISSING_OKAPI_URL = "Missing Okapi URL";
  public static final String MISSING_TENANT = "Missing Tenant";
  public static final String MISSING_TOKEN = "Missing Token";


  public Future<SamlConfiguration> dataMigration(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete);

  public Future<Integer> dataMigrationLoadData(Vertx vertx, OkapiHeaders okapiHeaders, boolean withDelete);

  public Future<SamlConfiguration> getConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, boolean isPut);

  public Future<SamlConfiguration> storeSamlConfiguration(Vertx vertx, OkapiHeaders okapiHeaders, SamlConfiguration samlConfiguration);

  public Future<SamlConfiguration> storeEntry(Vertx vertx, OkapiHeaders okapiHeaders, String code, String value);

  public static void verifyOkapiHeaders(OkapiHeaders okapiHeaders) throws MissingHeaderException {
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

  public static class MissingHeaderException extends RuntimeException {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }

}
