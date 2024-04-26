package org.folio.util;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.client.TenantClient;

/**
 * @author barbaraloehle
 */
public class TenantClientExtended extends TenantClient {

  private final String tenantIdLocal;
  private final String tokenLocal;
  private final String okapiUrlLocal;
  private final String okapiUrlToLocal;
  private final String permissionsLocal;
  private final WebClient webClientLocal;

  public TenantClientExtended(String okapiUrl, String okapiUrlTo, String tenantId, String token, String permissions,
    WebClient webClient) {
    super(okapiUrl, tenantId, token, webClient);
    this.okapiUrlLocal = okapiUrl;
    this.okapiUrlToLocal = okapiUrlTo;
    this.tenantIdLocal = tenantId;
    this.tokenLocal = token;
    this.permissionsLocal = permissions;
    this.webClientLocal = webClient;
  }

  /**
   * Service endpoint "/_/tenant"+queryParams.toString()
   * raml-module-builder/domain-models-runtime/target/generated-sources/raml-jaxrs/org/folio/rest/client/TenantClient.java
   */
  public Future<HttpResponse<Buffer>> postTenant(org.folio.rest.jaxrs.model.TenantAttributes TenantAttributes) {
    StringBuilder queryParams = new StringBuilder("?");
    Buffer buffer = Buffer.buffer();
    if (TenantAttributes!= null) {
      buffer.appendString(org.folio.rest.tools.ClientHelpers.pojo2json(TenantAttributes));
    }
    io.vertx.ext.web.client.HttpRequest<Buffer> request = webClientLocal
      .requestAbs(io.vertx.core.http.HttpMethod.POST, okapiUrlLocal+"/_/tenant"+queryParams.toString());
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json,text/plain");
    if (tenantIdLocal!= null) {
      request.putHeader("X-Okapi-Tenant", tenantIdLocal);
    }
    if (tokenLocal != null) {
      request.putHeader("X-Okapi-Token", tokenLocal);
    }
    if (okapiUrlLocal != null) {
      request.putHeader("X-Okapi-Url", okapiUrlLocal);
    }
    if (okapiUrlToLocal != null) {
      request.putHeader("X-Okapi-Url-to", okapiUrlToLocal);
    }
    if (permissionsLocal != null)
      request.putHeader("X-Okapi-Permissions", permissionsLocal);

    return request.sendBuffer(buffer);
  }
}
