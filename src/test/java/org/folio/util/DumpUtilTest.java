package org.folio.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.Test;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.metadata.SAML2IdentityProviderMetadataResolver;
import org.springframework.core.io.ByteArrayResource;

public class DumpUtilTest {

  @Test
  public void test() {
    UtilityClassTester.assertUtilityClass(DumpUtil.class);
  }

  @Test
  public void dumpNull() {
    assertThat(DumpUtil.dump(null), is("null"));
  }

  // try dumping the classes used, reflection might cause access failures
  // https://issues.folio.org/browse/MODLOGSAML-151

  @Test
  public void dumpResource() {
    var resource = new ByteArrayResource(new byte [] {100, 101, 102});
    assertThat(DumpUtil.dump(resource), allOf(containsString("ByteArrayResource"), containsString("100,101,102")));
  }

  @Test
  public void dumpResolver() {
    var resolver = new SAML2IdentityProviderMetadataResolver(new SAML2Configuration());
    assertThat(DumpUtil.dump(resolver), allOf(containsString("SAML2IdentityProviderMetadataResolver")));
  }

}
