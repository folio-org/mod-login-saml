package org.folio.util;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;

@RunWith(VertxUnitRunner.class)
public class UrlUtilTest {

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
  }

  @AfterClass
  public static void afterOnce(TestContext context) {
    mockVertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void checkIdpUrl(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/xml", vertx)
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void checkIdpUrlNon200(TestContext context) {
    int port = NetworkUtils.nextFreePort();
    UrlUtil.checkIdpUrl("http://localhost:" + port, vertx)
      .onComplete(context.asyncAssertFailure(cause ->
        // check locale independent prefix only.
        assertThat(cause.getMessage(), startsWith("ConnectException: "))
      ));
  }

  @Test
  public void checkIdpUrlNoContentType(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/", vertx)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Content-Type response header media type must be"))
      ));
  }

  @Test
  public void checkIdpUrlJson(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/json", vertx)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Content-Type response header media type must be"))
      ));
  }

  @Test
  public void contentTypeDuo() {
    @SuppressWarnings("unchecked")
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    when(httpResponse.getHeader("Content-Type")).thenReturn("text/xhtml");
    when(httpResponse.getHeader("Server")).thenReturn("Duo/1.0");
    assertThat(UrlUtil.validateXmlContentType(httpResponse), is(nullValue()));
  }

  void assertContentType(String contentType) {
    @SuppressWarnings("unchecked")
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    when(httpResponse.getHeader("Content-Type")).thenReturn(contentType);
    assertThat(UrlUtil.validateXmlContentType(httpResponse), is(nullValue()));
  }

  @Test(expected = RuntimeException.class)
  public void contentTypeXhtmlNonDuo() {
    assertContentType("text/xhtml");
  }

  @Test
  public void validContentTypes() {
    assertContentType("text/xml; charset=UTF-8");
    assertContentType("application/xml; charset=UTF-8");
    assertContentType("application/xhtml+xml; charset=UTF-8");
    assertContentType("application/samlmetadata+xml; charset=UTF-8");
  }

  @Test(expected = RuntimeException.class)
  public void invalidContentType() {
    assertContentType("foo/text/xml; charset=UTF-8");
  }
}
