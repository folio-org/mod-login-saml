package org.folio.util;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
   * Encodes a {@link String} with Base64, asnyc.
   *
   * @param context Vertx context
   * @param content String to encode
   * @return Buffer bytes of Base64 string
   */
  public static Future<Buffer> encode(Context context, String content) {

    Future<Buffer> result = Future.future();
    context.executeBlocking((Promise<Buffer> blockingCode) -> {
      byte[] encodedBytes = Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8));
      blockingCode.complete(Buffer.buffer(encodedBytes));
    }, result.completer());

    return result;
  }

}
