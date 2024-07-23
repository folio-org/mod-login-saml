package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.TenantClient;
import org.folio.util.TenantClientExtended;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestBase {//contained in "mock_content_with_delete.json"
  //Compare https://github.com/folio-org/mod-configuration/blob/master/mod-configuration-server/src/test/java/org/folio/rest/TestBase.java

  public static Vertx vertx;
  public static int modulePort;
  public static String moduleUrl;
  public static WebClient webClient;
  public static TenantClient tenantClient;
  public static final String TENANT = "diku";
  public static final String SCHEMA = TENANT + "_mod_login_saml";
  public static final String PERMISSIONS_HEADER = TENANT + "-permissons"; //for testing org.folio.util.model.OkapiHeaders.java
  public String localClassName = null;

  @BeforeClass
  public static void beforeAll(TestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    vertx = Vertx.vertx();

    modulePort = NetworkUtils.nextFreePort();
    moduleUrl = "http://localhost:" + modulePort;

    WebClientOptions webClientOptions = new WebClientOptions().setDefaultPort(modulePort);
    webClient = WebClient.create(vertx, webClientOptions);

    DeploymentOptions moduleOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", modulePort).put("mock", true));

    vertx.deployVerticle(new RestVerticle(), moduleOptions)
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

  public static Future<Void> postTenantExtendedWithToken(String okapiUrlTo, String permissions) {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.1");
      TenantClient tenantClient = new TenantClientExtended("http://localhost:" + modulePort, okapiUrlTo,
        TENANT, TENANT, permissions, webClient);
      return TenantInit.exec(tenantClient, ta, 600);
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
