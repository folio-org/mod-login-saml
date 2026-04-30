package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.config.SamlConfigHolder;
import org.folio.testutil.SimpleSamlPhpContainer;
import org.folio.util.DataMigrationHelper;
import org.folio.util.MockJsonExtended;
import org.folio.util.SamlTestHelper;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpCallbackTest extends TestBase {
  private static final String TENANT = "diku";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "mytoken");
  private static final int OKAPI_PORT = TestBase.setPreferredPort(9230);
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;

  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", OKAPI_URL);
  private static final String CALLBACK = "callback";
  private static MockJsonExtended okapi;

  private static Vertx vertx;
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);

  @ClassRule
  public static final SimpleSamlPhpContainer<?> IDP =
    new SimpleSamlPhpContainer<>(OKAPI_URL, "callback");

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    vertx = Vertx.vertx();

    IDP.init();

    okapi = new MockJsonExtended();
    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));
    okapi.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(okapi, okapiOptions)
      .compose(x -> postTenantInstall(OKAPI_URL))
      .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    TestBase.dropSchema(TestBase.SCHEMA)
      .compose(x -> vertx.close())
      .onComplete(context.asyncAssertSuccess());
  }

  @After
  public void after() {
    SamlConfigHolder.getInstance().removeClient(TENANT);
    deleteAllConfigurationRecords(vertx);
  }

  @Test
  public void post(TestContext context) {
    IDP.setPostBinding();
    setOkapi("mock_idptest_post_callback.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testPost(CALLBACK);
    }
  }

  @Test
  public void redirect(TestContext context) {
    IDP.setRedirectBinding();
    setOkapi("mock_idptest_redirect_callback.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testRedirect(CALLBACK);
    }
  }

  private void setOkapi(String resource) {
    okapi.setMockContent(resource,
      s -> s.replace("http://localhost:8888/simplesaml/", IDP.getBaseUrl()));
  }
}
