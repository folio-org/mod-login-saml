package org.folio.util;

import io.restassured.RestAssured;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.matcher.RestAssuredMatchers;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.SamlAPI;
import org.folio.testutil.SimpleSamlPhpContainer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.both;

public class SamlTestHelper {
  public static final int OKAPI_PORT = 9230;
  public static final int MODULE_PORT = 9231;
  public static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  public static final String TENANT = "diku";
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "mytoken");
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", OKAPI_URL);
  private static final String TEST_PATH = "/test/path";
  private static final String STRIPES_URL = "http://localhost:3000";
  private static MockJson OKAPI;

  public static void deployVerticle(Vertx vertx, TestContext context) {
    DeploymentOptions moduleOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MODULE_PORT)
        .put("mock", true)); // to use SAML2ClientMock

    OKAPI = new MockJson();
    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", OKAPI_PORT));

    vertx.deployVerticle(new RestVerticle(), moduleOptions)
      .compose(x -> vertx.deployVerticle(OKAPI, okapiOptions))
      .onComplete(context.asyncAssertSuccess());
  }

  public static void setOkapi(String resource, SimpleSamlPhpContainer idp) {
    OKAPI.setMockContent(resource, s -> s.replace("http://localhost:8888/simplesaml/", idp.getBaseUrl()));
  }

  public static void testPost(String callback) {
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
        containsString(String.format("action=\"%s/_/invoke/tenant/diku/saml/%s\">", OKAPI_URL, callback)))
      .extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    testCookieResponse(cookie, relayState, TEST_PATH, CookieSameSite.LAX.toString(),
      matcher.group(1), TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER, "/saml/" + callback);
  }

  public static void testRedirect(String  callback) {
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

    String body =
      given()
        .param(samlRequest[0], samlRequest[1])
        .param(relayState[0], relayState[1])
        .when()
        .get(location)
        .then()
        .statusCode(200)
        .body(containsString(" method=\"post\" "),
          containsString(String.format("action=\"%s/_/invoke/tenant/diku/saml/%s\">", OKAPI_URL, callback)))
        .extract().asString();

    var matcher = Pattern.compile("name=\"SAMLResponse\" value=\"([^\"]+)").matcher(body);
    assertThat(matcher.find(), is(true));

    testCookieResponse(cookie, relayState[1], TEST_PATH, CookieSameSite.LAX.toString(),
      matcher.group(1), TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER,
      "/saml/" + callback);
  }

  public static void testCookieResponse(Cookie cookie, String relayState, String testPath, String sameSite,
                                        String samlResponse, Header tenantHeader, Header tokenHeader,
                                        Header okapiUrlHeader, String callbackUrl) {
    RestAssured.given()
      .header(tenantHeader)
      .header(tokenHeader)
      .header(okapiUrlHeader)
      .cookie(cookie)
      .formParam("SAMLResponse", samlResponse)
      .formParam("RelayState", relayState)
      .post(callbackUrl)
      .then()
      .statusCode(302)
      .cookie("folioRefreshToken", RestAssuredMatchers.detailedCookie()
        .value("saml-refresh-token")
        .path("/authn") // Refresh is restricted to this path.
        .httpOnly(true)
        .secured(true)
        .domain(is(nullValue())) // Not setting domain disables subdomains.
        .sameSite(sameSite))
      .cookie("folioAccessToken", RestAssuredMatchers.detailedCookie()
        .value("saml-access-token")
        .path("/") // Path must be set in this way for it to mean "all paths".
        .httpOnly(true)
        .secured(true)
        .domain(is(nullValue())) // Not setting domain disables subdomains.
        .sameSite(sameSite))
      .header("Location", containsString("fwd"))
      .header("Location", containsString(PercentCodec.encodeAsString(testPath)))
      .header("Location", containsString(SamlAPI.ACCESS_TOKEN_EXPIRATION))
      .header("Location", containsString(SamlAPI.REFRESH_TOKEN_EXPIRATION))
      .header("Location", both(containsString(PercentCodec.encodeAsString("2050-10-05T20:19:33Z")))
        .and(containsString(PercentCodec.encodeAsString("2050-10-05T20:19:33Z"))));
  }

  private static String jsonEncode(String key, String value) {
    return new JsonObject().put(key, value).encode();
  }
}
