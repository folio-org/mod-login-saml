package org.folio.util;

import io.restassured.internal.matcher.xml.XmlXsdMatcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.w3c.dom.ls.LSResourceResolver;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import static io.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;

/**
 * A {@link org.hamcrest.Matcher} that base64 decode the incoming content then run the xsd matching
 *
 * @author rsass
 */
public class Base64AwareXsdMatcher extends TypeSafeMatcher<String> {

  private XmlXsdMatcher xsdMatcher;

  public static Base64AwareXsdMatcher matchesBase64XsdInClasspath(String xsdPath, LSResourceResolver resolver) {
    return new Base64AwareXsdMatcher(xsdPath, resolver);
  }

  public Base64AwareXsdMatcher(String xsdPath, LSResourceResolver resolver) {
    Objects.requireNonNull(xsdPath);
    Objects.requireNonNull(resolver);
    xsdMatcher = matchesXsdInClasspath(xsdPath).using(resolver);
  }

  @Override
  protected boolean matchesSafely(String item) {
    byte[] decodedString = Base64.getDecoder().decode(item.getBytes(StandardCharsets.UTF_8));
    return xsdMatcher.matches(new String(decodedString));
  }


  @Override
  public void describeTo(Description description) {
    xsdMatcher.describeTo(description);
  }
}
