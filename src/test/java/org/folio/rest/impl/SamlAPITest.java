package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.folio.util.Base64AwareXsdMatcher.matchesBase64XsdInClasspath;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Map;

import io.restassured.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.service.UserService;
import org.folio.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.BasicUserProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.pac4j.saml.client.SAML2Client;
import org.w3c.dom.ls.LSResourceResolver;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.matcher.RestAssuredMatchers;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 */
@RunWith(VertxUnitRunner.class)
public class SamlAPITest extends TestBase {
  public static final String CALLBACK_URL = "/saml/callback";
  public static final String CALLBACK_WITH_EXPIRY_URL = "/saml/callback-with-expiry";
  private static final Logger log = LogManager.getLogger(SamlAPITest.class);
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final Header ACCESS_CONTROL_REQ_HEADERS_HEADER = new Header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toString(),
    "content-type,x-okapi-tenant,x-okapi-token");
  private static final Header ACCESS_CONTROL_REQUEST_METHOD_HEADER = new Header(
    HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toString(), "POST");
  private static final String STRIPES_URL = "http://localhost:3000";

  public static final int IDP_MOCK_PORT = NetworkUtils.nextFreePort();
  private static final int MOCK_SERVER_PORT = NetworkUtils.nextFreePort();
  private static final int OKAPI_PROXY_PORT = NetworkUtils.nextFreePort();
  private static final Header OKAPI_URL_HEADER= new Header("X-Okapi-Url", "http://localhost:" + MOCK_SERVER_PORT);
  private static final Header OKAPI_PROXY_URL_HEADER=
      new Header("X-Okapi-Url", "http://localhost:" + OKAPI_PROXY_PORT + "/okapi");

  private static final MockJsonExtended mock = new MockJsonExtended();
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);

  @ClassRule
  public static WireMockClassRule okapiProxy = new WireMockClassRule(OKAPI_PROXY_PORT);

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = TestBase.modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    okapiProxy.stubFor(any(urlMatching("/okapi/.*"))
        .willReturn(aResponse()
            .proxiedFrom("http://localhost:" + MOCK_SERVER_PORT)
            .withProxyUrlPrefixToRemove("/okapi")));

    DeploymentOptions idpOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", IDP_MOCK_PORT));

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MOCK_SERVER_PORT));

    mock.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(IdpMock.class.getName(), idpOptions)
      .compose(x -> vertx.deployVerticle(mock, okapiOptions))
      .compose(x -> postTenantInstall("http://localhost:" + MOCK_SERVER_PORT))
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp(TestContext context) {
    log.info("Running {}", testName.getMethodName());
    mock.setMockContent("mock_content.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
    deleteAllConfigurationRecords(vertx);
  }

  @Test
  public void checkEndpointTests() {
    // bad
    given()
      .get("/saml/check")
      .then()
      .statusCode(400);

    SamlConfigHolder.getInstance().removeClient("TEST");

    // missing OKAPI_URL_HEADER
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .get("/saml/check")
      .then()
      .statusCode(400);

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
  public void loginEndpointTestsGoodDB() { //former method: void loginEndpointTestsGood
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
    assertThat(cookie, endsWith(relayState));

    SAML2Client saml2client = SamlConfigHolder.getInstance().findClient(TENANT).getClient();

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
    assertThat(cookie, endsWith(relayState));
    // saml2client should have been reused
    assertEquals(saml2client, SamlConfigHolder.getInstance().findClient(TENANT).getClient());

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
    saml2client = SamlConfigHolder.getInstance().findClient(TENANT).getClient();
    saml2client.setRedirectionActionBuilder(redirectionActionBuilder);

    // fails internally, drops the client, and the retry should result in 200
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
      .then()
      .statusCode(200);

    assertNotEquals(saml2client, SamlConfigHolder.getInstance().findClient(TENANT).getClient());
  }


  @Test
  public void loginCorsTests() {
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

  private void assertCallbackSuccess(TestContext context, String testTitle, String mockFile, String path) {
    log.info(testTitle);

    mock.setMockContent(mockFile);
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    given()
      .header(HttpHeaders.ORIGIN.toString(), "http://localhost")
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .contentType(ContentType.URLENC)
      .cookie(SamlAPI.RELAY_STATE, readResourceToString("relay_state.txt"))
      .body(readResourceToString("saml_response.txt"))
      .post(path)
      .then()
      .statusCode(302);
  }

  @Test
  public void callbackIdpMetadataTest_LegacyDB(TestContext context) {//former method: void  callbackIdpMetadataTest_Legac()
    assertCallbackSuccess(context,
        "=== Test Callback with right metadata - POST /saml/callback - success ===",
        "mock_content_with_metadata_legacy.json",
        "/saml/callback");
  }

  @Test
  public void callbackIdpMetadataTestDB(TestContext context) {
    assertCallbackSuccess(context,
        "=== Test Callback with right metadata - POST /saml/callback-with-expiry - success ===",
        "mock_content_with_metadata.json",
        "/saml/callback-with-expiry");
  }

  @Test
  public void callbackIdpMetadataHttp2Test_Legacy(TestContext context) {
    mock.setMockContent("mock_content_with_metadata_legacy.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    WebClient.create(vertx)
    .post(modulePort, "localhost", "/saml/callback")
    .putHeader("X-Okapi-Token", TENANT)
    .putHeader("X-Okapi-Tenant", TENANT)
    .putHeader("X-Okapi-Url", "http://localhost:" + MOCK_SERVER_PORT)
    .putHeader("Content-Type", "application/x-www-form-urlencoded")
    .putHeader("Cookie", SamlAPI.RELAY_STATE + "=" + readResourceToString("relay_state.txt").trim())
    .sendBuffer(Buffer.buffer(readResourceToString("saml_response.txt").trim()))
    .onComplete(context.asyncAssertSuccess(response -> {
      assertThat(response.statusMessage() + "\n" + response.bodyAsString(), response.statusCode(), is(302));
    }));
  }

  @Test
  public void callbackIdpMetadataHttp2Test(TestContext context) {
    mock.setMockContent("mock_content_with_metadata.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    WebClient.create(vertx)
      .post(modulePort, "localhost", "/saml/callback-with-expiry")
      .putHeader("X-Okapi-Token", TENANT)
      .putHeader("X-Okapi-Tenant", TENANT)
      .putHeader("X-Okapi-Url", "http://localhost:" + MOCK_SERVER_PORT)
      .putHeader("Content-Type", "application/x-www-form-urlencoded")
      .putHeader("Cookie", SamlAPI.RELAY_STATE + "=" + readResourceToString("relay_state.txt").trim())
      .sendBuffer(Buffer.buffer(readResourceToString("saml_response.txt").trim()))
      .onComplete(context.asyncAssertSuccess(response -> {
        assertThat(response.statusMessage() + "\n" + response.bodyAsString(), response.statusCode(), is(302));
      }));
  }

  @Test
  public void callbackCorsTests_Legacy() {
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
  public void callbackCorsTests() {
    String origin = "http://localhost";

    log.info("=== Test CORS preflight - OPTIONS /saml/callback - success ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), origin))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/callback-with-expiry")
      .then()
      .statusCode(204)
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), equalTo(origin))
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), equalTo("true"));
  }

  @Test
  public void getSamlAttributeValue() {
    UserProfile userProfile = new BasicUserProfile();
    userProfile.addAttribute("UserID", "foo");
    userProfile.addAttribute("uid", "bar");
    userProfile.addAttribute("username", List.of("baz"));
    userProfile.addAttribute("name", List.of("x", "a", "z"));
    userProfile.addAttribute("ohoh", null);
    userProfile.addAttribute("ohohlist", List.of());

    assertThat(UserService.getSamlAttributeValue(null, userProfile), is("foo"));
    assertThat(UserService.getSamlAttributeValue("uid", userProfile), is("bar"));
    assertThat(UserService.getSamlAttributeValue("username", userProfile), is("baz"));
    assertThat(UserService.getSamlAttributeValue("name", userProfile), is("x"));
    assertThrows(UserService.UserErrorException.class, () -> UserService.getSamlAttributeValue("ohoh", userProfile));
    assertThrows(UserService.UserErrorException.class, () -> UserService.getSamlAttributeValue("ohohlist", userProfile));
  }

  @Test
  public void testPutSamlConfigurationDB(TestContext context) { //former method: void testPutSamlConfiguration()
    mock.setMockContent("mock_nokeystore.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
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
  }

  @Test
  public void regenerateEndpointTestsDB(TestContext context) { //former method: regenerateEndpointTests()
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
    mock.setMockContent("after_regenerate.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
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
  public void callbackEndpointTests_LegacyDB(TestContext context) {//former method: void callbackEndpointTests_Legacy()
    mock.setMockContent("mock_content_legacy.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
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
      .header("Location", containsString(PercentCodec.encodeAsString(testPath)))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");

    testCallbackErrorCases(CALLBACK_URL, relayState, cookie);

    mock.setMockContent("mock_tokenresponse.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
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
      .header("Location", containsString(PercentCodec.encodeAsString(testPath)))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");
  }

  @Test
  public void callbackForConsortiumWithMultipleMatchingUserTenant(TestContext context) {
    String origin = "http://localhost";

    log.info("=== Test Callback for enabled consortium with multiple matching userTenant - success ===");

    mock.setMockContent("mock_multiple_user_tenant.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), origin))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .contentType(ContentType.URLENC)
      .cookie(SamlAPI.RELAY_STATE, readResourceToString("relay_state.txt"))
      .body(readResourceToString("saml_response.txt"))
      .post("/saml/callback")
      .then()
      .statusCode(302)
      .header("x-okapi-token", "new-saml-token")
      .cookie("ssoToken", "new-saml-token");
  }

  @Test
  public void callbackForConsortium(TestContext context) {
    assertCallbackSuccess(context,
        "=== Test Callback for enabled consortium - success ===",
        "mock_one_user_tenant.json",
        "/saml/callback-with-expiry");
  }

  @Test
  public void callbackEndpointTests(TestContext context) {
    // Default. No configuration needed. /saml/callback-with-expiry returns RTR tokens.
    testCallback(CALLBACK_WITH_EXPIRY_URL);
  }

  @Test
  public void callbackEndpointTestsUseSecureTokens() {
    // Configuration needed. /saml/callback returns RTR tokens allowing existing metadata to be used.
    mock.setMockContent("mock_content_secure_tokens.json");
    testCallback(CALLBACK_URL);
  }

  private void testCallback(String callbackUrl) {    final String testPath = "/test/path";

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
    Cookie detailedCookie = resp.detailedCookie(SamlAPI.RELAY_STATE);
    String relayState = resp.body().jsonPath().getString(SamlAPI.RELAY_STATE);
    String samlResponse = "saml-response";

    log.info("=== Test - POST /saml/callback-with-expiry - success ===");
    SamlTestHelper.testCookieResponse(detailedCookie, relayState, testPath, CookieSameSite.LAX.toString(),
                                      samlResponse, TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER, callbackUrl);

    CookieSameSiteConfig.set(Map.of("LOGIN_COOKIE_SAMESITE", CookieSameSite.NONE.toString()));
    SamlTestHelper.testCookieResponse(detailedCookie, relayState, testPath, CookieSameSite.NONE.toString(),
                                      samlResponse, TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER, callbackUrl);
    CookieSameSiteConfig.set(Map.of());

    log.info("=== Test - POST /saml/callback-with-expiry with okapi path ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_PROXY_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", samlResponse)
      .formParam("RelayState", relayState)
      .post("/saml/callback-with-expiry")
      .then()
      .statusCode(302)
      .cookie("folioRefreshToken", RestAssuredMatchers.detailedCookie().path("/okapi/authn"))
      .cookie("folioAccessToken", RestAssuredMatchers.detailedCookie().path("/okapi/"));

    testCallbackErrorCases(callbackUrl, relayState, cookie);
  }

  void postSamlLogin(int expectedStatus) {
    given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .header(JSON_CONTENT_TYPE_HEADER)
        .body(new JsonObject().put("stripesUrl", STRIPES_URL).encode())
        .post("/saml/login")
        .then()
        .statusCode(expectedStatus);
  }

  @Test
  public void reloadBogusMetadataDB(TestContext context) { //former method: void reloadBogusMetadata()
    mock.setMockContent("mock_metadata_bogus.json", s -> s.replace(":8888", ":" + MOCK_SERVER_PORT));
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
    postSamlLogin(500);

    // Check that the bogus IdP metadata is not cached after internal server error
    // https://issues.folio.org/browse/MODLOGSAML-107
    mock.setMockContent("mock_content.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
    postSamlLogin(200);
  }

  @Test
  public void getConfigurationEndpointDB(TestContext context) { //former method: getConfigurationEndpoint()
    // GET (Data from DB)
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
  public void getConfigurationEndpointLegacy(TestContext context) {
    mock.setMockContent("mock_content_legacy.json"); // Should not contain useSecureTokens in config. This is legacy mode.
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
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
      .body("callback", equalTo("callback"))
      .body("useSecureTokens", equalTo(Boolean.FALSE))
      .body("metadataInvalidated", equalTo(Boolean.FALSE));
  }

  @Test
  public void getConfigurationEndpointUseSecureTokens(TestContext context) {
    mock.setMockContent("mock_content_secure_tokens.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);
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
      .body("callback", equalTo("callback"))
      .body("useSecureTokens", equalTo(Boolean.TRUE))
      .body("metadataInvalidated", equalTo(Boolean.FALSE));
  }

  @Test
  public void putConfigurationEndpoint(TestContext context) {
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
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
  public void putConfigurationUseSecureTokens() {
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
      .withSamlAttribute("UserID")
      .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
      .withUserProperty("externalSystemId")
      .withOkapiUrl(URI.create("http://localhost:9130"))
      .withCallback("callback")
      .withUseSecureTokens(true);

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
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"))
      .body("callback", equalTo("callback"))
      .body("useSecureTokens", equalTo(Boolean.TRUE));
  }

  @Test
  public void putConfigurationWithIdpMetadataDB(TestContext context) { //former method: putConfigurationWithIdpMetadata(TestContext context)
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
      .withSamlAttribute("UserID")
      .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
      .withUserProperty("externalSystemId")
      .withIdpMetadata(readResourceToString("meta_test.xml"))
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
  public void putConfiguration_Legacy() {
    mock.setMockContent("mock_content_legacy.json");

    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
      .withSamlAttribute("UserID")
      .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
      .withUserProperty("externalSystemId")
      .withOkapiUrl(URI.create("http://localhost:9130"))
      .withCallback("callback");

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
      .body("callback", equalTo("callback"))
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"))
      .body("callback", equalTo("callback"))
      .body("useSecureTokens", equalTo(Boolean.FALSE));
  }

  @Test
  public void putConfiguration() {
    mock.setMockContent("mock_content.json");

    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml"))
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

  private String readResourceToString(String idpMetadataFile) {
    try {
      return IOUtils.toString(Objects
        .requireNonNull(getClass().getClassLoader()
          .getResourceAsStream(idpMetadataFile)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
  public void testWithConfigurationNoUser(TestContext context) { //former method: void testPutSamlConfiguration() with mock_400.json
    // GET                                                       //compare in Class configurationsDaoImplTest: DataMigrationWithoutData

    mock.setMockContent("mock_nouser_db.json"); //a user without login has noc access to the db.
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

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
  public void regenerateEndpointNoIdPDB(TestContext context) { //former method: void regenerateEndpointNoIdP()
    mock.setMockContent("mock_noidp.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

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
  public void regenerateEndpointNoKeystoreDB(TestContext context) { //former method: void regenerateEndpointNoKeystore()
    mock.setMockContent("mock_nokeystore.json");
    dataMigrationHelper.dataMigrationCompleted(vertx, context, false);

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
      .queryParam("value",  "http://localhost:" + IDP_MOCK_PORT + "/xml")
      .get("/saml/validate")
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("valid", is(true))
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlValidateResponse.json"));
  }

  @Test
  public void testGetValidateBadIdp() {
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .queryParam("type", "idpurl")
      .queryParam("value",  "http://localhost:" + MOCK_SERVER_PORT + "/xml")
      .get("/saml/validate")
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("valid", is(false))
      .body("error", startsWith("Content-Type response header media type must be"))
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlValidateResponse.json"));
  }

  class TemporaryRedirectAction extends RedirectionAction {
    private static final long serialVersionUID = 7340537453740028328L;

    TemporaryRedirectAction() {
      super(302);
    }
  }

  @Test
  public void getCqlUserQuery() {
    assertEquals("personal.email==\"user@saml.com\"",
      UserService.getCqlUserQuery("personal.email", "user@saml.com"));

    assertEquals("externalSystemId==\"\\*\"",
      UserService.getCqlUserQuery("externalSystemId", "*"));

    assertEquals("Unsupported user property: email", assertThrows(RuntimeException.class, () ->
      UserService.getCqlUserQuery("email", "user@saml.com"))
      .getMessage());

    assertEquals("Unsupported user property: externalsystemid", assertThrows(RuntimeException.class, () ->
      UserService.getCqlUserQuery("externalsystemid", "user@saml.com"))
      .getMessage());
  }

  @Test
  public void invalidCallbackUrlThrowsException() {
    assertThrows(SamlClientLoader.InvalidCallbackUrlException.class, () -> {
      SamlClientLoader.buildCallbackUrl("url", "tenant", "abc");
    });
  }

  @Test
  public void isValidCallbackUrlWhenValid_Legacy() {
    var callback = SamlClientLoader.buildCallbackUrl("okapi", "tenantId1", "callback");
    var expectedCallback = "okapi/_/invoke/tenant/tenantId1/saml/callback";
    assertEquals(expectedCallback, callback);
  }

  @Test
  public void isValidCallbackUrlWhenValid() {
    var callbackWithExpiry = SamlClientLoader.buildCallbackUrl("okapi", "tenantId1", "callback-with-expiry");
    var expectedCallbackWithExpiry = "okapi/_/invoke/tenant/tenantId1/saml/callback-with-expiry";
    assertEquals(expectedCallbackWithExpiry, callbackWithExpiry);
  }

  private void testCallbackErrorCases(String callbackUrl, String relayState, String cookie) {
    log.info("=== Test - POST /saml/{} - failure (wrong cookie) ===", callbackUrl);
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie + "bad")
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post(callbackUrl)
      .then()
      .statusCode(403)
      .body(is("CSRF attempt detected"));

    log.info("=== Test - POST /saml/{} - failure (invalid relay) ===", callbackUrl);
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie.replace("localhost", "^"))
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      .then()
      .statusCode(400)
      .body(containsString("Invalid url in relayState cookie"));

    log.info("=== Test - POST /saml/{} - failure (no cookie) ===", callbackUrl);
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie.replace("localhost", "^"))
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      .then()
      .statusCode(400)
      .body(containsString("Invalid url in relayState cookie"));

    // not found ..
    mock.setMockContent("mock_400.json");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post( callbackUrl)
      .then()
      .statusCode(500)
      .body(is("Response status code 404 is not equal to 200"));

    mock.setMockContent("mock_nouser.json");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post(callbackUrl)
      .then()
      .statusCode(400)
      .body(is("No user found by externalSystemId == saml-user-id"));

    mock.setMockContent("mock_inactiveuser.json");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(SamlAPI.RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post(callbackUrl)
      .then()
      .statusCode(403)
      .body(is("Inactive user account!"));
  }
}
