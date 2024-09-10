package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.RestVerticle;
import org.folio.util.MockJson;
import org.folio.util.SamlTestHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpCallbackTest {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdpTest.class);
  private static final boolean DEBUG = false;
  private static final ImageFromDockerfile simplesamlphp =
      new ImageFromDockerfile().withFileFromPath(".", Path.of("src/test/resources/simplesamlphp/"));

  private static final String TENANT = "diku";

  private static final int MODULE_PORT = 9231;
  private static final int OKAPI_PORT = 9230;
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  private static final String CALLBACK = "callback";

  private static int IDP_PORT;
  private static String IDP_BASE_URL;
  private static MockJson OKAPI;

  private static Vertx VERTX;

  @ClassRule
  public static final GenericContainer<?> IDP = new GenericContainer<>(simplesamlphp)
      .withExposedPorts(8080)
      .withEnv("SIMPLESAMLPHP_SP_ENTITY_ID", OKAPI_URL + "/_/invoke/tenant/diku/saml/callback")
      .withEnv("SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE",
               OKAPI_URL + "/_/invoke/tenant/diku/saml/callback");

  @BeforeClass
  public static void setupOnce(TestContext context)  {
    RestAssured.port = MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    VERTX = Vertx.vertx();

    if (DEBUG) {
      IDP.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
    }
    IDP_PORT = IDP.getFirstMappedPort();
    IDP_BASE_URL = "http://" + IDP.getHost() + ":" + IDP_PORT + "/simplesaml/";
    String baseurlpath = IDP_BASE_URL.replace("/", "\\/");
    exec("sed", "-i", "s/'baseurlpath' =>.*/'baseurlpath' => '" + baseurlpath + "',/",
        "/var/www/simplesamlphp/config/config.php");
    exec("sed", "-i", "s/'auth' =>.*/'auth' => 'example-static',/",
        "/var/www/simplesamlphp/metadata/saml20-idp-hosted.php");

    DeploymentOptions moduleOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MODULE_PORT)
      .put("mock", true)); // to use SAML2ClientMock

    OKAPI = new MockJson();
    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));

    VERTX.deployVerticle(new RestVerticle(), moduleOptions)
      .compose(x -> VERTX.deployVerticle(OKAPI, okapiOptions))
      .onComplete(context.asyncAssertSuccess());
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
    setIdpBinding("POST");
    setOkapi("mock_idptest_post_secure_tokens.json");

    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testPost(CALLBACK);
    }
  }

  @Test
  public void redirectCallback() {
    setIdpBinding("Redirect");
    setOkapi("mock_idptest_redirect_secure_tokens.json");

    for (int i = 0; i < 2; i++) {
      SamlTestHelper.testRedirect(CALLBACK);
    }
  }

  private void setIdpBinding(String binding) {
    // append entry at end, last entry wins
    exec("sed", "-i",
      "s/];/'SingleSignOnServiceBinding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-" + binding + "',\\n];/",
      "/var/www/simplesamlphp/metadata/saml20-idp-hosted.php");
  }

  private static void exec(String... command) {
    try {
      var result = IDP.execInContainer(command);
      if (result.getExitCode() > 0) {
        System.out.println(result.getStdout());
        System.err.println(result.getStderr());
        throw new RuntimeException("failure in IDP.execInContainer");
      }
    } catch (UnsupportedOperationException | IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void setOkapi(String resource) {
    OKAPI.setMockContent(resource, s -> s.replace("http://localhost:8888/simplesaml/", IDP_BASE_URL));
  }
}
