package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
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

//import java.util.ArrayList;
//import java.util.List;
import java.util.concurrent.TimeUnit;
import io.vertx.core.VertxOptions;

public class TestBase {//contained in "mock_content_with_delete.json"
  //Compare https://github.com/folio-org/mod-configuration/blob/master/mod-configuration-server/src/test/java/org/folio/rest/TestBase.java

  public static Vertx vertx;
  public static int MODULE_PORT;
  public static String MODULE_URL;
  public static WebClient webClient;
  public static TenantClient tenantClient;
  //public static final String TENANT = "harvard";
  public static final String TENANT = "diku";
  public static final String SCHEMA = TENANT + "_mod_login_saml";
  public String localClassName = null;

  @BeforeClass
  public static void beforeAll(TestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    //vertx = Vertx.vertx();
    vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(TimeUnit.MILLISECONDS.convert(150L, TimeUnit.MINUTES))
    .setMaxEventLoopExecuteTime(TimeUnit.NANOSECONDS.convert(200L, TimeUnit.MINUTES)));

    MODULE_PORT = NetworkUtils.nextFreePort();//setPreferredPort(9231);
    MODULE_URL = "http://localhost:" + MODULE_PORT;

    WebClientOptions webClientOptions = new WebClientOptions().setDefaultPort(MODULE_PORT);
    webClient = WebClient.create(vertx, webClientOptions);

    DeploymentOptions moduleOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MODULE_PORT).put("mock", true));

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

  public static Future<Void> postTenant() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, null, webClient);
      return TenantInit.exec(tenantClient, ta, 600);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

  public static Future<Void> postTenantWithToken() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, TENANT, webClient);
      //TenantClient tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, TENANT, true, 1000, 10000); //deprecated
      //TenantClient tenantClient = new TenantClient("http://localhost:" + MODULE_PORT, TENANT, TENANT, true); //deprecated
      return TenantInit.exec(tenantClient, ta, 600);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

  public static Future<Void> postTenantExtendedWithToken(String okapiUrlTo, String permissions) {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClientExtended("http://localhost:" + MODULE_PORT, okapiUrlTo,
        TENANT, TENANT, permissions, webClient);
      return TenantInit.exec(tenantClient, ta, 600);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

  public static void postTenantWithTokenCompleted(TestContext context) {
    Async async = context.async();
    postTenantWithToken()
      .onComplete(context.asyncAssertSuccess(result -> async.complete()));
    async.awaitSuccess(TimeUnit.MILLISECONDS.convert(120, TimeUnit.SECONDS));
  }

  public static int setPreferredPort(int port) {
    int localPort = port;
    if (!NetworkUtils.isLocalPortFree(localPort))
      localPort = NetworkUtils.nextFreePort();
    return localPort;
  }
}
