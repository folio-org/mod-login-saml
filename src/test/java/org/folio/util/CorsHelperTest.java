package org.folio.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.folio.config.model.SamlConfiguration;
import org.junit.Test;

import io.vertx.core.json.JsonArray;

public class CorsHelperTest {

  @Test
  public void testConstructor() {
    SamlConfiguration conf = new SamlConfiguration();
    conf.setCorsAllowableOrigins("[]");
    
    assertNull(new CorsHelper(conf).handler);
    
    conf.setCorsAllowableOrigins(new JsonArray().add("http://localhost").encode());
    
    assertNotNull(new CorsHelper(conf).handler);
  }
  
  @Test
  public void testFormAllowedOriginRegex() {
    List<String> allowed = new ArrayList<>();
    String regex = CorsHelper.formAllowedOriginRegex(allowed);

    assertEquals(CorsHelper.REGEX_PREFIX + ")", regex);
    assertFalse(Pattern.matches(regex, "http://localhost"));

    allowed.add("bogus");
    regex = CorsHelper.formAllowedOriginRegex(allowed);

    assertEquals(CorsHelper.REGEX_PREFIX + ")", regex);
    assertFalse(Pattern.matches(regex, "http://localhost"));
    
    allowed.add("http://localhost");
    regex = CorsHelper.formAllowedOriginRegex(allowed);

    assertEquals(CorsHelper.REGEX_PREFIX + "localhost)", regex);
    assertTrue(Pattern.matches(regex, "http://localhost"));
    assertTrue(Pattern.matches(regex, "https://localhost"));
    assertFalse(Pattern.matches(regex, "http://localhost:3000"));

    allowed.add("https://idp.foo.com");
    regex = CorsHelper.formAllowedOriginRegex(allowed);

    assertEquals(CorsHelper.REGEX_PREFIX + "localhost|idp.foo.com)", regex);
    assertTrue(Pattern.matches(regex, "http://localhost"));
    assertTrue(Pattern.matches(regex, "https://localhost"));
    assertTrue(Pattern.matches(regex, "http://idp.foo.com"));
    assertTrue(Pattern.matches(regex, "https://idp.foo.com"));
    assertFalse(Pattern.matches(regex, "http://localhost:3000"));

    allowed.add("http://localhost:3000");
    regex = CorsHelper.formAllowedOriginRegex(allowed);

    assertEquals(CorsHelper.REGEX_PREFIX + "localhost|idp.foo.com|localhost:3000)", regex);
    assertTrue(Pattern.matches(regex, "http://localhost"));
    assertTrue(Pattern.matches(regex, "https://localhost"));
    assertTrue(Pattern.matches(regex, "http://idp.foo.com"));
    assertTrue(Pattern.matches(regex, "https://idp.foo.com"));
    assertTrue(Pattern.matches(regex, "http://localhost:3000"));
    assertTrue(Pattern.matches(regex, "https://localhost:3000"));
    assertFalse(Pattern.matches(regex, "http://zippity.zap"));
  }

}
