package org.folio.config;

import static org.folio.config.ConfigurationsClient.MISSING_OKAPI_URL;
import static org.folio.config.ConfigurationsClient.MISSING_TENANT;
import static org.folio.config.ConfigurationsClient.MISSING_TOKEN;
import static org.junit.Assert.assertEquals;

import org.folio.config.ConfigurationsClient.MissingHeaderException;
import org.folio.util.model.OkapiHeaders;
import org.junit.Test;

public class ConfigurationClientTest {

  @Test
  public void testVerifyOkapiHeadersAllPresent() throws MissingHeaderException {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
  }

  @Test
  public void testVerifyOkapiHeadersMissingToken() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();

    okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TOKEN, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingTenant() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();

    okapiHeaders = new OkapiHeaders();
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsClient.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TENANT, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingUrl() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();

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
