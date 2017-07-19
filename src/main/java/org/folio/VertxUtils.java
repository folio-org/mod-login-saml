package org.folio;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.pac4j.vertx.VertxWebContext;


public class VertxUtils {

  public static Vertx getVertxFromContextOrNew() {
    Context context = Vertx.currentContext();
    return context != null ? context.owner() : Vertx.vertx();
  }

  /**
   * Create a Pac4j {@link VertxWebContext} from {@link RoutingContext} with {@code null} SessionStore
   */
  public static VertxWebContext createWebContext(RoutingContext routingContext) {
    return new VertxWebContext(routingContext, null);
  }

}
