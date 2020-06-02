package org.folio.config;

import static org.folio.config.ConfigurationsClient.MISSING_OKAPI_URL;
import static org.folio.config.ConfigurationsClient.MISSING_TENANT;
import static org.folio.config.ConfigurationsClient.MISSING_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.folio.config.ConfigurationsClient.MissingHeaderException;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.util.model.OkapiHeaders;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class ConfigurationClientTest {

  @Test
  public void testVerifyOkapiHeaders() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      fail(e.getMessage());
    }

    okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TOKEN, e.getMessage());
    }

    okapiHeaders = new OkapiHeaders();
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TENANT, e.getMessage());
    }

    okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_OKAPI_URL, e.getMessage());
    }
  }
}
