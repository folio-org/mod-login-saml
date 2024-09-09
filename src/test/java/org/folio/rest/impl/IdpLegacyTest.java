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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import org.folio.config.SamlConfigHolder;
import org.folio.testutil.SimpleSamlPhpContainer;
import org.folio.rest.RestVerticle;
import org.folio.util.MockJson;
import org.folio.util.StringUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/**
 * Test against a real IDP: https://simplesamlphp.org/ running in a Docker container.
 */
@RunWith(VertxUnitRunner.class)
public class IdpLegacyTest {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IdpLegacyTest.class);
  private static final boolean DEBUG = false;
  private static final String TENANT = "diku";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "mytoken");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";
  private static final int MODULE_PORT = 9231;
  private static final String MODULE_URL = "http://localhost:" + MODULE_PORT;
  private static final int OKAPI_PORT = 9230;
  private static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", OKAPI_URL);
  private static MockJson OKAPI;

  private static Vertx VERTX;

  @ClassRule
  public static final SimpleSamlPhpContainer<?> IDP =
      new SimpleSamlPhpContainer<>(OKAPI_URL, "callback");

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    VERTX = Vertx.vertx();

    if (DEBUG) {
      IDP.followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
    }

    IDP.init();

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
    IDP.setPostBinding();;
    setOkapi("mock_idptest_post_legacy.json");

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
      post(MODULE_URL + "/saml/callback").
    then().
      statusCode(302).
      header("x-okapi-token", "saml-token").
      header("Location", startsWith("http://localhost:3000/sso-landing?ssoToken=saml-token"));
  }

  @Test
  public void redirect() {
    IDP.setRedirectBinding();
    setOkapi("mock_idptest_redirect_legacy.json");

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
      post(MODULE_URL + "/saml/callback").
    then().
      statusCode(302).
      header("x-okapi-token", "saml-token").
      header("Location", startsWith("http://localhost:3000/sso-landing?ssoToken=saml-token"));
  }

  private void setOkapi(String resource) {
    OKAPI.setMockContent(resource, s -> s.replace("http://localhost:8888/simplesaml/", IDP.getBaseUrl()));
  }

  private String jsonEncode(String key, String value) {
    return new JsonObject().put(key, value).encode();
  }
}
