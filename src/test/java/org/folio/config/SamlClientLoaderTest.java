package org.folio.config;

import static org.folio.config.SamlClientLoader.DEFAULT_MAXIMUM_AUTHENTICATION_LIFETIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.MalformedURLException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@RunWith(VertxUnitRunner.class)
public class SamlClientLoaderTest {

  @Test
  public void configureSaml2ClientTest() throws MalformedURLException {
    String okapiUrl = "okapiUrl";
    String tenantId = "tenantId";
    String keystorePassword = "keystorePassword";
    String privateKeyPassword = "privateKeyPassword";
    UrlResource idpUrlResource = new UrlResource("http://localhost:80");
    ByteArrayResource keystoreResource = new ByteArrayResource(new byte[]{});
    String samlBinding = "samlBinding";
    Resource idpMetadata = new UrlResource("http://localhost:80");

    var cfg = SamlClientLoader.getSaml2ConfigurationForByteArrayResource(keystoreResource,
        keystorePassword, privateKeyPassword, idpUrlResource, idpMetadata);
    var saml2Client = SamlClientLoader.assembleSaml2Client(okapiUrl, tenantId, cfg, samlBinding,
        Vertx.vertx().getOrCreateContext(), "callback-with-expiry");

    Assert.assertNotNull(saml2Client);
  }

  @Test
  public void setMaximumAuthTime() {
    SamlClientLoader.setMaximumAuthenticationLifetime(null);
    var conf = SamlClientLoader.getSaml2ConfigurationForByteArrayResource(null, null, null, null, null);
    assertThat(conf.getMaximumAuthenticationLifetime(), is(DEFAULT_MAXIMUM_AUTHENTICATION_LIFETIME));

    SamlClientLoader.setMaximumAuthenticationLifetime("789");
    conf = SamlClientLoader.getSaml2ConfigurationForByteArrayResource(null, null, null, null, null);
    assertThat(conf.getMaximumAuthenticationLifetime(), is(789L));

    SamlClientLoader.setMaximumAuthenticationLifetime(null);
    conf = SamlClientLoader.getSaml2ConfigurationForByteArrayResource(null, null, null, null, null);
    assertThat(conf.getMaximumAuthenticationLifetime(), is(DEFAULT_MAXIMUM_AUTHENTICATION_LIFETIME));
  }

  @Test
  public void setMaximumAuthTimeException() {
    assertThrows(NumberFormatException.class, () -> SamlClientLoader.setMaximumAuthenticationLifetime("foo"));
  }
}
