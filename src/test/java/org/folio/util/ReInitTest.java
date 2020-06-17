package org.folio.util;

import org.opensaml.saml.common.xml.SAMLConstants;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;

public class ReInitTest {

  public static void main(String[] args) throws Exception {
    SAML2Configuration config = new SAML2Configuration();
    config.setAuthnRequestBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    SAML2Client client = new SAML2Client(config);
    
    client.setCallbackUrl("http://localhost/callback");
    client.init();
    
    config.setAuthnRequestBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
    
    client.init();
    
  }
  
}
