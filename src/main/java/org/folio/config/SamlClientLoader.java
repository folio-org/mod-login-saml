package org.folio.config;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.vertx.core.Context;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SAML2ClientMock;
import org.folio.config.model.SamlClientComposite;
import org.folio.config.model.SamlConfiguration;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.state.SAML2StateGenerator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.springframework.core.io.Resource;

/**
 * Load Pac4j {@link SAML2Client} from configuration
 *
 * @author rsass
 */
public class SamlClientLoader {

  public static final String CALLBACK_ENDPOINT = "/saml/callback";
  private static final Logger log = LogManager.getLogger(SamlClientLoader.class);

  private SamlClientLoader() {

  }

  public static Future<SamlClientComposite> loadFromConfiguration(RoutingContext routingContext,
    boolean generateMissingKeyStore, Context vertxContext) {
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);
    final String tenantId = okapiHeaders.getTenant();

    Vertx vertx = vertxContext.owner();
    return ConfigurationsClient.getConfiguration(vertx, okapiHeaders)
      .compose(samlConfiguration -> {
        final String idpUrl = samlConfiguration.getIdpUrl();
        final String keystore = samlConfiguration.getKeystore();
        final String keystorePassword = samlConfiguration.getKeystorePassword();
        final String privateKeyPassword = samlConfiguration.getPrivateKeyPassword();
        final String samlBinding = samlConfiguration.getSamlBinding();
        final Resource idpMetadata = samlConfiguration.getIdpMetadata() != null ?
          new ByteArrayResource(samlConfiguration.getIdpMetadata().getBytes()) : null;
        final String okapiUrl = samlConfiguration.getOkapiUrl();
        final String callback = samlConfiguration.getCallback();
        // TODO Check for null here and return login-with-expiry if needed for this.

        if (StringUtils.isBlank(idpUrl)) {
          return Future.failedFuture("There is no IdP configuration stored!");
        }
        if (StringUtils.isBlank(keystore)) {
          if (!generateMissingKeyStore) {
            return Future.failedFuture("No KeyStore stored in configuration and regeneration is not allowed.");
          }
          // Generate new KeyStore

          final String randomId = RandomStringUtils.randomAlphanumeric(12);
          final String randomFileName = RandomStringUtils.randomAlphanumeric(12);

          final String actualKeystorePassword = StringUtils.isBlank(keystorePassword) ? randomId : keystorePassword;
          final String actualPrivateKeyPassword = StringUtils.isBlank(privateKeyPassword) ? randomId : privateKeyPassword;
          final String keystoreFileName = "temp_" + randomFileName + ".jks";

          SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, idpUrl, actualKeystorePassword,
            actualPrivateKeyPassword, keystoreFileName, samlBinding, idpMetadata,  vertxContext);

          return vertx.executeBlocking(blockingHandler -> {
            saml2Client.init();
            blockingHandler.complete();
          }).compose(res ->
            storeKeystore(okapiHeaders, vertx, keystoreFileName, actualKeystorePassword, actualPrivateKeyPassword)
              .map(keystoreBytes -> {
                ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                try {
                  UrlResource idpUrlResource = new UrlResource(idpUrl);
                  SAML2Client reinitedSaml2Client = configureSaml2Client(okapiUrl, tenantId, actualKeystorePassword,
                    actualPrivateKeyPassword, idpUrlResource, keystoreResource, samlBinding, idpMetadata, vertxContext);

                  return new SamlClientComposite(reinitedSaml2Client, samlConfiguration);
                } catch (MalformedURLException e) {
                  throw new RuntimeException(e);
                }
              })
          );
        }
        // Load KeyStore from configuration
        Buffer keystoreBytes = Buffer.buffer(Base64.getDecoder().decode(keystore));
        ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
        try {
          UrlResource idpUrlResource = new UrlResource(idpUrl);
          SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, keystorePassword, privateKeyPassword,
            idpUrlResource, keystoreResource, samlBinding, idpMetadata, vertxContext);

          return Future.succeededFuture(new SamlClientComposite(saml2Client, samlConfiguration));
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      });
  }

  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(OkapiHeaders okapiHeaders, Vertx vertx, String keystoreFileName,
    String keystorePassword, String privateKeyPassword) {

    // read generated jks file
    return vertx.fileSystem().readFile(keystoreFileName).compose(fileResult -> {
      final byte[] rawBytes = fileResult.getBytes();
      // base64 encode
      Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(rawBytes));

      // store in mod-configuration with passwords, wait for all operations to finish
      return CompositeFuture.all(
          ConfigurationsClient.storeEntry(vertx, okapiHeaders,
            SamlConfiguration.KEYSTORE_FILE_CODE, encodedBytes.toString(StandardCharsets.UTF_8)),
          ConfigurationsClient.storeEntry(vertx, okapiHeaders,
            SamlConfiguration.KEYSTORE_PASSWORD_CODE, keystorePassword),
          ConfigurationsClient.storeEntry(vertx, okapiHeaders,
            SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, privateKeyPassword),
          ConfigurationsClient.storeEntry(vertx, okapiHeaders,
            SamlConfiguration.METADATA_INVALIDATED_CODE, "true") // if keystore modified, current metadata is invalid.
        )
        .compose(res -> vertx.fileSystem().delete(keystoreFileName))
        .map(x -> Buffer.buffer(rawBytes));
    });
  }


  private static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String idpUrl,
    String keystorePassword, String actualPrivateKeyPassword, String keystoreFileName, String samlBinding, Resource idpMetadata,
    Context vertxContext) {

    final SAML2Configuration cfg = new SAML2Configuration(keystoreFileName,
      keystorePassword,
      actualPrivateKeyPassword,
      idpUrl);
    if(idpMetadata != null) {
      cfg.setIdentityProviderMetadataResource(idpMetadata);
    }
    cfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, cfg, samlBinding, vertxContext);
  }

  protected static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String keystorePassword, String privateKeyPassword, UrlResource idpUrlResource, ByteArrayResource keystoreResource, String samlBinding, Resource idpMetadata, Context vertxContext) {

    final SAML2Configuration byteArrayCfg = new SAML2Configuration(keystoreResource,
      keystorePassword,
      privateKeyPassword,
      idpUrlResource);
    if(idpMetadata != null) {
      byteArrayCfg.setIdentityProviderMetadataResource(idpMetadata);
    }
    byteArrayCfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, byteArrayCfg, samlBinding, vertxContext);
  }

  private static SAML2Client assembleSaml2Client(String okapiUrl, String tenantId, SAML2Configuration cfg,
    String samlBinding, Context vertxContext) {

    if ("REDIRECT".equals(samlBinding)) {
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    } else {
      // POST is the default
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
    }

    Boolean mock = vertxContext.config().getBoolean("mock", false);
    SAML2Client saml2Client = Boolean.TRUE.equals(mock) ? new SAML2ClientMock(cfg) : new SAML2Client(cfg);
    saml2Client.setName(tenantId);
    saml2Client.setCallbackUrl(buildCallbackUrl(okapiUrl, tenantId));
    saml2Client.setRedirectionActionBuilder(new JsonReponseSaml2RedirectActionBuilder(saml2Client));
    saml2Client.setStateGenerator(new SAML2StateGenerator(saml2Client));


    return saml2Client;
  }

  private static String buildCallbackUrl(String okapiUrl, String tenantId) {
    return okapiUrl + "/_/invoke/tenant/" + CommonHelper.urlEncode(tenantId) + CALLBACK_ENDPOINT;
  }
}
