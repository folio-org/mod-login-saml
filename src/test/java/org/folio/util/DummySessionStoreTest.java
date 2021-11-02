package org.folio.util;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.NetworkUtils;
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
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;

import java.util.Optional;

@RunWith(VertxUnitRunner.class)
public class DummySessionStoreTest {

  public static final Logger logger = LogManager.getLogger(DummySessionStoreTest.class);

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

    server.requestHandler(router)
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
      .statusCode(200).log().ifValidationFails();
  }

  private void handle(RoutingContext rc) {
    try {
      Session session = new SharedDataSessionImpl(new PRNG(vertx));
      session.put(KEY, VALUE);

      SessionStore sessionStore = new DummySessionStore(vertx, rc.session());

      VertxWebContext webContext = new VertxWebContext(rc, sessionStore);
      assertTrue(sessionStore.get(webContext, KEY).isEmpty());
      assertEquals("", sessionStore.getSessionId(webContext, true).get());

      Optional<SessionStore> optSessionStore = sessionStore.buildFromTrackableSession(webContext, session);
      assertTrue(optSessionStore.isPresent());

      sessionStore = optSessionStore.get();

      assertEquals(VALUE, sessionStore.get(webContext, KEY).get());

      sessionStore.set(webContext, KEY, VALUE2);
      assertEquals(VALUE2, sessionStore.get(webContext, KEY).get());

      assertNotNull(sessionStore.getSessionId(webContext, true));

      assertTrue(sessionStore.renewSession(webContext));

      assertTrue(sessionStore.destroySession(webContext));
      assertTrue(sessionStore.getTrackableSession(webContext).isEmpty());

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
