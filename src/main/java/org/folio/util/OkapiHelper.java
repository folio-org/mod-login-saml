package org.folio.util;

import io.vertx.ext.web.RoutingContext;
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

    headers.setUrl(routingContext.request().getHeader(OkapiHeaders.OKAPI_URL_HEADER));
    headers.setTenant(routingContext.request().getHeader(OkapiHeaders.OKAPI_TENANT_HEADER));
    headers.setToken(routingContext.request().getHeader(OkapiHeaders.OKAPI_TOKEN_HEADER));
    headers.setPermissions(routingContext.request().getHeader(OkapiHeaders.OKAPI_PERMISSIONS_HEADER));

    return headers;

  }

  public static OkapiHeaders okapiHeaders(Map<String, String> parsedHeaders) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(parsedHeaders.get(OkapiHeaders.OKAPI_URL_HEADER));
    headers.setTenant(parsedHeaders.get(OkapiHeaders.OKAPI_TENANT_HEADER));
    headers.setToken(parsedHeaders.get(OkapiHeaders.OKAPI_TOKEN_HEADER));
    headers.setPermissions(parsedHeaders.get(OkapiHeaders.OKAPI_PERMISSIONS_HEADER));

    return headers;
    
  }
}
