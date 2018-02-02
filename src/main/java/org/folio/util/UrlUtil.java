package org.folio.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.folio.util.model.UrlCheckResult;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author rsass
 */
public class UrlUtil {


  public static URI parseBaseUrl(URI originalUrl) {
    try {
      return new URI(originalUrl.getScheme() + "://" + originalUrl.getAuthority());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Malformed URI...", e);
    }
  }

  public static Future<UrlCheckResult> checkIdpUrl(String url, Vertx vertx) {

    Future<UrlCheckResult> future = Future.future();
    HttpClient client = createClient(vertx);

    try {
      client.getAbs(url, responseHandler -> {
        String contentType = responseHandler.getHeader("Content-Type");
        if (MediaType.TEXT_XML.equals(contentType) || MediaType.APPLICATION_XML.equals(contentType)) {
          future.complete(UrlCheckResult.emptySuccessResult());
        } else {
          future.complete(UrlCheckResult.failResult("Response content-type is not XML"));
        }
      }).exceptionHandler(exc -> {
        future.complete(UrlCheckResult.failResult(exc.getMessage()));
      }).end();

    } catch (Exception e) {
      future.complete(UrlCheckResult.failResult(e.getMessage()));
    }

    return future;
  }

  public static Future<UrlCheckResult> checkOkapiUrl(String url, Vertx vertx) {

    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/proxy/modules";

    Future<UrlCheckResult> future = Future.future();
    HttpClient client = createClient(vertx);

    try {
      client.getAbs(url, responseHandler -> {
        String contentType = responseHandler.getHeader("Content-Type");
        if (MediaType.APPLICATION_JSON.equals(contentType)) {
          future.complete(UrlCheckResult.emptySuccessResult());
        } else {
          future.complete(UrlCheckResult.failResult("Response content-type is not JSON!"));
        }
      }).exceptionHandler(exc -> {
        future.complete(UrlCheckResult.failResult(exc.getMessage()));
      }).end();

    } catch (Exception e) {
      future.complete(UrlCheckResult.failResult(e.getMessage()));
    }

    return future;
  }

  private static HttpClient createClient(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setKeepAlive(false).setConnectTimeout(5000);
    return vertx.createHttpClient(options);
  }
}
