package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.impl.SessionImpl;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlClientComposite;
import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.OkapiClient;
import org.folio.rest.jaxrs.model.SamlCheck;
import org.folio.rest.jaxrs.model.SamlLogin;
import org.folio.rest.jaxrs.model.SamlLoginRequest;
import org.folio.rest.jaxrs.resource.SamlResource;
import org.folio.rest.tools.utils.BinaryOutStream;
import org.folio.session.NoopSession;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.UrlUtil;
import org.folio.util.VertxUtils;
import org.folio.util.model.OkapiHeaders;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements SamlResource {

  private static final Logger log = LoggerFactory.getLogger(SamlAPI.class);
  public static final String QUOTATION_MARK_CHARACTER = "\"";

  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    findSaml2Client(routingContext, false, false)
      .setHandler(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withJsonOK(new SamlCheck().withActive(false))));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.withJsonOK(new SamlCheck().withActive(true))));
        }
      });
  }

  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    String stripesUrl = requestEntity.getStripesUrl();

    // register non-persistent session (this request only) to overWrite relayState
    Session session = new SessionImpl(new PRNG(vertxContext.owner()));
    session.put("samlRelayState", stripesUrl);
    routingContext.setSession(session);


    findSaml2Client(routingContext, false, false) // do not allow login, if config is missing
      .setHandler(samlClientHandler -> {
        Response response;
        if (samlClientHandler.succeeded()) {
          SAML2Client saml2Client = samlClientHandler.result().getClient();
          try {
            RedirectAction redirectAction = saml2Client.getRedirectAction(VertxUtils.createWebContext(routingContext));
            String responseJsonString = redirectAction.getContent();
            SamlLogin dto = Json.decodeValue(responseJsonString, SamlLogin.class);
            routingContext.response().headers().clear(); // saml2Client sets Content-Type: text/html header
            response = PostSamlLoginResponse.withJsonOK(dto);
          } catch (HttpAction httpAction) {
            response = HttpActionMapper.toResponse(httpAction);
          }
        } else {
          log.warn("Login called but cannot load client to handle", samlClientHandler.cause());
          response = PostSamlLoginResponse.withPlainInternalServerError("Login called but cannot load client to handle");
        }
        asyncResultHandler.handle(Future.succeededFuture(response));
      });
  }


  @Override
  public void postSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    registerFakeSession(routingContext);

    final VertxWebContext webContext = VertxUtils.createWebContext(routingContext);
    final String relayState = webContext.getRequestParameter("RelayState"); // There is no better way to get RelayState.
    final URI originalUrl = new URI(relayState); // throws exception if invalid -> automatic bad request response.
    final URI stripesBaseUrl = UrlUtil.parseBaseUrl(originalUrl);

    findSaml2Client(routingContext, false, false)
      .setHandler(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(
            Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(samlClientHandler.cause().getMessage())));
        } else {
          try {
            final SamlClientComposite samlClientComposite = samlClientHandler.result();
            final SAML2Client client = samlClientComposite.getClient();
            final SamlConfiguration configuration = samlClientComposite.getConfiguration();
            String userPropertyName = configuration.getUserProperty() == null ? "externalSystemId" : configuration.getUserProperty();
            String samlAttributeName = configuration.getSamlAttribute() == null ? "UserID" : configuration.getSamlAttribute();


            SAML2Credentials credentials = client.getCredentials(webContext);
            log.debug("credentials: {}", credentials);

            // Get user id
            //String query = null;
            List samlAttributeList = (List) credentials.getUserProfile().getAttribute(samlAttributeName);
            if (samlAttributeList == null || samlAttributeList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainBadRequest("SAML attribute doesn't exist: " + samlAttributeName)));
              return;
            }
            final String samlAttributeValue = samlAttributeList.get(0).toString();

            final String usersCql = new StringBuilder()
              .append(userPropertyName)
              .append("==")
              .append(QUOTATION_MARK_CHARACTER).append(samlAttributeValue).append(QUOTATION_MARK_CHARACTER)
              .toString();

            final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();

            // TODO: workaround OkapiClient(routingContext) not sending Accept header
            HashMap<String, String> okapiClientRequestHeaders = new HashMap<>(okapiHeaders);
            okapiClientRequestHeaders.put("Accept", "application/json,text/plain");

            OkapiClient usersClient = new OkapiClient(okapiHeaders.get(OkapiHeaders.OKAPI_URL_HEADER), vertxContext.owner(), okapiClientRequestHeaders);
            usersClient.get(userQuery, okapiClientResponse -> {
              if (okapiClientResponse.failed()) {
                asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(okapiClientResponse.cause().getMessage())));
              } else {
                JsonObject resultObject = new JsonObject(okapiClientResponse.result());

                int recordCount = resultObject.getInteger("totalRecords");
                if (recordCount > 1) {
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainBadRequest("More than one user record found!")));
                } else if (recordCount == 0) {
                  String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
                  log.warn(message);
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainBadRequest(message)));
                } else {

                  final JsonObject userObject = resultObject.getJsonArray("users").getJsonObject(0);
                  String userId = userObject.getString("id");
                  if (!userObject.getBoolean("active")) {
                    log.warn("User " + userId + " is inactive!"); // TODO: should we deny login from an inactive account?
                  }

                  JsonObject payload = new JsonObject().put("payload", new JsonObject().put("sub", userObject.getString("username")).put("user_id", userId));

                  OkapiClient tokenClient = new OkapiClient(okapiHeaders.get(OkapiHeaders.OKAPI_URL_HEADER), vertxContext.owner(), okapiClientRequestHeaders);
                  tokenClient.post("/token", payload.toString(), tokenResponse -> {
                    if (tokenResponse.failed()) {
                      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(tokenResponse.cause().getMessage())));
                    } else {
                      // we don't need response, only the token header.
                      final String authToken = tokenClient.getRespHeaders().get(OkapiHeaders.OKAPI_TOKEN_HEADER);
                      // log.info("Auth token: " + authToken);

                      final String location = UriBuilder.fromUri(stripesBaseUrl)
                        .path("sso-landing")
                        .queryParam("ssoToken", authToken)
                        .queryParam("fwd", originalUrl.getPath())
                        .build()
                        .toString();

                      final String cookie = new NewCookie("ssoToken", authToken, "", originalUrl.getHost(), "", 3600, false).toString();

                      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withMovedTemporarily(cookie, authToken, location)));

                    }
                  });
                }
              }
            });
          } catch (HttpAction httpAction) {
            asyncResultHandler.handle(Future.succeededFuture(HttpActionMapper.toResponse(httpAction)));
          }
        }
      });
  }


  @Override
  public void getSamlRegenerate(RoutingContext routingContext, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    regenerateSaml2Config(routingContext)
      .setHandler(regenerationHandler -> {
        if (regenerationHandler.failed()) {
          log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
          String message =
            "Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage();
          asyncResultHandler
            .handle(Future.succeededFuture(GetSamlRegenerateResponse.withPlainInternalServerError(message)));
        } else {
          String metadata = regenerationHandler.result();

          BinaryOutStream outStream = new BinaryOutStream();
          outStream.setData(metadata.getBytes(StandardCharsets.UTF_8));
          asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withXmlOK("attachment; filename=sp-metadata.xml", outStream)));
        }
      });
  }

  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Future<String> result = Future.future();
    final Vertx vertx = routingContext.vertx();

    findSaml2Client(routingContext, true, true) // generate KeyStore if missing
      .setHandler(handler -> {
        if (handler.failed()) {
          result.fail(handler.cause());
        } else {
          SAML2Client saml2Client = handler.result().getClient();

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

  /**
   * @param routingContext        the actual routing context
   * @param generateMissingConfig if the encryption key and passwords are missing should we generate and store it?
   * @param reloadClient          should we drop the loaded client and reload it with (maybe modified) configuration?
   * @return Future of loaded {@link SAML2Client} or failed future if it cannot be loaded.
   */
  private Future<SamlClientComposite> findSaml2Client(RoutingContext routingContext, boolean generateMissingConfig, boolean reloadClient) {

    String tenantId = OkapiHelper.okapiHeaders(routingContext).getTenant();
    SamlConfigHolder configHolder = SamlConfigHolder.getInstance();

    SamlClientComposite clientComposite = configHolder.findClient(tenantId);

    if (clientComposite != null && !reloadClient) {
      return Future.succeededFuture(clientComposite);
    } else {
      if (reloadClient) {
        configHolder.removeClient(tenantId);
        clientComposite = null;
      }

      Future<SamlClientComposite> result = Future.future();
      SamlClientLoader.loadFromConfiguration(routingContext, generateMissingConfig)
        .setHandler(clientResult -> {
          if (clientResult.failed()) {
            result.fail(clientResult.cause());
          } else {
            SamlClientComposite newClientComposite = clientResult.result();
            configHolder.putClient(tenantId, newClientComposite);
            result.complete(newClientComposite);
          }
        });
      return result;
    }

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
