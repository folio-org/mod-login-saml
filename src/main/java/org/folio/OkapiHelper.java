package org.folio;

import io.vertx.ext.web.RoutingContext;

public class OkapiHelper {


  /**
   * Extract Okapi specific headers from current routing context
   */
  public static OkapiHeaders okapiHeaders(RoutingContext routingContext) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(routingContext.request().getHeader(OkapiHeaders.OKAPI_URL_HEADER));
    headers.setTenant(routingContext.request().getHeader(OkapiHeaders.OKAPI_TENANT_HEADER));
    headers.setToken(routingContext.request().getHeader(OkapiHeaders.OKAPI_TOKEN_HEADER));
    headers.setPermissions(routingContext.request().getHeader(OkapiHeaders.OKAPI_PERMISSIONS_HEADER));

    return headers;

  }
}
