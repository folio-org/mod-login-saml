package org.folio.util;

import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.util.model.UrlCheckResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;

@RunWith(VertxUnitRunner.class)
public class UrlUtilTest {

  private static final Logger log = LoggerFactory.getLogger(UrlUtilTest.class);

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  private HttpServer server;


  @Before
  public void before(TestContext context) throws IOException {

    // obtain a free port
    ServerSocket ss = new ServerSocket(0);
    int port = ss.getLocalPort();
    ss.close();

    server = rule.vertx().createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader("Content-Type", "application/xml")
        .end("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }).
      listen(port, context.asyncAssertSuccess(hnd -> {
        log.info("Running test http listener on port " + hnd.actualPort());
      }));

  }

  @After
  public void after(TestContext context) {
    server.close(context.asyncAssertSuccess());
  }

  @Test
  public void checkIdpUrl(TestContext context) throws MalformedURLException {
    Async async = context.async();

    UrlUtil.checkIdpUrl("http://localhost:" + server.actualPort(), rule.vertx())
      .setHandler(handler -> {
        if (handler.failed()) {
          context.fail(handler.cause());
        } else {
          UrlCheckResult result = handler.result();
          log.info("Result is {} with message {}", result.getStatus(), result.getMessage());
          async.complete();
        }
      });

  }
}
