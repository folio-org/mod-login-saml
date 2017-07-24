package org.folio.config;

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;

/**
 * Singleton for holding Pac4j {@link Config}
 *
 * @author rsass
 */
public class Pac4jConfigurationHolder {

  private static Pac4jConfigurationHolder instance;

  private Config config;

  private Pac4jConfigurationHolder() {

//    SessionStore localSessionStore = new NoopSessionStore();
    this.config = buildConfig();

  }

  public static Pac4jConfigurationHolder getInstance() {
    if (instance == null) {
      instance = new Pac4jConfigurationHolder();
    }
    return instance;
  }

  public Config getConfig() {
    return config;
  }

  private Config buildConfig(Object... parameters) {
    Clients clients = new Clients();
    Config config = new Config(clients);
    return config;
  }
}
