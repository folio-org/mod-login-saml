package org.folio.dao.impl;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.impl.TestBase;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.DataMigrationHelper;
import org.folio.util.MockJsonExtended;
import org.folio.util.SamlConfigurationHelper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
/**
 * @author barbaraloehle
 */

@RunWith(VertxUnitRunner.class)
public class ConfigurationsDaoImplMigrationTest extends TestBase {

  private static final Logger log = LogManager.getLogger(ConfigurationsDaoImplMigrationTest.class);

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);

  private static final int JSON_MOCK_PORT = NetworkUtils.nextFreePort();
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + JSON_MOCK_PORT);

  private static final MockJsonExtended mock = new MockJsonExtended();
  private static DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);
  private static final Map<String, String> DATA_MIGRATION_HELPER_HEADERS = dataMigrationHelper.getHeaders();
  private ConfigurationsDaoImpl configurationsDaoImpl = new ConfigurationsDaoImpl();
  private ConfigurationsDao configurationsDao = configurationsDaoImpl;

  @Rule
  public TestName testName = new TestName();
  public static final String LOCALHOST_ORIGIN = "http://localhost";

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", JSON_MOCK_PORT));

    mock.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(mock, okapiOptions)
      .compose(x -> tenantInitExec(vertx, TENANT_ATTRIBUTES_INSTALLATION, DATA_MIGRATION_HELPER_HEADERS))
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp(TestContext context) {
    dataMigrationHelper.deleteAllConfigurationRecordsCompleted(vertx, context);
    mock.resetReceivedData();
    mock.resetRequestedUrlList();
    log.info("Running {}", testName.getMethodName());
    mock.setMockContent("mock_content_with_delete.json");
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
    deleteAllConfigurationRecords(vertx);
  }

  @Test
  public void dataMigrationServerStatus400(TestContext context) {
    mock.setMockContent("mock_400.json");
    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), false)
      .onFailure(cause -> {
        context.assertEquals("Response status code 400 is not equal to 200 - {}", cause.getMessage());
      });
  }

  @Test
  public void dataMigrationWithoutDataWithoutAndWithDeletion(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();

    testDataMigration(context, samlConfiguration, false);
    testDataMigration(context, samlConfiguration, true);
  }

  private void testDataMigration(TestContext context, SamlConfiguration samlConfiguration, boolean withDeletion) {
    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), withDeletion)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
      }));
  }

  @Test
  public void dataMigrationEmptyDBWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();

    testDataMigration(context, samlConfiguration, false);
  }

  @Test
  public void dataMigrationServerStatusSuccessWithDeletion(TestContext context) {
    boolean expectedBoolean = true;
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    mock.setMockIds();

    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), true)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
        assertEquals(expectedBoolean, mock.getRequestedUrlList().containsAll(mock.getMockPartialContentIds()));
        log.info("All entries are deleted");
      }));
  }

  @Test
  public void dataMigrationServerStatusSuccessExistentEntryWithAndWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    dataMigrationHelper.createDatabaseEntry(vertx, context, samlConfiguration);

    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), false)
      .onComplete(context.asyncAssertSuccess(result ->
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty())));

    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), true)
      .onComplete(context.asyncAssertSuccess(result ->
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty())));
  }

  @Test
  public void dataMigrationServerStatusSuccess2ExistentEntriesWithAndWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    dataMigrationHelper.createDatabaseEntry(vertx, context, samlConfiguration);

    mock.setMockContent("mock_example_entries.json");
    SamlConfiguration samlConfigurationAdditional = mock.getMockPartialContent();
    dataMigrationHelper.createDatabaseEntry(vertx, context, samlConfigurationAdditional);

    mock.setMockContent("mock_content_with_delete.json");
    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), false)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Migration: Number of records are not unique. Instead the number is : 2"))));

    configurationsDao.dataMigration(vertx, dataMigrationHelper.getOkapiHeaders(), true)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Migration: Number of records are not unique. Instead the number is : 2"))));
   }
}
