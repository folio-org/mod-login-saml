package org.folio.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;

import java.util.List;

public class ConfigurationObjectMapperWithList {

  // prevent instantiating this static util class
  private ConfigurationObjectMapperWithList() {
  }

  public static SamlConfiguration map(JsonArray array,
    SamlConfiguration samlConfiguration) {
    try {
      samlConfiguration.setIdsList(mapInternal(array));
      return samlConfiguration;
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  public static List<String> mapInternal(JsonArray array) {
    return array.stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .map(entry -> entry.getString("id"))
      .toList();
  }
}

