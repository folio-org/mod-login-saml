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
    // kenchan0130/simplesamlphp doesn't support https
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
