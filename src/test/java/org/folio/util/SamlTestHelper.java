package org.folio.util;

import io.restassured.RestAssured;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.matcher.RestAssuredMatchers;
import org.folio.rest.impl.SamlAPI;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;

public class SamlTestHelper {

  public static void testCookieResponse(Cookie cookie, String relayState, String testPath, String sameSite, String samlResponse,
                                        Header tenantHeader, Header tokenHeader, Header okapiUrlHeader) {
    RestAssured.given()
      .header(tenantHeader)
      .header(tokenHeader)
      .header(okapiUrlHeader)
      .cookie(cookie)
      .formParam("SAMLResponse", samlResponse)
      .formParam("RelayState", relayState)
      .post("/saml/callback-with-expiry")
      .then()
      .statusCode(302)
      .cookie("folioRefreshToken", RestAssuredMatchers.detailedCookie()
        .value("saml-refresh-token")
        .path("/authn") // Refresh is restricted to this domain.
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
}
