package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.folio.util.Base64AwareXsdMatcher.matchesBase64XsdInClasspath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
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
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.w3c.dom.ls.LSResourceResolver;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
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

  private static final String TENANT = "saml-test";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:9130");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final Header ACCESS_CONTROL_REQ_HEADERS_HEADER = new Header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toString(),
      "content-type,x-okapi-tenant,x-okapi-token");
  private static final Header ACCESS_CONTROL_REQUEST_METHOD_HEADER = new Header(
      HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toString(), "POST");
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

    mock = new HttpClientMock2("http://localhost:9130", TENANT);
    mock.setMockJsonContent("mock_content.json");

    vertx.deployVerticle(new RestVerticle(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    // Need to clear singleton to maintain test/order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
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

    SamlConfigHolder.getInstance().removeClient("TEST");

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
    ExtractableResponse<Response> resp = given()
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
      .statusCode(200)
      .extract();

    String cookie = resp.cookie(SamlAPI.RELAY_STATE);
    String relayState = resp.body().jsonPath().getString(SamlAPI.RELAY_STATE);
    assertEquals(cookie, relayState);

    // stripesUrl w/ query args
    resp = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "?foo=bar\"}")
      .post("/saml/login")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
      .body("bindingMethod", equalTo("POST"))
      .statusCode(200)
      .extract();

    cookie = resp.cookie(SamlAPI.RELAY_STATE);
    relayState = resp.body().jsonPath().getString("relayState");
    assertEquals(cookie, relayState);

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
      public Optional<RedirectionAction> getRedirectionAction(WebContext context, SessionStore sessionStore) {
        return Optional.of(new TemporaryRedirectAction());
      }
    };
    SamlConfigHolder.getInstance().findClient(TENANT).getClient()
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
  public void loginCorsTests() throws IOException {
    String origin = "http://localhost";

    log.info("=== Test CORS preflight - OPTIONS /saml/login - success ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), origin))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/login")
      .then()
      .statusCode(204)
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), equalTo(origin))
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), equalTo("true"));

    log.info("=== Test CORS preflight - OPTIONS /saml/login - failure - no origin ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/login")
      .then()
      .statusCode(400)
      .statusLine(containsString("Missing/Invalid origin header"));

    log.info("=== Test CORS preflight - OPTIONS /saml/login - failure - invalid origin ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), " "))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/login")
      .then()
      .statusCode(400)
      .statusLine(containsString("Invalid origin header"));

    log.info("=== Test CORS preflight - OPTIONS /saml/login - failure - invalid origin ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), "*"))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/login")
      .then()
      .statusCode(400)
      .statusLine(containsString("Invalid origin header"));
  }

  @Test
  public void callbackCorsTests() throws IOException {
    String origin = "http://localhost";

    log.info("=== Test CORS preflight - OPTIONS /saml/callback - success ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), origin))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/callback")
      .then()
      .statusCode(204)
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), equalTo(origin))
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), equalTo("true"));
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

    log.info("=== Setup - POST /saml/login - need relayState and cookie ===");
    ExtractableResponse<Response> resp = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + testPath + "\"}")
      .post("/saml/login")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
      .body("bindingMethod", equalTo("POST"))
      .statusCode(200)
      .extract();

    String cookie = resp.cookie(SamlAPI.RELAY_STATE);
    String relayState = resp.body().jsonPath().getString(SamlAPI.RELAY_STATE);

    log.info("=== Test - POST /saml/callback - success ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      .then()
      .statusCode(302)
      .header("Location", containsString(URLEncoder.encode(testPath, "UTF-8")))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");

    log.info("=== Test - POST /saml/callback - failure (wrong cookie) ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, "bad" + cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      .then()
      .statusCode(403);

    log.info("=== Test - POST /saml/callback - failure (wrong relay) ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState.replace("localhost", "demo"))
      .post("/saml/callback")
      .then()
      .statusCode(403);

    log.info("=== Test - POST /saml/callback - failure (no cookie) ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      .then()
      .statusCode(403);
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
        .get("/saml/configuration")
        .then()
        .statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("Cannot get configuration"));
  }


  @Test
  public void regenerateEndpointNoIdP() throws IOException {
    mock.setMockJsonContent("mock_noidp.json");

    given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .get("/saml/regenerate")
        .then()
        .statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("There is no IdP configuration stored"));
  }

  @Test
  public void regenerateEndpointNoKeystore() throws IOException {
    mock.setMockJsonContent("mock_nokeystore.json");

    given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .get("/saml/regenerate")
        .then()
        .statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("No KeyStore stored in configuration and regeneration is not allowed"));
  }

  @Test
  public void testGetValidateMissingType() {
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .queryParam("value", "http://localhost:9130")
      .get("/saml/validate")
      .then()
      .statusCode(400)
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlValidateResponse.json"))
      .body("valid", is(false))
      .body("error", is("missing type parameter"));
  }

  @Test
  public void testGetValidateMissingValue() {
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .queryParam("type", "okapiurl")
      .get("/saml/validate")
      .then()
      .statusCode(400)
      .contentType(ContentType.JSON)
      .body("valid", is(false))
      .body("error", is("missing value parameter"));
  }

  @Test
  public void testGetValidateOkapiUrl() {
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .queryParam("type", "okapiurl")
      .queryParam("value", "http://localhost:9130")
      .get("/saml/validate")
      .then()
      .statusCode(400)
      .contentType(ContentType.JSON)
      .body("valid", is(false))
      .body("error", is("unknown type: OKAPIURL"));
  }

  @Test
  public void testGetValidateIdpUrl() {
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .queryParam("type", "idpurl")
      .queryParam("value",  "http://localhost:" + MOCK_PORT + "/xml")
      .get("/saml/validate")
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("valid", is(true))
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlValidateResponse.json"));
  }

  class TemporaryRedirectAction extends RedirectionAction {
    TemporaryRedirectAction() {
      super(302);
    }
  }

}
