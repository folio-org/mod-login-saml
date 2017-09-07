package org.folio.util;

import org.apache.commons.compress.utils.IOUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads a file's content to string from classpath
 */
public class ClasspathResourceStringLoader {

  public static String loadAsString(String filename) throws IOException {

    InputStream inputStream = new ClassPathResource(filename).getInputStream();
    byte[] fileBytes = IOUtils.toByteArray(inputStream);
    return new String(fileBytes, StandardCharsets.UTF_8);

  }

}
