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

  private OkapiHelper() {}
  /**
   * Extract Okapi specific headers from current routing context
   */
  public static OkapiHeaders okapiHeaders(RoutingContext routingContext) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(routingContext.request().getHeader(XOkapiHeaders.URL));
    headers.setUrlTo(routingContext.request().getHeader(XOkapiHeaders.URL_TO));
    headers.setTenant(routingContext.request().getHeader(XOkapiHeaders.TENANT));
    headers.setToken(routingContext.request().getHeader(XOkapiHeaders.TOKEN));
    headers.setPermissions(routingContext.request().getHeader(XOkapiHeaders.PERMISSIONS));

    return headers;

  }

  public static OkapiHeaders okapiHeaders(Map<String, String> parsedHeaders) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(parsedHeaders.get(XOkapiHeaders.URL));
    headers.setUrlTo(parsedHeaders.get(XOkapiHeaders.URL_TO));
    headers.setTenant(parsedHeaders.get(XOkapiHeaders.TENANT));
    headers.setToken(parsedHeaders.get(XOkapiHeaders.TOKEN));
    headers.setPermissions(parsedHeaders.get(XOkapiHeaders.PERMISSIONS));

    return headers;

  }

  /**
  * XOkapiHeaders.URL must be overwritten by XOkapiHeaders.URL_TO
  * because in the class ConfigurationsClient the requests are sent to okapiHeaders.getUrl(), i.e. the URL of mod-configuration.
  * For example in the class ConfigurationsClient:
  * public static Future<JsonArray> ConfigurationsClient.checkConfig(Vertx vertx, OkapiHeaders okapiHeaders,String query)
  * return WebClientFactory.getWebClient(vertx)
  *   .getAbs(okapiHeaders.getUrl() + CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?limit=1000&query=" + encodedQuery) ....
  */
  public static OkapiHeaders okapiHeadersWithUrlTo(Map<String, String> parsedHeaders ) {
    OkapiHeaders headers = okapiHeaders(parsedHeaders);
    if (headers.getUrl() != null && headers.getUrlTo() != null)
      headers.setUrl(headers.getUrlTo());
    return headers;
  }
}
