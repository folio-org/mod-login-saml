package org.folio.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

public class ConfigurationObjectMapperTest {

  private static final String IDP_URL_VALUE = "https://idp.ssocircle.com";
  private static final String KEYSTORE_FILE_VALUE = "keystore file content";
  private static final String KEYSTORE_PASSWORD_VALUE = "p455w0rd";
  private static final String PRIVATEKEY_PASSWORD_VALUE = "p455word";
  private static final String METADATA_VALUE = "some";
  private static final String ANOTHER_METADATA_VALUE = "some1";

  @Test
  public void map() {

    JsonArray jsonArray = new JsonArray()
      .add(new JsonObject().put("code", "idp.url").put("value", IDP_URL_VALUE))
      .add(new JsonObject().put("code", "keystore.file").put("value", KEYSTORE_FILE_VALUE))
      .add(new JsonObject().put("code", "keystore.password").put("value", KEYSTORE_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "keystore.privatekey.password").put("value", PRIVATEKEY_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "idp.metadata").put("value", METADATA_VALUE))
      .add(new JsonObject().put("code", "unknownCode").put("value", "unknownValue"));

    SamlConfiguration pojoSimple = ConfigurationObjectMapper.map(jsonArray, SamlConfiguration.class);
    assertNotNull(pojoSimple);

    assertEquals(IDP_URL_VALUE, pojoSimple.getIdpUrl());
    assertEquals(METADATA_VALUE, pojoSimple.getIdpMetadata());
    assertEquals(KEYSTORE_FILE_VALUE, pojoSimple.getKeystore());
    assertEquals(KEYSTORE_PASSWORD_VALUE, pojoSimple.getKeystorePassword());
    assertEquals(PRIVATEKEY_PASSWORD_VALUE, pojoSimple.getPrivateKeyPassword());

    pojoSimple.setIdpMetadata(ANOTHER_METADATA_VALUE);
    assertEquals(ANOTHER_METADATA_VALUE, pojoSimple.getIdpMetadata());
  }

  @Test
  public void map1Fail() {
    assertThrows(IllegalArgumentException.class, () -> {
      ConfigurationObjectMapper.map(null, SamlConfiguration.class);});
  }
}
