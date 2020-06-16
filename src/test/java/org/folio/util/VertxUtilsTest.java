package org.folio.util;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.VertxUtils.DummySessionStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.vertx.VertxWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;

@RunWith(VertxUnitRunner.class)
public class VertxUtilsTest {

  public static final Logger logger = LoggerFactory.getLogger(VertxUtilsTest.class);

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

    router.route("/foo")
      .handler(this::handle);

    server.requestHandler(router::handle)
      .listen(port, context.asyncAssertSuccess());
  }

  @After
  public void after(TestContext context) {
    server.close(context.asyncAssertSuccess());
  }

  @Test
  public void testVertxUtils(TestContext context) {
    given().get("http://localhost:" + port + "/foo")
      .then()
      .statusCode(200);
  }

  private void handle(RoutingContext rc) {
    try {
      Session session = new SharedDataSessionImpl(new PRNG(vertx));
      session.put(KEY, VALUE);

      SessionStore<VertxWebContext> sessionStore = new DummySessionStore(vertx, null);

      VertxWebContext ctx = VertxUtils.createWebContext(rc);
      assertNull(sessionStore.get(ctx, KEY));
      assertEquals("", sessionStore.getOrCreateSessionId(ctx));
      
      sessionStore = sessionStore.buildFromTrackableSession(ctx, session);
      assertEquals(VALUE, sessionStore.get(ctx, KEY));

      sessionStore.set(ctx, KEY, VALUE2);
      assertEquals(VALUE2, sessionStore.get(ctx, KEY));

      assertNotNull(sessionStore.getOrCreateSessionId(ctx));

      assertTrue(sessionStore.renewSession(ctx));

      assertTrue(sessionStore.destroySession(ctx));
      assertNull(sessionStore.getTrackableSession(ctx));

      rc.response()
        .setStatusCode(200)
        .end();
    } catch (Throwable t) {
      logger.error("Unexpected Error", t);
      rc.response()
        .setStatusCode(500)
        .end(t.getMessage());
    }
  }

}
