package org.folio.util;

import static java.util.stream.Collectors.toMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.ReflectionDiffBuilder;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.folio.config.model.SamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/**
 * @author barbaraloehle
 */
public class SamlConfigurationUtil {

  private SamlConfigurationUtil() {}
  private static final Logger LOGGER = LogManager.getLogger(SamlConfigurationUtil.class);

  public static Map<String, String> samlConfiguration2Map(SamlConfiguration samlConfiguration) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      String samlConfiguration2json = mapper.writeValueAsString(samlConfiguration);
      Map<String, Object> localMapAsObject = mapper.readValue(samlConfiguration2json, new TypeReference<>() {});
      return localMapAsObject.entrySet()
        .stream()
        .filter(element -> Objects.nonNull(element.getValue()))
        .filter(element -> (!element.getKey().equals(SamlConfiguration.ID_CODE)
          && !element.getKey().equals(SamlConfiguration.IDS_LIST_CODE)))
        .collect(toMap(Map.Entry::getKey, element -> String.valueOf(element.getValue())));
    } catch (JsonProcessingException jsonProcEx) {
      LOGGER.warn("Conversion of an SamlConfiguration Object failed: {}", jsonProcEx.getMessage());
      return new HashMap<>();
    }
  }

  public static DiffResult<SamlConfiguration> compareSamlConfigurations(SamlConfiguration samlConfigFirst, SamlConfiguration samlConfigSecond) {
    return new ReflectionDiffBuilder<>(samlConfigFirst, samlConfigSecond, ToStringStyle.SHORT_PREFIX_STYLE)
      .setExcludeFieldNames(SamlConfiguration.ID_CODE, SamlConfiguration.IDS_LIST_CODE)
      .build();
  }
}
