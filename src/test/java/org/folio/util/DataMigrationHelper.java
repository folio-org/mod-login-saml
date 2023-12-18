package org.folio.util;

import io.restassured.http.Header;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.dao.impl.ConfigurationsDaoImpl;
import org.folio.rest.impl.TestBase;
import org.folio.util.DataMigrationHelper;
import org.folio.util.model.OkapiHeaders;

/**
 * @author barbaraloehle
 */
public final class DataMigrationHelper {
  private ConfigurationsDao configurationsDao;
  private Map<String, Header> headerHashMap = new HashMap<String, Header>(3);
  private Map<String, String> parsedHeaders = new HashMap<String, String>(3);
  private OkapiHeaders okapiHeaders = new OkapiHeaders();

  public DataMigrationHelper() {}

  public DataMigrationHelper(Header tenantHeader, Header tokenHeader, Header okapiUrlHeader) {

    configurationsDao = new ConfigurationsDaoImpl();

    headerHashMap.put("tenant", tenantHeader);
    headerHashMap.put("token", tokenHeader);
    headerHashMap.put("okapiUrl", okapiUrlHeader);

    parsedHeaders.put(headerHashMap.get("tenant").getName(), headerHashMap.get("tenant").getValue());
    parsedHeaders.put(headerHashMap.get("token").getName(), headerHashMap.get("token").getValue());
    parsedHeaders.put(headerHashMap.get("okapiUrl").getName(), headerHashMap.get("okapiUrl").getValue());

    okapiHeaders = createOkapiHeaders();
  }

  private Future<SamlConfiguration> dataMigration(Vertx vertx, boolean withDelete) {

    return configurationsDao.dataMigration(vertx, okapiHeaders, withDelete);
  }

  public void dataMigrationCompleted(Vertx vertx, TestContext context, boolean withDelete) {

    deleteAllConfigurationRecordsCompleted(vertx, context);
    Async asyncDataMigration = context.async();
    dataMigration(vertx, withDelete)
      .onComplete(result -> asyncDataMigration.complete());

    asyncDataMigration.awaitSuccess(TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES));
  }

  public Map<String, String> getHeaders() {
    return parsedHeaders;
  }

  public OkapiHeaders getOkapiHeaders() {
    return okapiHeaders;
  }

  private static void deleteAllConfigurationRecordsCompleted(Vertx vertx, TestContext context) {

    Async asyncDelete = context.async();
    TestBase.deleteAllConfigurationRecords(vertx)
      .onComplete(result -> asyncDelete.complete());
    asyncDelete.awaitSuccess(TimeUnit.MILLISECONDS.convert(1L, TimeUnit.MINUTES));
  }

  private OkapiHeaders createOkapiHeaders() {
    return OkapiHelper.okapiHeaders(parsedHeaders);
  }
}
