package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.jaxrs.resource.SamlResource;
import org.folio.rest.jaxrs.resource.support.ResponseWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.session.NoopSession;
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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static org.pac4j.core.util.CommonHelper.assertNotNull;

/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements SamlResource {

  public static final String CALLBACK_ENDPOINT = "/saml/callback";
  public static final String LOGIN_ENDPOINT = "/saml/login";
  public static final String REGENERATE_ENDPOINT = "/saml/regenerate";
  public static final String CHECK_ENDPOINT = "/saml/check";
  private final Logger log = LoggerFactory.getLogger(SamlAPI.class);
//  private Config config = null;


  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  private void getSamlCheck(RoutingContext routingContext, Handler<AsyncResult<Response>> asyncResultHandler) {

    findSaml2Client(routingContext, false)
      .setHandler(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withPlainOK("false")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withPlainOK("true")));
        }
      });
  }


  private void getSamlLogin(RoutingContext routingContext, Handler<AsyncResult<Response>> asyncResultHandler) {

    routingContext.setSession(new NoopSession()); // registering fake session

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
          // TODO: need to call async result handler?
        } else {
          log.warn("Login called but cannot load client to handle", samlClientHandler.cause());
          asyncResultHandler.handle(Future.succeededFuture(GetSamlLoginResponse.withPlainInternalServerError("Login called but cannot load client to handle")));
//          routingContext.response()
//            .setStatusCode(500)
//            .end(samlClientHandler.cause().getMessage());
        }
      });
  }

  private void postSamlCallback(RoutingContext routingContext, Handler<AsyncResult<Response>> asyncResultHandler) {

    routingContext.setSession(new NoopSession()); // registering fake session

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

//            HttpServerResponse response = routingContext.response();
//            response.putHeader("content-type", "text/plain");
//            response.end("Successful authentication. A valid JWT will be returned here. \n\nCredentials: " + credentials + "\n\nProfile" + profile);
            String message = "Successful authentication. A valid JWT will be returned here. \n\nCredentials: " + credentials + "\n\nProfile" + profile;
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainOK(message)));

          } catch (HttpAction httpAction) {
            new DefaultHttpActionAdapter().adapt(httpAction.getCode(), webContext);
            // todo: need co call asyncResultHandler?
          }
        }
      });
  }

  private void getSamlRegenerate(RoutingContext routingContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    //HttpServerResponse response = routingContext.response();
    regenerateSaml2Config(routingContext)
      .setHandler(regenerationHandler -> {
        if (regenerationHandler.failed()) {
          log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
//          response.setStatusCode(404).end("Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage());
          String message = "Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage();
          asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withPlainInternalServerError(message)));
        } else {
          String metadata = regenerationHandler.result();
//          response.headers().add("content-type", "application/xml");
//          response.end(metadata);

          OutStream outStream = new OutStream();
          outStream.setData(metadata);
          asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withXmlOK(outStream)));
        }
      });
  }


  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Future<String> result = Future.future();
    final Vertx vertx = routingContext.vertx();

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
    Config config = SamlConfigHolder.getInstance().getConfig();
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


  @Override
  public void getSamlRegenerate(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void getSamlLogin(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void postSamlCallback(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void getSamlCheck(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {


//    asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withPlainOK("true");));


    SamlConfigHolder holder = SamlConfigHolder.getInstance();

    if (holder == null) {
      log.error("holder is null");
    } else {
      Config config = holder.getConfig();
      if (config == null) {
        log.error("config is null");
      } else {
//        SessionStore sessionStore =
        log.info("Clients: " + config.getClients());
      }
    }
    asyncResultHandler.handle(Future.succeededFuture(ResponseWrapper.status(200).type(MediaType.TEXT_PLAIN).entity("true").build()));
  }
}
