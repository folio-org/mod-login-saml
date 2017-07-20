package org.folio.session;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * @author rsass
 */
public class NoopSessionStore implements SessionStore {


  @Override
  public long retryTimeout() {
    return 0;
  }

  @Override
  public Session createSession(long timeout) {
    return new NoopSession();
  }

  @Override
  public Session createSession(long timeout, int length) {
    return new NoopSession();
  }

  @Override
  public void get(String id, Handler<AsyncResult<Session>> resultHandler) {
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void delete(String id, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void put(Session session, Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void clear(Handler<AsyncResult<Boolean>> resultHandler) {
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> resultHandler) {
    resultHandler.handle(Future.succeededFuture());
  }

  @Override
  public void close() {

  }
}
