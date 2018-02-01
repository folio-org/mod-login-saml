package org.folio.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

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

  public static Future<Void> chackIdpUrl(URL url, Vertx vertx) {

    Future<Void> future = Future.future();
    HttpClient client = createClient(vertx);

    client.getAbs(url.toString(), responseHandler -> {
      String contentType = responseHandler.getHeader("Content-Type");
      if (MediaType.TEXT_XML.equals(contentType) || MediaType.APPLICATION_XML.equals(contentType)) {
        future.complete();
      } else {
        future.fail("Response content-type is not XML");
      }

    })
      .exceptionHandler(exceptionHandler -> {
        future.fail(exceptionHandler.getCause());
      })
      .end();

    return future;
    

  }

  private static HttpClient createClient(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setKeepAlive(false);
    return vertx.createHttpClient(options);
  }
}
