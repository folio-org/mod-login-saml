/*
  Copyright 2015 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.folio.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.sstore.SessionStore;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;

/**
 * Instantiates an empty configuration object for Pac4j.
 *
 * @author rsass
 */
public class Pac4jConfigurationFactory implements ConfigFactory {

  private final JsonObject jsonConf;
  private final Vertx vertx;
  private final SessionStore sessionStore;

  public Pac4jConfigurationFactory(final JsonObject jsonConf, final Vertx vertx, final SessionStore sessionStore) {
    this.jsonConf = jsonConf;
    this.vertx = vertx;
    this.sessionStore = sessionStore;
  }

  @Override
  public Config build(Object... parameters) {
    Clients clients = new Clients();
    Config config = new Config(clients);
    return config;
  }

}
