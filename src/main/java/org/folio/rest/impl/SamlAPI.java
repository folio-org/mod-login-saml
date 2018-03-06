package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.impl.SessionImpl;
import org.folio.config.ConfigurationsClient;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlClientComposite;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.SamlResource;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.session.NoopSession;
import org.folio.util.*;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.UrlCheckResult;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.springframework.util.StringUtils;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

            // Get user id
            //String query = null;
            List samlAttributeList = (List) credentials.getUserProfile().getAttribute(samlAttributeName);
            if (samlAttributeList == null || samlAttributeList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainBadRequest("SAML attribute doesn't exist: " + samlAttributeName)));
              return;
            }
            final String samlAttributeValue = samlAttributeList.get(0).toString();

            final String usersCql = userPropertyName +
              "=="
              + QUOTATION_MARK_CHARACTER + samlAttributeValue + QUOTATION_MARK_CHARACTER;

            final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();

            OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);

            Map<String, String> headers = new HashMap<>();
            headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, parsedHeaders.getToken());

            HttpClientInterface usersClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
            usersClient.setDefaultHeaders(headers);
            usersClient.request(userQuery)
              .whenComplete((userQueryResponse, ex) -> {
                if (!org.folio.rest.tools.client.Response.isSuccess(userQueryResponse.getCode())) {
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(userQueryResponse.getError().toString())));
                } else { // success
                  JsonObject resultObject = userQueryResponse.getBody();

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


                    HttpClientInterface tokenClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
                    tokenClient.setDefaultHeaders(headers);
                    try {
                      tokenClient.request(HttpMethod.POST, payload, "/token", null)
                        .whenComplete((tokenResponse, tokenError) -> {
                          if (!org.folio.rest.tools.client.Response.isSuccess(tokenResponse.getCode())) {
                            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(tokenResponse.getError().toString())));
                          } else {
                            final String authToken = tokenResponse.getHeaders().get(OkapiHeaders.OKAPI_TOKEN_HEADER);

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
                    } catch (Exception httpClientEx) {
                      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(httpClientEx.getMessage())));
                    }

                  }

                }
              });


          } catch (HttpAction httpAction) {
            asyncResultHandler.handle(Future.succeededFuture(HttpActionMapper.toResponse(httpAction)));
          } catch (Exception ex) {
            String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Unknown error: " + ex.getClass().getName();
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.withPlainInternalServerError(message)));
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

          ConfigurationsClient.storeEntry(OkapiHelper.okapiHeaders(okapiHeaders), SamlConfiguration.METADATA_INVALIDATED_CODE, "false")
            .setHandler(configurationEntryStoredEvent -> {

              if (configurationEntryStoredEvent.failed()) {
                asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withPlainInternalServerError("Cannot persist metadata invalidated flag!")));
              } else {
                String metadata = regenerationHandler.result();

                Base64Util.encode(vertxContext, metadata)
                  .setHandler(base64Result -> {
                    if (base64Result.failed()) {
                      String message = base64Result.cause() == null ? "" : base64Result.cause().getMessage();
                      GetSamlRegenerateResponse response = GetSamlRegenerateResponse.withPlainInternalServerError("Cannot encode file content " + message);
                      asyncResultHandler.handle(Future.succeededFuture(response));
                    } else {
                      SamlRegenerateResponse responseEntity = new SamlRegenerateResponse()
                        .withFileContent(base64Result.result().toString(StandardCharsets.UTF_8));
                      asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.withJsonOK(responseEntity)));
                    }

                  });

              }
            });
        }
      });
  }

  @Override
  public void getSamlConfiguration(RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    ConfigurationsClient.getConfiguration(OkapiHelper.okapiHeaders(okapiHeaders))
      .setHandler(configurationResult -> {

        AsyncResult<SamlConfig> result = configurationResult.map(this::configToDto);

        if (result.failed()) {
          log.warn("Cannot load configuration", result.cause());
          asyncResultHandler.handle(
            Future.succeededFuture(
              GetSamlConfigurationResponse.withPlainInternalServerError("Cannot get configuration")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlConfigurationResponse.withJsonOK(result.result())));
        }

      });

  }


  @Override
  public void putSamlConfiguration(SamlConfigRequest updatedConfig, RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    checkConfigValues(updatedConfig, vertxContext.owner())
      .setHandler(checkValuesHandler -> {
        if (checkValuesHandler.failed()) {
          SamlValidateResponse errorEntity = new SamlValidateResponse().withValid(false).withError(checkValuesHandler.cause().getMessage());
          asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.withJsonBadRequest(errorEntity)));
        } else {
          OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
          ConfigurationsClient.getConfiguration(parsedHeaders).setHandler((AsyncResult<SamlConfiguration> configRes) -> {
            if (configRes.failed()) {
              asyncResultHandler.handle(Future.succeededFuture(
                PutSamlConfigurationResponse.withPlainInternalServerError(configRes.cause() != null ? configRes.cause().getMessage() : "Cannot load current configuration")));
            } else {

              Map<String, String> updateEntries = new HashMap<>();

              SamlConfiguration config = configRes.result();

              ConfigEntryUtil.valueChanged(config.getIdpUrl(), updatedConfig.getIdpUrl().toString(), idpUrl -> {
                updateEntries.put(SamlConfiguration.IDP_URL_CODE, idpUrl);
                updateEntries.put(SamlConfiguration.METADATA_INVALIDATED_CODE, "true");
              });

              ConfigEntryUtil.valueChanged(config.getSamlBinding(), updatedConfig.getSamlBinding().toString(), samlBindingCode ->
                updateEntries.put(SamlConfiguration.SAML_BINDING_CODE, samlBindingCode));

              ConfigEntryUtil.valueChanged(config.getSamlAttribute(), updatedConfig.getSamlAttribute(), samlAttribute ->
                updateEntries.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, samlAttribute));

              ConfigEntryUtil.valueChanged(config.getUserProperty(), updatedConfig.getUserProperty(), userProperty ->
                updateEntries.put(SamlConfiguration.USER_PROPERTY_CODE, userProperty));

              ConfigEntryUtil.valueChanged(config.getOkapiUrl(), updatedConfig.getOkapiUrl().toString(), okapiUrl -> {
                updateEntries.put(SamlConfiguration.OKAPI_URL, okapiUrl);
                updateEntries.put(SamlConfiguration.METADATA_INVALIDATED_CODE, "true");
              });
              
              storeConfigEntries(rc, asyncResultHandler, parsedHeaders, updateEntries);

            }
          });
        }
      });


  }

  private void storeConfigEntries(RoutingContext rc, Handler<AsyncResult<Response>> asyncResultHandler, OkapiHeaders parsedHeaders, Map<String, String> updateEntries) {
    ConfigurationsClient.storeEntries(parsedHeaders, updateEntries)
      .setHandler(configuratiuonSavedEvent -> {
        if (configuratiuonSavedEvent.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutSamlConfigurationResponse.withPlainInternalServerError(configuratiuonSavedEvent.cause() != null ? configuratiuonSavedEvent.cause().getMessage() : "Cannot save configuration")));
        } else {
          findSaml2Client(rc, true, true)
            .setHandler(configurationLoadEvent -> {
              if (configurationLoadEvent.failed()) {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutSamlConfigurationResponse.withPlainInternalServerError(configurationLoadEvent.cause() != null ? configurationLoadEvent.cause().getMessage() : "Cannot reload current configuration")));
              } else {

                SamlConfiguration newConf = configurationLoadEvent.result().getConfiguration();
                SamlConfig dto = configToDto(newConf);

                asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.withJsonOK(dto)));

              }
            });
        }
      });
  }


  @Override
  public void getSamlValidate(Type type, String value, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    Handler<AsyncResult<UrlCheckResult>> handler = hnd -> {
      if (hnd.succeeded()) {
        UrlCheckResult result = hnd.result();
        SamlValidateResponse response = new SamlValidateResponse();
        if (result.isSuccess()) {
          response.setValid(true);
        } else {
          response.setValid(false);
          response.setError(result.getMessage());
        }
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.withJsonOK(response)));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.withPlainInternalServerError("unknown error")));
      }
    };

    switch (type) {
      case idpurl:
        UrlUtil.checkIdpUrl(value, vertxContext.owner()).setHandler(handler);
        break;
      case okapiurl:
        UrlUtil.checkOkapiUrl(value, vertxContext.owner()).setHandler(handler);
        break;
      default:
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.withPlainInternalServerError("unknown type: " + type.toString())));
    }


  }

  private Future<Void> checkConfigValues(SamlConfigRequest updatedConfig, Vertx vertx) {

    Future<Void> result = Future.future();

    List<Future> futures = Arrays.asList(UrlUtil.checkOkapiUrl(updatedConfig.getOkapiUrl().toString(), vertx),
      UrlUtil.checkIdpUrl(updatedConfig.getIdpUrl().toString(), vertx));

    CompositeFuture.all(futures)
      .setHandler(hnd -> {
        if (hnd.succeeded()) {
          // all success
          Optional<Future> failedCheck = futures.stream()
            .filter(future -> !((UrlCheckResult) future.result()).getStatus().equals(UrlCheckResult.Status.SUCCESS))
            .findFirst();

          if (failedCheck.isPresent()) {
            Future<UrlCheckResult> future = failedCheck.get();
            UrlCheckResult urlCheckResult = future.result();
            result.fail(urlCheckResult.getMessage());

          } else {
            result.complete();
          }
        } else {
          result.fail(hnd.cause());
        }
      });

    return result;

  }

  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Future<String> result = Future.future();
    final Vertx vertx = routingContext.vertx();

    findSaml2Client(routingContext, false, false) // generate KeyStore if missing
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

  /**
   * Converts internal {@link SamlConfiguration} object to DTO, checks illegal values
   */
  private SamlConfig configToDto(SamlConfiguration config) {
    SamlConfig samlConfig = new SamlConfig()
      .withSamlAttribute(config.getSamlAttribute())
      .withUserProperty(config.getUserProperty())
      .withMetadataInvalidated(Boolean.valueOf(config.getMetadataInvalidated()));
    try {
      URI uri = URI.create(config.getOkapiUrl());
      samlConfig.setOkapiUrl(uri);
    } catch (Exception e) {
      log.debug("Okapi URI is in a bad format");
      samlConfig.setOkapiUrl(URI.create(""));
    }

    try {
      URI uri = URI.create(config.getIdpUrl());
      samlConfig.setIdpUrl(uri);
    } catch (Exception x) {
      samlConfig.setIdpUrl(URI.create(""));
    }

    try {
      SamlConfig.SamlBinding samlBinding = SamlConfig.SamlBinding.fromValue(config.getSamlBinding());
      samlConfig.setSamlBinding(samlBinding);
    } catch (Exception x) {
      samlConfig.setSamlBinding(null);
    }

    return samlConfig;
  }

}
