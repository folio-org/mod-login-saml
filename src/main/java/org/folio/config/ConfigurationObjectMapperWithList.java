package org.folio.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.config.model.SamlConfiguration;

import java.util.ArrayList;
import java.util.stream.Collector;

public class ConfigurationObjectMapperWithList {

  // prevent instantiating this static util class
  private ConfigurationObjectMapperWithList() {
  }

  public static SamlConfiguration map(JsonArray array, SamlConfiguration samlConfiguration) {
    try {
      samlConfiguration.setIdsList(mapInternal(array));
      return samlConfiguration;
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  private static ArrayList<String> mapInternal(JsonArray array) {

    return array.stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .collect(toLocalList());
  }

  private static Collector<JsonObject, ArrayList<String>, ArrayList<String>> toLocalList() {

    return Collector.of(
      ArrayList<String>::new,
      (resultSupplier, entry) -> resultSupplier.add(entry.getString("id")),
      (result, resultSupplier) -> {
        result.addAll(resultSupplier);
        return result;
      }
    );
  }
}

