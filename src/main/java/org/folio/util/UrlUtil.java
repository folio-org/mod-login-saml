package org.folio.util;

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


}
