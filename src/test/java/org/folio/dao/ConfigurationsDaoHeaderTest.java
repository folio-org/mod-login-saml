package org.folio.dao;

import static org.folio.dao.ConfigurationsDao.MISSING_OKAPI_URL;
import static org.folio.dao.ConfigurationsDao.MISSING_TENANT;
import static org.folio.dao.ConfigurationsDao.MISSING_TOKEN;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

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
    var e = assertThrows(MissingHeaderException.class,
      () -> ConfigurationsDao.verifyOkapiHeaders(okapiHeaders));
    assertThat(e.getMessage(), is(MISSING_TOKEN));
  }

  @Test
  public void testVerifyOkapiHeadersMissingTenant() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    var e = assertThrows(MissingHeaderException.class,
      () -> ConfigurationsDao.verifyOkapiHeaders(okapiHeaders));
    assertThat(e.getMessage(), is(MISSING_TENANT));
  }

  @Test
  public void testVerifyOkapiHeadersMissingUrl() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    var e = assertThrows(MissingHeaderException.class,
      () -> ConfigurationsDao.verifyOkapiHeaders(okapiHeaders));
    assertThat(e.getMessage(), is(MISSING_OKAPI_URL));
  }
}
