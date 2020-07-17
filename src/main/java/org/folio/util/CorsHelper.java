package org.folio.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.XOkapiHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;

public class CorsHelper {
  private static final Logger log = LoggerFactory.getLogger(CorsHelper.class.getName());

  protected static final String REGEX_PREFIX = "http[s]?://(";
  protected CorsHandler handler;

  public CorsHelper(SamlConfiguration configuration) {
    Assert.notNull(configuration, "Configuration cannot be null!");

    String allowableOrigins = formAllowedOriginRegex(configuration.getCorsAllowableOrigins());
    if (allowableOrigins.equals(REGEX_PREFIX + ")")) {
      log.warn("No Allowable Origins were configured");
    } else {
      log.info("Allowable Origins: {}", allowableOrigins);

      this.handler = new CorsHandlerImpl(allowableOrigins)
        .allowedMethod(HttpMethod.PUT)
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.POST)
        .allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
        .allowedHeader(XOkapiHeaders.TENANT)
        .allowedHeader(XOkapiHeaders.TOKEN)
        .allowedHeader(XOkapiHeaders.AUTHORIZATION)
        .allowedHeader(XOkapiHeaders.REQUEST_ID) // expose response headers
        .allowedHeader(XOkapiHeaders.MODULE_ID)
        .allowCredentials(true)
        .exposedHeader(HttpHeaders.LOCATION.toString())
        .exposedHeader(XOkapiHeaders.TRACE)
        .exposedHeader(XOkapiHeaders.TOKEN)
        .exposedHeader(XOkapiHeaders.AUTHORIZATION)
        .exposedHeader(XOkapiHeaders.REQUEST_ID)
        .exposedHeader(XOkapiHeaders.MODULE_ID);
    }
  }

  protected static String formAllowedOriginRegex(List<String> allowed) {
    StringBuilder sb = new StringBuilder();
    sb.append(REGEX_PREFIX);
    boolean appendPipe = false;
    for (String originStr : allowed) {
      try {
        URL url = new URL(originStr);
        if (appendPipe) {
          sb.append("|");
        }
        int port = url.getPort();
        sb.append(url.getHost());

        if (port > 0) {
          sb.append(":")
            .append(port);
        }
        appendPipe = true;
      } catch (MalformedURLException e) {
        log.warn("Invalid URL for origin: {}", originStr, e);
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public void handle(RoutingContext routingContext) {
    if (handler == null) {
      routingContext.next();
    } else {
      handler.handle(routingContext);
    }
  }
}
