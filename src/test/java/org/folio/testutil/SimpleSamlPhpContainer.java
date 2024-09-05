package org.folio.testutil;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class SimpleSamlPhpContainer<C extends SimpleSamlPhpContainer<C>>
    extends GenericContainer<C> {

  private static final boolean DEBUG = false;
  private static final Logger logger = LoggerFactory.getLogger(SimpleSamlPhpContainer.class);

  private String baseUrl;

  /**
   * @param callback either "callback" or "callback-with-expiry"
   */
  public SimpleSamlPhpContainer(String okapiUrl, String callback) {
    super(DockerImageName.parse("kenchan0130/simplesamlphp:1.19.9"));
    // CI builds like https://jenkins-aws.indexdata.com/ don't run containers on
    // localhost but on IP 172.17.0.1. See also
    // https://java.testcontainers.org/features/networking/#getting-the-container-host
    //
    // How to generate the files server.key and server.crt for both localhost and the IP:
    // openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -days 10000 -nodes \
    //     -keyout server.key -out server.crt \
    //     -subj "/CN=localhost"   -addext "subjectAltName=DNS:localhost,IP:172.17.0.1"
    //
    // How to display the certificate:
    // openssl x509 -text -noout < server.crt
    copyResource("server.key", "/var/www/simplesamlphp/cert/server.key");
    copyResource("server.crt", "/var/www/simplesamlphp/cert/server.crt");
    copyResource("authsources.php", "/var/www/simplesamlphp/config/authsources.php");
    withExposedPorts(8080);
    var callbackUrl = okapiUrl + "/_/invoke/tenant/diku/saml/" + callback;
    withEnv("SIMPLESAMLPHP_SP_ENTITY_ID", callbackUrl);
    withEnv("SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE", callbackUrl);
  }

  private void copyResource(String filename, String destination) {
    var file = MountableFile.forClasspathResource("simplesamlphp/" + filename);
    withCopyFileToContainer(file, destination);
  }

  public void init() {
    if (DEBUG) {
      followOutput(new Slf4jLogConsumer(logger).withSeparateOutputStreams());
    }
    baseUrl = "http://" + getHost() + ":" + getFirstMappedPort() + "/simplesaml/";
    String baseurlpath = baseUrl.replace("/", "\\/");
    exec("sed", "-i", "s/'baseurlpath' =>.*/'baseurlpath' => '" + baseurlpath + "',/",
        "/var/www/simplesamlphp/config/config.php");
    exec("sed", "-i", "s/'auth' =>.*/'auth' => 'example-static',/",
        "/var/www/simplesamlphp/metadata/saml20-idp-hosted.php");
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void exec(String... command) {
    try {
      var result = execInContainer(command);
      if (result.getExitCode() > 0) {
        System.out.println(result.getStdout());
        System.err.println(result.getStderr());
        throw new RuntimeException("failure in execInContainer");
      }
    } catch (UnsupportedOperationException | IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void setPostBinding() {
    setIdpBinding("POST");
  }

  public void setRedirectBinding() {
    setIdpBinding("Redirect");
  }

  private void setIdpBinding(String binding) {
    // append entry at end, last entry wins
    exec("sed", "-i",
        "s/];/'SingleSignOnServiceBinding' => 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-" + binding + "',\\n];/",
        "/var/www/simplesamlphp/metadata/saml20-idp-hosted.php");
  }

}
