package org.folio.util;

import java.net.URI;
import java.net.URISyntaxException;

import org.folio.util.model.UrlCheckResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 */
public class UrlUtil {

  private UrlUtil() {

  }

  public static URI parseBaseUrl(URI originalUrl) {
    try {
      return new URI(originalUrl.getScheme() + "://" + originalUrl.getAuthority());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Malformed URI...", e);
    }
  }

  public static Future<UrlCheckResult> checkIdpUrl(String url, Vertx vertx) {
    Promise<UrlCheckResult> promise = Promise.promise();

    WebClient client = WebClientFactory.getWebClient(vertx);

    client.getAbs(url).send(ar -> {
      try {
        if (ar.failed()) {
          promise.complete(UrlCheckResult.failResult(ar.cause().getMessage()));
          return;
        }
        String contentType = ar.result().getHeader("Content-Type");
        if (contentType.contains("xml")) {
          promise.complete(UrlCheckResult.emptySuccessResult());
          return;
        }
        promise.complete(UrlCheckResult.failResult("Response content-type is not XML"));
      } catch (Exception e) {
        promise.complete(UrlCheckResult.failResult("Unexpected error: " + e.getMessage()));
      }
    });

    return promise.future();
  }
}
