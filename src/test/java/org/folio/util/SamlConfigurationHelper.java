package org.folio.util;

import org.apache.commons.lang3.builder.DiffResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author barbaraloehle
 */
public final class SamlConfigurationHelper {
  private static final Logger log = LogManager.getLogger(SamlConfigurationHelper.class);

  //Compare http://www.java2s.com/Tutorial/Java/0125__Reflection/howtousereflectiontoprintthenamesandvaluesofallnonstaticfieldsofanobject.htm
  public static String printPojo(Object obj) {
    StringBuffer buffer = new StringBuffer();
    try {
      Field[] fields = obj.getClass().getDeclaredFields();
      for (Field f : fields) {
        if (!Modifier.isStatic(f.getModifiers())) {
          f.setAccessible(true);
          Object value = f.get(obj);
          buffer.append(f.getType().getName());
          buffer.append(" ");
          buffer.append(f.getName());
          buffer.append("=");
          buffer.append("" + value);
          buffer.append("\n");
        }
      }
      return buffer.toString();
    } catch (IllegalAccessException iEx) {
      return("IllegalAccessException: " + iEx.getMessage());
    }
  }

  public static DiffResult<SamlConfiguration> createDiffResult(SamlConfiguration result, SamlConfiguration samlConfiguration) {
    DiffResult<SamlConfiguration> diffResult = SamlConfigurationUtil.compareSamlConfigurations(samlConfiguration, result);
    log.info("result = {}", SamlConfigurationHelper.printPojo(result));
    log.info("numberOfDiffs = {}", diffResult.getNumberOfDiffs());
    return diffResult;
  }
}
