package org.folio.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author rsass
 */
public class Base64UtilTest {

  private static final String HELLO = "hello";
  private static final String HELLO_AS_BASE64 = "aGVsbG8=";

  @Test
  public void encode() {
    Assert.assertEquals(HELLO_AS_BASE64, Base64Util.encode(HELLO));
  }

}
