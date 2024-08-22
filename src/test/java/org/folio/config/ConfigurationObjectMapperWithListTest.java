package org.folio.config;

import io.vertx.core.json.JsonArray;
import org.folio.config.model.SamlConfiguration;
import org.folio.util.MockJsonExtended;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationObjectMapperWithListTest {
  private static final MockJsonExtended mock = new MockJsonExtended();
  private static List<String> expectedList = new ArrayList<>(0);

  private void addValues() {
    expectedList.add("60eead4f-de97-437c-9cb7-09966ce50e49");
    expectedList.add("6dc15218-ed83-49e0-85ab-bb891e3f42c9");
    expectedList.add("2dd0d26d-3be4-4e80-a631-f7bda5311719");
  }

  @Test
  public void map() {
    addValues();
    mock.setMockContent("mock_example_entries.json");
    JsonArray jsonArray = mock.getMockConfigs();
    boolean expectedBoolean = true;
    SamlConfiguration samlConfiguration = ConfigurationObjectMapperWithList.map(jsonArray, new SamlConfiguration());
    assertEquals(expectedBoolean, samlConfiguration.getIdsList().equals(expectedList));
  }

  @Test(expected = IllegalArgumentException.class)
  public void mapFail() {
    ConfigurationObjectMapperWithList.map(null, new SamlConfiguration());
  }
}
