package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.config.SamlConfigHolder;
import org.folio.testutil.SimpleSamlPhpContainer;
import org.folio.util.SamlTestHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpCallbackTest {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdpTest.class);
  private static final boolean DEBUG = false;
  private static final String TENANT = SamlTestHelper.TENANT;
  private static final int MODULE_PORT = SamlTestHelper.MODULE_PORT;
  private static final String OKAPI_URL = SamlTestHelper.OKAPI_URL;
  private static final String CALLBACK = "callback";
  private static Vertx VERTX;

  @ClassRule
  public static final SimpleSamlPhpContainer<?> IDP = new SimpleSamlPhpContainer<>(OKAPI_URL, CALLBACK);

  @BeforeClass
  public static void setupOnce(TestContext context)  {
    RestAssured.port = MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    VERTX = Vertx.vertx();

    if (DEBUG) {
      IDP.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
    }
    IDP.init();

    SamlTestHelper.deployVerticle(VERTX, context);
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    VERTX.close()
      .onComplete(context.asyncAssertSuccess());
  }

  @After
  public void after() {
    SamlConfigHolder.getInstance().removeClient(TENANT);
  }

  @Test
  public void postCallback() {
    IDP.setPostBinding();
    SamlTestHelper.setOkapi("mock_idptest_post_secure_tokens.json", IDP);

    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testPost(CALLBACK);
    }
  }

  @Test
  public void redirectCallback() {
    IDP.setRedirectBinding();
    SamlTestHelper.setOkapi("mock_idptest_redirect_secure_tokens.json", IDP);

    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testRedirect(CALLBACK);
    }
  }
}
