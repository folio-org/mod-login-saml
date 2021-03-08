package org.folio.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UrlUtilTest {

  private static final Logger log = LogManager.getLogger(UrlUtilTest.class);

  public static final int MOCK_PORT = NetworkUtils.nextFreePort();

  private static Vertx mockVertx = Vertx.vertx();

  private Vertx vertx;

  @BeforeClass
  public static void setupOnce(TestContext context) {
    DeploymentOptions mockOptions = new DeploymentOptions().setConfig(new JsonObject()
        .put("http.port", MOCK_PORT))
        .setWorker(true);

    mockVertx.deployVerticle(IdpMock.class.getName(), mockOptions, context.asyncAssertSuccess());
  }

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    WebClientFactory.init(vertx);
  }

  @AfterClass
  public static void afterOnce(TestContext context) {
    mockVertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void checkIdpUrl(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/xml", vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("", result.getMessage());
      }));
  }

  @Test
  public void checkIdpUrlNon200(TestContext context) {
    int port = NetworkUtils.nextFreePort();
    UrlUtil.checkIdpUrl("http://localhost:" + port, vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
          context.assertEquals("Connection refused: localhost/127.0.0.1:" + port, result.getMessage());
      }));
  }

  @Test
  public void checkIdpUrlUnexpectedError(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/", vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("Unexpected error: null", result.getMessage());
      }));
  }

  @Test
  public void checkIdpUrlJson(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/json", vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("Response content-type is not XML", result.getMessage());
      }));
  }
}
