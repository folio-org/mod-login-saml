package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.ext.unit.Async;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantInit;
import org.folio.util.TenantClientExtended;
import org.folio.util.MockJson;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * @author barabaraloehle
 */
@RunWith(VertxUnitRunner.class)
public class TenantRefAPITest extends TestBase {
  private static final Logger log = LogManager.getLogger(TenantRefAPITest.class);

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");

  private final int jsonMockPort = TestBase.MODULE_PORT;
  private final Header okapiUrlHeader = new Header("X-Okapi-Url", "http://localhost:" + jsonMockPort);
  //private final int JSON_MOCK_PORT = TestBase.MODULE_PORT; //NetworkUtils.nextFreePort();
  //private final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + JSON_MOCK_PORT);

  private static MockJson mock = new MockJson();

  @Rule
  public TestName testName = new TestName();
  public final String LOCALHOST_ORIGIN = "http://localhost";

  @Before
  public void setupOnce(TestContext context) {
    log.info("Running {}", testName.getMethodName());
    RestAssured.port = TestBase.MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", jsonMockPort));

    mock.setMockContent("mock_content_with_delete.json");
    vertx.deployVerticle(mock, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
    //deleteAllConfigurationRecords(vertx);
  }

  @Test
  public void loadDataWithMock(TestContext context) {
    postTenantExtendedWithTokenCompleted(context);

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(okapiUrlHeader)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
      .then()
      .statusCode(200)

      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"))
      .body("idpUrl", equalTo("https://idp.ssocircle.com"))
      .body("samlBinding", equalTo("POST"))
      .body("metadataInvalidated", equalTo(Boolean.FALSE));
  }

  private void postTenantExtendedWithTokenCompleted(TestContext context) {
    Async asyncDataMigration = context.async();
    postTenantExtendedWithToken()
      .onComplete(result -> asyncDataMigration.complete());
    asyncDataMigration.awaitSuccess();
  }

  private Future<Void> postTenantExtendedWithToken() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-login-saml-2.0");
      TenantClient tenantClient = new TenantClientExtended("http://localhost:" + TestBase.MODULE_PORT,
        "http://localhost:" + jsonMockPort, TENANT, TENANT, webClient);
      return TenantInit.exec(tenantClient, ta, 6000);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

}
