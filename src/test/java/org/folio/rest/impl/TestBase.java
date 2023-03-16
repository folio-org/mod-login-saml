package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
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
  public static int PORT;//8081;
  public static WebClient webClient;
  public static TenantClient tenantClient;
  public static final String TENANT = "harvard";
  public static final String SCHEMA = TENANT + "_mod_login_saml";

  @BeforeClass
  public static void beforeAll(TestContext context) {
  PostgresClient.setPostgresTester(new PostgresTesterContainer());;

    vertx = Vertx.vertx();
    //vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(600000).setMaxEventLoopExecuteTime(600000));
    PORT = NetworkUtils.nextFreePort();
    
    WebClientOptions webClientOptions = new WebClientOptions().setDefaultPort(PORT);
    webClient = WebClient.create(vertx, webClientOptions);

    tenantClient = new TenantClient("http://localhost:" + PORT, TENANT, null, webClient);

    DeploymentOptions options = new DeploymentOptions()
	.setConfig(new JsonObject().put("http.port", PORT).put("mock", true));

    dropSchema(SCHEMA)
    .compose(x -> vertx.deployVerticle(new RestVerticle(), options))
    .compose(x -> SamlAPITest.postTenant())
    .onComplete(context.asyncAssertSuccess());
  }

    /*  @AfterClass
  public static void afterAll(TestContext context) {
    dropSchema(SCHEMA).onComplete(context.asyncAssertSuccess());
    }*/

  public static Future<Void> dropSchema(String schema) {
    PostgresClient postgresClient = PostgresClient.getInstance(vertx);
    return postgresClient.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE")
        .compose(x -> postgresClient.execute("DROP ROLE IF EXISTS " + schema))
        .mapEmpty();
  }

  static Future<Void> postTenant() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClient("http://localhost:" + PORT, TENANT, null, webClient);
      return TenantInit.exec(tenantClient, ta, 60000);
      //return TenantInit.exec(tenantClient, ta, 120000);//BL
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

}
