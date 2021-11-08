package org.folio.util;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Session;
import org.folio.session.NoopSession;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import java.util.Optional;

public class DummySessionStore implements SessionStore {
  private final Vertx vertx;
  private Session session;

  public DummySessionStore(Vertx vertx, Session session) {
    this.vertx = vertx;
    this.session = session == null ? new NoopSession() : session;
  }

  @Override
  public Optional<String> getSessionId(WebContext webContext, boolean b) {
    String id = session.id();
    return Optional.of(id == null ? "" : id);
  }

  @Override
  public Optional<Object> get(WebContext webContext, String key) {
    return Optional.ofNullable(session.get(key));
  }

  @Override
  public void set(WebContext webContext, String key, Object value) {
    session.put(key, value);
  }

  @Override
  public boolean destroySession(WebContext webContext) {
    session = null;
    return true;
  }

  @Override
  public Optional<Object> getTrackableSession(WebContext webContext) {
    return Optional.empty();
  }

  @Override
  public Optional<SessionStore> buildFromTrackableSession(WebContext webContext, Object trackableSession) {
    return Optional.of(new DummySessionStore(vertx, (Session) trackableSession));
  }

  @Override
  public boolean renewSession(WebContext webContext) {
    return true;
  }
}
