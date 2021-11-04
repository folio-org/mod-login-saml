package org.folio.util;

import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.model.OkapiHeaders;

import java.util.Map;

/**
 * Okapi utils
 *
 * @author rsass
 */
public class OkapiHelper {


  /**
   * Extract Okapi specific headers from current routing context
   */
  public static OkapiHeaders okapiHeaders(RoutingContext routingContext) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(routingContext.request().getHeader(XOkapiHeaders.URL));
    headers.setTenant(routingContext.request().getHeader(XOkapiHeaders.TENANT));
    headers.setToken(routingContext.request().getHeader(XOkapiHeaders.TOKEN));
    headers.setPermissions(routingContext.request().getHeader(XOkapiHeaders.PERMISSIONS));

    return headers;

  }

  public static OkapiHeaders okapiHeaders(Map<String, String> parsedHeaders) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(parsedHeaders.get(XOkapiHeaders.URL));
    headers.setTenant(parsedHeaders.get(XOkapiHeaders.TENANT));
    headers.setToken(parsedHeaders.get(XOkapiHeaders.TOKEN));
    headers.setPermissions(parsedHeaders.get(XOkapiHeaders.PERMISSIONS));

    return headers;

  }
}
