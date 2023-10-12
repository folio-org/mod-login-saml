package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.RestVerticle;
import org.folio.util.MockJson;
import org.folio.util.SamlTestHelper;
import org.folio.util.StringUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpTest {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdpTest.class);
  private static final boolean DEBUG = false;
  private static final ImageFromDockerfile simplesamlphp =
      new ImageFromDockerfile().withFileFromPath(".", Path.of("src/test/resources/simplesamlphp/"));

  private static final String TENANT = "diku";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "mytoken");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";

  private static final int MODULE_PORT = 9231;
  private static final String MODULE_URL = "http://localhost:" + MODULE_PORT;
  private static final int OKAPI_PORT = 9230;
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;

  private static final String TEST_PATH = "/test/path";

  private static int IDP_PORT;
  private static String IDP_BASE_URL;
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", OKAPI_URL);
  private static MockJson OKAPI;

  private static Vertx VERTX;

  @ClassRule
  public static final GenericContainer<?> IDP = new GenericContainer<>(simplesamlphp)
      .withExposedPorts(8080)
      .withEnv("SIMPLESAMLPHP_SP_ENTITY_ID", OKAPI_URL + "/_/invoke/tenant/diku/saml/callback-with-expiry")
      .withEnv("SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE",
               OKAPI_URL + "/_/invoke/tenant/diku/saml/callback-with-expiry");

  @BeforeClass
  public static void setupOnce(TestContext context) throws Exception {
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
  public void post() {
    setIdpBinding("POST");
    setOkapi("mock_idptest_post.json");

    for (int i = 0; i < 2; i++) {
      post0();
    }
  }

  private void post0() {
    ExtractableResponse<Response> resp = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonEncode("stripesUrl", STRIPES_URL + TEST_PATH))
      .post("/saml/login")
      .then()
      .statusCode(200)
      .body("bindingMethod", is("POST"))
      .extract();

    String location = resp.body().jsonPath().getString("location");
    String samlRequest = resp.body().jsonPath().getString("samlRequest");
    String relayState = resp.body().jsonPath().getString(SamlAPI.RELAY_STATE);
    Cookie cookie = resp.detailedCookie(SamlAPI.RELAY_STATE);
    assertThat(cookie.getValue(), is(relayState));

    String body = given()
      .formParams("RelayState", relayState)
      .formParams("SAMLRequest", samlRequest)
      .post(location)
      .then()
      .statusCode(200)
      .body(containsString("<form method=\"post\" "),
            containsString("action=\"" + OKAPI_URL + "/_/invoke/tenant/diku/saml/callback-with-expiry\">"))
      .extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    given()
      .header("X-Okapi-Url", OKAPI_URL)
      .header("X-Okapi-Tenant", "diku")
      .cookie(cookie)
      .formParams("RelayState", relayState)
      .formParams("SAMLResponse", matcher.group(1))
      .post(MODULE_URL + "/saml/callback-with-expiry")
      .then()
      .statusCode(302)
      .header("Location", startsWith("http://localhost:3000/sso-landing"))
      .header("Location", containsString(SamlAPI.ACCESS_TOKEN_EXPIRATION))
      .header("Location", containsString(SamlAPI.REFRESH_TOKEN_EXPIRATION));
  }

  @Test
  public void redirect() {
    setIdpBinding("Redirect");
    setOkapi("mock_idptest_redirect.json");

    for (int i = 0; i < 2; i++) {
      redirect0();
    }
  }

  private void redirect0() {
    ExtractableResponse<Response> resp = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonEncode("stripesUrl", STRIPES_URL + TEST_PATH))
      .when()
      .post("/saml/login")
      .then()
      .statusCode(200)
      .body("bindingMethod", is("GET"))
      .body("location", containsString("/simplesaml/saml2/idp/SSOService.php?"))
      .extract();

    Cookie cookie = resp.detailedCookie(SamlAPI.RELAY_STATE);
    String location = resp.body().jsonPath().getString("location");
    URL url;
    try {
      url = new URL(location);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    String [] parameters = StringUtil.urlDecode(url.getQuery()).split("&", 2);
    String [] samlRequest = parameters[0].split("=", 2);
    String [] relayState = parameters[1].split("=", 2);
    location = location.substring(0, location.indexOf("?"));

    String body = given()
      .param(samlRequest[0], samlRequest[1])
      .param(relayState[0], relayState[1])
      .when()
      .get(location)
      .then()
      .statusCode(200)
      .body(containsString(" method=\"post\" "),
            containsString("action=\"" + OKAPI_URL + "/_/invoke/tenant/diku/saml/callback-with-expiry\">"))
      .extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    SamlTestHelper.testCookieResponse(cookie, relayState[1], TEST_PATH, "None", matcher.group(1),
                                      TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);
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

  private String jsonEncode(String key, String value) {
    return new JsonObject().put(key, value).encode();
  }
}
