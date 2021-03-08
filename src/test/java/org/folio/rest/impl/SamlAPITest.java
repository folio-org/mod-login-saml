package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.folio.util.Base64AwareXsdMatcher.matchesBase64XsdInClasspath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotEquals;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Optional;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.IdpMock;
import org.folio.util.TestingClasspathResolver;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.exception.http.TemporaryRedirectAction;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.w3c.dom.ls.LSResourceResolver;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * @author rsass
 */
@RunWith(VertxUnitRunner.class)
public class SamlAPITest {
  private static final Logger log = LogManager.getLogger(SamlAPITest.class);

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", "saml-test");
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "saml-test");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:9130");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";

  public static final int PORT = 8081;
  public static final int MOCK_PORT = NetworkUtils.nextFreePort();
  public HttpClientMock2 mock;

  private static Vertx mockVertx = Vertx.vertx();

  private Vertx vertx;

  @Rule
  public TestName testName = new TestName();

  @Before
  public void printTestMethod() {
    log.info("Running {}", testName.getMethodName());
  }

  @BeforeClass
  public static void setupOnce(TestContext context) {
    DeploymentOptions mockOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MOCK_PORT))
      .setWorker(true);
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    mockVertx.deployVerticle(IdpMock.class.getName(), mockOptions, context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    mockVertx.close();
  }

  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", PORT)
        .put(HttpClientMock2.MOCK_MODE, "true")
      );

    RestAssured.port = PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    mock = new HttpClientMock2("http://localhost:9130", "saml-test");
    mock.setMockJsonContent("mock_content.json");

    vertx.deployVerticle(new RestVerticle(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    mockVertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void checkEndpointTests() {

    // bad
    given()
      .get("/saml/check")
      .then()
      .statusCode(400);

    SamlConfigHolder.getInstance().removeClient("saml-test");

    // missing OKAPI_URL_HEADER -> "active": false
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .get("/saml/check")
      .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlCheck.json"))
      .body("active", equalTo(false));

    // good -> "active": true
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/check")
      .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlCheck.json"))
      .body("active", equalTo(true));

  }

  @Test
  public void loginEndpointTestsBad() {
    // empty body
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .post("/saml/login")
      .then()
      .statusCode(400);
  }

  @Test
  public void loginEndpointTestsGood() {
    // good
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
      .body("bindingMethod", equalTo("POST"))
      .body("relayState", equalTo(STRIPES_URL))
      .statusCode(200);

    // AJAX 401
    given()
      .header(HttpConstants.AJAX_HEADER_NAME, HttpConstants.AJAX_HEADER_VALUE)
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
      .then()
      .statusCode(401);

    // configure a wrong redirection action: TemporaryRedirectAction
    RedirectionActionBuilder redirectionActionBuilder = new RedirectionActionBuilder() {
      @Override
      public Optional<RedirectionAction> getRedirectionAction(WebContext context) {
        return Optional.of(new TemporaryRedirectAction("foo"));
      }
    };
    SamlConfigHolder.getInstance().findClient("saml-test").getClient()
    .setRedirectionActionBuilder(redirectionActionBuilder);

    // 500 internal server error
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
      .then()
      .statusCode(500);
  }

  @Test
  public void regenerateEndpointTests() throws IOException {


    LSResourceResolver resolver = new TestingClasspathResolver("schemas/");

    String metadata = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlRegenerateResponse.json"))
      .body("fileContent", matchesBase64XsdInClasspath("schemas/saml-schema-metadata-2.0.xsd", resolver))
      .statusCode(200)
      .extract().asString();

    // Update the config
    mock.setMockJsonContent("after_regenerate.json");
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
        .withIdpUrl(URI.create("http://localhost:" + MOCK_PORT + "/xml"))
        .withSamlAttribute("UserID")
        .withSamlBinding(SamlConfigRequest.SamlBinding.REDIRECT)
        .withUserProperty("externalSystemId")
        .withOkapiUrl(URI.create("http://localhost:9130"));

    String jsonString = Json.encode(samlConfigRequest);

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonString)
      .put("/saml/configuration")
      .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"));

    // Get metadata, ensure it's changed
    String regeneratedMetadata = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlRegenerateResponse.json"))
      .body("fileContent", matchesBase64XsdInClasspath("schemas/saml-schema-metadata-2.0.xsd", resolver))
      .statusCode(200)
      .extract().asString();

    assertNotEquals(metadata, regeneratedMetadata);
  }

  @Test
  public void callbackEndpointTests() throws IOException {


    final String testPath = "/test/path";

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", STRIPES_URL + testPath)
      .post("/saml/callback")
      .then()
      .statusCode(302)
      .header("Location", containsString(URLEncoder.encode(testPath, "UTF-8")))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");

  }

  @Test
  public void getConfigurationEndpoint() {

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
      .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"))
      .body("idpUrl", equalTo("https://idp.ssocircle.com"))
      .body("samlBinding", equalTo("POST"))
      .body("metadataInvalidated", equalTo(Boolean.FALSE));
  }

  @Test
  public void putConfigurationEndpoint(TestContext context) {
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + MOCK_PORT + "/xml"))
      .withSamlAttribute("UserID")
      .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
      .withUserProperty("externalSystemId")
      .withOkapiUrl(URI.create("http://localhost:9130"));

    String jsonString = Json.encode(samlConfigRequest);

    // PUT
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonString)
      .put("/saml/configuration")
      .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"));
  }

  @Test
  public void healthEndpointTests() {

    // good
    given()
      .get("/admin/health")
      .then()
      .statusCode(200);

  }

  @Test
  public void testWithConfiguration400(TestContext context) throws IOException {

    mock.setMockJsonContent("mock_400.json");

    // GET
    given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .header(JSON_CONTENT_TYPE_HEADER)
        .get("/saml/configuration")
        .then()
        .statusCode(500);
  }

}
