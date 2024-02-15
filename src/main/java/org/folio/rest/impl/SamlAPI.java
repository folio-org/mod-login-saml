package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.*;
import static org.folio.util.UserFields.*;
import static org.pac4j.saml.state.SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE;
import static org.folio.rest.impl.ApiInitializer.MAX_FORM_ATTRIBUTE_SIZE;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
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
import org.folio.service.UserService;
import org.folio.session.NoopSession;
import org.folio.util.Base64Util;
import org.folio.util.ConfigEntryUtil;
import org.folio.util.DumpUtil;
import org.folio.util.DummySessionStore;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.UrlUtil;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.CookieSameSiteConfig;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
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
  private static final String TOKEN_SIGN_ENDPOINT_LEGACY = "/token";
  private static final String TOKEN_SIGN_ENDPOINT = "/token/sign";
  public static final String SET_COOKIE = "Set-Cookie";
  public static final String LOCATION = "Location";
  public static final String REFRESH_TOKEN = "refreshToken";
  public static final String ACCESS_TOKEN = "accessToken";
  public static final String FOLIO_ACCESS_TOKEN = "folioAccessToken";
  public static final String FOLIO_REFRESH_TOKEN = "folioRefreshToken";
  public static final String REFRESH_TOKEN_EXPIRATION = "refreshTokenExpiration";
  public static final String ACCESS_TOKEN_EXPIRATION = "accessTokenExpiration";
  public static final String TENANT_ID = "tenantId";

  private final UserService userService = new UserService();

  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
      super(message);
    }
  }

  public static class FetchTokenException extends RuntimeException {
    public FetchTokenException(String message) {
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
    doPostSamlCallback(body, routingContext, okapiHeaders, asyncResultHandler, vertxContext, TOKEN_SIGN_ENDPOINT);
  }

  @Override
  public void postSamlCallbackWithExpiry(String body, RoutingContext routingContext, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    doPostSamlCallback(body, routingContext, okapiHeaders, asyncResultHandler, vertxContext, TOKEN_SIGN_ENDPOINT);
  }

  private void doPostSamlCallback(String body, RoutingContext routingContext, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, String tokenSignEndpoint) {

    registerFakeSession(routingContext);

    final SessionStore sessionStore = new DummySessionStore(routingContext.vertx(), routingContext.session());
    final VertxWebContext webContext = new VertxWebContext(routingContext, sessionStore);
    final String relayState = getRelayState(routingContext, body);

    URI relayStateUrl;
    try {
      assert (relayState != null);  // this avoids a Sonar warning later on
      relayStateUrl = new URI(relayState);
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain(
        "Invalid relay state url: " + relayState)));
      return;
    }
    URI originalUrl = relayStateUrl;
    URI stripesBaseUrl = UrlUtil.parseBaseUrl(originalUrl);

    Cookie relayStateCookie = routingContext.getCookie(RELAY_STATE);
    if (relayStateCookie == null || !relayState.contentEquals(relayStateCookie.getValue())) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("CSRF attempt detected")));
      return;
    }

    findSaml2Client(routingContext, false, false, vertxContext)
      .compose(samlClientComposite -> {
        final SAML2Client client = samlClientComposite.getClient();
        final SamlConfiguration configuration = samlClientComposite.getConfiguration();
        OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
        WebClient webClient = WebClientFactory.getWebClient(vertxContext.owner());

        return userService.getUser(webClient, configuration, webContext, client, sessionStore, parsedHeaders)
            .compose(userObject -> {
          String userId = userObject.getString(ID);
          if (Boolean.FALSE.equals(userObject.getBoolean("active", false))) {
            throw new ForbiddenException("Inactive user account!");
          }
          JsonObject payload = new JsonObject().put("payload",
            new JsonObject().put("sub", userObject.getString(USERNAME)).put("user_id", userId));

          return fetchToken(webClient, payload, parsedHeaders, tokenSignEndpoint).map(jsonResponse -> {
            if (isLegacyResponse(tokenSignEndpoint)) {
              return redirectResponseLegacy(jsonResponse, stripesBaseUrl, originalUrl);
            } else {
              return redirectResponse(jsonResponse, stripesBaseUrl, originalUrl);
            }
          });
        });
      })
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(cause -> {
        var response = failCallbackResponse(cause, routingContext);
        asyncResultHandler.handle(Future.succeededFuture(response));
      });
  }

  private PostSamlCallbackResponse failCallbackResponse(Throwable cause, RoutingContext routingContext) {
    PostSamlCallbackResponse response;
    if (cause instanceof ForbiddenException) {
      response = PostSamlCallbackResponse.respond403WithTextPlain(cause.getMessage());
    } else if (cause instanceof UserService.UserErrorException) {
      response = PostSamlCallbackResponse.respond400WithTextPlain(cause.getMessage());
    } else {
      removeSaml2Client(routingContext);
      response = PostSamlCallbackResponse.respond500WithTextPlain(cause.getMessage());
    }
    log.error(cause.getMessage(), cause);
    return response;
  }

  private boolean isLegacyResponse(String endpoint) {
    return endpoint.equals(TOKEN_SIGN_ENDPOINT_LEGACY);
  }

  private Future<JsonObject> fetchToken(WebClient client, JsonObject payload, OkapiHeaders parsedHeaders, String endpoint) {
    HttpRequest<Buffer> request = client.postAbs(parsedHeaders.getUrl() + endpoint);

    request
      .putHeader(XOkapiHeaders.TENANT, parsedHeaders.getTenant())
      .putHeader(XOkapiHeaders.TOKEN, parsedHeaders.getToken())
      .putHeader(XOkapiHeaders.URL, parsedHeaders.getUrl());

    return request.sendJson(payload).map(response -> {
      if (response.statusCode() != 201) {
        throw new FetchTokenException("Got response " + response.statusCode() + " fetching token");
      }

      return response.bodyAsJsonObject();
    });
  }

  private Response redirectResponseLegacy(JsonObject jsonObject, URI stripesBaseUrl, URI originalUrl) {
    String authToken = jsonObject.getString("token");

    final String location = UriBuilder.fromUri(stripesBaseUrl)
      .path("sso-landing")
      .queryParam("ssoToken", authToken)
      .queryParam("fwd", originalUrl.getPath())
      .build()
      .toString();

    final String cookie = new NewCookie("ssoToken",
      authToken, "", originalUrl.getHost(), "", 3600, true).toString();
    var headers = PostSamlCallbackResponse.headersFor302().withSetCookie(cookie).withXOkapiToken(authToken).withLocation(location);
    return PostSamlCallbackResponse.respond302(headers);
  }

  private Response redirectResponse(JsonObject jsonObject, URI stripesBaseUrl, URI originalUrl) {
    String accessToken = jsonObject.getString(ACCESS_TOKEN);
    String refreshToken = jsonObject.getString(REFRESH_TOKEN);
    String accessTokenExpiration = jsonObject.getString(ACCESS_TOKEN_EXPIRATION);
    String refreshTokenExpiration = jsonObject.getString(REFRESH_TOKEN_EXPIRATION);
    String tenantId = jsonObject.getString(TENANT_ID);

    final String location = UriBuilder.fromUri(stripesBaseUrl)
      .path("sso-landing")
      .queryParam("fwd", originalUrl.getPath())
      .queryParam(TENANT_ID, tenantId)
      .queryParam(ACCESS_TOKEN_EXPIRATION, accessTokenExpiration, StandardCharsets.UTF_8)
      .queryParam(REFRESH_TOKEN_EXPIRATION, refreshTokenExpiration, StandardCharsets.UTF_8)
      .build()
      .toString();

    // NOTE RMB doesn't support sending multiple headers with the same key so we make our own response.
    return Response.status(302)
      .header(SET_COOKIE, accessTokenCookie(accessToken, accessTokenExpiration))
      .header(SET_COOKIE, refreshTokenCookie(refreshToken, refreshTokenExpiration))
      .header(LOCATION, location)
      .build();
  }

  private String refreshTokenCookie(String refreshToken, String refreshTokenExpiration) {
    // The refresh token expiration is the time after which the token will be considered expired.
    var exp = Instant.parse(refreshTokenExpiration).getEpochSecond();
    var ttlSeconds = exp - Instant.now().getEpochSecond();

    // RFC 6265 mandates that MaxAge is >= 1: https://datatracker.ietf.org/doc/html/rfc6265#page-9
    if (ttlSeconds < 1) {
      throw new FetchTokenException("MaxAge of cookie is < 1. This is not permitted.");
    }

    var rtCookie = Cookie.cookie(FOLIO_REFRESH_TOKEN, refreshToken)
      .setMaxAge(ttlSeconds)
      .setSecure(true)
      .setPath("/authn")
      .setHttpOnly(true)
      .setSameSite(CookieSameSiteConfig.get())
      .setDomain(null)
      .encode();

    log.debug("refreshToken cookie: {}", rtCookie);

    return rtCookie;
  }

  private String accessTokenCookie(String accessToken, String accessTokenExpiration) {
    // The refresh token expiration is the time after which the token will be considered expired.
    var exp = Instant.parse(accessTokenExpiration).getEpochSecond();
    var ttlSeconds = exp - Instant.now().getEpochSecond();

    // RFC 6265 mandates that MaxAge is >= 1: https://datatracker.ietf.org/doc/html/rfc6265#page-9
    if (ttlSeconds < 1) {
      throw new FetchTokenException("MaxAge of cookie is < 1. This is not permitted.");
    }

    var atCookie = Cookie.cookie(FOLIO_ACCESS_TOKEN, accessToken)
      .setMaxAge(ttlSeconds)
      .setSecure(true)
      .setPath("/")
      .setHttpOnly(true)
      .setSameSite(CookieSameSiteConfig.get())
      .encode();

    log.debug("accessToken cookie: {}", atCookie);

    return atCookie;
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

            ConfigEntryUtil.valueChanged(config.getCallback(), updatedConfig.getCallback(), callback ->
              updateEntries.put(SamlConfiguration.SAML_CALLBACK, callback));

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
      .withCallback(config.getCallback())
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

  @Override
  public void optionsSamlCallbackWithExpiry(RoutingContext routingContext, Map<String, String> okapiHeaders,
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
