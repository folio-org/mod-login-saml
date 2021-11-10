package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.vertx.core.http.HttpHeaders.ORIGIN;
import static io.vertx.core.http.HttpHeaders.VARY;
import static org.pac4j.saml.state.SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ConfigurationsClient;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlClientComposite;
import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.SamlCheck;
import org.folio.rest.jaxrs.model.SamlConfig;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.jaxrs.model.SamlLogin;
import org.folio.rest.jaxrs.model.SamlLoginRequest;
import org.folio.rest.jaxrs.model.SamlRegenerateResponse;
import org.folio.rest.jaxrs.model.SamlValidateGetType;
import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.rest.jaxrs.resource.Saml;
import org.folio.rest.jaxrs.resource.Saml.PostSamlCallbackResponse.HeadersFor302;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.session.NoopSession;
import org.folio.util.Base64Util;
import org.folio.util.ConfigEntryUtil;
import org.folio.util.DummySessionStore;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.UrlUtil;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.UrlCheckResult;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.springframework.util.StringUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.impl.Utils;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;

/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements Saml {

  private static final Logger log = LogManager.getLogger(SamlAPI.class);
  public static final String QUOTATION_MARK_CHARACTER = "\"";
  public static final String CSRF_TOKEN = "csrfToken";
  public static final String RELAY_STATE = "relayState";

  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders,
                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    findSaml2Client(routingContext, false, false)
      .onComplete(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(false))));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(true))));
        }
      });
  }


  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String csrfToken = UUID.randomUUID().toString();
    String stripesUrl = requestEntity.getStripesUrl();
    String relayState = stripesUrl + (stripesUrl.indexOf('?') >= 0 ? '&' : '?') + CSRF_TOKEN + '=' + csrfToken;
    Cookie relayStateCookie = Cookie.cookie(RELAY_STATE, relayState)
        .setPath("/").setHttpOnly(true).setSecure(true);
    routingContext.addCookie(relayStateCookie);


    // register non-persistent session (this request only) to overWrite relayState
    Session session = new SharedDataSessionImpl(new PRNG(vertxContext.owner()));
    session.put(SAML_RELAY_STATE_ATTRIBUTE, relayState);
    routingContext.setSession(session);

    findSaml2Client(routingContext, false, false) // do not allow login, if config is missing
      .map(SamlClientComposite::getClient)
      .map(saml2client -> postSamlLoginResponse(routingContext, saml2client))
      .recover(e -> {
        log.warn(e.getMessage(), e);
        return Future.succeededFuture(PostSamlLoginResponse.respond500WithTextPlain("Internal Server Error"));
      })
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)));
  }

  private Response postSamlLoginResponse(RoutingContext routingContext, SAML2Client saml2Client) {
    try {
      final SessionStore sessionStore = new DummySessionStore(routingContext.vertx(), routingContext.session());
      final VertxWebContext webContext = new VertxWebContext(routingContext, sessionStore);
      RedirectionAction redirectionAction = saml2Client
          .getRedirectionAction(webContext, sessionStore)
          .orElse(null);
      if (! (redirectionAction instanceof OkAction)) {
        throw new IllegalStateException("redirectionAction must be OkAction: " + redirectionAction);
      }
      String responseJsonString = ((OkAction) redirectionAction).getContent();
      SamlLogin dto = Json.decodeValue(responseJsonString, SamlLogin.class);
      routingContext.response().headers().clear(); // saml2Client sets Content-Type: text/html header
      addCredentialsAndOriginHeaders(routingContext);
      return PostSamlLoginResponse.respond200WithApplicationJson(dto);
    } catch (HttpAction httpAction) {
      return HttpActionMapper.toResponse(httpAction);
    }
  }

  @Override
  public void postSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    registerFakeSession(routingContext);

    final SessionStore sessionStore = new DummySessionStore(routingContext.vertx(), routingContext.session());
    final VertxWebContext webContext = new VertxWebContext(routingContext, sessionStore);
    // Form parameters "RelayState" is not part webContext.
    final String relayState = routingContext.request().getFormAttribute("RelayState");
    URI relayStateUrl;
    try {
      relayStateUrl = new URI(relayState);
    } catch (URISyntaxException e1) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("Invalid relay state url: " + relayState)));
      return;
    }
    final URI originalUrl = relayStateUrl;
    final URI stripesBaseUrl = UrlUtil.parseBaseUrl(originalUrl);

    Cookie relayStateCookie = routingContext.getCookie(RELAY_STATE);
    if (relayStateCookie == null || !relayState.contentEquals(relayStateCookie.getValue())) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("CSRF attempt detected")));
      return;
    }

    findSaml2Client(routingContext, false, false)
      .onComplete(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(
            Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(samlClientHandler.cause().getMessage())));
        } else {
          try {
            final SamlClientComposite samlClientComposite = samlClientHandler.result();
            final SAML2Client client = samlClientComposite.getClient();
            final SamlConfiguration configuration = samlClientComposite.getConfiguration();
            String userPropertyName = configuration.getUserProperty() == null ? "externalSystemId" : configuration.getUserProperty();
            String samlAttributeName = configuration.getSamlAttribute() == null ? "UserID" : configuration.getSamlAttribute();

            SAML2Credentials credentials = (SAML2Credentials) client.getCredentials(webContext, sessionStore).get();

            // Get user id
            List<?> samlAttributeList = (List<?>) credentials.getUserProfile().getAttribute(samlAttributeName);
            if (samlAttributeList == null || samlAttributeList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("SAML attribute doesn't exist: " + samlAttributeName)));
              return;
            }
            final String samlAttributeValue = samlAttributeList.get(0).toString();

            final String usersCql = userPropertyName +
              "=="
              + QUOTATION_MARK_CHARACTER + samlAttributeValue + QUOTATION_MARK_CHARACTER;

            final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();

            OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);

            Map<String, String> headers = new HashMap<>();
            headers.put(XOkapiHeaders.TOKEN, parsedHeaders.getToken());

            HttpClientInterface usersClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
            usersClient.setDefaultHeaders(headers);
            usersClient.request(userQuery)
              .whenComplete((userQueryResponse, ex) -> {
                if (!org.folio.rest.tools.client.Response.isSuccess(userQueryResponse.getCode())) {
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(userQueryResponse.getError().toString())));
                } else { // success
                  JsonObject resultObject = userQueryResponse.getBody();

                  int recordCount = resultObject.getInteger("totalRecords");
                  if (recordCount > 1) {
                    asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("More than one user record found!")));
                  } else if (recordCount == 0) {
                    String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
                    log.warn(message);
                    asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain(message)));
                  } else {

                    final JsonObject userObject = resultObject.getJsonArray("users").getJsonObject(0);
                    String userId = userObject.getString("id");
                    if (!userObject.getBoolean("active")) {
                      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("Inactive user account!")));
                    } else {

                      JsonObject payload = new JsonObject().put("payload", new JsonObject().put("sub", userObject.getString("username")).put("user_id", userId));


                      HttpClientInterface tokenClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
                      tokenClient.setDefaultHeaders(headers);
                      try {
                        tokenClient.request(HttpMethod.POST, payload, "/token", null)
                          .whenComplete((tokenResponse, tokenError) -> {
                            if (!org.folio.rest.tools.client.Response.isSuccess(tokenResponse.getCode())) {
                              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(tokenResponse.getError().toString())));
                            } else {
                              String candidateAuthToken = null;
                              if (tokenResponse.getCode() == 200) {
                                candidateAuthToken = tokenResponse.getHeaders().get(XOkapiHeaders.TOKEN);
                              } else { //mod-authtoken v2.x returns 201, with token in JSON response body
                                try {
                                  candidateAuthToken = tokenResponse.getBody().getString("token");
                                } catch(Exception e) {
                                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(e.getMessage())));
                                }
                              }
                              final String authToken = candidateAuthToken;

                              final String location = UriBuilder.fromUri(stripesBaseUrl)
                                .path("sso-landing")
                                .queryParam("ssoToken", authToken)
                                .queryParam("fwd", originalUrl.getPath())
                                .build()
                                .toString();

                              final String cookie = new NewCookie("ssoToken", authToken, "", originalUrl.getHost(), "", 3600, false).toString();

                              HeadersFor302 headers302 = PostSamlCallbackResponse.headersFor302().withSetCookie(cookie).withXOkapiToken(authToken).withLocation(location);
                              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond302(headers302)));

                            }
                          });
                      } catch (Exception httpClientEx) {
                        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(httpClientEx.getMessage())));
                      }
                    }

                  }

                }
              });


          } catch (HttpAction httpAction) {
            asyncResultHandler.handle(Future.succeededFuture(HttpActionMapper.toResponse(httpAction)));
          } catch (Exception ex) {
            String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Unknown error: " + ex.getClass().getName();
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(message)));
          }
        }
      });
  }


  @Override
  public void getSamlRegenerate(RoutingContext routingContext, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    regenerateSaml2Config(routingContext)
      .onComplete(regenerationHandler -> {
        if (regenerationHandler.failed()) {
          log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
          String message =
            "Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage();
          asyncResultHandler
            .handle(Future.succeededFuture(GetSamlRegenerateResponse.respond500WithTextPlain(message)));
        } else {

          ConfigurationsClient.storeEntry(OkapiHelper.okapiHeaders(okapiHeaders), SamlConfiguration.METADATA_INVALIDATED_CODE, "false")
            .onComplete(configurationEntryStoredEvent -> {

              if (configurationEntryStoredEvent.failed()) {
                asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.respond500WithTextPlain("Cannot persist metadata invalidated flag!")));
              } else {
                String metadata = regenerationHandler.result();

                Base64Util.encode(vertxContext, metadata)
                  .onComplete(base64Result -> {
                    if (base64Result.failed()) {
                      String message = base64Result.cause() == null ? "" : base64Result.cause().getMessage();
                      GetSamlRegenerateResponse response = GetSamlRegenerateResponse.respond500WithTextPlain("Cannot encode file content " + message);
                      asyncResultHandler.handle(Future.succeededFuture(response));
                    } else {
                      SamlRegenerateResponse responseEntity = new SamlRegenerateResponse()
                        .withFileContent(base64Result.result().toString(StandardCharsets.UTF_8));
                      asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.respond200WithApplicationJson(responseEntity)));
                    }

                  });

              }
            });
        }
      });
  }

  @Override
  public void getSamlConfiguration(RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ConfigurationsClient.getConfiguration(OkapiHelper.okapiHeaders(okapiHeaders))
      .onComplete(configurationResult -> {

        AsyncResult<SamlConfig> result = configurationResult.map(this::configToDto);

        if (result.failed()) {
          log.warn("Cannot load configuration", result.cause());
          asyncResultHandler.handle(
            Future.succeededFuture(
              GetSamlConfigurationResponse.respond500WithTextPlain("Cannot get configuration")));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlConfigurationResponse.respond200WithApplicationJson(result.result())));
        }

      });

  }


  @Override
  public void putSamlConfiguration(SamlConfigRequest updatedConfig, RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    checkConfigValues(updatedConfig, vertxContext.owner())
      .onComplete(checkValuesHandler -> {
        if (checkValuesHandler.failed()) {
          SamlValidateResponse errorEntity = new SamlValidateResponse().withValid(false).withError(checkValuesHandler.cause().getMessage());
          asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.respond400WithApplicationJson(errorEntity)));
        } else {
          OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
          ConfigurationsClient.getConfiguration(parsedHeaders).onComplete((AsyncResult<SamlConfiguration> configRes) -> {
            if (configRes.failed()) {
              asyncResultHandler.handle(Future.succeededFuture(
                PutSamlConfigurationResponse.respond500WithTextPlain(configRes.cause() != null ? configRes.cause().getMessage() : "Cannot load current configuration")));
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

              ConfigEntryUtil.valueChanged(config.getSamlAttribute(), updatedConfig.getIdmXml(), idmXml ->
                updateEntries.put(SamlConfiguration.SAML_IDM_XML, idmXml));

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
      .onComplete(configuratiuonSavedEvent -> {
        if (configuratiuonSavedEvent.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(
            PutSamlConfigurationResponse.respond500WithTextPlain(configuratiuonSavedEvent.cause() != null ? configuratiuonSavedEvent.cause().getMessage() : "Cannot save configuration")));
        } else {
          findSaml2Client(rc, true, true)
            .onComplete(configurationLoadEvent -> {
              if (configurationLoadEvent.failed()) {
                asyncResultHandler.handle(Future.succeededFuture(
                  PutSamlConfigurationResponse.respond500WithTextPlain(configurationLoadEvent.cause() != null ? configurationLoadEvent.cause().getMessage() : "Cannot reload current configuration")));
              } else {

                SamlConfiguration newConf = configurationLoadEvent.result().getConfiguration();
                SamlConfig dto = configToDto(newConf);

                asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.respond200WithApplicationJson(dto)));

              }
            });
        }
      });
  }

  @Override
  public void getSamlValidate(SamlValidateGetType type, String value, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (type == null) {
      asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond400WithApplicationJson(
        new SamlValidateResponse().withValid(false).withError("missing type parameter"))));
      return;
    }
    if (value == null) {
      asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond400WithApplicationJson(
        new SamlValidateResponse().withValid(false).withError("missing value parameter"))));
      return;
    }
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
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond200WithApplicationJson(response)));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond500WithTextPlain("unknown error")));
      }
    };

    switch (type) {
      case IDPURL:
        UrlUtil.checkIdpUrl(value, vertxContext.owner()).onComplete(handler);
        break;
      default:
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond400WithApplicationJson(
          new SamlValidateResponse().withValid(false).withError("unknown type: " + type))));
    }
  }

  private Future<Void> checkConfigValues(SamlConfigRequest updatedConfig, Vertx vertx) {

    Promise<Void> result = Promise.promise();

    List<Future> futures = Arrays.asList(UrlUtil.checkIdpUrl(updatedConfig.getIdpUrl().toString(), vertx)); //NOSONAR

    CompositeFuture.all(futures)
      .onComplete(hnd -> {
        if (hnd.succeeded()) {
          // all success
          Optional<Future> failedCheck = futures.stream() //NOSONAR
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

    return result.future();

  }

  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Promise<String> result = Promise.promise();
    final Vertx vertx = routingContext.vertx();

    findSaml2Client(routingContext, false, false)
      .onComplete(handler -> {
        if (handler.failed()) {
          result.fail(handler.cause());
        } else {
          SAML2Client saml2Client = handler.result().getClient();

          vertx.executeBlocking(blockingCode -> {
            SAML2Configuration cfg = saml2Client.getConfiguration();

            // force metadata generation then init
            cfg.setForceServiceProviderMetadataGeneration(true);
            saml2Client.init();
            cfg.setForceServiceProviderMetadataGeneration(false);

            try {
              blockingCode.complete(saml2Client.getServiceProviderMetadataResolver().getMetadata());
            } catch (Exception e) {
              blockingCode.fail(e);
            }
          }, result);
        }
      });
    return result.future();
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
      }

      Promise<SamlClientComposite> result = Promise.promise();
      SamlClientLoader.loadFromConfiguration(routingContext, generateMissingConfig)
        .onComplete(clientResult -> {
          if (clientResult.failed()) {
            result.fail(clientResult.cause());
          } else {
            SamlClientComposite newClientComposite = clientResult.result();
            configHolder.putClient(tenantId, newClientComposite);
            result.complete(newClientComposite);
          }
        });
      return result.future();
    }

  }

  /**
   * Registers a no-op session. Pac4j want to access session variables and fails if there is no session.
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

  @Override
  public void optionsSamlLogin(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptions(routingContext);
  }

  @Override
  public void optionsSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptions(routingContext);
  }

  private void handleOptions(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String origin = request.headers().get(ORIGIN);
    if (isInvalidOrigin(origin)) {
      response.setStatusCode(400).setStatusMessage("Missing/Invalid origin header").end();
      return;
    }
    response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ORIGIN);
    response.putHeader(ACCESS_CONTROL_ALLOW_METHODS, request.getHeader(ACCESS_CONTROL_REQUEST_METHOD));
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ACCESS_CONTROL_REQUEST_METHOD);
    response.putHeader(ACCESS_CONTROL_ALLOW_HEADERS, request.getHeader(ACCESS_CONTROL_REQUEST_HEADERS));
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ACCESS_CONTROL_REQUEST_HEADERS);
    response.setStatusCode(204).end();
  }

  private void addCredentialsAndOriginHeaders(RoutingContext routingContext) {
    String origin = routingContext.request().headers().get(ORIGIN);
    if (isInvalidOrigin(origin)) {
      return;
    }
    HttpServerResponse response = routingContext.response();
    response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
  }

  private boolean isInvalidOrigin(String origin) {
    return origin == null || origin.isBlank() || origin.trim().contentEquals("*");
  }

}
