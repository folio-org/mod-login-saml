package org.folio.util;

import java.net.ConnectException;
import java.net.URI;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 */
public class UrlUtil {

  private UrlUtil() {

  }

  public static URI parseBaseUrl(URI originalUrl) {
    return URI.create(originalUrl.getScheme() + "://" + originalUrl.getAuthority());
  }

  public static Future<Void> checkIdpUrl(String url, Vertx vertx) {
    WebClient client = WebClientFactory.getWebClient(vertx);

    return client.getAbs(url).send()
      .map(httpResponse -> {
        String contentType = httpResponse.getHeader("Content-Type");
        if (contentType == null || ! contentType.contains("xml")) {
          throw new RuntimeException("Response content-type is not XML");
        }
        return (Void) null;
      })
      .recover(cause -> {
        if (cause instanceof ConnectException) {
          return Future.failedFuture("ConnectException: " + cause.getMessage());
        } else {
          return Future.failedFuture(cause);
        }
      });
  }
}
