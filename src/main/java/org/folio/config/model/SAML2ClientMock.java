package org.folio.config.model;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensaml.saml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDBuilder;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.NameID;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SAML2ClientMock extends SAML2Client {

  private static final Logger log = LoggerFactory.getLogger(SAML2ClientMock.class);
  public static final String SAML_USER_ID = "saml-user-id";

  public SAML2ClientMock(final SAML2Configuration cfg) {
    super(cfg);
    log.info("SAML2 Client MOCK mode");
  }

  @Override
  protected SAML2Credentials retrieveCredentials(WebContext context) throws HttpAction {
    log.info("Mocking SAML2Client retrieveCredentials...");

    NameID nameId = new NameIDBuilder().buildObject();
    String issuerId = this.getClass().getName();
    List<Attribute> samlAttributes = new ArrayList<>();
    Conditions conditions = new ConditionsBuilder().buildObject();
    List<String> authnContexts = new ArrayList<>();
    SAML2Credentials cred = new SAML2Credentials(nameId, issuerId, samlAttributes, conditions, "1", authnContexts);

    CommonProfile userProfile = new CommonProfile();
    userProfile.addAttribute("UserID", Arrays.asList(SAML_USER_ID));
    cred.setUserProfile(userProfile);
    return cred;
  }

}
