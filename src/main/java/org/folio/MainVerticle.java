package org.folio;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.folio.config.Pac4jConfigurationFactory;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.vertx.VertxWebContext;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.context.session.VertxSessionStore;
import org.pac4j.vertx.handler.impl.CallbackHandler;
import org.pac4j.vertx.handler.impl.CallbackHandlerOptions;
import org.pac4j.vertx.handler.impl.SecurityHandler;
import org.pac4j.vertx.handler.impl.SecurityHandlerOptions;

/**
 * Hello world!
 */
public class MainVerticle extends AbstractVerticle {

  private final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  private SessionStore<VertxWebContext> sessionStore;
  //  private final Handler<RoutingContext> protectedIndexRenderer = DemoHandlers.protectedIndexHandler(sessionStore);
  private final Pac4jAuthProvider authProvider = new Pac4jAuthProvider(); // We don't need to instantiate this on demand
  private Config config = null;

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    JsonObject inheritedConfig = config();
    inheritedConfig.put("baseUrl", "http://localhost:8080");


    LocalSessionStore vertxSessionStore = LocalSessionStore.create(vertx);
    sessionStore = new VertxSessionStore(vertxSessionStore);
    SessionHandler sessionHandler = SessionHandler.create(vertxSessionStore);

    log.info("DemoServerVerticle: config is \n" + inheritedConfig.encodePrettily());
    this.config = new Pac4jConfigurationFactory(inheritedConfig, vertx, vertxSessionStore).build();

    final Router router = Router.router(vertx);

    router.route().handler(CookieHandler.create());
    router.route().handler(sessionHandler);
    router.route().handler(UserSessionHandler.create(authProvider));


    SecurityHandlerOptions options = new SecurityHandlerOptions().setClients("SAML2Client");
    router.get("/login-saml").handler(new SecurityHandler(vertx, sessionStore, this.config, authProvider, options));
    router.get("/login-saml").handler(routingContext -> {

      // This handler will be called for every request
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain");

      // Write to the response and end it
      response.end("Hello World from Vert.x-Web!");
    });

    final CallbackHandlerOptions callbackHandlerOptions = new CallbackHandlerOptions()
        .setDefaultUrl("/")
        .setMultiProfile(false);
    final CallbackHandler callbackHandler = new CallbackHandler(vertx, sessionStore, this.config, callbackHandlerOptions);
    router.get("/callback").handler(callbackHandler); // This will deploy the callback handler
    router.post("/callback").handler(BodyHandler.create().setMergeFormAttributes(true));
    router.post("/callback").handler(callbackHandler);

    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(8080);

//    startFuture.complete();

  }

}
