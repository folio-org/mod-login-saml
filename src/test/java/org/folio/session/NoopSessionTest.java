package org.folio.session;

import io.vertx.ext.web.Session;
import org.junit.Assert;
import org.junit.Test;

public class NoopSessionTest {
  @Test
  public void test() {
    Session session = new NoopSession();
    Assert.assertEquals("", session.id());
    Assert.assertEquals(session, session.put("foo", "bar"));
    Assert.assertEquals(session, session.putIfAbsent("foo", null));
    Assert.assertEquals(session, session.computeIfAbsent("foo", null));
  }
}
