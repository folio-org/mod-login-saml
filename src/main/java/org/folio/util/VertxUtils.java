package org.folio.util;

import java.util.UUID;

import org.folio.session.NoopSession;
import org.pac4j.core.context.session.SessionStore;
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

  private VertxUtils() {}

  public static class DummySessionStore implements SessionStore<VertxWebContext>  {

    private final Vertx vertx;
    private Session session;

    public DummySessionStore(Vertx vertx, Session session) {
      this.vertx = vertx;
      this.session = session == null ? new NoopSession() : session;
    }

    @Override
    public String getOrCreateSessionId(VertxWebContext context) {
      String id = session.id();
      return id != null ? id : "";
    }

    @Override
    public Object get(VertxWebContext context, String key) {
      return session.get(key);
    }

    @Override
    public void set(VertxWebContext context, String key, Object value) {
      session.put(key, value);
    }

    @Override
    public boolean destroySession(VertxWebContext context) {
      session = null;
      return true;
    }

    @Override
    public Object getTrackableSession(VertxWebContext context) {
      return session;
    }

    @Override
    public SessionStore<VertxWebContext> buildFromTrackableSession(VertxWebContext context, Object trackableSession) {
      return new DummySessionStore(vertx, (Session) trackableSession);
    }

    @Override
    public boolean renewSession(VertxWebContext context) {
      return true;
    }
  }

}
