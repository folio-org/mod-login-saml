package org.folio.rest.impl;

import java.util.Map;
import javax.ws.rs.core.Response;
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
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.folio.util.TenantClientGeneralized;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestBase {//contained in "mock_content_with_delete.json"
  //Compare https://github.com/folio-org/mod-configuration/blob/master/mod-configuration-server/src/test/java/org/folio/rest/TestBase.java

  public static Vertx vertx;
  public static int modulePort;
  public static String moduleUrl;
  public static WebClient webClient;
  public static final String TENANT = "diku";
  public static final String TOKEN = "token";
  public static final String SCHEMA = TENANT + "_mod_login_saml";
  public static final String PERMISSIONS_HEADER = TENANT + "-permissons"; //for testing org.folio.util.model.OkapiHeaders.java
  protected static final TenantAttributes TENANT_ATTRIBUTES_INSTALLATION = new TenantAttributes()
    .withModuleTo("mod-login-saml-2.1.99");
  protected static final TenantAttributes TENANT_ATTRIBUTES_UPGRADE = new TenantAttributes()
    .withModuleTo("mod-login-saml-2.1.99").withModuleFrom("mod-login-saml-2.0.0");

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

  // compare https://github.com/folio-org/mod-permissions/blob/c0e893323c2254c2fedd4a0122abd5f121fec4d4/src/test/java/org/folio/permstest/TestUtil.java#L145
  public static Future<Response> tenantInitExec(Vertx vertx, TenantAttributes ta, Map<String, String> header) {
    return Future.future(handler -> new TenantRefAPI().postTenantSync(ta, header, handler, vertx.getOrCreateContext()));
  }

  public static Future<Void> postTenant(TenantAttributes ta, String okapiUrl) {
    try {
      TenantClient tenantClient = new TenantClientGeneralized("http://localhost:" + modulePort, okapiUrl, TENANT, TENANT, PERMISSIONS_HEADER, webClient);
      return TenantInit.exec(tenantClient, ta, 60000);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

  public static Future<Void> postTenantInstall(TenantAttributes taInstall, String okapiUrl) {
    return postTenant(taInstall, okapiUrl);
  }

  public static Future<Void> postTenantUpgrade(TenantAttributes taUpgrade, String okapiUrl) {
    return postTenant(taUpgrade, okapiUrl);
  }

  public static int setPreferredPort(int port) {
    int localPort = port;
    if (!NetworkUtils.isLocalPortFree(localPort))
      localPort = NetworkUtils.nextFreePort();
    return localPort;
  }
}
