package org.folio.config;

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;

/**
 * Singleton for holding Pac4j {@link Config}
 *
 * @author rsass
 */
public class SamlConfigHolder {

  private static SamlConfigHolder instance;
  private Config config;

  private SamlConfigHolder() {
    // new empty client list
    this.config = new Config(new Clients());
  }

  public static SamlConfigHolder getInstance() {
    if (instance == null) {
      instance = new SamlConfigHolder();
    }
    return instance;
  }

  public Config getConfig() {
    return config;
  }

}
