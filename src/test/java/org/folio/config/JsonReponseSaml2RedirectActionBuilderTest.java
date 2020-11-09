package org.folio.config;

import static org.mockito.Mockito.*;

import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.exception.http.StatusAction;
import org.pac4j.saml.client.SAML2Client;

public class JsonReponseSaml2RedirectActionBuilderTest {

  @Test
  public void statusAction500() {
    Assert.assertEquals("Performing a 500 HTTP action", Assert.assertThrows(StatusAction.class, () ->
      new JsonReponseSaml2RedirectActionBuilder(mock(SAML2Client.class))
      .getRedirectionAction(null)).getMessage());
  }

}
