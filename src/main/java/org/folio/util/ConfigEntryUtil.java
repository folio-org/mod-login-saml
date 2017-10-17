package org.folio.util;

import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author rsass
 */
public class ConfigEntryUtil {
  
  public static boolean valueChanged(String oldValue, String newValue) {

    if (!StringUtils.hasText(oldValue)) {
      return StringUtils.hasText(newValue);
    } else {
      return !oldValue.equals(newValue);
    }

  }

  /**
   * If value changed, calls the provided {@link Consumer} with the newValue
   */
  public static void valueChanged(String oldValue, String newValue, Consumer<String> onChanged) {
    Objects.requireNonNull(onChanged);

    if (valueChanged(oldValue, newValue)) {
      onChanged.accept(newValue);
    }

  }

}
