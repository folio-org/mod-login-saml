package org.folio.util;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

/**
 * @author rsass
 */
@RunWith(VertxUnitRunner.class)
public class Base64UtilTest {

  private static final String HELLO = "hello";
  private static final String HELLO_AS_BASE64 = "aGVsbG8=";

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  @Test
  public void encode(TestContext context) {

    Base64Util.encode(rule.vertx().getOrCreateContext(), HELLO)
      .onComplete(context.asyncAssertSuccess(result -> context.assertEquals(HELLO_AS_BASE64, result.toString(StandardCharsets.UTF_8))));

  }

}
