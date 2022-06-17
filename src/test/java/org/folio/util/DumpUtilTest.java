package org.folio.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.Test;

public class DumpUtilTest {

  @Test
  public void test() {
    UtilityClassTester.assertUtilityClass(DumpUtil.class);
  }

  @Test
  public void dumpNull() {
    assertThat(DumpUtil.dump(null), is("null"));
  }

  @Test
  public void dumpContent() {
    assertThat(DumpUtil.dump(new AtomicInteger(9876)), allOf(containsString("AtomicInteger"), containsString("9876")));
  }

}
