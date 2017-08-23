package org.folio.config;

import org.folio.config.model.SamlClientComposite;
import org.pac4j.core.config.Config;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton for holding Pac4j {@link Config}
 *
 * @author rsass
 */
public class SamlConfigHolder {

  private static SamlConfigHolder instance;
  private Map<String, SamlClientComposite> config; // key: tenantId

  private SamlConfigHolder() {
    // new empty client list
    this.config = new HashMap<>();
  }

  public static SamlConfigHolder getInstance() {
    if (instance == null) {
      instance = new SamlConfigHolder();
    }
    return instance;
  }

  public Map<String, SamlClientComposite> getConfig() {
    return config;
  }

  public SamlClientComposite findClient(String tenantId) {
    return this.config.get(tenantId);
  }

  public void removeClient(String tenantId) {
    this.config.remove(tenantId);
  }

  public void putClient(String tenantId, SamlClientComposite clientComposite) {
    Assert.hasText(tenantId, "tenantId cannot be empty!");
    Assert.notNull(clientComposite, "clientComposite cannot be null!");
    this.config.put(tenantId, clientComposite);
  }
}
