package org.folio.util;

import io.vertx.core.buffer.Buffer;

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
   * @return Buffer bytes of Base64 string
   */
  public static Buffer encode(String content) {
    return Buffer.buffer(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)));
  }

}
