package org.folio.rest.impl;


import io.vertx.core.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.sstore.SessionStore;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.session.NoopSessionHandler;
import org.folio.session.NoopSessionStore;
import org.pac4j.core.config.Config;

public class ApiInitializer implements InitAPI {
  private Config config;
  private Router router;

  public Config getConfig() {
    return config;
  }

  public Router getRouter() {
    return router;
  }

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    System.out.println("ApiInitializer::init called");

    // TODO: init code here


    SessionStore localSessionStore = new NoopSessionStore();
    //todo: null
//    this.config = new Pac4jConfigurationFactory(null, vertx, localSessionStore).build();

    this.router = Router.router(vertx);
    router.route().handler(new NoopSessionHandler(localSessionStore));


    handler.handle(Future.succeededFuture(true));
  }
}
