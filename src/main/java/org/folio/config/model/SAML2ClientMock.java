package org.folio.config.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDBuilder;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;

public class SAML2ClientMock extends SAML2Client {

  private static final Logger log = LogManager.getLogger(SAML2ClientMock.class);
  public static final String SAML_USER_ID = "saml-user-id";

  public SAML2ClientMock(final SAML2Configuration cfg) {
    super(cfg);
    log.info("SAML2 Client MOCK mode");
  }

  @Override
  protected Optional<Credentials> retrieveCredentials(WebContext context, SessionStore sessionStore) {
    log.info("Mocking SAML2Client retrieveCredentials...");

    assert(context.getRequestParameter("SAMLResponse").isPresent());

    SAML2Credentials.SAMLNameID nameId = SAML2Credentials.SAMLNameID.from(new NameIDBuilder().buildObject());
    String issuerId = this.getClass().getName();
    List<SAML2Credentials.SAMLAttribute> samlAttributes = SAML2Credentials.SAMLAttribute.from(Collections.emptyList());
    Conditions conditions = new ConditionsBuilder().buildObject();
    List<String> authnContexts = new ArrayList<>();

    SAML2Credentials cred = new SAML2Credentials(nameId, issuerId, samlAttributes,conditions, "1",
      authnContexts, "x");

    CommonProfile userProfile = new CommonProfile();
    userProfile.addAttribute("UserID", Arrays.asList(SAML_USER_ID));
    cred.setUserProfile(userProfile);
    return Optional.of(cred);
  }

}
