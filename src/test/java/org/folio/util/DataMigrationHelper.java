package org.folio.util;

import io.restassured.http.Header;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import java.util.Map;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.dao.impl.ConfigurationsDaoImpl;
import org.folio.rest.impl.SamlAPITest;
import org.folio.rest.impl.TestBase;
import org.folio.util.DataMigrationHelper;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;

/**
 * @author barbaraloehle
 */
public class DataMigrationHelper {
  private ConfigurationsDao configurationsDao;
  private Map<String, Header> headerHashMap = new HashMap<String, Header>(3);

  public DataMigrationHelper() {}
  
  public DataMigrationHelper(Header tenantHeader, Header tokenHeader, Header okapiUrlHeader) {
    
    configurationsDao = new ConfigurationsDaoImpl();
    headerHashMap.put("tenant", tenantHeader);
    headerHashMap.put("token", tokenHeader);
    headerHashMap.put("okapiUrl", okapiUrlHeader);
  }

  private Future<SamlConfiguration> dataMigration(Vertx vertx) {
    
    return configurationsDao.dataMigration(vertx, createOkapiHeaders());
  }

  public void dataMigrationCompleted(Vertx vertx, TestContext context) {
    
    deleteAllConfigurationRecordsCompleted(vertx, context);
    Async asyncDataMigration = context.async();
    dataMigration(vertx)
      .onComplete(result -> asyncDataMigration.complete());
    asyncDataMigration.awaitSuccess(20000);
  }
    
  private static void deleteAllConfigurationRecordsCompleted(Vertx vertx, TestContext context) {
    
    Async asyncDelete = context.async();
    TestBase.deleteAllConfigurationRecords(vertx)
      .onComplete(result -> asyncDelete.complete());
    asyncDelete.awaitSuccess(20000);
  }

  private OkapiHeaders createOkapiHeaders() {
    
    Map<String, String> parsedHeaders = new HashMap<String, String>(3);
    parsedHeaders.put(headerHashMap.get("tenant").getName(), headerHashMap.get("tenant").getValue());
    parsedHeaders.put(headerHashMap.get("token").getName(), headerHashMap.get("token").getValue());
    parsedHeaders.put(headerHashMap.get("okapiUrl").getName(), headerHashMap.get("okapiUrl").getValue());
    return OkapiHelper.okapiHeaders(parsedHeaders);
  }
}
