package org.folio.config;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.SamlLogin;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.context.SAML2MessageContext;
import org.pac4j.saml.sso.impl.SAML2AuthnRequestBuilder;
import org.pac4j.saml.transport.Pac4jSAMLResponse;

import io.vertx.core.json.Json;
import net.shibboleth.utilities.java.support.codec.Base64Support;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;

/**
 * Builds a {@link RedirectionAction} that contains a JSON-serialized {@link SamlLogin} object instead of
 * HTML content. Always contains content (in redirect binding case too).
 *
 * @author rsass
 */
public class JsonReponseSaml2RedirectActionBuilder implements RedirectionActionBuilder {

  private static final Logger log = LogManager.getLogger(JsonReponseSaml2RedirectActionBuilder.class);

  private final SAML2Client client;
  private final SAML2AuthnRequestBuilder saml2ObjectBuilder;

  public JsonReponseSaml2RedirectActionBuilder(final SAML2Client client) {
    CommonHelper.assertNotNull("client", client);
    this.client = client;
    this.saml2ObjectBuilder = new SAML2AuthnRequestBuilder(client.getConfiguration());
  }

  @Override
  public Optional<RedirectionAction> getRedirectionAction(WebContext webContext) {

    final SAML2MessageContext context = this.client.getContextProvider().buildContext(webContext);
    final String relayState = this.client.getStateGenerator().generateValue(webContext);

    final AuthnRequest authnRequest = this.saml2ObjectBuilder.build(context);
    String destination = authnRequest.getDestination();

    try {
      // Signature, etc.
      this.client.getProfileHandler().send(context, authnRequest, relayState);
      final Pac4jSAMLResponse adapter = context.getProfileRequestContextOutboundMessageTransportResponse();


      SamlLogin samlLogin = new SamlLogin();
      if (this.client.getConfiguration().getAuthnRequestBindingType().equalsIgnoreCase(SAMLConstants.SAML2_POST_BINDING_URI)) {

        String authnResuestAsString = SerializeSupport.nodeToString(XMLObjectSupport.marshall(authnRequest));
        String b64authnRequest = Base64Support.encode(authnResuestAsString.getBytes(StandardCharsets.UTF_8), Base64Support.UNCHUNKED);

        samlLogin.setBindingMethod(SamlLogin.BindingMethod.POST);
        samlLogin.setLocation(destination);
        samlLogin.setSamlRequest(b64authnRequest);
        samlLogin.setRelayState(relayState);
      } else {
        String redirectUrl = adapter.getRedirectUrl();
        samlLogin.setBindingMethod(SamlLogin.BindingMethod.GET);
        samlLogin.setLocation(redirectUrl);
      }
      return Optional.of(new RedirectionAction(200) {
        @Override
        public String toString() {
          return Json.encode(samlLogin);
        }
      });
    } catch (Exception e) {
      log.error("Exception processing SAML login request", e);
      return Optional.of(new RedirectionAction(500) {
        @Override
        public String toString() {
          return e.getMessage();
        }
      });
    }

  }

}
