package org.folio.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author rsass
 */
public class ConfigEntryUtilTest {
  @Test
  public void valueChanged() throws Exception {

    assertFalse(ConfigEntryUtil.valueChanged(null, ""));
    assertFalse(ConfigEntryUtil.valueChanged("", ""));
    assertFalse(ConfigEntryUtil.valueChanged("", null));

    assertTrue(ConfigEntryUtil.valueChanged(null, "x"));
    assertTrue(ConfigEntryUtil.valueChanged("x", "y"));
    assertTrue(ConfigEntryUtil.valueChanged("x", null));
  }
}
