package org.folio.config.model;


import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class SAML2ClientMock extends SAML2Client {

  private static final Logger log = LoggerFactory.getLogger(SAML2ClientMock.class);
  public static final String SAML_USER_ID = "saml-user-id";

  public SAML2ClientMock(final SAML2ClientConfiguration cfg) {
    super(cfg);
    log.info("SAML2 Client MOCK mode");
  }

  @Override
  protected SAML2Credentials retrieveCredentials(WebContext context) throws HttpAction {
    log.info("Mocking SAML2Client retrieveCredentials...");
    SAML2Credentials cred = new SAML2Credentials(null, null, null, this.getName(), "1");
    CommonProfile userProfile = new CommonProfile();
    userProfile.addAttribute("UserID", Arrays.asList(SAML_USER_ID));
    cred.setUserProfile(userProfile);
    return cred;
  }

}
