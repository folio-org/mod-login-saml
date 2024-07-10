package org.folio.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * POJO for strongly typed configuration client
 *
 * @author rsass
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SamlConfiguration {

  public static final String ID_CODE = "id";
  public static final String KEYSTORE_FILE_CODE = "keystore.file";
  public static final String KEYSTORE_PASSWORD_CODE = "keystore.password"; // NOSONAR
  public static final String KEYSTORE_PRIVATEKEY_PASSWORD_CODE = "keystore.privatekey.password"; // NOSONAR
  public static final String IDP_URL_CODE = "idp.url";
  public static final String SAML_BINDING_CODE = "saml.binding";
  public static final String SAML_ATTRIBUTE_CODE = "saml.attribute";
  public static final String IDP_METADATA_CODE = "idp.metadata";
  public static final String USER_PROPERTY_CODE = "user.property";
  public static final String METADATA_INVALIDATED_CODE = "metadata.invalidated";
  public static final String OKAPI_URL= "okapi.url";
  public static final String SAML_CALLBACK = "saml.callback";
  public static final String IDS_LIST_CODE = "idsList";

  @JsonProperty(ID_CODE)
  private String id;
  @JsonProperty(IDP_URL_CODE)
  private String idpUrl;
  @JsonProperty(KEYSTORE_FILE_CODE)
  private String keystore;
  @JsonProperty(KEYSTORE_PASSWORD_CODE)
  private String keystorePassword;
  @JsonProperty(KEYSTORE_PRIVATEKEY_PASSWORD_CODE)
  private String privateKeyPassword;
  @JsonProperty(SAML_BINDING_CODE)
  private String samlBinding;
  @JsonProperty(SAML_ATTRIBUTE_CODE)
  private String samlAttribute;
  @JsonProperty(USER_PROPERTY_CODE)
  private String userProperty;
  @JsonProperty(IDP_METADATA_CODE)
  private String idpMetadata;
  @JsonProperty(METADATA_INVALIDATED_CODE)
  private String metadataInvalidated = "true";
  @JsonProperty(OKAPI_URL)
  private String okapiUrl;
  @JsonProperty(SAML_CALLBACK)
  private String callback;
  @JsonProperty(IDS_LIST_CODE)
  private List<String> idsList;

  public SamlConfiguration(){
    idsList = new ArrayList<>(0);
  }

  public SamlConfiguration(String idpUrl) {
    idsList = new ArrayList<>(0);
    setIdpUrl(idpUrl);
    setId(null);
  }

  public String getCallback() { return callback; }

  public void setCallback(String callback) {
    this.callback = callback;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<String> getIdsList() {
    return idsList;
  }

  public void addToIdsList(String externalId) {
    this.idsList.add(externalId);
  }

  public void setIdsList(List<String> externalList) {
    this.idsList = externalList;
  }

  public String getIdpMetadata() {
    return idpMetadata;
  }

  public void setIdpMetadata(String idpMetadata) {
    this.idpMetadata = idpMetadata;
  }

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

  public String getMetadataInvalidated() {
    return metadataInvalidated;
  }

  public void setMetadataInvalidated(String metadataInvalidated) {
    this.metadataInvalidated = metadataInvalidated;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public void setOkapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl;
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
