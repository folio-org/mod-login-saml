package org.folio.util;

import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 */
public class UrlUtil {
  private static final Logger logger = LogManager.getLogger(UrlUtil.class);

  private UrlUtil() {

  }

  /**
   * Return the path component of the okapiUrl without tailing '/',
   * return empty string on parse error.
   */
  public static String getPathFromOkapiUrl(String okapiUrl) {
    try {
      var path = new URL(okapiUrl).getPath();
      if (path.endsWith("/")) {
        return path.substring(0, path.length() - 1);
      }
      return path;
    } catch (MalformedURLException e) {
      var message = "Malformed Okapi URL: " + okapiUrl;
      logger.error("{}", message, e);
      throw new UncheckedIOException(message, e);
    }
  }

  public static URI parseBaseUrl(URI originalUrl) {
    return URI.create(originalUrl.getScheme() + "://" + originalUrl.getAuthority());
  }

  public static Future<Void> checkIdpUrl(String url, Vertx vertx) {
    WebClient client = WebClientFactory.getWebClient(vertx);

    return client.getAbs(url).send()
      .map(UrlUtil::validateXmlContentType)
      .recover(cause -> {
        if (cause instanceof ConnectException) {
          return Future.failedFuture("ConnectException: " + cause.getMessage());
        } else {
          return Future.failedFuture(cause);
        }
      });
  }

  static Void validateXmlContentType(HttpResponse<Buffer> httpResponse) {
    String contentType = httpResponse.getHeader("Content-Type");
    if (contentType == null) {
      contentType = "";
    }
    // workaround for *.sso.duosecurity.com, https://issues.folio.org/browse/MODLOGSAML-134
    if ("text/xhtml".equals(contentType) && "Duo/1.0".equals(httpResponse.getHeader("Server"))) {
      return null;
    }
    // https://www.w3.org/International/articles/serving-xhtml/
    // https://www.w3.org/TR/xhtml-media-types/#media-types
    // https://www.iana.org/assignments/media-types/media-types.xhtml
    if (contentType.startsWith("text/xml")
        || contentType.startsWith("application/xml")
        || contentType.startsWith("application/xhtml+xml")
        || contentType.startsWith("application/samlmetadata+xml")) {
      return null;
    }
    throw new RuntimeException("Content-Type response header media type must be one of "
        + "text/xml, application/xml, application/xhtml+xml, application/samlmetadata+xml");
  }
}
