package org.folio.util;

import org.apache.commons.lang3.builder.DiffBuilder;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.folio.config.model.SamlConfiguration;
import java.lang.IllegalAccessException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
/**
 * @author barbaraloehle
 */
public final class SamlConfigurationHelper {

  public SamlConfigurationHelper() {}

  public static DiffResult<SamlConfiguration> compareSamlConfigurations(SamlConfiguration samlConfigFirst, SamlConfiguration samlConfigSecond) {
    DiffBuilder<SamlConfiguration> diffBuilder = new DiffBuilder<SamlConfiguration>(samlConfigFirst, samlConfigSecond,
      ToStringStyle.DEFAULT_STYLE)
      .append(SamlConfiguration.IDP_URL_CODE, samlConfigFirst.getIdpUrl(), samlConfigSecond.getIdpUrl())
      .append(SamlConfiguration.IDP_METADATA_CODE, samlConfigFirst.getIdpMetadata(), samlConfigSecond.getIdpMetadata())
      .append(SamlConfiguration.KEYSTORE_FILE_CODE, samlConfigFirst.getKeystore(), samlConfigSecond.getKeystore())
      .append(SamlConfiguration.KEYSTORE_PASSWORD_CODE, samlConfigFirst.getKeystorePassword(), samlConfigSecond.getKeystorePassword())
      .append(SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, samlConfigFirst.getPrivateKeyPassword(),
        samlConfigSecond.getPrivateKeyPassword())
      .append(SamlConfiguration.METADATA_INVALIDATED_CODE, samlConfigFirst.getMetadataInvalidated(), samlConfigSecond.getMetadataInvalidated())
      .append(SamlConfiguration.OKAPI_URL, samlConfigFirst.getOkapiUrl(), samlConfigSecond.getOkapiUrl())
      .append(SamlConfiguration.SAML_ATTRIBUTE_CODE, samlConfigFirst.getSamlAttribute(), samlConfigSecond.getSamlAttribute())
      .append(SamlConfiguration.SAML_BINDING_CODE, samlConfigFirst.getSamlBinding(), samlConfigSecond.getSamlBinding())
      .append(SamlConfiguration.SAML_CALLBACK, samlConfigFirst.getCallback(), samlConfigSecond.getCallback())
      .append(SamlConfiguration.USER_PROPERTY_CODE, samlConfigFirst.getUserProperty(), samlConfigSecond.getUserProperty());
    return diffBuilder.build();
  }

  public static Map<String, String> samlConfigurationToMap(SamlConfiguration samlConfiguration) {
    Map<String, String> entries = new HashMap<>();
    entries.put(SamlConfiguration.IDP_URL_CODE, samlConfiguration.getIdpUrl());
    entries.put(SamlConfiguration.IDP_METADATA_CODE, samlConfiguration.getIdpMetadata());
    entries.put(SamlConfiguration.KEYSTORE_FILE_CODE, samlConfiguration.getKeystore());
    entries.put(SamlConfiguration.KEYSTORE_PASSWORD_CODE, samlConfiguration.getKeystorePassword());
    entries.put(SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, samlConfiguration.getPrivateKeyPassword());
    //entries.put(SamlConfiguration.METADATA_INVALIDATED_CODE, "true");
    entries.put(SamlConfiguration.METADATA_INVALIDATED_CODE, samlConfiguration.getMetadataInvalidated());
    entries.put(SamlConfiguration.OKAPI_URL, samlConfiguration.getOkapiUrl());
    entries.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, samlConfiguration.getSamlAttribute());
    entries.put(SamlConfiguration.SAML_BINDING_CODE, samlConfiguration.getSamlBinding());
    entries.put(SamlConfiguration.SAML_CALLBACK, samlConfiguration.getCallback());
    entries.put(SamlConfiguration.USER_PROPERTY_CODE, samlConfiguration.getUserProperty());
    return entries;
  }

  //Compare http://www.java2s.com/Tutorial/Java/0125__Reflection/howtousereflectiontoprintthenamesandvaluesofallnonstaticfieldsofanobject.htm
  public static String printPojo(Object obj) {
    StringBuffer buffer = new StringBuffer();
    try {
      Field[] fields = obj.getClass().getDeclaredFields();
      for (Field f : fields) {
        if (!Modifier.isStatic(f.getModifiers())) {
          f.setAccessible(true);
          Object value = f.get(obj);
          buffer.append(f.getType().getName());
          buffer.append(" ");
          buffer.append(f.getName());
          buffer.append("=");
          buffer.append("" + value);
          buffer.append("\n");
        }
      }
      return buffer.toString();
    } catch (IllegalAccessException iEx) {
      return("IllegalAccessException: " + iEx.getMessage());
    }
  }
}
