package org.folio.util;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class WebClientFactory {

  private static final WebClientOptions options = webClientOptions();
  /** reduce from 60 seconds to 15 seconds */
  public static final int DEFAULT_TIMEOUT = 15000;

  private WebClientFactory() {
  }

  private static WebClientOptions webClientOptions() {
    var webClientOptions = new WebClientOptions();
    webClientOptions.setConnectTimeout(DEFAULT_TIMEOUT);
    webClientOptions.setIdleTimeout(DEFAULT_TIMEOUT);
    return webClientOptions;
  }

  public static WebClient getWebClient(Vertx vertx) {
    return org.folio.okapi.common.WebClientFactory.getWebClient(vertx, options);
  }
}
