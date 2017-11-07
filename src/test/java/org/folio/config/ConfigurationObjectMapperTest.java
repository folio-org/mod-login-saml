package org.folio.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigurationObjectMapperTest {

  private static final String IDP_URL_VALUE = "https://idp.ssocircle.com";
  private static final String KEYSTORE_FILE_VALUE = "keystore file content";
  private static final String KEYSTORE_PASSWORD_VALUE = "p455w0rd";
  private static final String PRIVATEKEY_PASSWORD_VALUE = "p455word";

  @Test
  public void map() throws Exception {

    JsonArray jsonArray = new JsonArray()
      .add(new JsonObject().put("code", "idp.url").put("value", IDP_URL_VALUE))
      .add(new JsonObject().put("code", "keystore.file").put("value", KEYSTORE_FILE_VALUE))
      .add(new JsonObject().put("code", "keystore.password").put("value", KEYSTORE_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "keystore.privatekey.password").put("value", PRIVATEKEY_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "unknownCode").put("value", "unknownValue"));

    SamlConfiguration pojo = ConfigurationObjectMapper.map(jsonArray, SamlConfiguration.class);
    assertNotNull(pojo);

    assertEquals(IDP_URL_VALUE, pojo.getIdpUrl());
    assertEquals(KEYSTORE_FILE_VALUE, pojo.getKeystore());
    assertEquals(KEYSTORE_PASSWORD_VALUE, pojo.getKeystorePassword());
    assertEquals(PRIVATEKEY_PASSWORD_VALUE, pojo.getPrivateKeyPassword());
  }

}
