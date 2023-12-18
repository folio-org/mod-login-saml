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
import org.folio.dao.ConfigurationsDao;
import org.folio.dao.impl.ConfigurationsDaoImpl;
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

  public static final String SAML = "/saml/";
  public static final String CALLBACK_WITH_EXPIRY = "callback-with-expiry";
  public static final String CALLBACK = "callback";
  private static final Logger log = LogManager.getLogger(SamlClientLoader.class);

  public static class SamlIdpUrlFormationException extends RuntimeException {
    private static final long serialVersionUID = 7340537453740028326L;

    public SamlIdpUrlFormationException(String message) {
      super(message);
    }
  }

  public static class InvalidCallbackUrlException extends RuntimeException {
    private static final long serialVersionUID = 7340537453740028327L;

    public InvalidCallbackUrlException(String message) {
      super(message);
    }
  }

  private SamlClientLoader() {}

  public static Future<SamlClientComposite> loadFromConfiguration(RoutingContext routingContext,
    boolean generateMissingKeyStore, Context vertxContext) {
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);
    final String tenantId = okapiHeaders.getTenant();
    ConfigurationsDao configurationsDao = new ConfigurationsDaoImpl();
    Vertx vertx = vertxContext.owner();

    return configurationsDao.getConfiguration(vertx, okapiHeaders, false)
      .compose(samlConfiguration -> {
        final String idpUrl = samlConfiguration.getIdpUrl();
        final String keystore = samlConfiguration.getKeystore();
        final String keystorePassword = samlConfiguration.getKeystorePassword();
        final String privateKeyPassword = samlConfiguration.getPrivateKeyPassword();
        final String samlBinding = samlConfiguration.getSamlBinding();
        final Resource idpMetadata = samlConfiguration.getIdpMetadata() != null ?
            new ByteArrayResource(samlConfiguration.getIdpMetadata().getBytes()) : null;
        final String okapiUrl = samlConfiguration.getOkapiUrl();
        final String callback = samlConfiguration.getCallback() == null ?
            CALLBACK_WITH_EXPIRY : samlConfiguration.getCallback();

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

          var cfg = getSaml2ConfigurationForFileResource(keystoreFileName, actualKeystorePassword,
              actualPrivateKeyPassword, idpUrl, idpMetadata);
          var saml2Client = assembleSaml2Client(okapiUrl, tenantId, cfg, samlBinding, vertxContext, callback);

          return vertx.executeBlocking(blockingHandler -> {
            saml2Client.init();
            blockingHandler.complete();
          }).compose(res ->
            storeKeystore(okapiHeaders, vertx, keystoreFileName, actualKeystorePassword, actualPrivateKeyPassword, configurationsDao)
              .map(keystoreBytes -> {
                ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                try {
                  UrlResource idpUrlResource = new UrlResource(idpUrl);
                  var reinitializedConfig = getSaml2ConfigurationForByteArrayResource(keystoreResource,
                      actualKeystorePassword, actualPrivateKeyPassword, idpUrlResource, idpMetadata);
                  var reinitializedSaml2Client = assembleSaml2Client(okapiUrl, tenantId, reinitializedConfig,
                      samlBinding, vertxContext, callback);

                  return new SamlClientComposite(reinitializedSaml2Client, samlConfiguration);
                } catch (MalformedURLException e) {
                  log.error("Saml IdP url was malformed", e);
                  throw new SamlIdpUrlFormationException(e.getMessage());
                }
              })
          );
        }
        // Load KeyStore from configuration
        Buffer keystoreBytes = Buffer.buffer(Base64.getDecoder().decode(keystore));
        ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
        try {
          UrlResource idpUrlResource = new UrlResource(idpUrl);
          var cfg = getSaml2ConfigurationForByteArrayResource(keystoreResource, keystorePassword,
            privateKeyPassword, idpUrlResource, idpMetadata);
          var saml2Client = assembleSaml2Client(okapiUrl, tenantId, cfg, samlBinding, vertxContext, callback);

          return Future.succeededFuture(new SamlClientComposite(saml2Client, samlConfiguration));
        } catch (MalformedURLException e) {
          log.error("Saml IdP url was malformed", e);
          throw new SamlIdpUrlFormationException(e.getMessage());
        }
      });
  }

  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(OkapiHeaders okapiHeaders, Vertx vertx, String keystoreFileName,
    String keystorePassword, String privateKeyPassword, ConfigurationsDao configurationsDao) {

    // read generated jks file
    return vertx.fileSystem().readFile(keystoreFileName).compose(fileResult -> {
      final byte[] rawBytes = fileResult.getBytes();
      // base64 encode
      Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(rawBytes));

      // store in mod-configuration with passwords, wait for all operations to finish
      return CompositeFuture.all(
        configurationsDao.storeEntry(vertx, okapiHeaders,
          SamlConfiguration.KEYSTORE_FILE_CODE, encodedBytes.toString(StandardCharsets.UTF_8)),
        configurationsDao.storeEntry(vertx, okapiHeaders,
          SamlConfiguration.KEYSTORE_PASSWORD_CODE, keystorePassword),
        configurationsDao.storeEntry(vertx, okapiHeaders,
          SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, privateKeyPassword),
        configurationsDao.storeEntry(vertx, okapiHeaders,
          SamlConfiguration.METADATA_INVALIDATED_CODE, "true") // if keystore modified, current metadata is invalid.
        )
        .compose(res -> vertx.fileSystem().delete(keystoreFileName))
        .map(x -> Buffer.buffer(rawBytes));
      });
  }

  protected static SAML2Configuration getSaml2ConfigurationForByteArrayResource(ByteArrayResource keystoreResource,
                                                                              String keystorePassword,
                                                                              String keystorePrivateKeyPassword,
                                                                              UrlResource idpUrlResource,
                                                                              Resource idpMetadata) {
    final var cfg = new SAML2Configuration(keystoreResource,
      keystorePassword,
      keystorePrivateKeyPassword,
      idpUrlResource);
    if (idpMetadata != null) {
      cfg.setIdentityProviderMetadataResource(idpMetadata);
    }
    cfg.setMaximumAuthenticationLifetime(18000);

    return cfg;
  }

  protected static SAML2Configuration getSaml2ConfigurationForFileResource(String keystoreFileName,
                                                                         String keystorePassword,
                                                                         String keystorePrivateKeyPassword,
                                                                         String idpUrlResource,
                                                                         Resource idpMetadata) {
    final var cfg = new SAML2Configuration(keystoreFileName,
      keystorePassword,
      keystorePrivateKeyPassword,
      idpUrlResource);
    if (idpMetadata != null) {
      cfg.setIdentityProviderMetadataResource(idpMetadata);
    }
    cfg.setMaximumAuthenticationLifetime(18000);

    return cfg;
  }

  protected static SAML2Client assembleSaml2Client(String okapiUrl, String tenantId, SAML2Configuration cfg,
    String samlBinding, Context vertxContext, String callback) {

    if ("REDIRECT".equals(samlBinding)) {
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    } else {
      // POST is the default
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
    }

    Boolean mock = vertxContext.config().getBoolean("mock", false);
    SAML2Client saml2Client = Boolean.TRUE.equals(mock) ? new SAML2ClientMock(cfg) : new SAML2Client(cfg);
    saml2Client.setName(tenantId);
    saml2Client.setCallbackUrl(buildCallbackUrl(okapiUrl, tenantId, callback));
    saml2Client.setRedirectionActionBuilder(new JsonReponseSaml2RedirectActionBuilder(saml2Client));
    saml2Client.setStateGenerator(new SAML2StateGenerator(saml2Client));

    return saml2Client;
  }

  public static String buildCallbackUrl(String okapiUrl, String tenantId, String callback) {
    if (isValidCallbackUrl(callback)) {
      return okapiUrl + "/_/invoke/tenant/" + CommonHelper.urlEncode(tenantId) + SAML + callback;
    }

    throw new InvalidCallbackUrlException("Callback url is invalid");
  }

  protected static boolean isValidCallbackUrl(String callbackUrl)  {
    return CALLBACK.equals(callbackUrl) || CALLBACK_WITH_EXPIRY.equals(callbackUrl);
  }
}
