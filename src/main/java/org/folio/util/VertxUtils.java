package org.folio.util;

import org.folio.session.NoopSession;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.vertx.VertxWebContext;

import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

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

    return new VertxWebContext(routingContext, new DummySessionStore(routingContext.vertx(), routingContext.session()));
  }

  public static class DummySessionStore implements SessionStore {

    private final Vertx vertx;
    private Session session;

    public DummySessionStore(Vertx vertx, Session session) {
      this.vertx = vertx;
      this.session = session == null ? (Session) new NoopSession() : session;
    }

    @Override
    public String getOrCreateSessionId(WebContext context) {
      return session.id();
    }

    @Override
    public Object get(WebContext context, String key) {
      return session.get(key);
    }

    @Override
    public void set(WebContext context, String key, Object value) {
      session.put(key, value);
    }

    @Override
    public boolean destroySession(WebContext context) {
      session = null;
      return true;
    }

    @Override
    public Object getTrackableSession(WebContext context) {
      return session;
    }

    @Override
    public SessionStore<?> buildFromTrackableSession(WebContext context, Object trackableSession) {
      return new DummySessionStore(vertx, (Session) trackableSession);
    }

    @Override
    public boolean renewSession(WebContext context) {
      return true;
    }

  }

}
