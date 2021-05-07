package org.folio.util;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.VertxUtils.DummySessionStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.vertx.VertxWebContext;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;

import java.util.Optional;

@RunWith(VertxUnitRunner.class)
public class VertxUtilsTest {

  public static final Logger logger = LogManager.getLogger(VertxUtilsTest.class);

  public static final String KEY = "key";
  public static final String VALUE = "foo";
  public static final String VALUE2 = "bar";

  public final Vertx vertx = Vertx.vertx();
  public final int port = NetworkUtils.nextFreePort();
  public HttpServer server;

  @Before
  public void before(TestContext context) {
    Router router = Router.router(vertx);
    server = vertx.createHttpServer();

    router.route().handler(BodyHandler.create());
    router.route("/foo")
      .handler(this::handle);

    server.requestHandler(router)
      .listen(port, context.asyncAssertSuccess());
  }

  @After
  public void after(TestContext context) {
    server.close(context.asyncAssertSuccess());
  }

  @Test
  public void testVertxUtils(TestContext context) {
    given()
      .formParam("form0", "value0")
      .param("param1", "value1")
      .post("http://localhost:" + port + "/foo")
      .then()
      .statusCode(200).log().ifValidationFails();
  }

  private void handle(RoutingContext rc) {
    try {
      Session session = new SharedDataSessionImpl(new PRNG(vertx));
      session.put(KEY, VALUE);

      SessionStore<VertxWebContext> sessionStore = new DummySessionStore(vertx, null);

      VertxWebContext ctx = VertxUtils.createWebContext(rc);
      assertTrue(sessionStore.get(ctx, KEY).isEmpty());
      assertEquals("", sessionStore.getOrCreateSessionId(ctx));
      assertEquals("value0", ctx.getRequestParameter("form0").get());
      assertTrue(ctx.getRequestParameter("form1").isEmpty());
      assertEquals("value1", ctx.getRequestParameter("param1").get());
      assertTrue(ctx.getRequestParameter("param2").isEmpty());

      Optional<SessionStore<VertxWebContext>> optSessionStore = sessionStore.buildFromTrackableSession(ctx, session);
      assertTrue(optSessionStore.isPresent());

      sessionStore = optSessionStore.get();

      assertEquals(VALUE, sessionStore.get(ctx, KEY).get());

      sessionStore.set(ctx, KEY, VALUE2);
      assertEquals(VALUE2, sessionStore.get(ctx, KEY).get());

      assertNotNull(sessionStore.getOrCreateSessionId(ctx));

      assertTrue(sessionStore.renewSession(ctx));

      assertTrue(sessionStore.destroySession(ctx));
      assertTrue(sessionStore.getTrackableSession(ctx).isEmpty());

      rc.response()
        .setStatusCode(200)
        .end();
    } catch (Exception t) {
      logger.error("Unexpected Error", t);
      rc.response()
        .setStatusCode(500)
        .end(t.getMessage());
    }
  }

}
