package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.jaxrs.resource.SamlResource;
import org.folio.rest.tools.utils.BinaryOutStream;
import org.folio.session.NoopSession;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.VertxUtils;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;

import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.pac4j.core.util.CommonHelper.assertNotNull;

/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements SamlResource {

  private static final Logger log = LoggerFactory.getLogger(SamlAPI.class);

  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    log.info("check");
    findSaml2Client(routingContext, false)
      .setHandler(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withPlainOK("false")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withPlainOK("true")));
        }
      });
  }

  @Override
  public void getSamlLogin(RoutingContext routingContext, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    registerFakeSession(routingContext);

    findSaml2Client(routingContext, false)     // do not allow login, if config is missing
      .setHandler(samlClientHandler -> {
        Response response;
        if (samlClientHandler.succeeded()) {
          SAML2Client saml2Client = samlClientHandler.result();
          try {
            RedirectAction redirectAction = saml2Client.getRedirectAction(VertxUtils.createWebContext(routingContext));
            if (redirectAction.getType().equals(RedirectAction.RedirectType.REDIRECT)) {
              response = GetSamlLoginResponse.withMovedTemporarily(redirectAction.getLocation()); // 302
            } else {
              response = GetSamlLoginResponse.withHtmlOK(redirectAction.getContent()); // 200 -> html form
            }
          } catch (HttpAction httpAction) {
            response = HttpActionMapper.toResponse(httpAction);
          }
        } else {
          log.warn("Login called but cannot load client to handle", samlClientHandler.cause());
          response = GetSamlLoginResponse.withPlainInternalServerError("Login called but cannot load client to handle");
        }
        asyncResultHandler.handle(Future.succeededFuture(response));
      });
  }

  @Override
  public void postSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    registerFakeSession(routingContext);

    final VertxWebContext webContext = VertxUtils.createWebContext(routingContext);

    findSaml2Client(routingContext, false) // How can someone rich this point if no stored configuration? Obviously an error.
      .setHandler(samlClientHandler -> {

        Response response;
        if (samlClientHandler.failed()) {
          response = PostSamlCallbackResponse.withPlainInternalServerError(samlClientHandler.cause().getMessage());
        } else {
          try {
            SAML2Client client = samlClientHandler.result();

            SAML2Credentials credentials = client.getCredentials(webContext);
            log.debug("credentials: {}", credentials);


            final CommonProfile profile = client.getUserProfile(credentials, webContext);
            log.debug("profile: {}", profile);

            String message = "Successful authentication. A valid JWT will be returned here. \n\nCredentials: " + credentials + "\n\nProfile" + profile;
            response = PostSamlCallbackResponse.withPlainOK(message);

          } catch (HttpAction httpAction) {
            response = HttpActionMapper.toResponse(httpAction);
          }
        }
        asyncResultHandler.handle(Future.succeededFuture(response));
      });
  }

  @Override
  public void getSamlRegenerate(RoutingContext routingContext, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    regenerateSaml2Config(routingContext)
      .setHandler(regenerationHandler -> {
        if (regenerationHandler.failed()) {
          log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
          String message = "Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage();
          asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withPlainInternalServerError(message)));
        } else {
          String metadata = regenerationHandler.result();

          BinaryOutStream outStream = new BinaryOutStream();
          outStream.setData(metadata.getBytes(StandardCharsets.UTF_8));
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
            result.complete(loadedClient);
          }
        });
    }

    return result;
  }

  /**
   * Registers a no-op session. Pac4j want to access session variablas and fails if there is no session.
   *
   * @param routingContext the current routing context
   */
  private void registerFakeSession(RoutingContext routingContext) {
    routingContext.setSession(new NoopSession());
  }


}
