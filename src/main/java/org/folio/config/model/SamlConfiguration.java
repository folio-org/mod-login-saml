package org.folio.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO for strongly typed configuration client
 *
 * @author rsass
 */
@JsonIgnoreProperties()
public class SamlConfiguration {

  private String idpUrl;
  private String keystore;
  private String keystorePassword;
  private String privateKeyPassword;
  private String samlBinding;
  private String samlAttribute;
  private String userProperty;

  public String getIdpUrl() {
    return idpUrl;
  }

  public void setIdpUrl(String idpUrl) {
    this.idpUrl = idpUrl;
  }

  public String getKeystore() {
    return keystore;
  }

  public void setKeystore(String keystore) {
    this.keystore = keystore;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getPrivateKeyPassword() {
    return privateKeyPassword;
  }

  public void setPrivateKeyPassword(String privateKeyPassword) {
    this.privateKeyPassword = privateKeyPassword;
  }

  public String getSamlBinding() {
    return samlBinding;
  }

  public void setSamlBinding(String samlBinding) {
    this.samlBinding = samlBinding;
  }

  public String getSamlAttribute() {
    return samlAttribute;
  }

  public void setSamlAttribute(String samlAttribute) {
    this.samlAttribute = samlAttribute;
  }

  public String getUserProperty() {
    return userProperty;
  }

  public void setUserProperty(String userProperty) {
    this.userProperty = userProperty;
  }
}
