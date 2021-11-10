package org.folio.util.model;

/**
 * POJO for Okapi headers parsing
 *
 * @author rsass
 */
public class OkapiHeaders {

  private String url;
  private String token;
  private String tenant;
  private String permissions;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  public String getPermissions() {
    return permissions;
  }

  public void setPermissions(String permissions) {
    this.permissions = permissions;
  }
}
