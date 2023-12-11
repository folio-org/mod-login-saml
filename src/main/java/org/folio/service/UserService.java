package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.StringUtil;
import org.folio.util.model.OkapiHeaders;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;

import javax.ws.rs.core.UriBuilder;
import java.util.Collections;
import java.util.List;

import static org.folio.util.UserFields.*;

public class UserService {

  private static final Logger log = LogManager.getLogger(UserService.class);

  private static final String USER_TENANT_ENDPOINT = "/user-tenants";
  private static final String USER_TENANT_GET_ERROR = "Error getting user-tenant by %s = %s: %s";
  private static final String MULTIPLE_MATCHING_USERS = "Multiple matching users by {} = {}";
  private static final String USER_TENANT_DOES_NOT_HAVE_A_TENANT_ID_ERROR = "The matching user-tenant record founded by %s = %s does not have a tenant id";

  public static class UserErrorException extends RuntimeException {
    public UserErrorException(String message) {
      super(message);
    }
  }

  public static class UserFetchException extends RuntimeException {
    public UserFetchException(String message) {
      super(message);
    }
  }

  public Future<JsonObject> getUser(WebClient webClient, SamlConfiguration configuration,
                                    VertxWebContext webContext, SAML2Client client, SessionStore sessionStore,
                                    OkapiHeaders parsedHeaders) {
    String userPropertyName =
      configuration.getUserProperty() == null ? EXTERNAL_SYSTEM_ID : configuration.getUserProperty();
    var credentialsOptional = client.getCredentials(webContext, sessionStore);
    var credentials =
      (SAML2Credentials) credentialsOptional.orElseThrow(() -> new NullPointerException("Saml credentials was null"));

    String samlAttributeValue =
      getSamlAttributeValue(configuration.getSamlAttribute(), credentials.getUserProfile());
    String usersCql = getCqlUserQuery(userPropertyName, samlAttributeValue);
    String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();

    return extractTenantId(userPropertyName, samlAttributeValue, webClient, parsedHeaders)
      .compose(tenantId -> webClient.getAbs(parsedHeaders.getUrl() + userQuery)
        .putHeader(XOkapiHeaders.TOKEN, parsedHeaders.getToken())
        .putHeader(XOkapiHeaders.URL, parsedHeaders.getUrl())
        .putHeader(XOkapiHeaders.TENANT, tenantId)
        .expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON)
        .send()
        .map(res -> {
          JsonArray users = res.bodyAsJsonObject().getJsonArray("users");
          if (users.isEmpty()) {
            String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
            throw new UserErrorException(message);
          }
          return users.getJsonObject(0);
        }));
  }

  private Future<String> extractTenantId(String userPropertyName, String value, WebClient webClient, OkapiHeaders okapiHeaders) {
    return getUserTenant(userPropertyName, value, webClient, okapiHeaders)
      .compose(userTenantsObject -> {
        if (userTenantsObject.getInteger(TOTAL_RECORDS) == 0) {
          log.debug("No matching tenant, use tenantId from okapi headers");
          return Future.succeededFuture(okapiHeaders.getTenant());
        } else if (userTenantsObject.getInteger(TOTAL_RECORDS) > 1) {
          log.warn(MULTIPLE_MATCHING_USERS, userPropertyName, value);
          return Future.succeededFuture(okapiHeaders.getTenant());
        } else {
          log.debug("Single matching user-tenant, extract tenantId from /user-tenant response for ECS login");
          String tenantId = userTenantsObject.getJsonArray(USER_TENANTS).getJsonObject(0).getString(TENANT_ID);
          if (StringUtils.isBlank(tenantId)) {
            String errorMassage = String.format(USER_TENANT_DOES_NOT_HAVE_A_TENANT_ID_ERROR, userTenantsObject, value);
            log.error("extractTenantId: {}", errorMassage);
            return Future.failedFuture(errorMassage);
          }
          okapiHeaders.setTenant(tenantId);
          return Future.succeededFuture(tenantId);
        }
      });
  }

  private Future<JsonObject> getUserTenant(String userPropertyName, String value, WebClient webClient, OkapiHeaders okapiHeaders) {
    String userTenantQuery = okapiHeaders.getUrl() + USER_TENANT_ENDPOINT;
    HttpRequest<Buffer> request = webClient.getAbs(userTenantQuery);

    switch (userPropertyName) {
      case BARCODE -> request.addQueryParam(BARCODE, value);
      case EXTERNAL_SYSTEM_ID -> request.addQueryParam(EXTERNAL_SYSTEM_ID, value);
      case ID -> request.addQueryParam(USER_ID, value);
      case USERNAME -> request.addQueryParam(USERNAME, value);
      case PERSONAL_EMAIL -> request.addQueryParam(EMAIL, value);
      default -> {
        String errorMassage = String.format("The property name '%s' with value '%s' is not expected", userPropertyName, value);
        log.error("getUserTenant: {}", errorMassage);
        return Future.failedFuture(errorMassage);
      }
    }

    return request.putHeader(XOkapiHeaders.TOKEN, okapiHeaders.getToken())
      .putHeader(XOkapiHeaders.TENANT, okapiHeaders.getTenant())
      .expect(ResponsePredicate.SC_OK)
      .expect(ResponsePredicate.JSON)
      .send()
      .map(HttpResponse::bodyAsJsonObject)
      .recover(e -> {
        String message = String.format(USER_TENANT_GET_ERROR, userPropertyName, value, e.getMessage());
        log.error("{}", message, e);
        return Future.failedFuture(message);
      });
  }

  /**
   * Get the user id from the first samlAttribute of userProfile.
   *
   * @param samlAttribute attribute name, or null for "UserID"
   */
  public static String getSamlAttributeValue(String samlAttribute, UserProfile userProfile) {
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

  public static String getCqlUserQuery(String userPropertyName, String value) {
    // very sad that RMB does not have an option to reject fields with no index
    List<String> supported = List.of(BARCODE, EXTERNAL_SYSTEM_ID, ID, USERNAME, PERSONAL_EMAIL);
    if (!supported.contains(userPropertyName)) {
      throw new UserFetchException("Unsupported user property: " + userPropertyName);
    }
    return userPropertyName + "==" + StringUtil.cqlEncode(value);
  }
}
