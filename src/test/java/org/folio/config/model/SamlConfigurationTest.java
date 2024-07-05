package org.folio.config.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class SamlConfigurationTest {
  private static final String idpNameExpected = "https://idp.ssocircle.com";
  private static List<String> expectedList1 = new ArrayList<>(0);
  private static List<String> expectedList2 = new ArrayList<>(0);
  private static final boolean EXPECTED_BOOLEAN = true;

  @Test
  public void testConstructorWithParameter() {
    SamlConfiguration samlConfiguration = new SamlConfiguration(idpNameExpected);
    assertEquals(idpNameExpected, samlConfiguration.getIdpUrl());
    assertEquals(EXPECTED_BOOLEAN, samlConfiguration.getIdsList().equals(expectedList1));
    assertNull(samlConfiguration.getId());
  }

 @Test
  public void testAddToList() {
   expectedList2.add(idpNameExpected);
   SamlConfiguration samlConfiguration = new SamlConfiguration();
   samlConfiguration.addToIdsList(idpNameExpected);
   assertEquals(EXPECTED_BOOLEAN, samlConfiguration.getIdsList().equals(expectedList2));
   }
}
