package org.folio.config;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.MalformedURLException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pac4j.saml.client.SAML2Client;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@RunWith(VertxUnitRunner.class)
public class SamlClientLoaderTest {

  @Test
  public void configureSaml2ClientTest(TestContext context) throws MalformedURLException {
    String okaiUrl = "okaiUrl";
    String tenantId = "tenantId";
    String keystorePassword = "keystorePassword";
    String privateKeyPassword = "privateKeyPassword";
    UrlResource idpUrlResource = new UrlResource("http://localhost:80");
    ByteArrayResource keystoreResource = new ByteArrayResource(new byte[]{});
    String samlBinding = "samlBinding";
    Resource idpMetadata = new UrlResource("http://localhost:80");
    SAML2Client saml2Client = SamlClientLoader
      .configureSaml2Client(okaiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource,
        keystoreResource, samlBinding, idpMetadata, Vertx.vertx().getOrCreateContext(), "callback-with-expiry");
    Assert.assertNotNull(saml2Client);
  }
}
