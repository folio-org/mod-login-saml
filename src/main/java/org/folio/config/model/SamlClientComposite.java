package org.folio.config.model;

import org.pac4j.saml.client.SAML2Client;
import org.springframework.util.Assert;

/**
 * @author rsass
 */
public class SamlClientComposite {

  private final SAML2Client client;
  private final SamlConfiguration configuration;

  public SamlClientComposite(SAML2Client client, SamlConfiguration configuration) {
    Assert.notNull(client, "Client cannot be null!");
    Assert.notNull(configuration, "Configuration cannot be null!");
    this.client = client;
    this.configuration = configuration;
  }

  public SAML2Client getClient() {
    return client;
  }

  public SamlConfiguration getConfiguration() {
    return configuration;
  }
}
