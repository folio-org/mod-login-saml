package org.folio.util;

import org.apache.commons.lang3.builder.ToStringBuilder;

public final class DumpUtil {
  private DumpUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  public static String dump(Object o) {
    if (o == null) {
      return "null";
    }
    return ToStringBuilder.reflectionToString(o);
  }
}
