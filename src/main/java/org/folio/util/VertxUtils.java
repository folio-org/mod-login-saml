package org.folio.util;

import io.vertx.ext.web.RoutingContext;
import org.pac4j.vertx.VertxWebContext;


/**
 * Vert.x utils
 *
 * @author rsass
 */
public class VertxUtils {

  /**
   * Create a Pac4j {@link VertxWebContext} from {@link RoutingContext} with {@code null} SessionStore
   */
  public static VertxWebContext createWebContext(RoutingContext routingContext) {
    return new VertxWebContext(routingContext, null);
  }

}
