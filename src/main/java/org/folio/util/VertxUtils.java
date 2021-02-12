package org.folio.util;

import org.folio.session.NoopSession;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.vertx.VertxWebContext;

import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

import java.util.Optional;

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
    public Optional<Object> get(VertxWebContext vertxWebContext, String key) {
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
    public Optional getTrackableSession(VertxWebContext vertxWebContext) {
      return Optional.of(session);
    }

    @Override
    public Optional<SessionStore<VertxWebContext>> buildFromTrackableSession(VertxWebContext vertxWebContext, Object o) {
      return Optional.of(new DummySessionStore(vertx, (Session) o));
    }

    @Override
    public boolean renewSession(VertxWebContext context) {
      return true;
    }
  }

}
