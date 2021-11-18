package org.folio.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author rsass
 */
public class Base64Util {

  private Base64Util() {
  }

  /**
   * Encodes a {@link String} with Base64.
   *
   * @param content String to encode
   * @return Base64 string.
   */
  public static String encode(String content) {
    return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
  }

}
