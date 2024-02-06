package org.folio.dao;

import static org.folio.dao.ConfigurationsDao.MISSING_OKAPI_URL;
import static org.folio.dao.ConfigurationsDao.MISSING_TENANT;
import static org.folio.dao.ConfigurationsDao.MISSING_TOKEN;
import static org.junit.Assert.assertEquals;

import org.folio.dao.ConfigurationsDao.MissingHeaderException;
import org.folio.util.model.OkapiHeaders;
import org.junit.Test;

public class ConfigurationsDaoHeaderTest {

  @Test
  public void testVerifyOkapiHeadersAllPresent() throws MissingHeaderException {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
  }

  @Test
  public void testVerifyOkapiHeadersMissingToken() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TOKEN, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingTenant() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    try {
      ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_TENANT, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingUrl() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    try {
      ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
    } catch (MissingHeaderException e) {
      assertEquals(MISSING_OKAPI_URL, e.getMessage());
    }
  }
}
