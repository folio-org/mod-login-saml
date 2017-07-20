package org.folio.session;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * @author rsass
 */
public class NoopSessionHandler implements SessionHandler {

  private final SessionStore sessionStore;

  public NoopSessionHandler(final SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public SessionHandler setSessionTimeout(long timeout) {
    return this;
  }

  @Override
  public SessionHandler setNagHttps(boolean nag) {
    return this;
  }

  @Override
  public SessionHandler setCookieSecureFlag(boolean secure) {
    return this;
  }

  @Override
  public SessionHandler setCookieHttpOnlyFlag(boolean httpOnly) {
    return this;
  }

  @Override
  public SessionHandler setSessionCookieName(String sessionCookieName) {
    return this;
  }

  @Override
  public SessionHandler setMinLength(int minLength) {
    return this;
  }

  @Override
  public void handle(RoutingContext rc) {
    Session session = sessionStore.createSession(0);
    rc.setSession(session);
    rc.next();

    // TODO: add addStoreSessionHandler  ( -> context.addHeadersEndHandler(...)  )
  }
}
