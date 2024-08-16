package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.is;

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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.folio.config.SamlConfigHolder;
import org.folio.util.DataMigrationHelper;
import org.folio.util.MockJsonExtended;
import org.folio.util.StringUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpLegacyTest extends TestBase{
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdpLegacyTest.class);
  private static final boolean DEBUG = false;
  private static final ImageFromDockerfile simplesamlphp =
      new ImageFromDockerfile().withFileFromPath(".", Path.of("src/test/resources/simplesamlphp/"));

  private static final String TENANT = "diku";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "mytoken");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";

  private static final int OKAPI_PORT = TestBase.setPreferredPort(9230);
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  private static int idpPort;
  private static String idpBaseUrl;
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", OKAPI_URL);
  private static MockJsonExtended okapi;

  private static Vertx vertx;
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);

  @ClassRule
  public static final GenericContainer<?> IDP = new GenericContainer<>(simplesamlphp)
      .withExposedPorts(8080)
      .withEnv("SIMPLESAMLPHP_SP_ENTITY_ID", OKAPI_URL + "/_/invoke/tenant/diku/saml/callback")
      .withEnv("SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE",
               OKAPI_URL + "/_/invoke/tenant/diku/saml/callback");

  @BeforeClass
  public static void setupOnce(TestContext context) throws Exception {
    RestAssured.port = modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    vertx = Vertx.vertx();

    if (DEBUG) {
      IDP.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
    }
    idpPort = IDP.getFirstMappedPort();
    idpBaseUrl = "http://" + IDP.getHost() + ":" + idpPort + "/simplesaml/";
    String baseurlpath = idpBaseUrl.replace("/", "\\/");
    exec("sed", "-i", "s/'baseurlpath' =>.*/'baseurlpath' => '" + baseurlpath + "',/",
        "/var/www/simplesamlphp/config/config.php");
    exec("sed", "-i", "s/'auth' =>.*/'auth' => 'example-static',/",
        "/var/www/simplesamlphp/metadata/saml20-idp-hosted.php");

    okapi = new MockJsonExtended();
    DeploymentOptions okapiOptions = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", OKAPI_PORT));
    okapi.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(okapi, okapiOptions)
      .compose(x -> postTenantExtendedWithToken(OKAPI_URL, PERMISSIONS_HEADER))
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
    setIdpBinding("POST");
    setOkapi("mock_idptest_post_legacy.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
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
      .body(jsonEncode("stripesUrl", STRIPES_URL))
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

    String body =
        given().
        formParams("RelayState", relayState).
        formParams("SAMLRequest", samlRequest).
        post(location).
        then().
        statusCode(200).
        body(containsString("<form method=\"post\" "),
             containsString("action=\"" + OKAPI_URL + "/_/invoke/tenant/diku/saml/callback\">")).
        extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    given().
      header("X-Okapi-Url", OKAPI_URL).
      header("X-Okapi-Tenant", "diku").
      cookie(cookie).
      formParams("RelayState", relayState).
      formParams("SAMLResponse", matcher.group(1)).
      post(moduleUrl + "/saml/callback").
    then().
      statusCode(302).
      header("x-okapi-token", "saml-token").
      header("Location", startsWith("http://localhost:3000/sso-landing?ssoToken=saml-token"));
  }

  @Test
  public void redirect(TestContext context) {
    setIdpBinding("Redirect");
    setOkapi("mock_idptest_redirect_legacy.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    for (int i = 0; i < 2; i++) {
      redirect0();
    }
  }

  private void redirect0() {
    ExtractableResponse<Response> resp =
        given().
          header(TENANT_HEADER).
          header(TOKEN_HEADER).
          header(OKAPI_URL_HEADER).
          header(JSON_CONTENT_TYPE_HEADER).
          body(jsonEncode("stripesUrl", STRIPES_URL)).
        when().
          post("/saml/login").
        then().
          statusCode(200).
          body("bindingMethod", is("GET")).
          body("location", containsString("/simplesaml/saml2/idp/SSOService.php?")).
          extract();

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

    String body =
        given().
          param(samlRequest[0], samlRequest[1]).
          param(relayState[0], relayState[1]).
        when().
          get(location).
        then().
          statusCode(200).
          body(containsString(" method=\"post\" "),
               containsString("action=\"" + OKAPI_URL + "/_/invoke/tenant/diku/saml/callback\">")).
          extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    given().
      header("X-Okapi-Url", OKAPI_URL).
      header("X-Okapi-Tenant", "diku").
      cookie(cookie).
      params("RelayState", relayState[1]).
      params("SAMLResponse", matcher.group(1)).
    when().
      post(moduleUrl + "/saml/callback").
    then().
      statusCode(302).
      header("x-okapi-token", "saml-token").
      header("Location", startsWith("http://localhost:3000/sso-landing?ssoToken=saml-token"));
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
    okapi.setMockContent(resource, s -> s.replace("http://localhost:8888/simplesaml/", idpBaseUrl));
  }

  private String jsonEncode(String key, String value) {
    return new JsonObject().put(key, value).encode();
  }
}
