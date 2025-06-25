package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.resource.interfaces.InitAPI;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

public class ApiInitializer implements InitAPI {

  private final Logger log = LogManager.getLogger(ApiInitializer.class);

  public static final int MAX_FORM_ATTRIBUTE_SIZE = 64 * 1024;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    String tacEnv = System.getenv("TRUST_ALL_CERTIFICATES");

    if ("true".equals(tacEnv)) {
      trustAllCertificates();
    }

    String disableResolver = System.getProperty("vertx.disableDnsResolver");
    log.info("vertx.disableDnsResolver (netty workaround): {}", disableResolver);

    // https://issues.folio.org/browse/RMB-856
    RestVerticle.getHttpServerOptions().setMaxFormAttributeSize(MAX_FORM_ATTRIBUTE_SIZE);

    handler.handle(Future.succeededFuture(true));
  }

  /**
   * A HACK for disable HTTPS security checks. DO NOT USE IN PRODUCTION!
   * https://stackoverflow.com/a/2893932
   */
  private void trustAllCertificates() {
    log.warn("Applying trustAllCertificates() to bypass HTTPS cert errors. " +
      "This is needed by IdPs with self signed certificates. " +
      "Do not use this in production!");

    // Create a trust manager that does not validate certificate chains
    @SuppressWarnings("java:S4830")  // suppress "Enable server certificate validation"
    TrustManager[] trustAllCerts = new TrustManager[]{
      new X509TrustManager() {
        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
          // don't validate certificates
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
          // don't validate certificates
        }
      }
    };

    // Install the all-trusting trust manager
    try {
      SSLContext sc = SSLContext.getInstance("TLSv1.2");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (GeneralSecurityException e) {
      // ignore
    }
  }
}
