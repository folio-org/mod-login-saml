package org.folio.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.stream.Collector;

/**
 * Maps mod-configuration's configuraton entry list to a POJO.
 * The outgoing object can be annotated with JSON annotations.
 */
public class ConfigurationObjectMapper {

  // prevent instantiating this static util class
  private ConfigurationObjectMapper() {
  }

  public static <T> T map(JsonArray array, Class<T> clazz) throws IllegalArgumentException {
    try {
      return mapInternal(array, clazz);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  public static <T> T mapInternal(JsonArray array, Class<T> clazz) {

    return array.stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .collect(toSamlConfiguration(clazz));
  }

  private static <T> Collector<JsonObject, JsonObject, T> toSamlConfiguration(Class<T> clazz) {

    return Collector.of(
      JsonObject::new,
      (result, entry) -> result.put(entry.getString("code"), entry.getString("value")),
      JsonObject::mergeIn,
      result -> result.mapTo(clazz)
    );

  }
}
