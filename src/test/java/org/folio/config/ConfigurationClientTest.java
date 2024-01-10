package org.folio.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class ConfigurationClientTest extends TestBase {
  private static final Logger log = LogManager.getLogger(ConfigurationClientTest.class);
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);

  private static final int JSON_MOCK_PORT = NetworkUtils.nextFreePort();
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + JSON_MOCK_PORT);
  public static final int IDP_MOCK_PORT = NetworkUtils.nextFreePort();

  private static final MockJson mock = new MockJson();
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);

  @Rule
  public TestName testName = new TestName();
  public final String LOCALHOST_ORIGIN = "http://localhost";

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = TestBase.MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", JSON_MOCK_PORT));

    mock.setMockContent("mock_content_with_delete.json");
    vertx.deployVerticle(mock, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp() {
    log.info("Running {}", testName.getMethodName());
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
  }

  @Test
  public void testGetConfiguration(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    ConfigurationsClient.getConfiguration(vertx, dataMigrationHelper.getOkapiHeaders())
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
      }));
  }

  @Test
  public void testGetConfigurationNullWithoutMockUrl(TestContext context) {
    mock.setMockContent("mock_empty.json");
    ConfigurationsClient.getConfiguration(vertx, dataMigrationHelper.getOkapiHeaders())
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Response status code 404 is not equal to 200"))));
  }

  @Test
  public void testGetConfigurationNullStatus400(TestContext context) {
    mock.setMockContent("mock_400.json");
    ConfigurationsClient.getConfiguration(vertx, dataMigrationHelper.getOkapiHeaders())
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Response status code 400 is not equal to 200"))));
  }

  @Test
  public void testGetConfigurationNullStatus200(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    ConfigurationsClient.getConfiguration(vertx, dataMigrationHelper.getOkapiHeaders())
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
      }));
  }

  @Test
  public void testGetConfigurationWithIds(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    ConfigurationsClient.getConfigurationWithIds(vertx, dataMigrationHelper.getOkapiHeaders())
      .onComplete(context.asyncAssertSuccess(result -> {
         assertTrue(createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
      }));
  }

  private static DiffResult<SamlConfiguration> createDiffResult(SamlConfiguration result, SamlConfiguration samlConfiguration) {
    DiffResult<SamlConfiguration> diffResult = SamlConfigurationHelper.compareSamlConfigurations(samlConfiguration, result);
    log.info("result = " + SamlConfigurationHelper.printPojo(result));
    log.info("numberOfDiffs = " + diffResult.getNumberOfDiffs());
    return diffResult;
  }

  @Test
  public void testStoreEntriesPut(TestContext context) throws MalformedURLException {

    mock.setMockContent("mock_content_with_delete.json");
    Map<String, String> entries = new HashMap<>(10);
    SamlConfiguration samlConfigurationByDataSentToMock = new SamlConfiguration();
    String idpUrl = URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml").toURL().toString();
    entries.put(SamlConfiguration.IDP_URL_CODE, idpUrl);
    samlConfigurationByDataSentToMock.setIdpUrl(idpUrl);
    entries.put(SamlConfiguration.SAML_BINDING_CODE, SamlConfigRequest.SamlBinding.POST.toString());
    samlConfigurationByDataSentToMock.setSamlBinding(SamlConfigRequest.SamlBinding.POST.toString());
    if (entries != null) {
      ConfigurationsClient.storeEntries(vertx, dataMigrationHelper.getOkapiHeaders(), entries)
        .onComplete(context.asyncAssertSuccess(result -> {
          SamlConfiguration samlConfigurationReceivedbyMock = mock.getReceivedData();
          assertTrue(createDiffResult(samlConfigurationByDataSentToMock, samlConfigurationReceivedbyMock).getDiffs().isEmpty());
          mock.resetReceivedData();
        }));
    }
  }

  @Test
  public void testStoreEntriesPost(TestContext context) throws MalformedURLException {

    mock.setMockContent("mock_content_with_delete.json");
    Map<String, String> entries = new HashMap<>(10);
    SamlConfiguration samlConfigurationByDataSentToMock = new SamlConfiguration();
    entries.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, "UserID");
    samlConfigurationByDataSentToMock.setSamlAttribute("UserID");
    if (entries != null) {
      ConfigurationsClient.storeEntries(vertx, dataMigrationHelper.getOkapiHeaders(), entries)
        .onComplete(context.asyncAssertSuccess(result -> {
          SamlConfiguration samlConfigurationReceivedbyMock = mock.getReceivedData();
          assertTrue(createDiffResult(samlConfigurationByDataSentToMock, samlConfigurationReceivedbyMock).getDiffs().isEmpty());
          mock.resetReceivedData();
        }));
    }
  }
}
