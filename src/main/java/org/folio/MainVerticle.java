package org.folio;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.SessionStore;
import org.folio.config.ConfigurationsClient;
import org.folio.config.Pac4jConfigurationFactory;
import org.folio.config.SamlClientLoader;
import org.folio.session.NoopSessionHandler;
import org.folio.session.NoopSessionStore;
import org.folio.util.OkapiHelper;
import org.folio.util.VertxUtils;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.pac4j.vertx.http.DefaultHttpActionAdapter;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.pac4j.core.util.CommonHelper.assertNotNull;

/**
 * Main entry point of module
 *
 * @author rsass
 */
public class MainVerticle extends AbstractVerticle {

  public static final String CALLBACK_ENDPOINT = "/saml-callback";
  private final Logger log = LoggerFactory.getLogger(MainVerticle.class);
  private Config config = null;

  @Override
  public void start(Future<Void> startFuture) throws Exception {


    JsonObject verticleConfig = config();
    log.debug("Loaded configuration {}", verticleConfig.encode());

    trustAllCertificates(); // TODO: DO NOT USE IN PRODUCTION!

    SessionStore localSessionStore = new NoopSessionStore();
    SessionHandler sessionHandler = new NoopSessionHandler(localSessionStore);

    this.config = new Pac4jConfigurationFactory(verticleConfig, vertx, localSessionStore).build();
    final Router router = Router.router(vertx);

    router.route().handler(sessionHandler);

    router.get("/").handler(rc -> {
      HttpServerResponse response = rc.response();
      response.putHeader("content-type", "text/html");
      response.setChunked(true);
      response.write("<html><body>");
      response.write("<a href=\"/saml-login\">/saml-login</a><br>");
      response.write("<a href=\"/saml-regenerate\">/regenerate</a>");
      response.end("</body></html>");
    });


    // TODO: remove, this is a testing endpoint
    router.get("/saml-config-check").handler(rc -> {
      ConfigurationsClient.getConfiguration(rc)
        .setHandler(configuration -> {
          if (configuration.failed()) {
            log.warn("Failed to retrive configuration. ", configuration.cause());
            rc.response()
              .setStatusCode(500)
              .end("Failed to retrive configuration: " + configuration.cause().getMessage());
          } else {
            rc.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(JsonObject.mapFrom(configuration.result()).encodePrettily());
          }
        });
    });

    router.get("/saml-regenerate").handler(this::regenerateHandler);
    router.get("/saml-login").handler(this::loginHandler);
    router.post(CALLBACK_ENDPOINT).handler(BodyHandler.create().setMergeFormAttributes(true));
    router.post(CALLBACK_ENDPOINT).handler(this::callbackHandler);


    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080, "0.0.0.0", listenHandler -> {
        if (listenHandler.failed()) {
          startFuture.fail(listenHandler.cause());
        } else {
          log.info("HTTP server listening on port {}", listenHandler.result().actualPort());
          startFuture.complete();
        }
      });

  }


  private void loginHandler(RoutingContext routingContext) {
    VertxWebContext vertxWebContext = VertxUtils.createWebContext(routingContext);

    findSaml2Client(routingContext, false)     // do not allow login, if config is missing
      .setHandler(samlClientHandler -> {
        HttpAction action;
        if (samlClientHandler.succeeded()) {
          SAML2Client saml2Client = samlClientHandler.result();
          try {
            action = saml2Client.redirect(vertxWebContext); // highly blocking
          } catch (HttpAction httpAction) {
            action = httpAction;
          }
          new DefaultHttpActionAdapter().adapt(action.getCode(), vertxWebContext);
        } else {
          log.warn("Login called but cannot load client to handle", samlClientHandler.cause());
          routingContext.response()
            .setStatusCode(500)
            .end(samlClientHandler.cause().getMessage());
        }
      });
  }

  private void callbackHandler(RoutingContext routingContext) {
    final VertxWebContext webContext = VertxUtils.createWebContext(routingContext);


    findSaml2Client(routingContext, false) // How can someone rich this point if no stored configuration? Obviously an error.
      .setHandler(samlClientHandler -> {

        if (samlClientHandler.failed()) {
          routingContext.response().setStatusCode(500).end(samlClientHandler.cause().getMessage());
        } else {
          try {
            SAML2Client client = samlClientHandler.result();
            SAML2Credentials credentials = client.getCredentials(webContext);
            log.debug("credentials: {}", credentials);

            final CommonProfile profile = client.getUserProfile(credentials, webContext);
            log.debug("profile: {}", profile);

            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/plain");
            response.end("Successful authentication. A valid JWT will be returned here. \n\nCredentials: " + credentials + "\n\nProfile" + profile);

          } catch (HttpAction httpAction) {
            new DefaultHttpActionAdapter().adapt(httpAction.getCode(), webContext);
          }
        }
      });
  }

  private void regenerateHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    regenerateSaml2Config(routingContext)
      .setHandler(regenerationHandler -> {
        if (regenerationHandler.failed()) {
          log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
          response.setStatusCode(404).end("Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage());
        } else {
          String metadata = regenerationHandler.result();
          response.headers().add("content-type", "application/xml");
          response.end(metadata);
        }
      });
  }


  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Future<String> result = Future.future();

    findSaml2Client(routingContext, true) // generate KeyStore if missing
      .setHandler(handler -> {
        if (handler.failed()) {
          result.fail(handler.cause());
        } else {
          SAML2Client saml2Client = handler.result();

          vertx.executeBlocking(blockingCode -> {

            SAML2ClientConfiguration cfg = saml2Client.getConfiguration();

            // force metadata generation then init
            cfg.setForceServiceProviderMetadataGeneration(true);
            saml2Client.reinit(VertxUtils.createWebContext(routingContext));
            cfg.setForceServiceProviderMetadataGeneration(false);

            blockingCode.complete(saml2Client.getServiceProviderMetadataResolver().getMetadata());

          }, result);
        }
      });

    return result;
  }

  private Future<SAML2Client> findSaml2Client(RoutingContext routingContext, boolean generateMissingConfig) {

    String tenantId = OkapiHelper.okapiHeaders(routingContext).getTenant();

    final Clients clients = config.getClients();
    assertNotNull("clients", clients);

    Future<SAML2Client> result = Future.future();

    try {
      final Client client = clients.findClient(tenantId);
      if (client != null && client instanceof SAML2Client) {
        result.complete((SAML2Client) client);
      } else {
        result.fail("No client loaded or not a SAML2 client.");
      }
    } catch (TechnicalException ex) {

      // Client not loaded, try to load from configuration
      SamlClientLoader.loadFromConfiguration(routingContext, generateMissingConfig)
        .setHandler(clientResult -> {
          if (clientResult.failed()) {
            result.fail(clientResult.cause());
          } else {
            SAML2Client loadedClient = clientResult.result();

            List<Client> registeredClients = clients.getClients();
            if (registeredClients == null) {
              clients.setClients(loadedClient);
            } else {
              registeredClients.add(loadedClient);
            }
            // TODO: need manual reinit?
            // clients.reinit();
            result.complete(loadedClient);
          }
        });
    }

    return result;
  }

  /**
   * A HACK for disable HTTPS security checks. DO NOT USE IN PRODUCTION!
   * https://stackoverflow.com/a/2893932
   */
  private void trustAllCertificates() {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[]{
      new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }

        public void checkClientTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
        }
      }
    };

    // Install the all-trusting trust manager
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (GeneralSecurityException e) {
    }
  }

}
