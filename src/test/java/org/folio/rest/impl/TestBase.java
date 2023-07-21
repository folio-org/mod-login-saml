package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestBase {
  //Compare https://github.com/folio-org/mod-configuration/blob/master/mod-configuration-server/src/test/java/org/folio/rest/TestBase.java
  public static Vertx vertx;
  public static int MODULE_PORT;
  public static String MODULE_URL;
  public static WebClient webClient;
  public static TenantClient tenantClient;
  public static final String TENANT = "diku";
  public static final String SCHEMA = TENANT + "_mod_login_saml";
  
  @BeforeClass
  public static void beforeAll(TestContext context) {
    
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    vertx = Vertx.vertx();
    //vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(600000).setMaxEventLoopExecuteTime(600000));
    MODULE_PORT = setPreferredPort(9231);
    MODULE_URL = "http://localhost:" + MODULE_PORT;
      
    WebClientOptions webClientOptions = new WebClientOptions().setDefaultPort(MODULE_PORT);
    webClient = WebClient.create(vertx, webClientOptions);

    tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, null, webClient);

    DeploymentOptions moduleOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MODULE_PORT).put("mock", true));

    dropSchema(SCHEMA)
    .compose(x -> vertx.deployVerticle(new RestVerticle(), moduleOptions))
    .compose(x -> SamlAPITest.postTenant())
    .onComplete(context.asyncAssertSuccess());
  }

  
  @AfterClass
  public static void afterAll(TestContext context) {
    dropSchema(SCHEMA)
      .onComplete(context.asyncAssertSuccess())
      .compose(x -> vertx.close());
  }

  public static Future<RowSet<Row>> deleteAllConfigurationRecords(Vertx vertx) {
    return deleteAllConfigurationRecordsFromTable("configuration", vertx);
  }

  private static Future<RowSet<Row>> deleteAllConfigurationRecordsFromTable(String table, Vertx vertx) {
    return PostgresClient.getInstance(vertx, TENANT)
      .execute("DELETE FROM " + SCHEMA + "." + table);
  }
     
  public static Future<Void> dropSchema(String schema) {
    PostgresClient postgresClient = PostgresClient.getInstance(vertx);
    return postgresClient.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE")
      .compose(x -> postgresClient.execute("DROP ROLE IF EXISTS " + schema))
      .mapEmpty();
  }
    
  public static Future<Void> postTenant() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, null, webClient);
      return TenantInit.exec(tenantClient, ta, 60000);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

  public static int setPreferredPort(int port) {
    int localPort = port;
    if (!NetworkUtils.isLocalPortFree(localPort))
      localPort = NetworkUtils.nextFreePort();
    return localPort;
  }
}
