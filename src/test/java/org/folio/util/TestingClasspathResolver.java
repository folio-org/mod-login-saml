package org.folio.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.stax.impl.util.ConcreteLSInput;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * XML schemaLocation resolver that loads files from Classpath from the defined basePath
 */
public class TestingClasspathResolver implements LSResourceResolver {

  private final Logger log = LogManager.getLogger(TestingClasspathResolver.class);
  private final String basePath;

  /**
   * @param basePath the directory where schema files are located under resources.
   */
  public TestingClasspathResolver(String basePath) {
    this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
  }

  @Override
  public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {

    final String path = this.basePath + systemId;
    try {
      final InputStream resourceStream = new ClassPathResource(path).getInputStream();
      LSInput result = new ConcreteLSInput();
      result.setSystemId(systemId);
      result.setPublicId(publicId);
      result.setByteStream(resourceStream);
      return result;
    } catch (IOException e) {
      log.warn("Cannot load XSD: " + e.getMessage());
      return null;
    }

  }
}
