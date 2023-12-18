package org.folio.config;

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
}
