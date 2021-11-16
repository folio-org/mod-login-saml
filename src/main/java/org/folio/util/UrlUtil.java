package org.folio.util;

import java.net.ConnectException;
import java.net.URI;

import org.folio.util.model.UrlCheckResult;
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

  public static Future<UrlCheckResult> checkIdpUrl(String url, Vertx vertx) {
    WebClient client = WebClientFactory.getWebClient(vertx);

    return client.getAbs(url).send()
      .map(httpResponse -> {
        String contentType = httpResponse.getHeader("Content-Type");
        if (! contentType.contains("xml")) {
          return UrlCheckResult.failResult("Response content-type is not XML");
        }
        return UrlCheckResult.emptySuccessResult();
      })
      .otherwise(cause -> {
        if (cause instanceof ConnectException) {
          // add locale independent prefix, Netty puts a locale dependent translation into getMessage(),
          // for example German "Verbindungsaufbau abgelehnt:" for English "Connection refused:"
          return UrlCheckResult.failResult("ConnectException: " + cause.getMessage());
        }
        return UrlCheckResult.failResult("Unexpected error: " + cause.getMessage());
      });
  }
}
