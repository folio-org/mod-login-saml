package org.folio.util;

import java.net.MalformedURLException;
import java.net.URL;

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

  private static final String REGEX_PREFIX = "http[s]?://";
  private CorsHandler handler;

  public CorsHelper(SamlConfiguration configuration) {
    Assert.notNull(configuration, "Configuration cannot be null!");

    StringBuilder sb = new StringBuilder();
    sb.append(REGEX_PREFIX);
    boolean appendPipe = false;
    for (String originStr : configuration.getCorsAllowableOrigins()) {
      try {
        URL url = new URL(originStr);
        if (appendPipe) {
          sb.append("|");
        }
        sb.append("(")
          .append(url.getHost())
          .append(")");
        appendPipe = true;
      } catch (MalformedURLException e) {
        log.warn("Invalid URL for origin: {}", originStr, e);
      }
    }

    String allowableOrigins = sb.toString();
    if (allowableOrigins.equals(REGEX_PREFIX)) {
      log.warn("No Allowable Origins were configured");
    } else {
      log.info("Allowable Origins: {}", allowableOrigins);

      this.handler = CorsHandler.create(allowableOrigins)
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

  public void handle(RoutingContext routingContext) {
    if (handler == null) {
      routingContext.next();
    } else {
      handler.handle(routingContext);
    }
  }
}
