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
import static org.folio.rest.impl.ApiInitializer.MAX_FORM_ATTRIBUTE_SIZE;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.impl.Utils;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;
import org.apache.commons.io.IOUtils;
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
import org.folio.session.NoopSession;
import org.folio.util.Base64Util;
import org.folio.util.ConfigEntryUtil;
import org.folio.util.DumpUtil;
import org.folio.util.DummySessionStore;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.StringUtil;
import org.folio.util.UrlUtil;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;


/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements Saml {

  private static final Logger log = LogManager.getLogger(SamlAPI.class);
  public static final String CSRF_TOKEN = "csrfToken";
  public static final String RELAY_STATE = "relayState";

  public static class UserErrorException extends RuntimeException {
    public UserErrorException(String message) {
      super(message);
    }
  }

  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
      super(message);
    }
  }


  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    findSaml2Client(routingContext, false, false, vertxContext)
      .onComplete(samlClientHandler ->
          asyncResultHandler.handle(Future.succeededFuture(
            GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(samlClientHandler.succeeded()))
          )));
  }


  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    postSamlLogin(requestEntity, routingContext, vertxContext, false)
    .otherwise(e -> PostSamlLoginResponse.respond500WithTextPlain("Fail and retry"))
    .compose(response -> {
      if (response.getStatus() == 200) {
        return Future.succeededFuture(response);
      }
      // retry after reloading client
      removeSaml2Client(routingContext);
      return postSamlLogin(requestEntity, routingContext, vertxContext, true);
    })
    .otherwise(e -> {
      log.error(e.getMessage(), e);
      return PostSamlLoginResponse.respond500WithTextPlain("Internal Server Error");
    })
    .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)));
  }

  private Future<Response> postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext,
      Context vertxContext, boolean reloadClient) {

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

    final boolean generateMissingConfig = false;   // do not allow login if config is missing
    return findSaml2Client(routingContext, generateMissingConfig, reloadClient, vertxContext)
      .map(SamlClientComposite::getClient)
      .map(saml2client -> postSamlLoginResponse(routingContext, saml2client));
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

  private String getRelayState(RoutingContext routingContext, String body) {
    String relayState = routingContext.request().getFormAttribute("RelayState");

    if (relayState == null && body.length() > MAX_FORM_ATTRIBUTE_SIZE) {
      log.error("HTTP body size {} exceeds MAX_FORM_ATTRIBUTE_SIZE={}",
          body.length(), MAX_FORM_ATTRIBUTE_SIZE);
    }

    return relayState;
  }

  @Override
  public void postSamlCallback(String body, RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    registerFakeSession(routingContext);

    final SessionStore sessionStore = new DummySessionStore(routingContext.vertx(), routingContext.session());
    final VertxWebContext webContext = new VertxWebContext(routingContext, sessionStore);
    final String relayState = getRelayState(routingContext, body);

    URI relayStateUrl;
    try {
      assert(relayState != null);  // this avoids a Sonar warning later on
      relayStateUrl = new URI(relayState);
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain(
          "Invalid relay state url: " + relayState)));
      return;
    }
    final URI originalUrl = relayStateUrl;
    final URI stripesBaseUrl = UrlUtil.parseBaseUrl(originalUrl);

    Cookie relayStateCookie = routingContext.getCookie(RELAY_STATE);
    if (relayStateCookie == null || !relayState.contentEquals(relayStateCookie.getValue())) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("CSRF attempt detected")));
      return;
    }

    findSaml2Client(routingContext, false, false, vertxContext)
      .compose(samlClientComposite -> {
        final SAML2Client client = samlClientComposite.getClient();
        final SamlConfiguration configuration = samlClientComposite.getConfiguration();
        final String userPropertyName =
            configuration.getUserProperty() == null ? "externalSystemId" : configuration.getUserProperty();
        final SAML2Credentials credentials = (SAML2Credentials) client.getCredentials(webContext, sessionStore).get();
        final String samlAttributeValue =
            getSamlAttributeValue(configuration.getSamlAttribute(), credentials.getUserProfile());
        final String usersCql = getCqlUserQuery(userPropertyName, samlAttributeValue);
        final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();

        OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);

        WebClient webClient = WebClientFactory.getWebClient(vertxContext.owner());
        return webClient.getAbs(parsedHeaders.getUrl() + userQuery)
          .putHeader(XOkapiHeaders.TOKEN, parsedHeaders.getToken())
          .putHeader(XOkapiHeaders.URL, parsedHeaders.getUrl())
          .putHeader(XOkapiHeaders.TENANT, parsedHeaders.getTenant())
          .expect(ResponsePredicate.SC_OK)
          .expect(ResponsePredicate.JSON)
          .send()
          .compose(res -> {
            JsonArray users = res.bodyAsJsonObject().getJsonArray("users");
            if (users.isEmpty()) {
              String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
              throw new UserErrorException(message);
            }
            final JsonObject userObject = users.getJsonObject(0);
            String userId = userObject.getString("id");
            if (!userObject.getBoolean("active", false)) {
              throw new ForbiddenException("Inactive user account!");
            }
            JsonObject payload = new JsonObject().put("payload", new JsonObject().put("sub", userObject.getString("username")).put("user_id", userId));
            return webClient.postAbs(parsedHeaders.getUrl() + "/token")
              .putHeader(XOkapiHeaders.TOKEN, parsedHeaders.getToken())
              .putHeader(XOkapiHeaders.URL, parsedHeaders.getUrl())
              .putHeader(XOkapiHeaders.TENANT, parsedHeaders.getTenant())
              .sendJsonObject(payload)
              .map(tokenResponse -> {
                String candidateAuthToken;
                if (tokenResponse.statusCode() == 200) {
                  candidateAuthToken = tokenResponse.getHeader(XOkapiHeaders.TOKEN);
                } else if (tokenResponse.statusCode() == 201) { //mod-authtoken v2.x returns 201, with token in JSON response body
                  try {
                    candidateAuthToken = tokenResponse.bodyAsJsonObject().getString("token");
                  } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                  }
                } else {
                  throw new RuntimeException("POST /token returned " + tokenResponse.statusCode());
                }
                final String authToken = candidateAuthToken;

                final String location = UriBuilder.fromUri(stripesBaseUrl)
                  .path("sso-landing")
                  .queryParam("ssoToken", authToken)
                  .queryParam("fwd", originalUrl.getPath())
                  .build()
                  .toString();

                final String cookie = new NewCookie("ssoToken", authToken, "", originalUrl.getHost(), "", 3600, false).toString();
                return PostSamlCallbackResponse
                  .headersFor302().withSetCookie(cookie).withXOkapiToken(authToken).withLocation(location);
              });
          });
      })
      .onSuccess(headers ->
        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond302(headers)))
      )
      .onFailure(cause -> {
        PostSamlCallbackResponse response;
        if (cause instanceof ForbiddenException) {
          response = PostSamlCallbackResponse.respond403WithTextPlain(cause.getMessage());
        } else if (cause instanceof UserErrorException) {
          response = PostSamlCallbackResponse.respond400WithTextPlain(cause.getMessage());
        } else {
          removeSaml2Client(routingContext);
          response = PostSamlCallbackResponse.respond500WithTextPlain(cause.getMessage());
        }
        log.error(cause.getMessage(), cause);
        asyncResultHandler.handle(Future.succeededFuture(response));
      });
  }

  /**
   * Get the user id from the first samlAttribute of userProfile.
   *
   * @param samlAttribute attribute name, or null for "UserID"
   */
  static String getSamlAttributeValue(String samlAttribute, UserProfile userProfile) {
    String samlAttributeName = samlAttribute == null ? "UserID" : samlAttribute;
    List<?> samlAttributeList = getList(userProfile.getAttribute(samlAttributeName));
    if (samlAttributeList.isEmpty()) {
      throw new UserErrorException("SAML attribute doesn't exist: " + samlAttributeName);
    }
    return samlAttributeList.get(0).toString();
  }

  private static List<?> getList(Object o) {
    if (o == null) {
      return Collections.emptyList();
    }
    if (o instanceof List) {
      return (List<?>) o;
    }
    return List.of(o);
  }

  @Override
  public void getSamlRegenerate(RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    regenerateSaml2Config(routingContext, vertxContext)
      .compose(metadata ->
        ConfigurationsClient.storeEntry(vertxContext.owner(), OkapiHelper.okapiHeaders(okapiHeaders),
            SamlConfiguration.METADATA_INVALIDATED_CODE, "false")
          .map(configurationEntryStoredEvent ->
            new SamlRegenerateResponse().withFileContent(Base64Util.encode(metadata))
          )
      )
      .onSuccess(res ->
        asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.respond200WithApplicationJson(res)))
      )
      .onFailure(cause -> {
        log.error(cause.getMessage(), cause);
        asyncResultHandler
          .handle(Future.succeededFuture(GetSamlRegenerateResponse.respond500WithTextPlain(cause.getMessage())));
      });
  }

  @Override
  public void getSamlConfiguration(RoutingContext rc, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ConfigurationsClient.getConfiguration(vertxContext.owner(), OkapiHelper.okapiHeaders(okapiHeaders))
      .onFailure(cause -> {
        log.warn("Cannot load configuration", cause);
        asyncResultHandler.handle(
          Future.succeededFuture(
            GetSamlConfigurationResponse.respond500WithTextPlain("Cannot get configuration")));
      })
      .onSuccess(result ->
          asyncResultHandler.handle(
            Future.succeededFuture(GetSamlConfigurationResponse.respond200WithApplicationJson(configToDto(result)))
      ));
  }


  @Override
  public void putSamlConfiguration(SamlConfigRequest updatedConfig, RoutingContext rc,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    checkConfigValues(updatedConfig, vertxContext.owner())
      .onFailure(cause -> {
        SamlValidateResponse errorEntity = new SamlValidateResponse().withValid(false).withError(cause.getMessage());
        asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.respond400WithApplicationJson(errorEntity)));
      })
      .onSuccess(checkValuesHandler -> {
        OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
        ConfigurationsClient.getConfiguration(vertxContext.owner(), parsedHeaders)
          .compose(config -> {
            Map<String, String> updateEntries = new HashMap<>();

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

            ConfigEntryUtil.valueChanged(config.getSamlAttribute(), updatedConfig.getIdpMetadata(), idpMetadata ->
              updateEntries.put(SamlConfiguration.IDP_METADATA_CODE, idpMetadata));

            ConfigEntryUtil.valueChanged(config.getOkapiUrl(), updatedConfig.getOkapiUrl().toString(), okapiUrl -> {
              updateEntries.put(SamlConfiguration.OKAPI_URL, okapiUrl);
              updateEntries.put(SamlConfiguration.METADATA_INVALIDATED_CODE, "true");
            });
            return storeConfigEntries(rc, parsedHeaders, updateEntries, vertxContext);
          })
          .onFailure(cause -> {
            log.error(cause.getMessage(), cause);
            asyncResultHandler.handle(Future.succeededFuture(
              PutSamlConfigurationResponse.respond500WithTextPlain(cause.getMessage())));
          })
          .onSuccess(result -> asyncResultHandler.handle(Future.succeededFuture(
            PutSamlConfigurationResponse.respond200WithApplicationJson(result))));
      });
  }

  private Future<SamlConfig> storeConfigEntries(RoutingContext rc, OkapiHeaders parsedHeaders,
    Map<String, String> updateEntries, Context vertxContext) {

    return ConfigurationsClient.storeEntries(vertxContext.owner(), parsedHeaders, updateEntries)
      .compose(configurationSavedEvent ->
        findSaml2Client(rc, true, true, vertxContext))
      .map(configurationLoadEvent -> configToDto(configurationLoadEvent.getConfiguration())
      );
  }

  @Override
  public void getSamlValidate(SamlValidateGetType type, String value, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

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
    if (type.equals(SamlValidateGetType.IDPURL)) {
      UrlUtil.checkIdpUrl(value, vertxContext.owner())
        .onComplete(result -> {
          SamlValidateResponse response = new SamlValidateResponse();
          if (result.succeeded()) {
            response.setValid(true);
          } else {
            response.setValid(false);
            response.setError(result.cause().getMessage());
          }
          asyncResultHandler.handle(
            Future.succeededFuture(GetSamlValidateResponse.respond200WithApplicationJson(response)));
        });
    } else {
      asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond400WithApplicationJson(
        new SamlValidateResponse().withValid(false).withError("unknown type: " + type))));
    }
  }

  private Future<Void> checkConfigValues(SamlConfigRequest updatedConfig, Vertx vertx) {
    return UrlUtil.checkIdpUrl(updatedConfig.getIdpUrl().toString(), vertx);
  }

  private Future<String> regenerateSaml2Config(RoutingContext routingContext, Context vertxContext) {

    return findSaml2Client(routingContext, false, false, vertxContext)
      .<String>compose(result -> {
        Vertx vertx = vertxContext.owner();
        SAML2Client saml2Client = result.getClient();
        return vertx.executeBlocking(blockingCode -> {
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
        });
      })
      .onFailure(e -> removeSaml2Client(routingContext));
  }

  /**
   * @param routingContext        the actual routing context
   * @param generateMissingConfig if the encryption key and passwords are missing should we generate and store it?
   * @param reloadClient          should we drop the loaded client and reload it with (maybe modified) configuration?
   * @return Future of loaded {@link SAML2Client} or failed future if it cannot be loaded.
   */
  private Future<SamlClientComposite> findSaml2Client(RoutingContext routingContext, boolean generateMissingConfig,
    boolean reloadClient, Context vertxContext) {

    String tenantId = OkapiHelper.okapiHeaders(routingContext).getTenant();
    SamlConfigHolder configHolder = SamlConfigHolder.getInstance();
    SamlClientComposite clientComposite = configHolder.findClient(tenantId);

    if (clientComposite != null && !reloadClient) {
      return Future.succeededFuture(clientComposite);
    }
    if (reloadClient) {
      configHolder.removeClient(tenantId);
    }
    return SamlClientLoader.loadFromConfiguration(routingContext, generateMissingConfig, vertxContext)
      .onSuccess(result -> configHolder.putClient(tenantId, result));
  }

  private void removeSaml2Client(RoutingContext routingContext) {
    String tenantId = OkapiHelper.okapiHeaders(routingContext).getTenant();
    try {
      SAML2Configuration conf = SamlConfigHolder.getInstance().findClient(tenantId).getClient().getConfiguration();
      log.debug(() -> "IdP metadata resolver: " + DumpUtil.dump(conf.getIdentityProviderMetadataResolver()));
      log.debug(() -> "IdP metadata resource: " + DumpUtil.dump(conf.getIdentityProviderMetadataResource()));
      try (InputStream inputStream = conf.getIdentityProviderMetadataResource().getInputStream()) {
        String metadata = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        log.debug(() -> "IdP metadata: " + metadata);
      }
    } catch (Exception e) {
      // ignore
    }
    SamlConfigHolder.getInstance().removeClient(tenantId);
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

  static String getCqlUserQuery(String userPropertyName, String value) {
    // very sad that RMB does not have an option to reject fields with no index
    List<String> supported = List.of("barcode", "externalSystemId", "id", "username", "personal.email");
    if (!supported.contains(userPropertyName)) {
      throw new RuntimeException("Unsupported user property: " + userPropertyName);
    }
    return userPropertyName + "==" + StringUtil.cqlEncode(value);
  }

}
