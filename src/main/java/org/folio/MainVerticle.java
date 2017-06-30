package org.folio;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.folio.config.Pac4jConfigurationFactory;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.context.session.VertxSessionStore;
import org.pac4j.vertx.http.DefaultHttpActionAdapter;

import static org.pac4j.core.util.CommonHelper.assertNotNull;
import static org.pac4j.core.util.CommonHelper.assertTrue;

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


    // TODO:
    // java.lang.IllegalStateException: Session required for use of getSessionAttribute
    // pl IndirectClient-ben: context.getSessionAttribute(getName() + ATTEMPTED_AUTHENTICATION_SUFFIX);
    // ... viszont ugy nez ki, hogy CookieHandler nélkül is megy, mert így boldog, hogy van session-je... az mondiegy, hogy nem csinál vele semmit

    //    router.route().handler(CookieHandler.create());
    router.route().handler(sessionHandler);
    //    router.route().handler(UserSessionHandler.create(authProvider));

    router.get("/").handler(rc -> {
      HttpServerResponse response = rc.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);
      response.write("<html><body>");
      response.write("<a href=\"/saml-login\">/saml-login</a><br>");
      response.write("<a href=\"/regenerate\">/regenerate</a>");
      response.end("</body></html>");
    });

    router.get("/regenerate").blockingHandler(routingContext -> {
      // TODO: upload new idp-metadata.xml?
      VertxWebContext vertxWebContext = new VertxWebContext(routingContext, null);
      String metadata = regenerateSaml2Config(vertxWebContext);

      HttpServerResponse response = routingContext.response();
      response.headers().add("content-type", "application/xml");
      response.end(metadata);
    });


    router.get("/saml-login").blockingHandler(routingContext -> {

      IndirectClient saml2Client = findSaml2Client();
      VertxWebContext vertxWebContext = new VertxWebContext(routingContext, null); // TODO: null ?!
      HttpAction action;
      try {
        action = saml2Client.redirect(vertxWebContext); // highly blocking
      } catch (HttpAction httpAction) {
        action = httpAction;
      }

      new DefaultHttpActionAdapter().adapt(action.getCode(), vertxWebContext);
    }, false);

    router.post("/saml-callback").handler(BodyHandler.create().setMergeFormAttributes(true));
    router.post("/saml-callback").blockingHandler(routingContext -> {

      final VertxWebContext webContext = new VertxWebContext(routingContext, null);

      IndirectClient client = findSaml2Client();
      try {
        SAML2Credentials credentials = (SAML2Credentials) client.getCredentials(webContext);
        log.debug("credentials: {}", credentials);

        final CommonProfile profile = client.getUserProfile(credentials, webContext);
        log.debug("profile: {}", profile);

        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/plain");
        response.end("Minden kiraly! Ide johet a JWT token kiadas, stb. Credentials: " + credentials + " profile" + profile);

      } catch (HttpAction httpAction) {
        new DefaultHttpActionAdapter().adapt(httpAction.getCode(), webContext);
      }

    }, false);


    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);

  }

  private String regenerateSaml2Config(VertxWebContext vertxWebContext) {
    SAML2Client saml2Client = findSaml2Client();
    SAML2ClientConfiguration cfg = saml2Client.getConfiguration();

    // force metadata generation then init
    cfg.setForceServiceProviderMetadataGeneration(true);
    saml2Client.reinit(vertxWebContext);
    cfg.setForceServiceProviderMetadataGeneration(false);

    // return xml as String
    return saml2Client.getServiceProviderMetadataResolver().getMetadata();
  }

  private SAML2Client findSaml2Client() {

    final Clients clients = config.getClients();
    assertNotNull("clients", clients);

    // logic
    final Client client = clients.findClient(SAML2Client.class);
    assertNotNull("client", client);
    assertTrue(client instanceof SAML2Client, "only indirect clients are allowed on the callback url");

    return (SAML2Client) client;
  }

}
