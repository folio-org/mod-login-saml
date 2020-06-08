package org.folio.util;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class WebClientFactory {

  public static final int DEFAULT_TIMEOUT = 5000;
  public static final boolean DEFAULT_KEEPALIVE = false;
  
  private static Map<Vertx, WebClient> clients = new HashMap<>();

  public static WebClient getWebClient(Vertx vertx) {
    return clients.get(vertx);
  }

  private WebClientFactory() {
  }

  /**
   * Initializes a WebClient for the provided Vertx.
   * Calling this method more than once with the same Vertx has no effect.
   *
   * @param vertx
   */
  public static synchronized void init(Vertx vertx) {
    if (vertx != null && !clients.containsKey(vertx)) {
      WebClientOptions options = new WebClientOptions();
      options.setKeepAlive(DEFAULT_KEEPALIVE);
      options.setConnectTimeout(DEFAULT_TIMEOUT);
      options.setIdleTimeout(DEFAULT_TIMEOUT);
      clients.put(vertx, WebClient.create(vertx, options));
    }
  }
}
