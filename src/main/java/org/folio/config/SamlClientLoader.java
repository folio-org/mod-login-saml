package org.folio.config;

import com.google.common.base.Strings;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.folio.util.OkapiHelper;
import org.folio.util.VertxUtils;
import org.folio.util.model.OkapiHeaders;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.folio.config.ConfigurationsClient.*;

/**
 * Load Pac4j {@link SAML2Client} from configuration
 *
 * @author rsass
 */
public class SamlClientLoader {

  public static final String CALLBACK_ENDPOINT = "/saml/callback";

  public static Future<SAML2Client> loadFromConfiguration(RoutingContext routingContext, boolean generateMissingKeyStore) {

    Future<SAML2Client> result = Future.future();

    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);
    final String okapiUrl = okapiHeaders.getUrl();
    final String tenantId = okapiHeaders.getTenant();

    ConfigurationsClient.getConfiguration(routingContext)
      .compose(samlConfiguration -> {

        final Future<SAML2Client> clientInstantiationFuture = Future.future();

        final String idpUrl = samlConfiguration.getIdpUrl();
        final String keystore = samlConfiguration.getKeystore();
        final String keystorePassword = samlConfiguration.getKeystorePassword();
        final String privateKeyPassword = samlConfiguration.getPrivateKeyPassword();

        final Vertx vertx = routingContext.vertx();

        if (Strings.isNullOrEmpty(idpUrl)) {
          clientInstantiationFuture.fail("There is no IdP configuration stored!");
        } else {

          if (Strings.isNullOrEmpty(keystore)) {

            if (generateMissingKeyStore) {
              // Generate new KeyStore

              String randomId = RandomStringUtils.randomAlphanumeric(12);

              final String actualKeystorePassword = Strings.isNullOrEmpty(keystorePassword) ? randomId : keystorePassword;
              final String actualPrivateKeyPassword = Strings.isNullOrEmpty(privateKeyPassword) ? randomId : privateKeyPassword;
              final String keystoreFileName = randomId + ".jks";

              SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, idpUrl, actualKeystorePassword, actualPrivateKeyPassword, keystoreFileName);

              vertx.executeBlocking(blockingHandler -> {
                  saml2Client.init(VertxUtils.createWebContext(routingContext));
                  blockingHandler.complete();
                },
                samlClientInitHandler -> {
                  if (samlClientInitHandler.failed()) {
                    clientInstantiationFuture.fail(samlClientInitHandler.cause());
                  } else {
                    storeKeystore(routingContext, keystoreFileName, actualKeystorePassword, actualPrivateKeyPassword).setHandler(keyfileStorageHandler -> {
                      if (keyfileStorageHandler.succeeded()) {
                        // storeKeystore is deleting JKS file, recreate client from byteArray
                        Buffer keystoreBytes = keyfileStorageHandler.result();
                        ByteArrayResource keysotreResource = new ByteArrayResource(keystoreBytes.getBytes());
                        try {
                          UrlResource idpUrlResource = new UrlResource(idpUrl);
                          SAML2Client reinitedSaml2Client = configureSaml2Client(okapiUrl, tenantId, actualKeystorePassword, actualPrivateKeyPassword, idpUrlResource, keysotreResource);

                          clientInstantiationFuture.complete(reinitedSaml2Client);
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

            vertx.executeBlocking((Future<Buffer> blockingCode) -> {
              blockingCode.complete(Buffer.buffer(Base64.getDecoder().decode(keystore)));
            }, resultHandler -> {
              if (resultHandler.failed()) {
                clientInstantiationFuture.fail(resultHandler.cause());
              } else {
                Buffer keystoreBytes = resultHandler.result();
                ByteArrayResource keysotreResource = new ByteArrayResource(keystoreBytes.getBytes());
                try {
                  UrlResource idpUrlResource = new UrlResource(idpUrl);
                  SAML2Client saml2Client = configureSaml2Client(okapiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource, keysotreResource);

                  clientInstantiationFuture.complete(saml2Client);
                } catch (MalformedURLException e) {
                  clientInstantiationFuture.fail(e);
                }

              }
            });
          }
        }


        clientInstantiationFuture.setHandler(result.completer());
      }, result);

    return result;
  }


  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(RoutingContext rc, String keystoreFileName, String keystorePassword, String privateKeyPassword) {

    Vertx vertx = rc.vertx();
    Future<Buffer> future = Future.future();

    // read generated jks file
    vertx.fileSystem().readFile(keystoreFileName, fileResult -> {
      if (fileResult.failed()) {
        future.fail(fileResult.cause());
      } else {
        final byte[] rawBytes = fileResult.result().getBytes();

        // base64 encode
        vertx.executeBlocking((Future<Buffer> blockingFuture) -> {
          Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(rawBytes));
          blockingFuture.complete(encodedBytes);
        }, resultHandler -> {
          Buffer encodedBytes = resultHandler.result();

          // store in mod-configuration with passwords, wait for all operations to finish
          CompositeFuture.all(
            ConfigurationsClient.storeEntry(rc, KEYSTORE_FILE_CODE, encodedBytes.toString(StandardCharsets.UTF_8)),
            ConfigurationsClient.storeEntry(rc, KEYSTORE_PASSWORD_CODE, keystorePassword),
            ConfigurationsClient.storeEntry(rc, KEYSTORE_PRIVATEKEY_PASSWORD_CODE, privateKeyPassword)
          ).setHandler(allConfiguratiuonsStoredHandler -> {
            if (allConfiguratiuonsStoredHandler.failed()) {
              future.fail(allConfiguratiuonsStoredHandler.cause());
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

    return future;

  }


  private static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String idpUrl, String keystorePassword, String actualPrivateKeyPassword, String keystoreFileName) {
    final SAML2ClientConfiguration cfg = new SAML2ClientConfiguration(keystoreFileName,
      keystorePassword,
      actualPrivateKeyPassword,
      idpUrl);
    cfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, cfg);
  }

  private static SAML2Client configureSaml2Client(String okapiUrl, String tenantId, String keystorePassword, String privateKeyPassword, UrlResource idpUrlResource, ByteArrayResource keysotreResource) {

    final SAML2ClientConfiguration byteArrayCfg = new SAML2ClientConfiguration(keysotreResource,
      keystorePassword,
      privateKeyPassword,
      idpUrlResource);
    byteArrayCfg.setMaximumAuthenticationLifetime(18000);

    return assembleSaml2Client(okapiUrl, tenantId, byteArrayCfg);
  }

  private static SAML2Client assembleSaml2Client(String okapiUrl, String tenantId, SAML2ClientConfiguration cfg) {
    SAML2Client saml2Client = new SAML2Client(cfg);
    saml2Client.setName(tenantId);
    saml2Client.setIncludeClientNameInCallbackUrl(false);
    saml2Client.setCallbackUrl(buildCallbackUrl(okapiUrl, tenantId));
    return saml2Client;
  }

  private static String buildCallbackUrl(String okapiUrl, String tenantId) {
    return okapiUrl + "/_/invoke/tenant/" + CommonHelper.urlEncode(tenantId) + CALLBACK_ENDPOINT;
  }


}
