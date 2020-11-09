package org.folio.config;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.lang3.RandomStringUtils;
import org.folio.config.model.SAML2ClientMock;
import org.folio.config.model.SamlClientComposite;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.state.SAML2StateGenerator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;

import com.google.common.base.Strings;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

/**
 * Load Pac4j {@link SAML2Client} from configuration
 *
 * @author rsass
 */
public class SamlClientLoader {

  public static final String CALLBACK_ENDPOINT = "/saml/callback";

  private SamlClientLoader() {

  }

  public static Future<SamlClientComposite> loadFromConfiguration(RoutingContext routingContext, boolean generateMissingKeyStore) {

    Promise<SamlClientComposite> result = Promise.promise();

    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);
    final String tenantId = okapiHeaders.getTenant();

    ConfigurationsClient.getConfiguration(okapiHeaders)
      .compose(samlConfiguration -> {

        final Promise<SamlClientComposite> clientInstantiationFuture = Promise.promise();

        final String idpUrl = samlConfiguration.getIdpUrl();
        final String keystore = samlConfiguration.getKeystore();
        final String keystorePassword = samlConfiguration.getKeystorePassword();
        final String privateKeyPassword = samlConfiguration.getPrivateKeyPassword();
        final String samlBinding = samlConfiguration.getSamlBinding();
        final String okapiUrl = samlConfiguration.getOkapiUrl();

        final Vertx vertx = routingContext.vertx();

        if (Strings.isNullOrEmpty(idpUrl)) {
          clientInstantiationFuture.fail("There is no IdP configuration stored!");
        } else {

          if (Strings.isNullOrEmpty(keystore)) {

            if (generateMissingKeyStore) {
              // Generate new KeyStore

              final String randomId = RandomStringUtils.randomAlphanumeric(12);
              final String randomFileName = RandomStringUtils.randomAlphanumeric(12);

              final String actualKeystorePassword = Strings.isNullOrEmpty(keystorePassword) ? randomId : keystorePassword;
              final String actualPrivateKeyPassword = Strings.isNullOrEmpty(privateKeyPassword) ? randomId : privateKeyPassword;
              final String keystoreFileName = "temp_" + randomFileName + ".jks";

              SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, idpUrl, actualKeystorePassword, actualPrivateKeyPassword, keystoreFileName, samlBinding);

              vertx.executeBlocking(blockingHandler -> {
                  saml2Client.init();
                  blockingHandler.complete();
                },
                samlClientInitHandler -> {
                  if (samlClientInitHandler.failed()) {
                    clientInstantiationFuture.fail(samlClientInitHandler.cause());
                  } else {
                    storeKeystore(okapiHeaders, vertx, keystoreFileName, actualKeystorePassword, actualPrivateKeyPassword).onComplete(keyfileStorageHandler -> {
                      if (keyfileStorageHandler.succeeded()) {
                        // storeKeystore is deleting JKS file, recreate client from byteArray
                        Buffer keystoreBytes = keyfileStorageHandler.result();
                        ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                        try {
                          UrlResource idpUrlResource = new UrlResource(idpUrl);
                          SAML2Client reinitedSaml2Client = configureSaml2Client(okapiUrl, tenantId, actualKeystorePassword, actualPrivateKeyPassword, idpUrlResource, keystoreResource, samlBinding);

                          clientInstantiationFuture.complete(new SamlClientComposite(reinitedSaml2Client, samlConfiguration));
                        } catch (MalformedURLException e) {
                          clientInstantiationFuture.fail(e);
                        }
                      } else {
                        clientInstantiationFuture.fail(keyfileStorageHandler.cause());
                      }
                    });
                  }
                });
            } else {
              clientInstantiationFuture.fail("No KeyStore stored in configuration and regeneration is not allowed.");
            }
          } else {
            // Load KeyStore from configuration

            vertx.executeBlocking((Promise<Buffer> blockingCode) ->
              blockingCode.complete(Buffer.buffer(Base64.getDecoder().decode(keystore))),
              resultHandler -> {
              if (resultHandler.failed()) {
                clientInstantiationFuture.fail(resultHandler.cause());
              } else {
                Buffer keystoreBytes = resultHandler.result();
                ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                try {
                  UrlResource idpUrlResource = new UrlResource(idpUrl);
                  SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource, keystoreResource, samlBinding);

                  clientInstantiationFuture.complete(new SamlClientComposite(saml2Client, samlConfiguration));
                } catch (MalformedURLException e) {
                  clientInstantiationFuture.fail(e);
                }

              }
            });
          }
        }


        clientInstantiationFuture.future().onComplete(result.future()::handle);
        return result.future();
      });

    return result.future();
  }


  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(OkapiHeaders okapiHeaders, Vertx vertx, String keystoreFileName, String keystorePassword, String privateKeyPassword) {


    Promise<Buffer> future = Promise.promise();

    // read generated jks file
    vertx.fileSystem().readFile(keystoreFileName, fileResult -> {
      if (fileResult.failed()) {
        future.fail(fileResult.cause());
      } else {
        final byte[] rawBytes = fileResult.result().getBytes();

        // base64 encode
        vertx.executeBlocking((Promise<Buffer> blockingFuture) -> {
          Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(rawBytes));
          blockingFuture.complete(encodedBytes);
        }, resultHandler -> {
          Buffer encodedBytes = resultHandler.result();

          // store in mod-configuration with passwords, wait for all operations to finish
          CompositeFuture.all(
            ConfigurationsClient.storeEntry(okapiHeaders, SamlConfiguration.KEYSTORE_FILE_CODE, encodedBytes.toString(StandardCharsets.UTF_8)),
            ConfigurationsClient.storeEntry(okapiHeaders, SamlConfiguration.KEYSTORE_PASSWORD_CODE, keystorePassword),
            ConfigurationsClient.storeEntry(okapiHeaders, SamlConfiguration.KEYSTORE_PRIVATEKEY_PASSWORD_CODE, privateKeyPassword),
            ConfigurationsClient.storeEntry(okapiHeaders, SamlConfiguration.METADATA_INVALIDATED_CODE, "true") // if keystore modified, current metasata is invalid.
          ).onComplete(allConfigurationsStoredHandler -> {

            if (allConfigurationsStoredHandler.failed()) {
              vertx.fileSystem().delete(keystoreFileName, deleteResult ->
                // it is already a failed operation, deleteResult is not important
                future.fail(allConfigurationsStoredHandler.cause())
              );
            } else {
              // delete the file
              vertx.fileSystem().delete(keystoreFileName, deleteResult -> {
                if (deleteResult.failed()) {
                  future.fail(deleteResult.cause());
                } else {
                  future.complete(Buffer.buffer(rawBytes));
                }
              });
            }
          });
        });
      }
    });

    return future.future();

  }


  private static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String idpUrl, String keystorePassword, String actualPrivateKeyPassword, String keystoreFileName, String samlBinding) {
    final SAML2Configuration cfg = new SAML2Configuration(keystoreFileName,
      keystorePassword,
      actualPrivateKeyPassword,
      idpUrl);
    cfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, cfg, samlBinding);
  }

  private static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String keystorePassword, String privateKeyPassword, UrlResource idpUrlResource, ByteArrayResource keystoreResource, String samlBinding) {

    final SAML2Configuration byteArrayCfg = new SAML2Configuration(keystoreResource,
      keystorePassword,
      privateKeyPassword,
      idpUrlResource);
    byteArrayCfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, byteArrayCfg, samlBinding);
  }

  private static SAML2Client assembleSaml2Client(String okapiUrl, String tenantId, SAML2Configuration cfg, String samlBinding) {

    boolean mock = Boolean.parseBoolean(System.getProperty(HttpClientMock2.MOCK_MODE));

    if (StringUtils.hasText(samlBinding) && samlBinding.equals("REDIRECT")) {
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
    } else {
      // POST is the default
      cfg.setAuthnRequestBindingType(SAMLConstants.SAML2_POST_BINDING_URI);
    }

    SAML2Client saml2Client = mock ? new SAML2ClientMock(cfg) : new SAML2Client(cfg);
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
