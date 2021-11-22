package org.folio.config;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;
import org.junit.Assert;
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

    Future<SamlConfiguration> pojoFuture = ConfigurationObjectMapper.map(jsonArray, SamlConfiguration.class);
    Assert.assertTrue(pojoFuture.isComplete());
    Assert.assertTrue(pojoFuture.succeeded());
    SamlConfiguration pojo = pojoFuture.result();
    Assert.assertNotNull(pojoFuture.result());

    Assert.assertEquals(IDP_URL_VALUE, pojo.getIdpUrl());
    Assert.assertEquals(METADATA_VALUE, pojo.getIdpMetadata());
    Assert.assertEquals(KEYSTORE_FILE_VALUE, pojo.getKeystore());
    Assert.assertEquals(KEYSTORE_PASSWORD_VALUE, pojo.getKeystorePassword());
    Assert.assertEquals(PRIVATEKEY_PASSWORD_VALUE, pojo.getPrivateKeyPassword());

    pojo.setIdpMetadata(ANOTHER_METADATA_VALUE);
    Assert.assertEquals(ANOTHER_METADATA_VALUE, pojo.getIdpMetadata());
  }

  @Test
  public void map1Fail() {
    Future<SamlConfiguration> samlConfigurationFuture = ConfigurationObjectMapper.map(null, SamlConfiguration.class);
    Assert.assertTrue(samlConfigurationFuture.isComplete());
    Assert.assertTrue(samlConfigurationFuture.failed());
  }
}
